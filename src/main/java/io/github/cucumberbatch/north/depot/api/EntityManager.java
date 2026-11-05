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
     * @param entity an entity instance that was created via
     *   {@link Spawn#spawn} or other similar method
     * @param component is an object (or component) instance
     * @return 
     * @throws NullPointerException if {@code entity} or {@code componentType} is null
     */
    <C> C add(E entity, C component);

    /**
     * Returns a component of {@code componentType} that is attached to {@code entity}.
     *
     * @param entity an entity instance that was created via
     *   {@link Spawn#spawn} or other similar method
     * @param componentType is a class of some object (or component)
     * @return 
     * @throws NullPointerException if {@code entity} or {@code componentType} is null
     */ 
    <C> C get(E entity, Class<C> componentType);
    
    /**
     * Checks if an {@code entity} has a component of provided {@code componentType}
     * 
     * @param entity an entity instance that was created via
     *   {@link Spawn#spawn} or other similar method
     * @param componentType is a class of some object (or component)
     * @return {@code true} if {@code entity} has a component of {@code componentType},
     *   {@code false} - otherwise
     * @throws NullPointerException if {@code entity} or {@code componentType} is null
     */
    <C> boolean has(E entity, Class<C> componentType);

    /**
     * Checks if an {@code entity} has components of all provided {@code componentTypes}.
     *
     * @param entity an entity instance that was created via {@link Spawn#spawn} or other similar method
     * @param componentTypes component types that must all be present on the entity
     * @return {@code true} if the entity has every component type from {@code componentTypes}, otherwise {@code false}
     * @throws NullPointerException if {@code entity}, {@code componentTypes}, or any element of {@code componentTypes} is null
     */
    default boolean has(E entity, Class<?>... componentTypes) {
        if (componentTypes == null) {
            throw new NullPointerException("Provided componentTypes must not be null!");
        }
        for (Class<?> componentType : componentTypes) {
            if (componentType == null) {
                throw new NullPointerException("Provided componentTypes must not contain null!");
            }
            @SuppressWarnings("rawtypes")
            Class rawType = componentType;
            if (!has(entity, rawType)) {
                return false;
            }
        }
        return true;
    }

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
