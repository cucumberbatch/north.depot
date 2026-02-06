package io.github.cucumberbatch.north.depot.api;


public interface EntityManager<E> {

    Spawn<E> spawn(Class<?>... componentTypes);
//    void spawnWith(SpawnFunction<E> spawnFunction, Class<?>... componentTypes);

    View<E> view(Class<?>... componentTypes);
//    void forAllWith(ViewFunction<E> viewFunction, Class<?>... componentTypes);

    
    <C> C add(E entity, C component);

    /**
     * Returns a component 
     */ 
    <C> C get(E entity, Class<C> componentType);
    
    /**
     * Checks if an {@code entity} has a component of provided {@code componentType}
     * 
     * @param entity an entity reference that was previously created via
     *   {@link org.north.core.architecture.v2.ecs.Spawn#spawn} or other similar method
     * @param componentType is a class of some object (or component)
     * @return true if {@code entity} has a component of {@code componentType}, false - otherwise
     */
    <C> boolean has(E entity, Class<C> componentType);

    <C> boolean remove(E entity, Class<C> componentType);

}
