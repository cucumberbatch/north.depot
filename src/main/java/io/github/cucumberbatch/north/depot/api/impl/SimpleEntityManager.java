package io.github.cucumberbatch.north.depot.api.impl;

import io.github.cucumberbatch.north.depot.api.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

public final class SimpleEntityManager<E> implements EntityManager<E> {
    private final EntityFactory<E>    factory;
    private final ArchetypeStorage<E> archetypeStorage;
    private final ComponentStorage<E> components;

    public SimpleEntityManager(IntFunction<E> factory, ToIntFunction<E> idExtractor) {
        this.factory          = new EntityFactory<>(factory, idExtractor);
        this.archetypeStorage = new ArchetypeStorage<>();
        this.components       = new ComponentStorage<>();
    }

    @Override public Spawn<E> spawn(Class<?>... types) {
        return new BatchSpawn<>(this, archetypeStorage, new Archetype(types), factory);
    }

    @Override public View<E> view(Class<?>... types) {
        return new QueryView<>(this, archetypeStorage, new Archetype(types));
    }

    @SuppressWarnings("unchecked")
    @Override public <C> C add(E entity, C component) {
        checkEntityAndComponentNotNull(entity, component);
        
        Class<C> type = (Class<C>) component.getClass();
        Archetype archetype = archetypeStorage.getArchetype(entity);
        archetypeStorage.ensureArchetypeContains(entity, archetype, type);
        components.store(entity, type, component);
        return component;
    }

    @Override public <C> C get(E entity, Class<C> type) {
        checkEntityAndComponentTypeNotNull(entity, type);
        
        return components.get(entity, type);
    }

    @Override public <C> boolean has(E entity, Class<C> type) {
        checkEntityAndComponentTypeNotNull(entity, type);
        
        return archetypeStorage.hasComponent(entity, type);
    }

    @Override public <C> boolean remove(E entity, Class<C> type) {
        checkEntityAndComponentTypeNotNull(entity, type);
        
        Archetype archetype = archetypeStorage.getArchetype(entity);
        if (!archetype.contains(type)) return false;
        archetypeStorage.alterArchetype(entity, archetype, type);
        components.remove(entity, type);
        return true;
    }

    
    private <C> void checkEntityAndComponentNotNull(E entity, C component) {
        if (entity == null || component == null) {
            throw new NullPointerException("Provided entity and component must not be null!");
        }
    }

    private <C> void checkEntityAndComponentTypeNotNull(E entity, Class<C> type) {
        if (entity == null || type == null) {
            throw new NullPointerException("Provided entity and component type must not be null!");
        }
    }

    static final class QueryView<E> implements View<E> {
        private final SimpleEntityManager<E> manager;
        private final ArchetypeStorage<E>    storage;
        private final Archetype              archetype;

        QueryView(SimpleEntityManager<E> manager,
                  ArchetypeStorage<E>    storage,
                  Archetype              archetype) {
            this.manager   = manager;
            this.storage   = storage;
            this.archetype = archetype;
        }

        @Override public <C> Accessor<E, C> accessor(Class<C> type) {
            return accessor(type, AccessType.READ);
        }

        @Override public <C> Accessor<E, C> accessor(Class<C> type, AccessType accessType) {
            return new Accessor<>() {
                @Override public C          get(E entity)      { return manager.get(entity, type); }
                @Override public Class<C>   getComponentType() { return type; }
                @Override public AccessType getAccessType()    { return accessType; }
            };
        }

        @Override public View<E> except(Class<?>... componentTypes) {
            return new FilteredView<>(this, manager, componentTypes);
        }

        @Override public Iterator<E> iterator() { return storage.entityIterator(archetype); }
        @Override public void        close()    {}
    }

    static final class FilteredView<E> implements View<E> {
        private final View<E>                base;
        private final SimpleEntityManager<E> manager;
        private final Class<?>[]             excludedTypes;

        FilteredView(View<E>                base,
                     SimpleEntityManager<E> manager,
                     Class<?>...            componentTypes) {
            validateComponentTypes(componentTypes);
            this.base          = base;
            this.manager       = manager;
            this.excludedTypes = componentTypes.clone();
        }

        private Iterator<E> filterIterator(Iterator<E> baseIterator, Class<?>[] excludedTypesSnapshot) {
            return new Iterator<>() {
                private E       next;
                private boolean nextComputed = false;
                private boolean hasNextValue = false;

                private void computeNext() {
                    if (nextComputed) return;
                    nextComputed = true;
                    hasNextValue = false;
                    next = null;

                    while (baseIterator.hasNext()) {
                        E entity = baseIterator.next();
                        boolean excluded = false;
                        for (Class<?> excludedType : excludedTypesSnapshot) {
                            if (manager.has(entity, excludedType)) {
                                excluded = true;
                                break;
                            }
                        }
                        if (!excluded) {
                            next = entity;
                            hasNextValue = true;
                            return;
                        }
                    }
                }

                @Override
                public boolean hasNext() {
                    computeNext();
                    return hasNextValue;
                }

                @Override
                public E next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    E result = next;
                    next = null;
                    nextComputed = false;
                    hasNextValue = false;
                    return result;
                }
            };
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

        @Override public Iterator<E> iterator() { return filterIterator(base.iterator(), excludedTypes); }
        @Override public void        close()    { base.close(); }
    }

    static final class BatchSpawn<E> implements Spawn<E> {
        private final SimpleEntityManager<E> manager;
        private final ArchetypeStorage<E>    storage;
        private final EntityFactory<E>       factory;
        private final Archetype              archetype;

        BatchSpawn(SimpleEntityManager<E> manager,
                   ArchetypeStorage<E>    storage,
                   Archetype              archetype,
                   EntityFactory<E>       factory) {
            this.manager   = manager;
            this.storage   = storage;
            this.archetype = archetype;
            this.factory   = factory;
        }

        @Override public <C> Linker<E, C> linker(Class<C> type) {
            return new Linker<>() {
                @Override public void     link(E e, C c)     { manager.add(e, c); }
                @Override public Class<C> getComponentType() { return type; }
            };
        }

        @Override public Iterator<E> entities() {
            return new Iterator<>() {
                @Override public boolean hasNext() { return true; }
                @Override public E       next()    { return spawn(); }
            };
        }

        @Override public Iterator<E> entities(int count) {
            return new Iterator<>() {
                int remaining = count;
                @Override public boolean hasNext() { return remaining   > 0; }
                @Override public E       next()    {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    remaining--;
                    return spawn();
                }
            };
        }

        @Override public E spawn() {
            E entity = factory.create();
            storage.registerInArchetype(entity, archetype);
            return entity;
        }

        @Override public void close() { /* todo: deferred registration of spawned entities */ }
    }

    static final class Archetype {
        private final Set<Class<?>> types;

        private Archetype() {
            this(Set.of());
        }

        private Archetype(Class<?>[] types) {
            this(Set.of(types));
        }

        private Archetype(Set<Class<?>> types) {
            this.types = types;
        }

        private boolean contains(Class<?> type) {
            return this.types.contains(type);
        }

        private boolean contains(Archetype archetype) {
            return this.types.containsAll(archetype.types);
        }

        private Archetype add(Class<?> type) {
            Set<Class<?>> updatedTypes = new HashSet<>(types);
            updatedTypes.add(type);
            return new Archetype(updatedTypes);
        }

        private Archetype remove(Class<?> type) {
            Set<Class<?>> updatedTypes = new HashSet<>(types);
            updatedTypes.remove(type);
            return new Archetype(updatedTypes);
        }

        @Override public boolean equals(Object o) {
            return o instanceof Archetype && this.types.equals(((Archetype) o).types);
        }

        @Override public int hashCode() {
            return types.hashCode();
        }
    }

    static final class ComponentStorage<E> {
        private final Map<E, Map<Class<?>, Object>> storage = new HashMap<>();

        <T> T get(E entity, Class<T> type) {
            return type.cast(storage.getOrDefault(entity, Map.of()).get(type));
        }

        <T> void store(E entity, Class<T> type, T component) {
            storage.computeIfAbsent(entity, key -> new IdentityHashMap<>()).put(type, component);
        }

        void remove(E entity, Class<?> type) {
            // a component type might be reserved by the archetype without
            // an actual instance being stored for it
            Map<Class<?>, Object> entityComponents = storage.get(entity);
            if (entityComponents != null) {
                entityComponents.remove(type);
            }
        }
    }

    static final class ArchetypeStorage<E> {
        private static final Archetype EMPTY_ARCHETYPE = new Archetype();
        
        private final Map<E, Archetype>      entityTypes     = new HashMap<>();
        private final Map<Archetype, Set<E>> archetypeGroups = new HashMap<>();

        Archetype getArchetype(E entity) {
            return entityTypes.getOrDefault(entity, EMPTY_ARCHETYPE);
        }

        boolean hasComponent(E entity, Class<?> type) {
            Archetype archetype = entityTypes.get(entity);
            return archetype != null && archetype.contains(type);
        }

        void registerInArchetype(E entity, Archetype archetype) {
            archetypeGroups.computeIfAbsent(archetype, key -> new LinkedHashSet<>()).add(entity);
            entityTypes.put(entity, archetype);
        }

        void alterArchetype(E entity, Archetype archetype, Class<?> alteringType) {
            Archetype updatedArchetype = archetype.contains(alteringType)
                    ? archetype.remove(alteringType)
                    : archetype.add(alteringType);
            moveEntityToArchetype(entity, archetype, updatedArchetype);
        }

        void ensureArchetypeContains(E entity, Archetype archetype, Class<?> componentType) {
            // Adding a component should NOT "toggle it off" when it's already present.
            if (archetype.contains(componentType)) {
                return;
            }

            Archetype updatedArchetype = archetype.add(componentType);
            moveEntityToArchetype(entity, archetype, updatedArchetype);
        }

        private void moveEntityToArchetype(E entity, Archetype currentArchetype, Archetype updatedArchetype) {
            Set<E> currentGroup = archetypeGroups.get(currentArchetype);
            if (currentGroup != null) {
                currentGroup.remove(entity);
                if (currentGroup.isEmpty()) {
                    archetypeGroups.remove(currentArchetype);
                }
            }
            registerInArchetype(entity, updatedArchetype);
        }

        Iterator<E> entityIterator(Archetype archetype) {
            List<Set<E>> matchingGroups = new ArrayList<>();
            for (Map.Entry<Archetype, Set<E>> entry : archetypeGroups.entrySet()) {
                if (entry.getKey().contains(archetype)) {
                    matchingGroups.add(entry.getValue());
                }
            }

            if (matchingGroups.isEmpty()) {
                return Collections.emptyIterator();
            }

            return new Iterator<>() {
                private final Iterator<Set<E>> groupIterator = matchingGroups.iterator();
                private Iterator<E>            current       = Collections.emptyIterator();

                @Override
                public boolean hasNext() {
                    while (!current.hasNext() && groupIterator.hasNext()) {
                        current = groupIterator.next().iterator();
                    }
                    return current.hasNext();
                }

                @Override
                public E next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return current.next();
                }
            };
        }

    }

    static final class EntityFactory<E> {
        private final IntFunction<E>   factory;
        private final ToIntFunction<E> idExtractor;
        private int nextId;

        EntityFactory(IntFunction<E>   factory,
                      ToIntFunction<E> idExtractor) {
            this.factory     = factory;
            this.idExtractor = idExtractor;
        }

        E   create()        { return factory.apply(nextId++); }
        int getId(E entity) { return idExtractor.applyAsInt(entity); }
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
