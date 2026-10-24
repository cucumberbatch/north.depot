package io.github.cucumberbatch.north.depot.api;

import io.github.cucumberbatch.north.depot.api.impl.ArchetypeEntityManager;

public class TestArchetypeEntityManagerApi extends TestEntityManagerApi<Integer> {
    @Override
    public EntityManager<Integer> getEntityManager() {
        return new ArchetypeEntityManager<>(Integer::valueOf, Integer::intValue);
    }
}
