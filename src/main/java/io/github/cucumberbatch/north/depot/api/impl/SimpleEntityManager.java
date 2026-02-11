package io.github.cucumberbatch.north.depot.api.impl;

import io.github.cucumberbatch.north.depot.api.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

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
        archetypeStorage.alterArchetype(entity, archetype, type);
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

    
    private <E, C> void checkEntityAndComponentNotNull(E entity, C component) {
        if (entity == null || component == null) {
            throw new NullPointerException("Provided entity and component must not be null!");
        }
    }

    private <E, C> void checkEntityAndComponentTypeNotNull(E entity, Class<C> type) {
        if (entity == null || type == null) {
            throw new NullPointerException("Provided entity and component type must not be null!");
        }
    }

    static final class QueryView<E> implements View<E> {
        private final SimpleEntityManager<E> manager;
        private final Iterator<E>            entityIterator;

        QueryView(SimpleEntityManager<E> manager,
                  ArchetypeStorage<E>    storage,
                  Archetype              archetype) {
            this.manager        = manager;
            this.entityIterator = storage.entityIterator(archetype);
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

        @Override public Iterator<E> iterator() { return entityIterator; }
        @Override public void        close()    {}
    }

    static final class BatchSpawn<E> implements Spawn<E> {
        private final SimpleEntityManager<E> manager;
        private final ArchetypeStorage<E>    storage;
        private final EntityFactory<E>       factory;
        private final Archetype              archetype;
        private final List<E>                batch;

        BatchSpawn(SimpleEntityManager<E> manager,
                   ArchetypeStorage<E>    storage,
                   Archetype              archetype,
                   EntityFactory<E>       factory) {
            this.manager   = manager;
            this.storage   = storage;
            this.archetype = archetype;
            this.factory   = factory;
            this.batch     = new ArrayList<>();
        }

        @Override public <C> Linker<E, C> linker(Class<C> type) {
            return new Linker<>() {
                @Override public void     linkTo(E e, C c)   { manager.add(e, c); }
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
                @Override public E       next()    { return remaining-- > 0 ? spawn() : null; }
            };
        }

        @Override public E spawn() {
            E entity = factory.create();
            storage.registerInArchetype(entity, archetype);
            batch.add(entity);
            return entity;
        }

        @Override public void close() { batch.clear(); }
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
            storage.get(entity).remove(type);
        }
    }

    static final class ArchetypeStorage<E> {
        private final Map<E, Archetype>       entityTypes     = new HashMap<>();
        private final Map<Archetype, List<E>> archetypeGroups = new HashMap<>();

        Archetype getArchetype(E entity) {
            return entityTypes.getOrDefault(entity, new Archetype());
        }

        boolean hasComponent(E entity, Class<?> type) {
            return entityTypes.get(entity).contains(type);
        }

        void registerInArchetype(E entity, Archetype archetype) {
            archetypeGroups.computeIfAbsent(archetype, key -> new ArrayList<>()).add(entity);
            entityTypes.put(entity, archetype);
        }

        void alterArchetype(E entity, Archetype archetype, Class<?> alteringType) {
            Archetype updatedArchetype = archetype.contains(alteringType)
                    ? archetype.remove(alteringType)
                    : archetype.add(alteringType);

            archetypeGroups.computeIfPresent(archetype, (key, entities) -> {
                entities.remove(entity);
                return entities;
            });
            
            registerInArchetype(entity, updatedArchetype);
        }

        Iterator<E> entityIterator(Archetype archetype) {
            return archetypeGroups.entrySet().stream()
                    .filter(entry -> entry.getKey().contains(archetype))
                    .flatMap(entry -> entry.getValue().stream())
                    .iterator();
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
}
