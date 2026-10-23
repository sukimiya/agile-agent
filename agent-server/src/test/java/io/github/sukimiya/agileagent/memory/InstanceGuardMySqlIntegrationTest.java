package io.github.sukimiya.agileagent.memory;

import org.junit.jupiter.api.Tag;

/** {@link InstanceGuardTest} on real MySQL 8.4 (database-time heartbeats and stale detection). */
@Tag("mysql-it")
class InstanceGuardMySqlIntegrationTest extends InstanceGuardTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
