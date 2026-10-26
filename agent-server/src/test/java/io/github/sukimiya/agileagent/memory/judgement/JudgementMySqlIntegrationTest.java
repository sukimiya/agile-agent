package io.github.sukimiya.agileagent.memory.judgement;

import org.junit.jupiter.api.Tag;

import io.github.sukimiya.agileagent.memory.MemoryMySqlTestSupport;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/** Re-runs judgement handler cases on MySQL 8.4. */
@Tag("mysql-it")
class JudgementMySqlIntegrationTest extends JudgementJobHandlerTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
