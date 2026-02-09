package io.github.cucumberbatch.north.depot.api;

/**
 * Specifies the type of access for components when using {@link Accessor},
 * primarily for coordinating concurrent access in multithreaded environments.
 *
 * <p>This enum determines how {@link View} and {@link Accessor} interact
 * with components during parallel system execution. It is used for scheduling queries
 * and preventing data races between systems.</p>
 *
 * <p>Typical usage:
 * <pre>{@code
 *     // Read-only movement system (can run in parallel)
 *     View<EntityId> movement  = em.view(Position.class, Velocity.class, AccessType.READ);
 *
 *     // Collision system that writes to Position (exclusive access)
 *     View<EntityId> collision = em.view(Position.class, Collider.class, AccessType.WRITE);
 * }</pre></p>
 */
public enum AccessType {
    /**
     * Read-only access. Multiple {@link Accessor}s with {@code READ} access
     * can safely access the same components concurrently from different threads.
     *
     * <p><b>Guarantees:</b> No data races during concurrent reads. Multiple reader
     * systems can execute in parallel.</p>
     *
     * <p>Suitable for: movement systems, AI decision-making, physics queries, rendering.</p>
     */
    READ,

    /**
     * Read-Write access. Only one {@link Accessor} with {@code WRITE} access
     * can operate on components at a time. Other threads (with either {@code READ}
     * or {@code WRITE} access) are blocked until completion.
     *
     * <p><b>Guarantees:</b> Exclusive access and atomicity of changes. No other
     * systems can read or write these components concurrently.</p>
     *
     * <p>Suitable for: collision resolution, damage application, state transitions.</p>
     */
    WRITE;
}
