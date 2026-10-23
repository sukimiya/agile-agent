package io.github.sukimiya.agileagent.memory;

import org.junit.jupiter.api.Tag;

/** Re-runs every {@link TurnToolRecorderTest} case against a real MySQL 8.4. Opt-in: {@code -Pmemory-mysql-it}. */
@Tag("mysql-it")
class TurnToolRecorderMySqlIntegrationTest extends TurnToolRecorderTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
