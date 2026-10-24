package io.github.cucumberbatch.north.depot.api;

import io.github.cucumberbatch.north.depot.api.impl.SimpleEntityManager;

public class TestSimpleEntityManagerApi extends TestEntityManagerApi<Integer> {
    @Override
    public EntityManager<Integer> getEntityManager() {
        return new SimpleEntityManager<>(Integer::valueOf, Integer::intValue);
    }
}
