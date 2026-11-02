package io.github.sukimiya.agileagent.memory.command;

import org.junit.jupiter.api.Tag;

import io.github.sukimiya.agileagent.memory.MemoryMySqlTestSupport;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/** Re-runs the in-chat forget / restore cases on MySQL 8.4. */
@Tag("mysql-it")
class MemoryForgetMySqlIntegrationTest extends MemoryForgetTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
