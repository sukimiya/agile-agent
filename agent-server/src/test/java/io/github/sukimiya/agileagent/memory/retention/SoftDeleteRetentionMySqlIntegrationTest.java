package io.github.sukimiya.agileagent.memory.retention;

import org.junit.jupiter.api.Tag;

import io.github.sukimiya.agileagent.memory.MemoryMySqlTestSupport;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/** Re-runs the soft delete and reference-tier purge cases on MySQL 8.4. */
@Tag("mysql-it")
class SoftDeleteRetentionMySqlIntegrationTest extends SoftDeleteRetentionTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
