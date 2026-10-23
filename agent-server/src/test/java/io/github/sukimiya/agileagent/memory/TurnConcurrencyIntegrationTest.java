package io.github.sukimiya.agileagent.memory;

import org.junit.jupiter.api.Tag;

/**
 * Re-runs every {@link TurnLifecycleTest} case on a real MySQL 8.4: each statement gets its own connection, so
 * the concurrent-accept cases exercise InnoDB locking and the unique constraints with independent sessions.
 * Opt-in: {@code mvn -Pmemory-mysql-it ...}.
 */
@Tag("mysql-it")
class TurnConcurrencyIntegrationTest extends TurnLifecycleTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
