package io.github.sukimiya.agileagent.memory;

import java.util.Set;

import org.junit.jupiter.api.Tag;

/** {@link M2aSchemaTest} on real MySQL 8.4: the vendor file adds ngram FULLTEXT indexes here only. */
@Tag("mysql-it")
class M2aSchemaMySqlIntegrationTest extends M2aSchemaTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }

    @Override
    protected Set<String> fulltextIndexes() {
        return Set.copyOf(fixture.jdbc().queryForList("SELECT DISTINCT CONCAT(TABLE_NAME, '.', INDEX_NAME)"
                + " FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND INDEX_TYPE = 'FULLTEXT'",
                String.class));
    }

    @Override
    protected Set<String> expectedFulltextIndexes() {
        return Set.of("memory_chunks.ft_memory_chunks_content", "memory_summaries.ft_memory_summaries_content");
    }
}
