package io.github.cucumberbatch.north.depot.api.impl;

import io.github.cucumberbatch.north.depot.api.*;

import java.util.*;
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
        return new BatchSpawn<>(this, factory, Set.of(types));
    }

    @Override public View<E> view(Class<?>... types) {
        return new QueryView<>(this, archetypeStorage, Set.of(types));
    }

    @SuppressWarnings("unchecked")
    @Override public <C> C add(E entity, C component) {
        Class<C> type = (Class<C>) component.getClass();
        Archetype oldArchetype = archetypeStorage.getArchetype(entity);
        archetypeStorage.moveToNewArchetype(entity, oldArchetype, type);
        components.store(entity, type, component);
        return component;
    }

    @Override public <C> C get(E entity, Class<C> type) {
        return components.get(entity, type);
    }

    @Override public <C> boolean has(E entity, Class<C> type) {
        return archetypeStorage.hasComponent(entity, type);
    }

    @Override public <C> boolean remove(E entity, Class<C> type) {
        Archetype archetype = archetypeStorage.getArchetype(entity);
        if (!archetype.contains(type)) return false;
        archetypeStorage.moveToNewArchetype(entity, archetype, type);
        components.remove(entity, type);
        return true;
    }

    static final class QueryView<E> implements View<E> {
        private final SimpleEntityManager<E> manager;
        private final List<E>                matchingEntities;

        QueryView(SimpleEntityManager<E> manager,
                  ArchetypeStorage<E>    storage,
                  Set<Class<?>>          required) {
            this.manager          = manager;
            this.matchingEntities = storage.getMatchingEntities(required);
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

        @Override public Iterator<E> iterator() {
            return matchingEntities.iterator();
        }

        @Override public void close() {}
    }

    static final class BatchSpawn<E> implements Spawn<E> {
        private final SimpleEntityManager<E> manager;
        private final EntityFactory<E>       factory;
        private final Set<Class<?>>          archetype;
        private final List<E>                batch;

        BatchSpawn(SimpleEntityManager<E> manager,
                   EntityFactory<E>       factory,
                   Set<Class<?>>          archetype) {
            this.manager   = manager;
            this.factory   = factory;
            this.archetype = archetype;
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
            archetype.forEach(t -> manager.archetypeStorage.entityTypes
                    .computeIfAbsent(entity, k -> new HashSet<>()).add(t));
            batch.add(entity);
            return entity;
        }

        @Override public void close() { batch.clear(); }
    }

    static final class Archetype {
        final Set<Class<?>> types;
        final int           hashCode;

        Archetype(Set<Class<?>> types) {
            this.types    = Set.copyOf(types);
            this.hashCode = types.hashCode();
        }

        boolean contains(Class<?> type) {
            return types.contains(type);
        }

        @Override public boolean equals(Object o) {
            return o instanceof Archetype && Objects.equals(types, ((Archetype) o).types);
        }

        @Override public int hashCode() {
            return hashCode;
        }
    }

    static final class ComponentStorage<E> {
        private final Map<E, Map<Class<?>, Object>> storage = new HashMap<>();

        @SuppressWarnings("unchecked")
        <T> T get(E entity, Class<T> type) {
            return (T) storage.getOrDefault(entity, Map.of()).get(type);
        }

        <T> void store(E entity, Class<T> type, T component) {
            storage.computeIfAbsent(entity, k -> new IdentityHashMap<>()).put(type, component);
        }

        void remove(E entity, Class<?> type) {
            storage.get(entity).remove(type);
        }
    }

    static final class ArchetypeStorage<E> {
        private final Map<E, Set<Class<?>>> entityTypes = new HashMap<>();
        private final Map<Set<Class<?>>, List<E>> archetypeGroups = new HashMap<>();

        Archetype getArchetype(E entity) {
            return new Archetype(entityTypes.getOrDefault(entity, Set.of()));
        }

        boolean hasComponent(E entity, Class<?> type) {
            return entityTypes.get(entity).contains(type);
        }

        void moveToNewArchetype(E entity, Archetype oldArchetype, Class<?> change) {
            Set<Class<?>> oldTypes = oldArchetype.types;
            Set<Class<?>> newTypes = change == null
                    ? removeType(oldTypes, change)
                    : addOrRemoveType(oldTypes, change);

            // Remove from old group
            Optional.ofNullable(archetypeGroups.get(oldTypes))
                    .ifPresent(entities -> entities.remove(entity));

            // Update entity types
            entityTypes.put(entity, newTypes);

            // Add to new group
            archetypeGroups.computeIfAbsent(newTypes, k -> new ArrayList<>()).add(entity);
        }

        private Set<Class<?>> addOrRemoveType(Set<Class<?>> types, Class<?> type) {
            Set<Class<?>> copy = new HashSet<>(types);
            if (copy.contains(type)) {
                copy.remove(type);
            } else {
                copy.add(type);
            }
            return copy;
        }

        private Set<Class<?>> removeType(Set<Class<?>> types, Class<?> type) {
            Set<Class<?>> copy = new HashSet<>(types);
            copy.remove(type);
            return copy.isEmpty() ? Set.of() : copy;
        }

        List<E> getMatchingEntities(Set<Class<?>> requiredTypes) {
            return archetypeGroups.entrySet().stream()
                    .filter(entry -> entry.getKey().containsAll(requiredTypes))
                    .flatMap(entry -> entry.getValue().stream())
                    .collect(Collectors.toList());
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
