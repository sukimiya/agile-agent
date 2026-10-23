package io.github.sukimiya.agileagent.memory;

import org.junit.jupiter.api.Tag;

/**
 * Re-runs every {@link ConversationHistoryTest} case on a real MySQL 8.4 (bound LIMIT, the messages/turns join,
 * DATETIME(6) mapping and utf8mb4_bin scoping). Opt-in: {@code mvn -Pmemory-mysql-it ...}.
 */
@Tag("mysql-it")
class ConversationHistoryMySqlIntegrationTest extends ConversationHistoryTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
