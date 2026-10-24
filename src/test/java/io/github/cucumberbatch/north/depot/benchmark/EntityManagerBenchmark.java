package io.github.cucumberbatch.north.depot.benchmark;

import io.github.cucumberbatch.north.depot.api.Accessor;
import io.github.cucumberbatch.north.depot.api.EntityManager;
import io.github.cucumberbatch.north.depot.api.Linker;
import io.github.cucumberbatch.north.depot.api.Spawn;
import io.github.cucumberbatch.north.depot.api.View;
import io.github.cucumberbatch.north.depot.api.impl.ArchetypeEntityManager;
import io.github.cucumberbatch.north.depot.api.impl.SimpleEntityManager;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Compares the performance of the {@link SimpleEntityManager} (a plain
 * hash-map based storage) and the {@link ArchetypeEntityManager} (a columnar,
 * archetype based storage).
 *
 * <p>Every benchmark is executed for both implementations, so the results can be
 * compared side by side. The entity type is an {@link Integer}, which means that
 * the id of an entity is the entity itself.</p>
 *
 * <p>Run with:</p>
 * <pre>{@code
 *   mvn -Pbenchmarks test
 *   mvn -Pbenchmarks test -Djmh.args="-f 2 -i 10 EntityManagerBenchmark"
 * }</pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = {"-Xms1g", "-Xmx1g"})
public class EntityManagerBenchmark {

    /**
     * A component which is used by the benchmarks.
     */
    public static final class Position {
        public float x, y;
    }

    /**
     * A component which is used by the benchmarks.
     */
    public static final class Velocity {
        public float dx, dy;
    }

    /**
     * A manager which is filled with the entities of the benchmark.
     */
    @State(Scope.Benchmark)
    public static class PopulatedState {
        @Param({"SIMPLE", "ARCHETYPE"})
        public String implementation;

        @Param({"1000", "10000"})
        public int entityCount;

        EntityManager<Integer> manager;
        View<Integer>          preparedView;
        Integer[]              entities;
        Integer[]              shuffledEntities;
        Velocity               velocity;
        int                    cursor;

        @Setup(Level.Trial)
        public void setUp() {
            manager = createManager(implementation);
            entities = new Integer[entityCount];
            velocity = new Velocity();

            try (Spawn<Integer> spawn = manager.spawn(Position.class, Velocity.class)) {
                Linker<Integer, Position> positions = spawn.linker(Position.class);
                Linker<Integer, Velocity> velocities = spawn.linker(Velocity.class);

                for (int i = 0; i < entityCount; i++) {
                    Integer entity = spawn.spawn();
                    entities[i] = entity;

                    Position position = new Position();
                    position.x = i + 1f;
                    position.y = i + 1f;
                    positions.link(entity, position);

                    Velocity spawnedVelocity = new Velocity();
                    spawnedVelocity.dx = 1f;
                    spawnedVelocity.dy = 1f;
                    velocities.link(entity, spawnedVelocity);
                }
            }

            preparedView = manager.view(Position.class, Velocity.class);

            // a random access order, so that the benchmarks are not
            // benefited by the sequential access to the entities
            shuffledEntities = entities.clone();
            Random random = new Random(42);
            for (int i = shuffledEntities.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                Integer tmp = shuffledEntities[i];
                shuffledEntities[i] = shuffledEntities[j];
                shuffledEntities[j] = tmp;
            }
        }
    }

    /**
     * A manager whose entities have no Velocity component, so that every
     * addition of that component is a real change of the archetype.
     */
    @State(Scope.Benchmark)
    public static class MigrationState {

        @Param({"SIMPLE", "ARCHETYPE"})
        public String implementation;

        @Param({"1000", "10000"})
        public int entityCount;

        EntityManager<Integer> manager;
        Integer[]              entities;
        Velocity               velocity;
        int                    cursor;

        @Setup(Level.Trial)
        public void setUp() {
            manager = createManager(implementation);
            entities = new Integer[entityCount];
            velocity = new Velocity();

            try (Spawn<Integer> spawn = manager.spawn(Position.class)) {
                Linker<Integer, Position> positions = spawn.linker(Position.class);

                for (int i = 0; i < entityCount; i++) {
                    Integer entity = spawn.spawn();
                    entities[i] = entity;

                    Position position = new Position();
                    position.x = i + 1f;
                    position.y = i + 1f;
                    positions.link(entity, position);
                }
            }
        }
    }

    /**
     * A manager which is empty, every benchmark invocation fills it on its own.
     */
    @State(Scope.Benchmark)
    public static class FreshState {

        @Param({"SIMPLE", "ARCHETYPE"})
        public String implementation;

        @Param({"1000"})
        public int batchSize;

        EntityManager<Integer> manager;

        @Setup(Level.Invocation)
        public void setUp() {
            manager = createManager(implementation);
        }
    }

    /**
     * A long-living manager, which is the one of a real application.
     * <p>
     * The manager is recreated from time to time, so that the state does not
     * grow infinitely. The cost of the recreation is negligible, it is
     * amortized over the thousands of the spawned entities.
     * </p>
     */
    @State(Scope.Benchmark)
    public static class WarmState {

        @Param({"SIMPLE", "ARCHETYPE"})
        public String implementation;

        @Param({"1000"})
        public int batchSize;

        EntityManager<Integer> manager;
        int                    spawned;

        @Setup(Level.Trial)
        public void setUp() {
            manager = createManager(implementation);
        }

        /**
         * Recreates the manager, once in a while, so that the number of the
         * living entities stays bounded.
         */
        void recreateIfNeeded(int spawnedEntities) {
            spawned += spawnedEntities;
            if (spawned > 65_536) {
                manager  = createManager(implementation);
                spawned = 0;
            }
        }
    }

    // ---------------------------------------------------------------------
    // spawning
    // ---------------------------------------------------------------------

    /**
     * The cost of the creation of a single entity.
     * <p>
     * The manager is recreated for every invocation, so this is the "cold"
     * path: the cost of the creation of the manager itself (the tables, the
     * chunks and the archetypes) is included here.
     * </p>
     */
    @Benchmark
    public int spawnEntity(FreshState state, Blackhole blackhole) {
        try (Spawn<Integer> spawn = state.manager.spawn()) {
            return spawn.spawn();
        }
    }

    /**
     * The cost of the creation of a single entity in a long-living manager,
     * which is the "warm" path of a real application.
     */
    @Benchmark
    public int spawnEntityWarm(WarmState state, Blackhole blackhole) {
        state.recreateIfNeeded(1);

        try (Spawn<Integer> spawn = state.manager.spawn()) {
            return spawn.spawn();
        }
    }

    /**
     * The cost of the creation of a batch of entities with two components
     * linked to every one of them.
     */
    @Benchmark
    public int spawnBatchWithComponents(FreshState state, Blackhole blackhole) {
        int checksum = 0;

        try (Spawn<Integer> spawn = state.manager.spawn(Position.class, Velocity.class)) {
            Linker<Integer, Position> positions = spawn.linker(Position.class);
            Linker<Integer, Velocity> velocities = spawn.linker(Velocity.class);

            for (int i = 0; i < state.batchSize; i++) {
                Integer entity = spawn.spawn();
                positions.link(entity, new Position());
                velocities.link(entity, new Velocity());
                checksum += entity;
            }
        }

        return checksum;
    }

    /**
     * The cost of the creation of a batch of entities with two components
     * linked to every one of them, in a long-living manager.
     */
    @Benchmark
    public int spawnBatchWithComponentsWarm(WarmState state, Blackhole blackhole) {
        state.recreateIfNeeded(state.batchSize);

        int checksum = 0;

        try (Spawn<Integer> spawn = state.manager.spawn(Position.class, Velocity.class)) {
            Linker<Integer, Position> positions = spawn.linker(Position.class);
            Linker<Integer, Velocity> velocities = spawn.linker(Velocity.class);

            for (int i = 0; i < state.batchSize; i++) {
                Integer entity = spawn.spawn();
                positions.link(entity, new Position());
                velocities.link(entity, new Velocity());
                checksum += entity;
            }
        }

        return checksum;
    }

    // ---------------------------------------------------------------------
    // reading
    // ---------------------------------------------------------------------

    /**
     * The cost of the iteration over a view, including the creation of the
     * view itself.
     */
    @Benchmark
    public long iterateView(PopulatedState state, Blackhole blackhole) {
        long checksum = 0;

        try (View<Integer> view = state.manager.view(Position.class, Velocity.class)) {
            Accessor<Integer, Position> positions = view.accessor(Position.class);

            for (Integer entity : view) {
                checksum += positions.get(entity).x;
            }
        }

        return checksum;
    }

    /**
     * The cost of the creation of a view itself, without any iteration.
     * The manager is long-living here, so this is the "warm" path, which is
     * the one of a game loop, where the views are created every frame.
     */
    @Benchmark
    public int createView(PopulatedState state, Blackhole blackhole) {
        try (View<Integer> view = state.manager.view(Position.class, Velocity.class)) {
            return view.hashCode();
        }
    }

    /**
     * The cost of the iteration over an already created view, without the
     * creation of the view itself.
     */
    @Benchmark
    public long iteratePreparedView(PopulatedState state, Blackhole blackhole) {
        long checksum = 0;

        for (Integer entity : state.preparedView) {
            checksum += entity;
        }

        return checksum;
    }

    /**
     * The cost of the iteration over a view with the component reads done
     * through the accessors, which is the most common way of the usage of a
     * view.
     */
    @Benchmark
    public long iterateViewWithAccessors(PopulatedState state, Blackhole blackhole) {
        long checksum = 0;

        try (View<Integer> view = state.manager.view(Position.class, Velocity.class)) {
            Accessor<Integer, Position> positions = view.accessor(Position.class);
            Accessor<Integer, Velocity> velocities = view.accessor(Velocity.class);

            for (Integer entity : view) {
                checksum += positions.get(entity).x;
                checksum += velocities.get(entity).dx;
            }
        }

        return checksum;
    }

    /**
     * The cost of the random access to a component of a known entity.
     */
    @Benchmark
    public float getComponentByEntity(PopulatedState state, Blackhole blackhole) {
        Integer entity = state.shuffledEntities[state.cursor++ % state.shuffledEntities.length];
        return state.manager.get(entity, Position.class).x;
    }

    /**
     * The cost of the check of the presence of a component of a known entity.
     */
    @Benchmark
    public boolean hasComponentByEntity(PopulatedState state, Blackhole blackhole) {
        Integer entity = state.shuffledEntities[state.cursor++ % state.shuffledEntities.length];
        return state.manager.has(entity, Position.class);
    }

    /**
     * The cost of the iteration over a filtered view.
     */
    @Benchmark
    public long iterateExceptView(PopulatedState state, Blackhole blackhole) {
        long checksum = 0;

        try (View<Integer> view = state.manager.view(Position.class).except(Velocity.class)) {
            for (Integer entity : view) {
                checksum += entity;
            }
        }

        return checksum;
    }

    // ---------------------------------------------------------------------
    // writing
    // ---------------------------------------------------------------------

    /**
     * The cost of the modification of the components of the entities of a view.
     */
    @Benchmark
    public long updateComponents(PopulatedState state, Blackhole blackhole) {
        long checksum = 0;

        try (View<Integer> view = state.manager.view(Position.class, Velocity.class)) {
            Accessor<Integer, Position> positions = view.accessor(Position.class);
            Accessor<Integer, Velocity> velocities = view.accessor(Velocity.class);

            for (Integer entity : view) {
                Position position = positions.get(entity);
                position.x += velocities.get(entity).dx;
                checksum += position.x;
            }
        }

        return checksum;
    }

    /**
     * The cost of the addition of a component to an entity, which changes the
     * archetype of that entity, plus the cost of its removal back.
     */
    @Benchmark
    public void migrateComponent(MigrationState state, Blackhole blackhole) {
        Integer entity = state.entities[state.cursor++ % state.entities.length];
        state.manager.add(entity, state.velocity);
        state.manager.remove(entity, Velocity.class);
    }

    static EntityManager<Integer> createManager(String implementation) {
        return switch (implementation) {
            case "SIMPLE"     -> new SimpleEntityManager<>(Integer::valueOf, Integer::intValue);
            case "ARCHETYPE"  -> new ArchetypeEntityManager<>(Integer::valueOf, Integer::intValue);
            default -> throw new IllegalArgumentException("Unknown implementation: " + implementation);
        };
    }
}
