package io.github.sukimiya.agileagent.memory.judgement;

import org.junit.jupiter.api.Tag;

import io.github.sukimiya.agileagent.memory.MemoryMySqlTestSupport;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/** Re-runs the judgement relation cases on MySQL 8.4. */
@Tag("mysql-it")
class JudgementRelationsMySqlIntegrationTest extends JudgementRelationsTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }
}
