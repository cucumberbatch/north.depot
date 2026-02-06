package io.github.cucumberbatch.north.depot.api;


public interface Batch<E> extends AutoCloseable {

    /**
     * Releases resources and applies any deferred modifications.
     *
     * <p>This method is called automatically when using try-with-resources. It:
     * <ul>
     *   <li>Applies all scheduled removals from {@link Accessor}s</li>
     *   <li>Releases internal chunk iteration state</li>
     *   <li>Notifies the ECS scheduler of completion (for dependency tracking)</li>
     * </ul></p>
     *
     * <p><b>Important:</b> Manual calls to {@code close()} are permitted but ensure
     * all {@link Accessor}s or {@link Linker} created from this result have also completed
     * their operations.</p>
     */
    @Override
    void close();

}
