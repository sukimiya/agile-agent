package io.github.sukimiya.agileagent.memory.judgement;

import org.junit.jupiter.api.Tag;

import io.github.sukimiya.agileagent.memory.MemoryMySqlTestSupport;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/** Re-runs the Rules replay cases on MySQL 8.4. */
@Tag("mysql-it")
class RulesReplayMySqlIntegrationTest extends RulesReplayTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
