# north.depot
A small ECS library written in Java

## Benchmarks

The project contains JMH benchmarks, which compare the performance of the two
entity manager implementations: the `SimpleEntityManager` (a hash-map based
storage) and the `ArchetypeEntityManager` (a columnar, archetype based storage).

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

The benchmarks are located in
`src/test/java/io/github/cucumberbatch/north/depot/benchmark/`.
