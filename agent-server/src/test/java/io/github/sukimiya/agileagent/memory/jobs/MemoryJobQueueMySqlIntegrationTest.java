package io.github.sukimiya.agileagent.memory.jobs;

import org.junit.jupiter.api.Tag;

import io.github.sukimiya.agileagent.memory.MemoryMySqlTestSupport;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/** {@link MemoryJobQueueTest} on real MySQL 8.4 (duplicate keys inside InnoDB transactions, utf8mb4_bin keys). */
@Tag("mysql-it")
class MemoryJobQueueMySqlIntegrationTest extends MemoryJobQueueTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
