package io.github.sukimiya.agileagent.memory.summary;

import org.junit.jupiter.api.Tag;

import io.github.sukimiya.agileagent.memory.MemoryMySqlTestSupport;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/** {@link SummaryJobHandlerTest} on real MySQL 8.4 (progress CAS and the range unique key inside InnoDB). */
@Tag("mysql-it")
class SummaryMySqlIntegrationTest extends SummaryJobHandlerTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
