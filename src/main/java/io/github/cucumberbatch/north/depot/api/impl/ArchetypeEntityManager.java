package io.github.cucumberbatch.north.depot.api.impl;

import io.github.cucumberbatch.north.depot.api.*;

import java.util.*;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/**
 * An archetype-based implementation of the {@link EntityManager} API.
 *
 * <p>Entities are stored in tables, and every table keeps only the entities which
 * share the exact same set of component types (an {@link Archetype}). Component
 * instances are stored in columnar chunks, so iterating over the entities of a
 * single archetype gives a cache-friendly access to the component data.</p>
 *
 * <p>The entity type is fully decoupled from the manager: the user provides a
 * factory which creates an entity instance for a given id, and an id extractor
 * which returns the id of a given entity instance. The manager assigns the ids
 * itself, so the id extractor must return the very same id which was passed
 * to the factory.</p>
 *
 * <p>Adding or removing a component of an entity moves that entity to another
 * table (an archetype migration), all the other components of the entity are
 * carried over to the new table.</p>
 *
 * @param <E> a type of the entities handled by this manager
 */
public final class ArchetypeEntityManager<E> implements EntityManager<E> {
    private static final int DEFAULT_CHUNK_CAPACITY = 256;

    final ComponentRegistry       registry;
    final List<Table<E>>          tables;
    final Map<Archetype, Table<E>> tablesByArchetype;
    final List<EntityLocation<E>> locations;
    final IntFunction<E>          factory;
    final ToIntFunction<E>        idExtractor;
    final int                     chunkCapacity;

    private int sequence;

    public ArchetypeEntityManager(IntFunction<E>   factory,
                                  ToIntFunction<E> idExtractor) {
        this(factory, idExtractor, Collections.emptySet());
    }

    public ArchetypeEntityManager(IntFunction<E>   factory,
                                  ToIntFunction<E> idExtractor,
                                  Set<Class<?>>    componentTypeSet) {
        this(factory, idExtractor, componentTypeSet, DEFAULT_CHUNK_CAPACITY);
    }

    public ArchetypeEntityManager(IntFunction<E>   factory,
                                  ToIntFunction<E> idExtractor,
                                  Set<Class<?>>    componentTypeSet,
                                  int              chunkCapacity) {
        this.registry      = new ComponentRegistry(componentTypeSet);
        this.tables        = new ArrayList<>();
        this.tablesByArchetype = new HashMap<>();
        this.locations     = new ArrayList<>();
        this.factory       = factory;
        this.idExtractor   = idExtractor;
        this.chunkCapacity = chunkCapacity;
        this.sequence      = 0;
    }

    @Override
    public Spawn<E> spawn(Class<?>... componentTypes) {
        return new SpawnImpl<>(this, componentTypes);
    }

    @Override
    public View<E> view(Class<?>... componentTypes) {
        return new ViewImpl<>(this, componentTypes);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C> C add(E entity, C component) {
        checkEntityAndComponentNotNull(entity, component);

        Class<C> componentType = (Class<C>) component.getClass();
        int       typeId       = registry.getTypeId(componentType);

        EntityLocation<E> location = getEntityLocation(entity);

        // the component is already a part of the archetype of the entity,
        // so it is enough to replace the stored instance
        if (location.table.archetype.contains(componentType)) {
            location.table.setComponent(location, typeId, component);
            return component;
        }

        // otherwise the entity must be moved to another table,
        // where its archetype contains the new component type
        Table<E> target = findOrCreateTableOfExactArchetype(
                registry.createArchetype(location.table.archetype.withComponent(componentType)));
        migrate(location, target);
        target.setComponent(location, typeId, component);

        return component;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C> C get(E entity, Class<C> componentType) {
        checkEntityAndComponentTypeNotNull(entity, componentType);

        EntityLocation<E> location = getEntityLocation(entity);
        int               typeId   = registry.getTypeId(componentType);
        return (C) location.table.getComponent(location, typeId);
    }

    @Override
    public <C> boolean has(E entity, Class<C> componentType) {
        checkEntityAndComponentTypeNotNull(entity, componentType);

        EntityLocation<E> location = getEntityLocation(entity);
        return location.table.archetype.contains(componentType);
    }

    @Override
    public <C> boolean remove(E entity, Class<C> componentType) {
        checkEntityAndComponentTypeNotNull(entity, componentType);

        EntityLocation<E> location = getEntityLocation(entity);

        if (!location.table.archetype.contains(componentType)) {
            return false;
        }

        Table<E> target = findOrCreateTableOfExactArchetype(
                registry.createArchetype(location.table.archetype.withoutComponent(componentType)));
        migrate(location, target);

        return true;
    }

    private <C> void checkEntityAndComponentNotNull(E entity, C component) {
        if (entity == null || component == null) {
            throw new NullPointerException("Provided entity and component must not be null!");
        }
    }

    private <C> void checkEntityAndComponentTypeNotNull(E entity, Class<C> componentType) {
        if (entity == null || componentType == null) {
            throw new NullPointerException("Provided entity and component type must not be null!");
        }
    }

    private EntityLocation<E> getEntityLocation(E entity) {
        if (entity == null) {
            throw new NullPointerException("Provided entity must not be null!");
        }

        int entityId = idExtractor.applyAsInt(entity);
        if (entityId < 0 || entityId >= locations.size()) {
            throw new IllegalArgumentException("The given entity is not managed by this entity manager!");
        }

        EntityLocation<E> location = locations.get(entityId);
        if (location == null) {
            throw new IllegalArgumentException("The given entity is not managed by this entity manager!");
        }
        return location;
    }

    /**
     * Moves an entity to the given table, carrying over all the components
     * which are present in both archetypes.
     */
    private void migrate(EntityLocation<E> location, Table<E> target) {
        Table<E>    source          = location.table;
        Archetype   sourceArchetype = source.archetype;
        Object[]    values          = new Object[sourceArchetype.componentsCount];

        for (int column = 0; column < sourceArchetype.componentsCount; column++) {
            values[column] = source.getComponentByColumn(location, column);
        }

        source.removeEntity(location);
        target.addEntity(location.entityId, location);

        for (int column = 0; column < sourceArchetype.componentsCount; column++) {
            if (values[column] == null) {
                continue;
            }
            int targetColumn = target.componentIndex(sourceArchetype.componentTypeIds[column]);
            if (targetColumn < 0) {
                continue;
            }
            target.setComponentByColumn(location, targetColumn, values[column]);
        }
    }

    private List<Table<E>> collectTablesContainingArchetype(Archetype archetype) {
        List<Table<E>> foundTables = new ArrayList<>();
        for (Table<E> table : tables) {
            if (table.archetype.contains(archetype)) {
                foundTables.add(table);
            }
        }
        return foundTables;
    }

    private Table<E> findOrCreateTableOfExactArchetype(Archetype archetype) {
        // we are trying to find a table with exact archetype
        Table<E> table = tablesByArchetype.get(archetype);
        if (table != null) {
            return table;
        }

        // otherwise, creating a new one
        table = new Table<>(archetype, chunkCapacity);
        tables.add(table);
        tablesByArchetype.put(archetype, table);
        return table;
    }

    EntityLocation<E> createEntity(Table<E> table) {
        int id = sequence++;
        E entity = factory.apply(id);

        int entityId = idExtractor.applyAsInt(entity);

        EntityLocation<E> location = new EntityLocation<>();
        location.entity    = entity;
        location.entityId  = entityId;

        table.addEntity(entityId, location);
        registerLocation(entityId, location);

        return location;
    }

    private void registerLocation(int entityId, EntityLocation<E> location) {
        while (locations.size() <= entityId) {
            locations.add(null);
        }
        locations.set(entityId, location);
    }

    private static class EntityLocation<E> {
        E        entity;
        Table<E> table;
        int      entityId;
        int      chunkIndex;
        int      slotIndex;
    }

    @SuppressWarnings("unchecked")
    private static class Table<E> {
        final Archetype      archetype;
        final List<Chunk<E>> chunks;
        final int            componentsCount;
        final int            chunkCapacity;
        final int[]          componentIdToIndex;

        Table(Archetype archetype, int chunkCapacity) {
            this.archetype          = archetype;
            this.chunks             = new ArrayList<>();
            this.componentsCount    = archetype.componentsCount;
            this.chunkCapacity      = chunkCapacity;
            this.componentIdToIndex = archetype.generateMapping();

            chunks.add(new Chunk<>(chunkCapacity, componentsCount));
        }

        /**
         * Returns the index of a column which keeps the components of the given
         * type id, or {@code -1} if this table has no such column.
         */
        int componentIndex(int componentId) {
            if (componentId < 0 || componentId >= componentIdToIndex.length) {
                return -1;
            }
            return componentIdToIndex[componentId];
        }

        void addEntity(int entityId, EntityLocation<E> location) {
            int chunkIndex = chunks.size() - 1;
            Chunk<E> chunk = chunks.get(chunkIndex);

            if (chunk.size >= chunkCapacity) {
                chunk = new Chunk<>(chunkCapacity, componentsCount);
                chunks.add(chunk);
                chunkIndex++;
            }

            int slotIndex = chunk.size;

            location.table      = this;
            location.chunkIndex = chunkIndex;
            location.slotIndex  = slotIndex;

            chunk.entityIdColumn[slotIndex] = entityId;
            chunk.locationColumn[slotIndex] = location;
            chunk.size++;
        }

        void removeEntity(EntityLocation<E> locationToRemove) {
            Chunk<E> chunk       = chunks.get(locationToRemove.chunkIndex);
            int removalSlotIndex = locationToRemove.slotIndex;
            int lastSlotIndex    = chunk.size - 1;

            if (removalSlotIndex != lastSlotIndex) {
                EntityLocation<E> movedLocation = chunk.locationColumn[lastSlotIndex];

                chunk.locationColumn[removalSlotIndex] = movedLocation;
                chunk.entityIdColumn[removalSlotIndex] = chunk.entityIdColumn[lastSlotIndex];

                // the moved entity now lives in another slot of this chunk,
                // so its location must be updated as well
                if (movedLocation != null) {
                    movedLocation.slotIndex = removalSlotIndex;
                }

                for (int componentIndex = 0; componentIndex < componentsCount; componentIndex++) {
                    chunk.componentTable[componentIndex][removalSlotIndex] =
                            chunk.componentTable[componentIndex][lastSlotIndex];
                }
            }

            chunk.locationColumn[lastSlotIndex] = null;
            chunk.entityIdColumn[lastSlotIndex] =   -1;

            chunk.size--;
        }

        void setComponent(EntityLocation<E> location, int componentId, Object component) {
            int componentIndex = componentIndex(componentId);
            if (componentIndex < 0) {
                throw new IllegalStateException("The archetype of this table has no components of the requested type!");
            }
            setComponentByColumn(location, componentIndex, component);
        }

        void setComponentByColumn(EntityLocation<E> location, int componentIndex, Object component) {
            Chunk<E> chunk = chunks.get(location.chunkIndex);
            chunk.componentTable[componentIndex][location.slotIndex] = component;
        }

        Object getComponent(EntityLocation<E> location, int componentId) {
            int componentIndex = componentIndex(componentId);
            if (componentIndex < 0) {
                return null;
            }
            return getComponentByColumn(location, componentIndex);
        }

        Object getComponentByColumn(EntityLocation<E> location, int componentIndex) {
            Chunk<E> chunk = chunks.get(location.chunkIndex);
            return chunk.componentTable[componentIndex][location.slotIndex];
        }

        Iterator<Chunk<E>> chunkIterator() {
            return chunks.iterator();
        }
    }

    @SuppressWarnings("unchecked")
    private static class Chunk<E> {
        final int           capacity;
        int                 size;
        int[]               entityIdColumn;
        EntityLocation<E>[] locationColumn;
        Object[][]          componentTable;


        Chunk(int capacity, int typeCount) {
            this.size            = 0;
            this.capacity        = capacity;
            this.entityIdColumn  = new int[capacity];
            this.locationColumn  = new EntityLocation[capacity];
            this.componentTable  = new Object[typeCount][capacity];

            Arrays.fill(this.entityIdColumn, -1);
        }
    }

    private static class ComponentRegistry {
        private final Map<Class<?>, Integer>     typeToId;
        private final Map<List<Class<?>>, Archetype> archetypes;
        private int                              sequence;

        ComponentRegistry(Collection<Class<?>> componentTypes) {
            this.sequence  = 0;
            this.typeToId  = new IdentityHashMap<>();
            this.archetypes = new HashMap<>();
            if (componentTypes != null) {
                for (Class<?> type : componentTypes) {
                    if (type != null) {
                        getTypeId(type);
                    }
                }
            }
        }

        int getTypeId(Class<?> componentType) {
            if (componentType == null) {
                throw new NullPointerException("Provided component type must not be null!");
            }
            Integer typeId = typeToId.get(componentType);
            if (typeId == null) {
                typeId = sequence++;
                typeToId.put(componentType, typeId);
            }
            return typeId;
        }

        int typeCount() {
            return sequence;
        }

        Archetype createArchetype(Class<?>... types) {
            final Class<?>[] componentTypes = types == null ? new Class<?>[0] : types;

            for (Class<?> type : componentTypes) {
                if (type == null) {
                    throw new NullPointerException("Provided component types must not contain null!");
                }
            }

            // the archetypes are cached, so that the repeated queries (a view
            // or a spawn of the very same component types) do not rebuild
            // the layout of the component columns again and again
            return archetypes.computeIfAbsent(List.of(componentTypes), key -> buildArchetype(componentTypes));
        }

        private Archetype buildArchetype(Class<?>[] types) {
            // the component types are ordered by their type ids,
            // so that the very same set of types always produces
            // the very same layout of the component columns
            Map<Integer, Class<?>> typesById = new TreeMap<>();

            for (Class<?> type : types) {
                typesById.put(getTypeId(type), type);
            }

            int[]       typeIds        = new int[typesById.size()];
            Class<?>[]  componentTypes = new Class<?>[typesById.size()];

            int index = 0;
            for (Map.Entry<Integer, Class<?>> entry : typesById.entrySet()) {
                typeIds[index]        = entry.getKey();
                componentTypes[index] = entry.getValue();
                index++;
            }

            return new Archetype(componentTypes, typeIds, typeCount());
        }
    }

    private static class Archetype {
        final Class<?>[]   componentTypes;
        final int[]        componentTypeIds;
        final BitSet       mask;
        final int          componentsCount;

        Archetype(Class<?>[] componentTypes, int[] componentTypeIds, int typeCount) {
            this.componentTypes   = componentTypes;
            this.componentTypeIds = componentTypeIds;
            this.componentsCount  = componentTypes.length;

            this.mask = new BitSet(typeCount);
            for (int typeId : componentTypeIds) {
                mask.set(typeId);
            }
        }

        public int length() {
            return componentTypes.length;
        }

        /**
         * Builds a mapping between the global component type ids and the
         * indexes of the component columns of this archetype.
         */
        public int[] generateMapping() {
            int maxTypeId = -1;
            for (int typeId : componentTypeIds) {
                maxTypeId = Math.max(maxTypeId, typeId);
            }

            int[] mappingArray = new int[maxTypeId + 1];
            Arrays.fill(mappingArray, -1);

            for (int column = 0; column < componentTypeIds.length; column++) {
                mappingArray[componentTypeIds[column]] = column;
            }
            return mappingArray;
        }

        public boolean isSame(Archetype archetype) {
            return mask.equals(archetype.mask);
        }

        /**
         * Checks that this archetype contains all the component types
         * of the given archetype.
         */
        public boolean contains(Archetype archetype) {
            for (int typeId : archetype.componentTypeIds) {
                if (!mask.get(typeId)) {
                    return false;
                }
            }
            return true;
        }

        public boolean contains(Class<?> type) {
            for (Class<?> componentType : componentTypes) {
                if (componentType == type) {
                    return true;
                }
            }
            return false;
        }

        public Class<?>[] withComponent(Class<?> type) {
            Class<?>[] updatedTypes = Arrays.copyOf(componentTypes, componentTypes.length + 1);
            updatedTypes[componentTypes.length] = type;
            return updatedTypes;
        }

        public Class<?>[] withoutComponent(Class<?> type) {
            Class<?>[] updatedTypes = new Class<?>[Math.max(0, componentTypes.length - 1)];
            int index = 0;
            for (Class<?> componentType : componentTypes) {
                if (componentType != type) {
                    updatedTypes[index++] = componentType;
                }
            }
            return updatedTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Archetype archetype = (Archetype) o;
            return mask.equals(archetype.mask);
        }

        @Override
        public int hashCode() {
            return mask.hashCode();
        }

        @Override
        public String toString() {
            return "Archetype{" +
                    "componentTypes=" + Arrays.toString(componentTypes) +
                    ", mask=" + mask +
                    '}';
        }
    }

    @SuppressWarnings("unchecked")
    private static class SpawnImpl<E> implements Spawn<E> {
        private final ArchetypeEntityManager<E> manager;
        private final ComponentRegistry         registry;
        private final Archetype                 archetype;
        private final Table<E>                  table;

        SpawnImpl(ArchetypeEntityManager<E> manager, Class<?>[] types) {
            this.manager   = manager;
            this.registry  = manager.registry;
            this.archetype = registry.createArchetype(types);
            this.table     = manager.findOrCreateTableOfExactArchetype(archetype);
        }

        @Override
        public <C> Linker<E, C> linker(Class<C> type) {
            if (type == null) {
                throw new NullPointerException("Provided component type must not be null!");
            }

            final int componentId = registry.getTypeId(type);

            return new Linker<E, C>() {
                @Override
                public void link(E entity, C component) {
                    if (entity == null || component == null) {
                        throw new NullPointerException("Provided entity and component must not be null!");
                    }

                    EntityLocation<E> location    = manager.getEntityLocation(entity);
                    Table<E>          currentTable = location.table;

                    // the archetype of the entity already contains the requested
                    // component type, so the component instance is just stored
                    // in the column of the current table
                    if (currentTable.archetype.contains(type)) {
                        currentTable.setComponent(location, componentId, component);
                        return;
                    }

                    // otherwise the entity must be moved to another table,
                    // where its archetype contains the linked component type
                    Table<E> target = manager.findOrCreateTableOfExactArchetype(
                            registry.createArchetype(currentTable.archetype.withComponent(type)));
                    manager.migrate(location, target);
                    target.setComponent(location, componentId, component);
                }

                @Override
                public Class<C> getComponentType() {
                    return type;
                }
            };
        }

        @Override
        public Iterator<E> entities() {
            return new Iterator<>() {
                @Override public boolean hasNext() { return true; }
                @Override public E       next()    { return spawn(); }
            };
        }

        @Override
        public Iterator<E> entities(int count) {
            return new Iterator<>() {
                private int remains = count;

                @Override public boolean hasNext() { return remains > 0; }

                @Override public E next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    remains--;
                    return spawn();
                }
            };
        }

        @Override
        public E spawn() {
            EntityLocation<E> location = manager.createEntity(table);
            return location.entity;
        }

        @Override
        public void close() {
            // the created entities are placed into their tables immediately,
            // so there is nothing to insert or to flush here
        }
    }

    @SuppressWarnings("unchecked")
    private static class ViewImpl<E> implements View<E> {
        private final ArchetypeEntityManager<E> manager;
        private final ComponentRegistry         registry;
        private final Archetype                 archetype;

        ViewImpl(ArchetypeEntityManager<E> manager, Class<?>[] types) {
            this.manager   = manager;
            this.registry  = manager.registry;
            this.archetype = registry.createArchetype(types);
        }

        @Override
        public <C> Accessor<E, C> accessor(Class<C> componentType) {
            return accessor(componentType, AccessType.READ);
        }

        @Override
        public <C> Accessor<E, C> accessor(Class<C> componentType, AccessType accessType) {
            if (componentType == null) {
                throw new NullPointerException("Provided component type must not be null!");
            }

            final int typeId = registry.getTypeId(componentType);

            return new Accessor<E, C>() {
                @Override
                public C get(E entity) {
                    if (entity == null) {
                        throw new NullPointerException("Provided entity must not be null!");
                    }
                    EntityLocation<E> location = manager.getEntityLocation(entity);
                    return (C) location.table.getComponent(location, typeId);
                }

                @Override
                public Class<C> getComponentType() {
                    return componentType;
                }

                @Override
                public AccessType getAccessType() {
                    return accessType;
                }
            };
        }

        @Override
        public View<E> except(Class<?>... componentTypes) {
            return new FilteredView<>(this, manager, componentTypes);
        }

        @Override
        public Iterator<E> iterator() {
            // the tables are collected on every iteration, so a view always
            // sees the entities which are currently placed in the manager
            return new EntityIterator<>(manager.collectTablesContainingArchetype(archetype));
        }

        @Override
        public void close() {
            // No resources to close
        }
    }

    private static class FilteredView<E> implements View<E> {
        private final View<E>                base;
        private final ArchetypeEntityManager<E> manager;
        private final Class<?>[]             excludedTypes;

        FilteredView(View<E>                base,
                     ArchetypeEntityManager<E> manager,
                     Class<?>...            componentTypes) {
            validateComponentTypes(componentTypes);
            this.base          = base;
            this.manager       = manager;
            this.excludedTypes = componentTypes.clone();
        }

        @Override public <C> Accessor<E, C> accessor(Class<C> componentType) {
            return base.accessor(componentType);
        }

        @Override public <C> Accessor<E, C> accessor(Class<C> componentType, AccessType accessType) {
            return base.accessor(componentType, accessType);
        }

        @Override public View<E> except(Class<?>... componentTypes) {
            return new FilteredView<>(this, manager, componentTypes);
        }

        @Override public Iterator<E> iterator() {
            return new FilteringIterator<>(base.iterator(), manager, excludedTypes);
        }

        @Override public void close() { base.close(); }
    }

    private static class EntityIterator<E> implements Iterator<E> {
        private final Iterator<Table<E>> tableIterator;

        private Iterator<Chunk<E>> chunkIterator = Collections.emptyIterator();
        private Chunk<E>            currentChunk;
        private int                 currentSlot = -1;
        private E                   nextEntity;
        private boolean             nextComputed;

        EntityIterator(List<Table<E>> tables) {
            this.tableIterator = tables.iterator();
        }

        @Override
        public boolean hasNext() {
            if (!nextComputed) {
                nextEntity   = advance();
                nextComputed = true;
            }
            return nextEntity != null;
        }

        @Override
        public E next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            E result      = nextEntity;
            nextEntity   = null;
            nextComputed = false;
            return result;
        }

        private E advance() {
            while (true) {
                if (currentChunk != null && currentSlot + 1 < currentChunk.size) {
                    currentSlot++;
                    EntityLocation<E> location = currentChunk.locationColumn[currentSlot];
                    if (location != null) {
                        return location.entity;
                    }
                    continue;
                }

                if (chunkIterator.hasNext()) {
                    currentChunk = chunkIterator.next();
                    currentSlot  = -1;
                    continue;
                }

                if (tableIterator.hasNext()) {
                    Table<E> table = tableIterator.next();
                    chunkIterator  = table.chunkIterator();
                    currentChunk   = null;
                    currentSlot    = -1;
                    continue;
                }

                return null;
            }
        }
    }

    private static class FilteringIterator<E> implements Iterator<E> {
        private final Iterator<E>            baseIterator;
        private final ArchetypeEntityManager<E> manager;
        private final Class<?>[]             excludedTypes;

        private E       nextEntity;
        private boolean nextComputed;

        FilteringIterator(Iterator<E>            baseIterator,
                          ArchetypeEntityManager<E> manager,
                          Class<?>[]             excludedTypes) {
            this.baseIterator  = baseIterator;
            this.manager       = manager;
            this.excludedTypes = excludedTypes;
        }

        @Override
        public boolean hasNext() {
            if (!nextComputed) {
                nextEntity   = advance();
                nextComputed = true;
            }
            return nextEntity != null;
        }

        @Override
        public E next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            E result      = nextEntity;
            nextEntity   = null;
            nextComputed = false;
            return result;
        }

        private E advance() {
            while (baseIterator.hasNext()) {
                E entity = baseIterator.next();

                boolean excluded = false;
                for (Class<?> excludedType : excludedTypes) {
                    if (manager.has(entity, excludedType)) {
                        excluded = true;
                        break;
                    }
                }

                if (!excluded) {
                    return entity;
                }
            }
            return null;
        }
    }

    private static void validateComponentTypes(Class<?>... componentTypes) {
        if (componentTypes == null) {
            throw new NullPointerException("Provided componentTypes must not be null!");
        }
        for (Class<?> type : componentTypes) {
            if (type == null) {
                throw new NullPointerException("Provided componentTypes must not contain null elements!");
            }
        }
    }
}
