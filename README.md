# north.depot

A small ECS (Entity–Component–System) library for Java 17.

`north.depot` keeps entities and components as plain user-defined objects — no
annotations, no inheritance requirements, no bytecode generation. The library
ships two interchangeable entity manager implementations: a simple hash-map
based one and a columnar, archetype based one.

> **Status:** `0.1.0`, early development. The API may change between releases.
> See [TODO.md](TODO.md) for the planned work.

## Contents

- [Features](#features)
- [Requirements](#requirements)
- [Build](#build)
- [Quick start](#quick-start)
- [API overview](#api-overview)
- [Implementations](#implementations)
- [Benchmarks](#benchmarks)
- [Project structure](#project-structure)
- [Testing](#testing)
- [Current limitations and roadmap](#current-limitations-and-roadmap)

## Features

- **Generic entity model.** The entity type is a type parameter: a `record`, an
  `Integer`, or any other class. You provide a factory and an id extractor.
- **Two storage backends.** `SimpleEntityManager` for simplicity and
  `ArchetypeEntityManager` for cache-friendly, columnar storage.
- **Batch spawning.** `Spawn` creates entities for a predefined set of component
  types and exposes type-safe `Linker`s for attaching components.
- **Views with accessors.** `View` iterates over entities that have all the
  requested components; `Accessor` provides typed component access.
- **Filtering.** `View.except(...)` excludes entities that have any of the given
  component types.
- **Component CRUD.** `add`, `get`, `has` and `remove` work directly on entities.
- **Scoped resources.** `Spawn` and `View` extend `AutoCloseable` and are meant
  to be used in try-with-resources blocks.
- **Access metadata.** `AccessType.READ` / `AccessType.WRITE` describe intended
  concurrent access for future parallel scheduling.
- **JMH benchmarks.** The two implementations can be compared side by side.

## Requirements

- JDK 17 or newer (the project is compiled with `maven.compiler.release=17`)
- Maven 3.8+

The library itself has no runtime dependencies. JUnit and JMH are test-scoped.

## Build

```bash
git clone git@github.com:cucumberbatch/north.depot.git
cd north.depot
mvn clean verify
```

To use the library locally, install it into the local Maven repository:

```bash
mvn clean install
```

```xml
<dependency>
  <groupId>io.github.cucumberbatch.north</groupId>
  <artifactId>north.depot</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Quick start

### Define components and an entity type

Components are ordinary classes. An entity can be anything that carries an id.

```java
public final class Position {
    public float x;
    public float y;
}

public final class Velocity {
    public float dx;
    public float dy;
}

public final class Frozen {
    // marker component
}

public record Entity(int id) { }
```

### Create an entity manager

The constructor takes two functions:

- `IntFunction<E>` — creates an entity instance for the id assigned by the manager;
- `ToIntFunction<E>` — extracts that id back from the entity instance.

```java
EntityManager<Entity> em =
        new ArchetypeEntityManager<>(Entity::new, Entity::id);
```

The id extractor must return exactly the id that was passed to the factory,
because the manager uses ids to look entities up.

### Spawn entities

`Spawn` is created for a set of component types and is closed with
try-with-resources.

```java
try (Spawn<Entity> spawn = em.spawn(Position.class, Velocity.class)) {
    Linker<Entity, Position> positions  = spawn.linker(Position.class);
    Linker<Entity, Velocity> velocities = spawn.linker(Velocity.class);

    Iterator<Entity> entities = spawn.entities(1_000);
    while (entities.hasNext()) {
        Entity entity = entities.next();

        positions.link(entity, new Position());
        velocities.link(entity, new Velocity());
    }
}
```

`spawn.spawn()` creates a single entity. `spawn.entities()` returns an infinite
iterator that keeps creating entities until the loop is broken.

### Iterate and modify components

```java
try (View<Entity> view = em.view(Position.class, Velocity.class)) {
    Accessor<Entity, Position> positions =
            view.accessor(Position.class, AccessType.WRITE);
    Accessor<Entity, Velocity> velocities =
            view.accessor(Velocity.class, AccessType.READ);

    for (Entity entity : view) {
        Position position = positions.get(entity);
        Velocity velocity = velocities.get(entity);

        position.x += velocity.dx;
        position.y += velocity.dy;
    }
}
```

Only entities that have **all** the components passed to `em.view(...)` are
returned by the iteration. `em.view()` without arguments iterates over every
entity.

### Work with individual entities

```java
Entity entity;
try (Spawn<Entity> spawn = em.spawn()) {
    entity = spawn.spawn();
}

em.add(entity, new Position());
em.add(entity, new Velocity());

boolean hasPosition = em.has(entity, Position.class);

// an overload that requires all the listed components at once
boolean isMovable = em.has(entity, Position.class, Velocity.class);

Position position = em.get(entity, Position.class);

em.remove(entity, Velocity.class);
```

Components are identified by their runtime class, so `get`/`has`/`remove` must
be called with the same class that was used to add the component.

### Filter a view

```java
try (View<Entity> movable = em.view(Position.class).except(Frozen.class)) {
    for (Entity entity : movable) {
        // entities have Position, but do not have Frozen
    }
}
```

Filters can be chained: `view(Position.class).except(Frozen.class).except(Stunned.class)`.

## API overview

### `EntityManager<E>`

| Method | Description |
| --- | --- |
| `Spawn<E> spawn(Class<?>... componentTypes)` | Creates a batch spawner for entities with the given components. |
| `View<E> view(Class<?>... componentTypes)` | Creates a view over entities that have all the given components. |
| `<C> C add(E entity, C component)` | Attaches a component instance to an entity and returns it. |
| `<C> C get(E entity, Class<C> componentType)` | Returns the component of the given type, or `null` if absent. |
| `<C> boolean has(E entity, Class<C> componentType)` | Checks whether the entity has the component. |
| `boolean has(E entity, Class<?>... componentTypes)` | Checks that all listed components are present (default method). |
| `<C> boolean remove(E entity, Class<C> componentType)` | Detaches the component, returning `false` if it was absent. |

### `Spawn<E>`

| Method | Description |
| --- | --- |
| `<C> Linker<E, C> linker(Class<C> componentType)` | Returns a type-safe linker for a component type. |
| `E spawn()` | Creates one entity. |
| `Iterator<E> entities()` | Infinite iterator that creates a new entity on every `next()`. |
| `Iterator<E> entities(int count)` | Iterator that creates `count` entities. |
| `void close()` | Ends the batch (see `Batch`). |

### `View<E>`

| Method | Description |
| --- | --- |
| `<C> Accessor<E, C> accessor(Class<C> componentType)` | Creates a read accessor for a queried component type. |
| `<C> Accessor<E, C> accessor(Class<C> componentType, AccessType accessType)` | Creates an accessor with explicit read/write intent. |
| `View<E> except(Class<?>... componentTypes)` | Excludes entities having any of the given component types. |
| `Iterator<E> iterator()` | Iterates over the matching entities. |
| `void close()` | Releases the view scope. |

### `Accessor<E, C>`, `Linker<E, C>`, `AccessType`, `Batch<E>`

- `Accessor.get(entity)` returns the component instance for an entity from the
  parent view; `getComponentType()` and `getAccessType()` expose its metadata.
- `Linker.link(entity, component)` attaches a component to an entity created by
  the same spawner; `getComponentType()` returns the linked type.
- `AccessType` is either `READ` (concurrent readers allowed) or `WRITE`
  (exclusive access). It currently records intent for scheduling purposes and
  does not lock anything by itself.
- `Batch` is the common `AutoCloseable` contract of `Spawn` and `View`.

## Implementations

### `SimpleEntityManager`

A straightforward, hash-map based implementation.

- Entities are grouped by their archetype using `HashMap` / `LinkedHashSet`.
- Component instances are kept in a per-entity map.
- Adding or removing a component moves the entity between archetype groups.
- Views scan all archetype groups that contain the requested component types.

It is easy to reason about and useful as a reference implementation, but it has
more indirection and allocations per component access than the archetype
implementation.

### `ArchetypeEntityManager`

A columnar implementation for performance-sensitive use.

- Component types are interned in a `ComponentRegistry` and receive stable type
  ids; archetypes are cached, so repeated queries reuse the same layout.
- Entities are stored in tables, one table per exact archetype.
- Each table is split into fixed-size chunks (256 slots by default). Every
  chunk keeps an entity-id column, a location column and one object column per
  component type.
- Adding or removing a component migrates the entity to another table and
  carries all other components over.
- Views collect the tables whose archetype contains the queried components and
  iterate their chunks.

Additional constructors allow pre-registering component types and tuning the
chunk capacity:

```java
EntityManager<Entity> em = new ArchetypeEntityManager<>(
        Entity::new,
        Entity::id,
        Set.of(Position.class, Velocity.class),
        512);
```

## Benchmarks

The project contains JMH benchmarks that compare the performance of the two
entity manager implementations.

Run them with the `benchmarks` profile:

```bash
mvn -Pbenchmarks test
```

The run can be tuned with the `jmh.args` property, which accepts any argument
of the JMH runner:

```bash
# a single benchmark, 2 forks, 10 measurement iterations
mvn -Pbenchmarks test -Djmh.args="-f 2 -i 10 EntityManagerBenchmark.createView"

# the results in a machine readable format
mvn -Pbenchmarks test -Djmh.args="-rf json -rff target/benchmarks.json EntityManagerBenchmark"
```

Both `SIMPLE` and `ARCHETYPE` implementations are measured, with 1 000 and
10 000 entities where applicable. The benchmark suite covers:

| Group | Benchmarks |
| --- | --- |
| Spawning | `spawnEntity`, `spawnEntityWarm`, `spawnBatchWithComponents`, `spawnBatchWithComponentsWarm` |
| Reading | `iterateView`, `createView`, `iteratePreparedView`, `iterateViewWithAccessors`, `getComponentByEntity`, `hasComponentByEntity`, `iterateExceptView` |
| Writing | `updateComponents`, `migrateComponent` |

The benchmarks are located in
`src/test/java/io/github/cucumberbatch/north/depot/benchmark/`.

## Project structure

```text
src/main/java/io/github/cucumberbatch/north/depot/
├── api/
│   ├── EntityManager.java   # core interface
│   ├── Spawn.java           # batch entity creation
│   ├── View.java            # filtered entity iteration
│   ├── Batch.java           # AutoCloseable lifecycle
│   ├── Accessor.java        # typed component access
│   ├── Linker.java          # typed component attachment
│   └── AccessType.java      # read/write access intent
└── api/impl/
    ├── SimpleEntityManager.java
    └── ArchetypeEntityManager.java

src/test/java/io/github/cucumberbatch/north/depot/
├── api/                     # shared API contract tests
└── benchmark/               # JMH benchmarks
```

## Testing

```bash
mvn test
```

The API contract is described by the abstract `TestEntityManagerApi` suite. Both
implementations plug into it through `TestSimpleEntityManagerApi` and
`TestArchetypeEntityManagerApi`; implementation-specific behaviour is covered by
the additional test classes.

## Current limitations and roadmap

- The managers are not thread-safe. `AccessType` only records the intended
  access mode; there is no scheduler or locking yet.
- There is no API for destroying an entity.
- `View` supports `except(...)` but not `require(...)` yet.
- Functional endpoints for `View` / `Spawn` pipelines are not implemented.
- There is no serialization or deserialization of an entity manager.
- Component instances are created by the caller and linked explicitly.

The planned features listed in [TODO.md](TODO.md) include functional-style
pipelines for `Batch` objects, `require(...)`, batch `operate(...)` methods, a
revised component storage model, and manager serialization.
