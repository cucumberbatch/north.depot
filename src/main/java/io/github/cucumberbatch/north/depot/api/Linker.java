package io.github.cucumberbatch.north.depot.api;


public interface Linker<E, C> {

    void linkTo(E entity, C component);

    /**
     * Returns the component class/type this linker provides work with.
     *
     * <p>This is the exact {@link Class} passed to
     * {@link Spawn#linker(Class)} and guaranteed to match the
     * components that links via {@link #linkTo(E entity, C component)}.</p>
     *
     * @return the component type class
     */
    Class<C> getComponentType();

}
