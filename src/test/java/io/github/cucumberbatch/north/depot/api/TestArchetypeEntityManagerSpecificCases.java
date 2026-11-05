package io.github.cucumberbatch.north.depot.api;

import io.github.cucumberbatch.north.depot.api.impl.ArchetypeEntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of the storage related behaviour of the {@link ArchetypeEntityManager},
 * which are not covered by the common {@link TestEntityManagerApi} contract.
 */
public class TestArchetypeEntityManagerSpecificCases {

    private ArchetypeEntityManager<Integer> manager;

    @BeforeEach
    public void setUpEntityManager() {
        this.manager = new ArchetypeEntityManager<>(Integer::valueOf, Integer::intValue, Set.of(), 4);
    }

    @Test
    public void testUnknownEntityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> manager.has(999, Position.class),
                "An entity which is not managed by the manager must be rejected!");
        assertThrows(NullPointerException.class, () -> manager.has(null, Position.class),
                "A null entity must be rejected!");
    }
}
