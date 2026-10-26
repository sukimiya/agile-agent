package io.github.sukimiya.agileagent.memory.recall;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryMySqlTestSupport;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;

/** {@link MemoryRetrieverTest} on real MySQL 8.4: ngram FULLTEXT over chunks and records, codes, scoping. */
@Tag("mysql-it")
class RecallMySqlIntegrationTest extends MemoryRetrieverTest {

    @Override
    protected MemoryTestFixture newFixture() {
        return MemoryMySqlTestSupport.freshDatabase();
    }

    @Test
    void chineseFulltextFindsPhraseOnMySql() {
        String turn = completedTurn("公司明年计划在杭州开设新的研发中心", "好的，已了解研发中心的选址计划");
        completedTurn("今天天气怎么样", "晴天");
        index();

        var result = recall(conversation, "杭州研发中心的计划进展如何");

        assertThat(turns(result)).containsExactly(turn);
        assertThat(result.items().getFirst().reason()).isEqualTo("CHUNK_FULLTEXT");
    }
}
