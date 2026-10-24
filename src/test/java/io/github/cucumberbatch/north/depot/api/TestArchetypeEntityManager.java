package io.github.cucumberbatch.north.depot.api;

import io.github.cucumberbatch.north.depot.api.impl.ArchetypeEntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the storage related behaviour of the {@link ArchetypeEntityManager},
 * which are not covered by the common {@link TestEntityManagerApi} contract.
 */
public class TestArchetypeEntityManager {

    private ArchetypeEntityManager<Integer> manager;

    @BeforeEach
    public void setUpEntityManager() {
        this.manager = new ArchetypeEntityManager<>(Integer::valueOf, Integer::intValue, Set.of(), 4);
    }

    @Test
    public void testEntitiesAreSpreadOverTheChunks() {
        int entityCount = 37;

        try (Spawn<Integer> spawn = manager.spawn(Position.class)) {
            Linker<Integer, Position> positions = spawn.linker(Position.class);
            for (int i = 0; i < entityCount; i++) {
                Position position = new Position();
                position.x = i + 1f;
                position.y = i + 1f;
                positions.link(spawn.spawn(), position);
            }
        }

        Set<Integer> foundEntities = new HashSet<>();
        try (View<Integer> view = manager.view(Position.class)) {
            for (Integer entity : view) {
                assertTrue(foundEntities.add(entity), "An entity must not be iterated twice!");
            }
        }

        assertEquals(entityCount, foundEntities.size(), "All the entities must be reachable through the view!");
    }

    @Test
    public void testComponentAdditionKeepsTheOtherComponents() {
        Integer entity;
        Position position = new Position();
        position.x = 42f;

        try (Spawn<Integer> spawn = manager.spawn(Position.class)) {
            entity = spawn.spawn();
            spawn.linker(Position.class).link(entity, position);
        }

        Velocity velocity = new Velocity();
        velocity.dx = 7f;
        manager.add(entity, velocity);

        assertTrue(manager.has(entity, Position.class), "The entity must keep its Position component!");
        assertTrue(manager.has(entity, Velocity.class), "The entity must have the added Velocity component!");
        assertSame(position, manager.get(entity, Position.class), "The Position component must survive the migration!");
        assertSame(velocity, manager.get(entity, Velocity.class), "The added Velocity component must be reachable!");

        int positionCount = 0;
        int velocityCount = 0;
        try (View<Integer> view = manager.view(Position.class)) {
            for (Integer ignored : view) positionCount++;
        }
        try (View<Integer> view = manager.view(Velocity.class)) {
            for (Integer ignored : view) velocityCount++;
        }

        assertEquals(1, positionCount, "The migrated entity must be visible in the view of its components!");
        assertEquals(1, velocityCount, "The migrated entity must be visible in the view of its components!");
    }

    @Test
    public void testComponentRemovalKeepsTheOtherComponents() {
        Integer entity;
        Velocity velocity = new Velocity();
        velocity.dx = 2f;

        try (Spawn<Integer> spawn = manager.spawn(Position.class, Velocity.class)) {
            entity = spawn.spawn();
            spawn.linker(Position.class).link(entity, new Position());
            spawn.linker(Velocity.class).link(entity, velocity);
        }

        assertTrue(manager.remove(entity, Position.class), "The removal of an existing component must be successful!");
        assertFalse(manager.remove(entity, Health.class), "The removal of a missing component must not be successful!");

        assertFalse(manager.has(entity, Position.class), "The removed component must not be attached anymore!");
        assertTrue(manager.has(entity, Velocity.class), "The entity must keep its Velocity component!");
        assertNull(manager.get(entity, Position.class), "The removed component must not be reachable!");
        assertSame(velocity, manager.get(entity, Velocity.class), "The Velocity component must survive the migration!");

        int positionCount = 0;
        int velocityCount = 0;
        try (View<Integer> view = manager.view(Position.class)) {
            for (Integer ignored : view) positionCount++;
        }
        try (View<Integer> view = manager.view(Velocity.class)) {
            for (Integer ignored : view) velocityCount++;
        }

        assertEquals(0, positionCount, "The entity must not be visible in the view of the removed component!");
        assertEquals(1, velocityCount, "The entity must be visible in the view of the remaining component!");
    }

    @Test
    public void testViewIterationIsRepeatable() {
        try (Spawn<Integer> spawn = manager.spawn(Position.class)) {
            spawn.spawn();
            spawn.spawn();
        }

        try (View<Integer> view = manager.view(Position.class)) {
            int firstIteration  = 0;
            int secondIteration = 0;

            for (Integer ignored : view) firstIteration++;
            for (Integer ignored : view) secondIteration++;

            assertEquals(2, firstIteration, "The first iteration must see all the entities!");
            assertEquals(firstIteration, secondIteration, "Every iteration of a view must see the same entities!");
        }
    }

    @Test
    public void testExceptViewIterationIsRepeatable() {
        try (Spawn<Integer> spawn = manager.spawn(Position.class)) {
            spawn.spawn();
            spawn.spawn();
        }
        try (Spawn<Integer> spawn = manager.spawn(Position.class, Velocity.class)) {
            spawn.spawn();
        }

        try (View<Integer> view = manager.view(Position.class).except(Velocity.class)) {
            int firstIteration  = 0;
            int secondIteration = 0;

            for (Integer ignored : view) firstIteration++;
            for (Integer ignored : view) secondIteration++;

            assertEquals(2, firstIteration, "The entities with the excluded component must be filtered out!");
            assertEquals(firstIteration, secondIteration, "Every iteration of a view must see the same entities!");
        }
    }

    @Test
    public void testSpawnReservesComponentTypes() {
        Integer entity;

        try (Spawn<Integer> spawn = manager.spawn(Position.class, Velocity.class)) {
            entity = spawn.spawn();

            assertTrue(manager.has(entity, Position.class), "The spawned entity must have the reserved component type!");
            assertTrue(manager.has(entity, Velocity.class), "The spawned entity must have the reserved component type!");
            assertFalse(manager.has(entity, Health.class), "The spawned entity must not have the unreserved component type!");
            assertNull(manager.get(entity, Position.class), "A reserved component type has no instance until it is linked!");
        }
    }

    @Test
    public void testEntitiesIteratorOfRequestedAmount() {
        int entityCount = 5;

        try (Spawn<Integer> spawn = manager.spawn()) {
            Iterator<Integer> iterator = spawn.entities(entityCount);

            int createdEntityCount = 0;
            while (iterator.hasNext()) {
                iterator.next();
                createdEntityCount++;
            }

            assertEquals(entityCount, createdEntityCount, "The iterator must create the requested amount of entities!");
            assertThrows(NoSuchElementException.class, iterator::next, "An exhausted iterator must throw!");
        }
    }

    @Test
    public void testUnknownEntityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> manager.has(999, Position.class),
                "An entity which is not managed by the manager must be rejected!");
        assertThrows(NullPointerException.class, () -> manager.has(null, Position.class),
                "A null entity must be rejected!");
    }
}
