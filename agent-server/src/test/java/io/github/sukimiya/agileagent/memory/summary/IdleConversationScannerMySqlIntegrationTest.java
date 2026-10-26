package io.github.sukimiya.agileagent.memory.summary;

import org.junit.jupiter.api.Tag;

import io.github.sukimiya.agileagent.memory.MemoryMySqlTestSupport;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/** {@link IdleConversationScannerTest} on real MySQL 8.4 (database-time idle cut-off and the scan query). */
@Tag("mysql-it")
class IdleConversationScannerMySqlIntegrationTest extends IdleConversationScannerTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
