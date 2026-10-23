package io.github.sukimiya.agileagent.memory;

import org.junit.jupiter.api.Tag;

/**
 * Re-runs every {@link MemoryFailureTest} scenario on the isolated loopback MySQL 8.4 test schema (name ending in
 * {@code _it}, cleaned and re-migrated per test). Opt-in: {@code mvn -Pmemory-mysql-it -pl agent-server -am -Dtest=MemoryFailureIntegrationTest test}.
 */
@Tag("mysql-it")
class MemoryFailureIntegrationTest extends MemoryFailureTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
