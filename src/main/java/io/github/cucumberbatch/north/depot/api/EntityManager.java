package io.github.cucumberbatch.north.depot.api;

/**
 * A general interface for managing entities of type {@code E} and their components.
 *
 */
public interface EntityManager<E> {

    /**
     * Returns a {@link Spawn} object for performing entity instantiation.
     *
     * @param componentTypes is an array of component types which must be added for spawned entities
     */
    Spawn<E> spawn(Class<?>... componentTypes);

    /**
     * Returns a {@link View} object for performing entity iteration.
     * 
     * @param componentTypes is an array of component types which must be present for viewed entities
     */
    View<E> view(Class<?>... componentTypes);

    /**
     * Attaches provided {@code component} instance to specific {@code entity}.
     * 
     * @param entity
     * @param component  
     * @return 
     * @throws NullPointerException if {@code entity} or {@code componentType} is null
     */
    <C> C add(E entity, C component);

    /**
     * Returns a component of {@code componentType} that is attached to {@code entity}.
     *
     * @param entity
     * @param componentType
     * @return 
     * @throws NullPointerException if {@code entity} or {@code componentType} is null
     */ 
    <C> C get(E entity, Class<C> componentType);
    
    /**
     * Checks if an {@code entity} has a component of provided {@code componentType}
     * 
     * @param entity an entity instance that was created via
     *   {@link org.north.core.architecture.v2.ecs.Spawn#spawn} or other similar method
     * @param componentType is a class of some object (or component)
     * @return {@code true} if {@code entity} has a component of {@code componentType},
     *   {@code false} - otherwise
     * @throws NullPointerException if {@code entity} or {@code componentType} is null
     */
    <C> boolean has(E entity, Class<C> componentType);

    /**
     * Detaches component of {@code componentType} from specific {@code entity} and
     * returns {@code true} if operation was successful, and {@code false} if such
     * component was not found for that entity. 
     *
     * @param entity an entity object
     * @param componentType a type of component that must be detached
     * @return {@code true} if component was detached successfully, {@code false} if
     *   such component was not found for that entity
     * @throws NullPointerException if {@code entity} or {@code componentType} is null
     */
    <C> boolean remove(E entity, Class<C> componentType);

}
