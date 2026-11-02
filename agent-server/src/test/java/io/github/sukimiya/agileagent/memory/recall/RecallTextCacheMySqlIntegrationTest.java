package io.github.sukimiya.agileagent.memory.recall;

import org.junit.jupiter.api.Tag;

import io.github.sukimiya.agileagent.memory.MemoryMySqlTestSupport;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/** {@link RecallTextCacheTest} on real MySQL 8.4. */
@Tag("mysql-it")
class RecallTextCacheMySqlIntegrationTest extends RecallTextCacheTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
