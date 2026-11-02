package io.github.sukimiya.agileagent.memory.jobs;

import org.junit.jupiter.api.Tag;

import io.github.sukimiya.agileagent.memory.MemoryMySqlTestSupport;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/** {@link ConsolidationQuotaTest} on real MySQL 8.4 (atomic concurrent reserves). */
@Tag("mysql-it")
class ConsolidationQuotaMySqlIntegrationTest extends ConsolidationQuotaTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
