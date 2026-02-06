package io.github.cucumberbatch.north.depot.api;


import java.util.Iterator;

public interface Spawn<E> extends Batch<E> {

    /**
     * Creates a linker object that provides convenient interface for
     * linking component instances with created entities
     * @param componentType
     * @return
     * @param <C>
     */
    <C> Linker<E, C> linker(Class<C> componentType);

    /**
     * Returns an infinite iterator over new entities.
     * The iterator will generate entities until the user-defined loop-break condition occurred
     * @return an infinite iterator over new entities
     */
    Iterator<E> entities();

    /**
     * Returns a finite iterator over new entities.
     * @param count specifies how many entities needs to create
     * @return a finite iterator over new entities
     */
    Iterator<E> entities(int count);

    /**
     * Create a single entity and return a reference to it
     * @return a reference to new entity
     */
    E spawn();
}
