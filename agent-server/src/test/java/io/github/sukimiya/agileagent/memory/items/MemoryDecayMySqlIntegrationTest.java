package io.github.sukimiya.agileagent.memory.items;

import org.junit.jupiter.api.Tag;

import io.github.sukimiya.agileagent.memory.MemoryMySqlTestSupport;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/** Re-runs the decay cases on MySQL 8.4. */
@Tag("mysql-it")
class MemoryDecayMySqlIntegrationTest extends MemoryDecayTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
