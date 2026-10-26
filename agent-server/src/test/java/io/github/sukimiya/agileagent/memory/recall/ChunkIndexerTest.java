package io.github.sukimiya.agileagent.memory.recall;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.sukimiya.agileagent.config.UploadProperties;
import io.github.sukimiya.agileagent.memory.MemoryMaintenance;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.service.FileStorageService;

/**
 * INDEX_CHUNKS (M2a Task 5, D6): one chunk per completed turn (user message + final answer), split by paragraph
 * above 1,500 estimated tokens, linked to its neighbours, with the codes it contains; idempotent; deleted
 * conversations are skipped and their chunks and pending index jobs removed. H2 here, MySQL in
 * {@link ChunkIndexerMySqlIntegrationTest}.
 */
class ChunkIndexerTest {

    static final Duration WAIT = Duration.ofSeconds(10);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;

    @TempDir Path uploadDir;

    MemoryTestStack stack;
    String conversation;
    MemoryJobWorker worker;
    ChunkIndexer indexer;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        stack = MemoryTestStack.create(newFixture(), p -> {}, t -> t, MemoryTestStack.testRedis());
        conversation = MemoryTestStack.newConversationId();
        var db = stack.fixture().databaseClient();
        worker = new MemoryJobWorker(db, () -> false);
        indexer = new ChunkIndexer(db, stack.fixture().connectionFactory(), new ChunkRepository(db,
                stack.conversations()), worker, stack.props());
    }

    @AfterEach
    void tearDown() {
        stack.close();
    }

    private String completedTurn(String question, String answer) {
        var ticket = stack.accept(conversation, question).ticket();
        assertThat(stack.finalizer().finish(ticket, TurnStatus.COMPLETED, answer).block(WAIT)).isTrue();
        return ticket.turnId();
    }

    private List<Map<String, Object>> chunks() {
        return stack.rows("SELECT id, turn_id, from_sequence, to_sequence, prev_chunk_id, next_chunk_id, content,"
                + " source_types, entities_json, tenant_id, subject FROM memory_chunks WHERE conversation_id = ?"
                + " ORDER BY from_sequence, id", conversation);
    }

    private int run() {
        return worker.runPending(50).block(WAIT);
    }

    @Test
    void indexesOnlyCompletedTurnsWithNeighbors() {
        String first = completedTurn("我的车牌是 A7-3921", "好的，已记下车牌 A7-3921");
        var failed = stack.accept(conversation, "失败轮次的问题").ticket();
        assertThat(stack.finalizer().finish(failed, TurnStatus.FAILED, null).block(WAIT)).isTrue();
        String second = completedTurn("订单号是 W-5518", "收到");

        assertThat(run()).isEqualTo(2);

        var rows = chunks();
        assertThat(rows).extracting(r -> r.get("TURN_ID")).containsExactly(first, second);
        var a = rows.get(0);
        var b = rows.get(1);
        assertThat((String) a.get("CONTENT")).isEqualTo("用户：我的车牌是 A7-3921\n助手：好的，已记下车牌 A7-3921");
        assertThat((String) a.get("SOURCE_TYPES")).isEqualTo("USER_INPUT,ASSISTANT");
        assertThat((String) a.get("ENTITIES_JSON")).isEqualTo("[\"A7-3921\"]");
        assertThat((String) b.get("ENTITIES_JSON")).isEqualTo("[\"W-5518\"]");
        assertThat(a).containsEntry("TENANT_ID", ALICE.tenantId()).containsEntry("SUBJECT", ALICE.subject());
        // Neighbours across turns of the conversation.
        assertThat(a.get("PREV_CHUNK_ID")).isNull();
        assertThat(a.get("NEXT_CHUNK_ID")).isEqualTo(b.get("ID"));
        assertThat(b.get("PREV_CHUNK_ID")).isEqualTo(a.get("ID"));
        assertThat(b.get("NEXT_CHUNK_ID")).isNull();
        assertThat(stack.rows("SELECT COUNT(*) AS n FROM chat_messages m JOIN memory_chunks c ON c.turn_id = m.turn_id"
                + " WHERE m.turn_id = ?", failed.turnId()).getFirst().get("N")).isEqualTo(0L);
        // Repeating the work writes nothing new.
        assertThat(indexer.index(ALICE, conversation, first).block(WAIT)).isFalse();
        assertThat(chunks()).hasSize(2);
        assertThat(stack.rows("SELECT indexed_through_sequence AS p FROM memory_index_progress WHERE"
                + " conversation_id = ?", conversation).getFirst().get("P"))
                .isEqualTo(b.get("TO_SEQUENCE"));
    }

    @Test
    void longTurnSplitByParagraphWithinChunkLimit() {
        stack.props().getRecall().setChunkMaxTokens(100);
        var paragraphs = String.join("\n\n", List.of("甲".repeat(60), "乙".repeat(60), "丙".repeat(60) + " B8-0001"));
        completedTurn("长问题", paragraphs);

        run();

        var rows = chunks();
        assertThat(rows).hasSizeGreaterThan(1);
        assertThat(rows).allSatisfy(r -> assertThat(
                io.github.sukimiya.agileagent.memory.context.TokenEstimator.estimate((String) r.get("CONTENT")))
                .isLessThanOrEqualTo(100));
        assertThat(String.join("", rows.stream().map(r -> (String) r.get("CONTENT")).toList()))
                .contains("甲".repeat(60)).contains("乙".repeat(60)).contains("B8-0001");
        for (int i = 1; i < rows.size(); i++) {
            assertThat(rows.get(i).get("PREV_CHUNK_ID")).isEqualTo(rows.get(i - 1).get("ID"));
            assertThat(rows.get(i - 1).get("NEXT_CHUNK_ID")).isEqualTo(rows.get(i).get("ID"));
        }
        assertThat(rows.getLast().get("ENTITIES_JSON")).isEqualTo("[\"B8-0001\"]");
    }

    @Test
    void deletingConversationRemovesChunksAndPendingIndexJobs() {
        completedTurn("问题一 A7-3921", "回答一");
        run();
        completedTurn("问题二", "回答二");
        assertThat(stack.rows("SELECT COUNT(*) AS n FROM memory_jobs WHERE job_type = 'INDEX_CHUNKS'"
                + " AND status = 'PENDING' AND conversation_id = ?", conversation).getFirst().get("N")).isEqualTo(1L);
        var uploads = new UploadProperties();
        uploads.setDir(uploadDir.toString());
        var maintenance = new MemoryMaintenance(stack.fixture().connectionFactory(), stack.fixture().databaseClient(),
                stack.cache(), new FileStorageService(uploads), stack.props(), stack.metrics());

        maintenance.deleteConversation(ALICE, conversation).block(WAIT);

        // The pending index job is gone with the deletion transaction; a late job for the deleted turn is a no-op.
        assertThat(stack.rows("SELECT COUNT(*) AS n FROM memory_jobs WHERE job_type = 'INDEX_CHUNKS'"
                + " AND conversation_id = ?", conversation).getFirst().get("N")).isEqualTo(1L);
        var lateTurn = (String) stack.rows("SELECT id FROM chat_turns WHERE conversation_id = ? ORDER BY created_at"
                + " DESC", conversation).getFirst().get("ID");
        assertThat(indexer.index(ALICE, conversation, lateTurn).block(WAIT)).isFalse();
        // The cleanup job removes the chunks.
        maintenance.runPending(10).block(WAIT);
        assertThat(chunks()).isEmpty();
    }
}
