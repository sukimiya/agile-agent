package io.github.sukimiya.agileagent.memory;

import org.junit.jupiter.api.Tag;

/** {@link MemoryDeletionTest} on real MySQL 8.4, including the accept/delete race on separate connections. */
@Tag("mysql-it")
class MemoryDeletionMySqlIntegrationTest extends MemoryDeletionTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
