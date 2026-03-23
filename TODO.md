# A TODO list of what is planned or may be reviewed as possible feature

- [ ] Function style pipelines for `Batch` objects
  - [ ] A new method for `Spawn` objects that will help define a count of entities which must be instantiated, which result type will be the same `Spawn` object for the ability to pipe things
  - [ ] The ability to use functional style pipelines on `View`
    - [x] An `except(Class<?>...)` method for excluding specific types from viewed entities, i.e. `manager.view(Health.class).exclude(EnemyAI.class)` gives you a `View` over entities which has a `Health` component, but has no `EnemyAI` attached to them.
    - [ ] A different `operate(Operation<E, C1, C2, C3, ...>)` terminal operations to perform batch updates on selected components/entities, so the user can apply own operational methods as a method referense like so `manager.view(Position.class).operate(this::updatePosition)`, which is probably more convenient than to use verbose logic in try-with-resource blocks with hand written `Accessor`s.
      - There will be a problem with implementation or even API with that approach, I will be forced to implement different `View` types for each parameter arity set i.e. `View1` for `view(Type1.class)`, `View2` for `view(Type1.class, type2.class)` and so on, and from the user point it also will be not so convenient and even ugly to work with that API. So, I must think on more convenient way
- [x] A helper method `has(Class<?>...)` for `EntityManager`
- [ ] A well-defined `EntityManager` methods for checking the existence of components: belonging to some archetype or having an actual reference to component
- [ ] Replace `entities(int count)` of `EntityManager` with something like `amountOf(int count)` (or another appropriate name) in `Spawn` object
- [ ] Serialization/deserialization of the entire `EntityManager` container
  - [ ] Thoughts about nice API
