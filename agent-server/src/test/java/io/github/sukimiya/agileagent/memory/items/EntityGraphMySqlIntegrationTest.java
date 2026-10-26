package io.github.sukimiya.agileagent.memory.items;

import org.junit.jupiter.api.Tag;

import io.github.sukimiya.agileagent.memory.MemoryMySqlTestSupport;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

@Tag("mysql-it")
class EntityGraphMySqlIntegrationTest extends EntityGraphRepositoryTest {
    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
