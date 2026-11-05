package io.github.cucumberbatch.north.depot.api;

import io.github.cucumberbatch.north.depot.api.impl.SimpleEntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public abstract class TestEntityManagerApi<E> {
    private EntityManager<E> manager;
    
    public abstract EntityManager<E> getEntityManager();

    @BeforeEach
    public void setUpEntityManager() {
        this.manager = getEntityManager();
    }


    @Test
    public void testEntireEntityManagerApi() {

        // Creation of a simple entity manager. The user must provide a function of entity creation, and also a function of entity extraction by id
        EntityManager<Entity> entityManager =
                new SimpleEntityManager<>(Entity::new, Entity::id);

        int entityCount = 1;
        int createdEntityCount = 0;

        // The entities themselves are created through the entity manager. To do this, the spawn method is called, which accepts a vararg array of component types to pre-place new entities in a specific archetype. The method returns AutoCloseable, so the try-with-resources construct is used.
        try (Spawn<Entity> spawn = entityManager.spawn(Position.class, Velocity.class)) {

            // The process of linking real components to entities is the responsibility of the user, and the entity manager does not create them. To do this, a binding mechanism is used through a parameterized Linker.
            Linker<Entity, Position> positions  = spawn.linker(Position.class);
            Linker<Entity, Velocity> velocities = spawn.linker(Velocity.class);

            // Spawn has the option to get an iterator on entities with a specified number of entities to create. In the future, it is planned to replace this method with a similar method that returns Spawn to enable the organization of processing chains/pipelines.
            Iterator<Entity> it = spawn.entities(entityCount);

            Random rng = new Random();
            List<Object> components = List.of();
            while (it.hasNext()) {

                // Each iterator's next() call creates an entity and returns a reference to it.
                Entity entity = it.next();

                Position position = new Position();
                position.x = rng.nextFloat();
                position.y = rng.nextFloat();

                Velocity velocity = new Velocity();
                velocity.dx = rng.nextFloat();
                velocity.dy = rng.nextFloat();

                // The process of linking entities to components
                positions.link(entity, position);
                velocities.link(entity, velocity);

                components = List.of(position, velocity);

                createdEntityCount++;
            }

            assertEquals(entityCount, createdEntityCount);
            assertEquals(entityCount, components.size() / 2);
        }

        // Creating an entity request object for the specified types of components
        View<Entity> view = entityManager.view(Position.class, Velocity.class);

        // Using a try-with-resources construct to release locks after working with the components
        try (view) {

            // Declaring accesses by component type (read/write)
            Accessor<Entity, Velocity> velocities = view.accessor(Velocity.class, AccessType.READ);
            Accessor<Entity, Position> positions  = view.accessor(Position.class, AccessType.WRITE);

            // Iterating based on the result (a set of entities from an iterated collection of archetypes/tables)
            for (Entity entity: view) {

                // It is possible to access the entity
                long entityId = entity.id();

                float offset = (float) entityId / entityCount;

                // Getting references to entity components through accessors. It is also planned to add a special method for the View, which will accept a lambda handler for all entities with the specified set of components.
                Position p = positions.get(entity);
                Velocity v = velocities.get(entity);

                p.x = p.x + v.dx + offset;
                p.y = p.y + v.dy - offset;

            }
        }
    }

    @Test
    public void testEntitySingleCreation() {
        E singleEntityId;

        try (Spawn<E> spawn = manager.spawn()) {
            singleEntityId = spawn.spawn();
        }

        assertNotNull(singleEntityId, "Id of created entity must not be null!");
    }

    @Test
    public void testHasAndGetComponentAfterAddUsingEntityManager() {
        E singleEntityId;

        try (Spawn<E> spawn = manager.spawn()) {
            singleEntityId = spawn.spawn();
        }

        Position position = new Position();
        Position createdPosition = manager.add(singleEntityId, position);

        assertTrue(manager.has(singleEntityId, Position.class), "Previously added component must be linked with created entity!");
        assertNotNull(manager.get(singleEntityId, Position.class), "Of course, a component instance that was linked with entity must be returned on manager.get() call!");
        assertSame(position, createdPosition, "The component which returned by add() method must be the same as provided!");
        assertSame(position, manager.get(singleEntityId, Position.class), "A returned component on manager.get() call must be identical to the created one!");
    }

    @Test
    public void testSpawnAndViewOfSingleEntityWithoutComponents() {
        int expectedEntityCount = 1;
        int actualEntityCount = 0;
        
        try (Spawn<E> spawn = manager.spawn()) {
            for (int count = 0; count < expectedEntityCount; count++) {
                spawn.spawn();
            }
        }

        try (View<E> view = manager.view()) {
            for (E entity : view) {
                actualEntityCount++;
            }
        }

        assertEquals(expectedEntityCount, actualEntityCount);
    }
    
    @Test
    public void testViewForSingleEntityWithOneComponent() {
        E singleEntityId;

        try (Spawn<E> spawn = manager.spawn()) {
            singleEntityId = spawn.spawn();
        }

        // adding a component to an entity
        Position position = new Position();
        Position createdPosition = manager.add(singleEntityId, position);

        int entityCount = 0;
        Position foundPosition = null;
        try (View<E> view = manager.view(Position.class)) {
            Accessor<E, Position> pos = view.accessor(Position.class);
            for (E entity: view) {
                foundPosition = pos.get(entity);
                entityCount++;
            }
        }
        
        assertEquals(1, entityCount, "There must be only one created entity with Position component!");
        assertNotNull(foundPosition, "An entity was created with a Position component, and the view() is accessing only those entities which MUST contains components of provided types. So it must be not null!");
        assertSame(position, foundPosition, "Created component and the found one must be the same!");
    }

    @Test
    public void testViewExceptFiltersEntities() {
        int positionOnlyCount = 5;
        int positionAndVelocityCount = 2;

        try (Spawn<E> spawn = manager.spawn(Position.class)) {
            Linker<E, Position> pos = spawn.linker(Position.class);
            for (int i = 0; i < positionOnlyCount; i++) {
                E entity = spawn.spawn();
                Position position = new Position();
                position.x = i + 1f;
                position.y = i + 1f;
                pos.link(entity, position);
            }
        }

        try (Spawn<E> spawn = manager.spawn(Position.class, Velocity.class)) {
            Linker<E, Position> pos = spawn.linker(Position.class);
            Linker<E, Velocity> vel = spawn.linker(Velocity.class);
            for (int i = 0; i < positionAndVelocityCount; i++) {
                E entity = spawn.spawn();

                Position position = new Position();
                position.x = i + 10f;
                position.y = i + 10f;

                Velocity velocity = new Velocity();
                velocity.dx = i + 0.1f;
                velocity.dy = i + 0.1f;

                pos.link(entity, position);
                vel.link(entity, velocity);
            }
        }

        int actualCount = 0;
        try (View<E> view = manager.view(Position.class).except(Velocity.class)) {
            for (E entity : view) {
                actualCount++;
            }
        }

        assertEquals(positionOnlyCount, actualCount, "except(...) should remove entities containing excluded components");
    }

    @Test
    public void testEntityCreationWithComponentsUsingLinker() {
        int expectedEntityCount = 10;
        try (Spawn<E> spawn = manager.spawn()) {
            Linker<E, Position> pos = spawn.linker(Position.class);
            for (int count = 0; count < expectedEntityCount; count++) {
                Position position = new Position();
                position.x = count + 1f;
                position.y = count + 1f;
                pos.link(spawn.spawn(), position);
            }
        }

        int actualEntityCount = 0;
        try (View<E> view = manager.view(Position.class)) {
            for (E entity: view) actualEntityCount++;
        }

        assertEquals(expectedEntityCount, actualEntityCount, "Created and queried entities count does not match!");

        E firstFoundEntity = null;
        try (View<E> view = manager.view(Position.class)) { firstFoundEntity = view.iterator().next(); }

        //change component set for one of entities
        manager.add(firstFoundEntity, new Velocity());
        manager.remove(firstFoundEntity, Position.class);

        int velocitiesCount = 0;
        try (View<E> view = manager.view(Velocity.class)) {
            for (E entity: view) velocitiesCount++;
        }

        
        int positionsCount = 0;
        try (View<E> view = manager.view(Position.class)) {
            for (E entity: view) positionsCount++;
        }


        assertEquals(1, velocitiesCount, "There must be only one entity with Velocity component!");
        assertEquals(9, positionsCount, "There must be less entities with Position component than before because of removing one!");
    }

    @Test
    public void testSpawnedEntityContainsComponents() {
        try (Spawn<E> spawn = manager.spawn(Position.class, Velocity.class)) {
            E entity = spawn.spawn();

            assertTrue(manager.has(entity, Position.class));
            assertTrue(manager.has(entity, Velocity.class));
            assertFalse(manager.has(entity, Health.class));
        }
    }
    
    @Test
    public void testSpawnedEntityComponentAddition() {
        try (Spawn<E> spawn = manager.spawn(Position.class)) {
            E entity = spawn.spawn();

            assertTrue(manager.has(entity, Position.class));
            assertFalse(manager.has(entity, Velocity.class));

            manager.add(entity, new Velocity());
            
            assertTrue(manager.has(entity, Position.class));
            assertTrue(manager.has(entity, Velocity.class));
        }
    }

    @Test
    public void testSpawnedEntityComponentLinkageAndRemoval() {
        try (Spawn<E> spawn = manager.spawn(Position.class)) {
            E entity = spawn.spawn();

            assertTrue(manager.has(entity, Position.class));
            assertFalse(manager.has(entity, Velocity.class));

            spawn.linker(Velocity.class)
                 .link(entity, new Velocity());
            
            assertTrue(manager.has(entity, Position.class));
            assertTrue(manager.has(entity, Velocity.class));

            assertDoesNotThrow(() -> {
                assertNotNull(manager.get(entity, Velocity.class));
            });
        }
    }
    
    @Test
    public void testEntitiesAreSpreadOverTheChunks() {
        int entityCount = 37;

        try (Spawn<E> spawn = manager.spawn(Position.class)) {
            Linker<E, Position> positions = spawn.linker(Position.class);
            for (int i = 0; i < entityCount; i++) {
                Position position = new Position();
                position.x = i + 1f;
                position.y = i + 1f;
                positions.link(spawn.spawn(), position);
            }
        }

        Set<E> foundEntities = new HashSet<>();
        try (View<E> view = manager.view(Position.class)) {
            for (E entity : view) {
                assertTrue(foundEntities.add(entity), "An entity must not be iterated twice!");
            }
        }

        assertEquals(entityCount, foundEntities.size(), "All the entities must be reachable through the view!");
    }

    @Test
    public void testComponentAdditionKeepsTheOtherComponents() {
        E entity;
        Position position = new Position();
        position.x = 42f;

        try (Spawn<E> spawn = manager.spawn(Position.class)) {
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
        try (View<E> view = manager.view(Position.class)) {
            for (E ignored : view) positionCount++;
        }
        try (View<E> view = manager.view(Velocity.class)) {
            for (E ignored : view) velocityCount++;
        }

        assertEquals(1, positionCount, "The migrated entity must be visible in the view of its components!");
        assertEquals(1, velocityCount, "The migrated entity must be visible in the view of its components!");
    }

    @Test
    public void testComponentRemovalKeepsTheOtherComponents() {
        E entity;
        Velocity velocity = new Velocity();
        velocity.dx = 2f;

        try (Spawn<E> spawn = manager.spawn(Position.class, Velocity.class)) {
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
        try (View<E> view = manager.view(Position.class)) {
            for (E ignored : view) positionCount++;
        }
        try (View<E> view = manager.view(Velocity.class)) {
            for (E ignored : view) velocityCount++;
        }

        assertEquals(0, positionCount, "The entity must not be visible in the view of the removed component!");
        assertEquals(1, velocityCount, "The entity must be visible in the view of the remaining component!");
    }

    @Test
    public void testViewIterationIsRepeatable() {
        try (Spawn<E> spawn = manager.spawn(Position.class)) {
            spawn.spawn();
            spawn.spawn();
        }

        try (View<E> view = manager.view(Position.class)) {
            int firstIteration  = 0;
            int secondIteration = 0;

            for (E ignored : view) firstIteration++;
            for (E ignored : view) secondIteration++;

            assertEquals(2, firstIteration, "The first iteration must see all the entities!");
            assertEquals(firstIteration, secondIteration, "Every iteration of a view must see the same entities!");
        }
    }

    @Test
    public void testExceptViewIterationIsRepeatable() {
        try (Spawn<E> spawn = manager.spawn(Position.class)) {
            spawn.spawn();
            spawn.spawn();
        }
        try (Spawn<E> spawn = manager.spawn(Position.class, Velocity.class)) {
            spawn.spawn();
        }

        try (View<E> view = manager.view(Position.class).except(Velocity.class)) {
            int firstIteration  = 0;
            int secondIteration = 0;

            for (E ignored : view) firstIteration++;
            for (E ignored : view) secondIteration++;

            assertEquals(2, firstIteration, "The entities with the excluded component must be filtered out!");
            assertEquals(firstIteration, secondIteration, "Every iteration of a view must see the same entities!");
        }
    }

    @Test
    public void testSpawnReservesComponentTypes() {
        E entity;

        try (Spawn<E> spawn = manager.spawn(Position.class, Velocity.class)) {
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

        try (Spawn<E> spawn = manager.spawn()) {
            Iterator<E> iterator = spawn.entities(entityCount);

            int createdEntityCount = 0;
            while (iterator.hasNext()) {
                iterator.next();
                createdEntityCount++;
            }

            assertEquals(entityCount, createdEntityCount, "The iterator must create the requested amount of entities!");
            assertThrows(NoSuchElementException.class, iterator::next, "An exhausted iterator must throw!");
        }
    }

}
