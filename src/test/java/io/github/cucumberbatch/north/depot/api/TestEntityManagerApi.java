package io.github.cucumberbatch.north.depot.api;

import io.github.cucumberbatch.north.depot.api.impl.SimpleEntityManager;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TestEntityManagerApi {
   
    private static final class Entity {
        private final int id;

        public Entity(int id) { this.id = id; }
        public int getId()    { return id; }
    }

    public static final class Position { float  x,  y; }
    public static final class Velocity { float dx, dy; }
    public static final class Health   { int value;    }
    
    private static final Set<Class<?>> componentTypes = Stream.of(
            Position.class,
            Velocity.class
    ).collect(Collectors.toSet());


    @Test
    public void testEntityManagerCreation() {
        assertDoesNotThrow(() ->
                new SimpleEntityManager<>(Entity::new, Entity::getId)
        );
    }

    @Test
    public void testIntEntityManagerCreation() {
        assertDoesNotThrow(() ->
                new SimpleEntityManager<>(Integer::valueOf, Integer::intValue)
        );
    }

    @Test
    public void testEntitySingleCreation() {
        EntityManager<Integer> manager =
                new SimpleEntityManager<>(Integer::valueOf, Integer::intValue);

        Integer singleEntityId = null;

        try (Spawn<Integer> spawn = manager.spawn()) {
            singleEntityId = spawn.spawn();
        }

        assertNotNull(singleEntityId, "Id of created entity must not be null!");

        // not sure about that...
        //assertEquals(1, singleEntityId, "The first entity generated via entity manager must have an id=1!");
    }

    @Test
    public void testHasAndGetComponentAfterAddUsingEntityManager() {
        EntityManager<Integer> manager =
                new SimpleEntityManager<>(Integer::valueOf, Integer::intValue);

        Integer singleEntityId = null;

        try (Spawn<Integer> spawn = manager.spawn()) {
            singleEntityId = spawn.spawn();
        }

        // adding a component to an entity
        Position position = new Position();
        Position createdPosition = manager.add(singleEntityId, position);

        // added Transform must be linked with existing entity
        assertTrue(manager.has(singleEntityId, Position.class), "Previously added component must be linked with created entity!");
        assertNotNull(manager.get(singleEntityId, Position.class), "Of course, a component instance that was linked with entity must be returned on manager.get() call!");
        assertSame(position, createdPosition, "The component which returned by add() method must be the same as provided!");
        assertSame(position, manager.get(singleEntityId, Position.class), "A returned component on manager.get() call must be identical to the created one!");
    }

    @Test
    public void testViewForSingleEntityWithOneComponent() {
        EntityManager<Integer> manager =
                new SimpleEntityManager<>(Integer::valueOf, Integer::intValue);

        Integer singleEntityId = null;

        try (Spawn<Integer> spawn = manager.spawn()) {
            singleEntityId = spawn.spawn();
        }

        // adding a component to an entity
        Position position = new Position();
        Position createdPosition = manager.add(singleEntityId, position);

        int entityCount = 0;
        Position foundPosition = null;
        try (View<Integer> view = manager.view(Position.class)) {
            Accessor<Integer, Position> pos = view.accessor(Position.class);
            for (Integer entity : view) {
                foundPosition = pos.get(entity);
                entityCount++;
            }
        }
        
        assertEquals(1, entityCount, "There must be only one created entity with Position component!");
        assertNotNull(foundPosition, "An entity was created with a Position component, and the view() is accessing only those entities which MUST contains components of provided types. So it must be not null!");
        assertSame(position, foundPosition, "Created component and the found one must be the same!");
    }

    @Test
    public void testEntityCreationWithComponentsUsingLinker() {
        EntityManager<Integer> manager =
                new SimpleEntityManager<>(Integer::valueOf, Integer::intValue);

        //creating of 10 entities with Position component
        int expectedEntityCount = 10;
        try (Spawn<Integer> spawn = manager.spawn()) {
            Linker<Integer, Position> pos = spawn.linker(Position.class);
            for (int count = 0; count < expectedEntityCount; count++) {
                Position position = new Position();
                position.x = count + 1f;
                position.y = count + 1f;
                pos.linkTo(spawn.spawn(), position);
            }
        }

        //checking if those entities have this components
        int actualEntityCount = 0;
        try (View<Integer> view = manager.view(Position.class)) {
            for (Integer entity : view) actualEntityCount++;
        }

        assertEquals(expectedEntityCount, actualEntityCount, "Created and queried entities count does not match!");

        Integer firstFoundEntity = null;
        try (View<Integer> view = manager.view(Position.class)) { firstFoundEntity = view.iterator().next(); }

        //change component set for one of entities
        manager.add(firstFoundEntity, new Velocity());
        manager.remove(firstFoundEntity, Position.class);

        int velocitiesCount = 0;
        try (View<Integer> view = manager.view(Velocity.class)) {
            for (Integer entity : view) velocitiesCount++;
        }

        
        int positionsCount = 0;
        try (View<Integer> view = manager.view(Position.class)) {
            for (Integer entity : view) positionsCount++;
        }


        assertEquals(1, velocitiesCount, "There must be only one entity with Velocity component!");
        assertEquals(9, positionsCount, "There must be less entities with Position component than before because of removing one!");
    }

    @Test
    public void testEntityQueryInSingleThread() {
        EntityManager<Entity> entityManager =
                new SimpleEntityManager<>(Entity::new, Entity::getId);

        int entityCount = 1;

        int createdEntityCount = 0;
        try (Spawn<Entity> spawn = entityManager.spawn(Position.class, Velocity.class)) {
            Linker<Entity, Position> positions  = spawn.linker(Position.class);
            Linker<Entity, Velocity> velocities = spawn.linker(Velocity.class);

            Random rng = new Random();
            Iterator<Entity> it = spawn.entities(entityCount);
            List<Object> components = List.of();
            while (it.hasNext()) {
                Entity entity = it.next();

                Position position = new Position();
                position.x = rng.nextFloat();
                position.y = rng.nextFloat();

                Velocity velocity = new Velocity();
                velocity.dx = rng.nextFloat();
                velocity.dy = rng.nextFloat();

                positions.linkTo(entity, position);
                velocities.linkTo(entity, velocity);

                components = List.of(position, velocity);

                createdEntityCount++;
            }
        }

        // Создание объекта запроса сущностей по указанным типам компонентов
        View<Entity> view = entityManager.view(Position.class, Velocity.class);

        // Использование try-with-resources конструкции для высвобождения блокировок
        // после выполнения работы с компонентами
        try (view) {

            // Декларирование доступов по типам компонентов (чтение/модифицирование)
            Accessor<Entity, Velocity> velocities = view.accessor(Velocity.class, AccessType.READ);
            Accessor<Entity, Position> positions  = view.accessor(Position.class, AccessType.WRITE);

            // Итерирование по полученному результату (набору сущностей из итерируемой коллекции архетипов/таблиц)
            for (Entity entity : view) {

                // Возможно обращение к entity
                long entityId = entity.getId();

                float offset = (float) entityId / entityCount;

                Position p = positions.get(entity);
                Velocity v = velocities.get(entity);

                p.x = p.x + v.dx + offset;
                p.y = p.y + v.dy - offset;

            }
        }
    
    }

    @Test
    public void testSpawnedEntityContainsComponents() {
        EntityManager<Integer> manager =
                new SimpleEntityManager<>(Integer::valueOf, Integer::intValue);

        try (Spawn<Integer> spawn = manager.spawn(Position.class, Velocity.class)) {
            Integer entityHandle = spawn.spawn();

            assertTrue(manager.has(entityHandle, Position.class));
            assertTrue(manager.has(entityHandle, Velocity.class));
            assertFalse(manager.has(entityHandle, Health.class));            
        }
    }
    
    @Test
    public void testSpawnedEntityComponentAddition() {
        EntityManager<Integer> manager =
                new SimpleEntityManager<>(Integer::valueOf, Integer::intValue);

        try (Spawn<Integer> spawn = manager.spawn(Position.class)) {
            Integer entityHandle = spawn.spawn();

            assertTrue(manager.has(entityHandle, Position.class));
            assertFalse(manager.has(entityHandle, Velocity.class));

            manager.add(entityHandle, new Velocity());
            
            assertTrue(manager.has(entityHandle, Position.class));            
            assertTrue(manager.has(entityHandle, Velocity.class));            
        }
    }

    @Test
    public void testSpawnedEntityComponentLinkageAndRemoval() {
        EntityManager<Integer> manager =
                new SimpleEntityManager<>(Integer::valueOf, Integer::intValue);

        try (Spawn<Integer> spawn = manager.spawn(Position.class)) {
            Integer entityHandle = spawn.spawn();

            assertTrue(manager.has(entityHandle, Position.class));
            assertFalse(manager.has(entityHandle, Velocity.class));

            Linker<Integer, Velocity> linker = spawn.linker(Velocity.class);
            linker.linkTo(entityHandle, new Velocity());
            
            assertTrue(manager.has(entityHandle, Position.class));            
            assertTrue(manager.has(entityHandle, Velocity.class));

            assertDoesNotThrow(() -> {
                assertNotNull(manager.get(entityHandle, Velocity.class));
            });
        }
    }


}
