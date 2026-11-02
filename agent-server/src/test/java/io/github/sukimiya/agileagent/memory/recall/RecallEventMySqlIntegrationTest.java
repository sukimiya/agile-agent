package io.github.sukimiya.agileagent.memory.recall;

import org.junit.jupiter.api.Tag;

import io.github.sukimiya.agileagent.memory.MemoryMySqlTestSupport;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/** Re-runs the recall explanation cases on MySQL 8.4. */
@Tag("mysql-it")
class RecallEventMySqlIntegrationTest extends RecallEventRecorderTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
