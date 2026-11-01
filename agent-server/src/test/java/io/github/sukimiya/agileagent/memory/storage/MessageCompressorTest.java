package io.github.sukimiya.agileagent.memory.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.memory.ConversationRepository;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.github.sukimiya.agileagent.memory.jobs.JobType;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobQueue;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.memory.recall.ChunkRepository;
import io.github.sukimiya.agileagent.memory.storage.MessagePayloadCodec.Field;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * M2b Task 9 tiered compression of stored original text on H2 ({@code MODE=MySQL});
 * {@link MessageCompressorMySqlIntegrationTest} runs the same on MySQL 8.4.
 */
class MessageCompressorTest {

    private static final MemoryScope ALICE = new MemoryScope("t1", "alice");
    private static final MemoryScope BOB = new MemoryScope("t2", "bob");

    protected MemoryTestFixture fixture;
    private JdbcTemplate jdbc;
    private MemoryProperties props;
    private SimpleMeterRegistry meters;
    private MessageCompressor compressor;
    private ConversationRepository conversations;
    private final ObjectMapper mapper = new ObjectMapper();

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        fixture = newFixture();
        jdbc = fixture.jdbc();
        props = new MemoryProperties();
        props.getStorage().setEnabled(true);
        meters = new SimpleMeterRegistry();
        compressor = new MessageCompressor(fixture.databaseClient(), props, meters);
        conversations = new ConversationRepository(fixture.databaseClient(), mapper);
    }

    // ---- seeding -------------------------------------------------------------------------------------------

    private void session(MemoryScope scope, String c) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sessions WHERE id = ?", Integer.class, c);
        if (n == 0) {
            jdbc.update("INSERT INTO sessions (id, user_id, tenant_id, title, created_at, updated_at) VALUES"
                    + " (?, ?, ?, 'title', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", c, scope.subject(),
                    scope.tenantId());
        }
    }

    /** A turn with a user and an assistant message created {@code ageDays} ago. */
    private void turn(MemoryScope scope, String c, String turnId, String status, int ageDays, String user,
            String answer) {
        session(scope, c);
        jdbc.update("INSERT INTO chat_turns (id, tenant_id, subject, conversation_id, request_hash, status,"
                + " created_at, updated_at, finished_at) VALUES (?, ?, ?, ?, 'h', ?,"
                + " TIMESTAMPADD(DAY, -?, CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))",
                turnId, scope.tenantId(), scope.subject(), c, status, ageDays);
        message(scope, c, turnId, "user", ageDays, LlmClient.Message.user(user));
        message(scope, c, turnId, "assistant", ageDays, LlmClient.Message.assistantText(answer));
    }

    private void message(MemoryScope scope, String c, String turnId, String role, int ageDays,
            LlmClient.Message message) {
        Long seq = jdbc.queryForObject("SELECT COALESCE(MAX(sequence), 0) + 1 FROM chat_messages"
                + " WHERE conversation_id = ?", Long.class, c);
        jdbc.update("INSERT INTO chat_messages (id, tenant_id, subject, conversation_id, turn_id, sequence, role,"
                + " source_type, payload, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?,"
                + " TIMESTAMPADD(DAY, -?, CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6))",
                turnId + "-" + role, scope.tenantId(), scope.subject(), c, turnId, seq, role,
                "user".equals(role) ? "USER_INPUT" : "ASSISTANT", conversations.serialize(message), ageDays);
    }

    private void toolEvent(MemoryScope scope, String c, String turnId, String id, int ageDays, String arguments,
            String result) {
        jdbc.update("INSERT INTO chat_tool_events (id, tenant_id, subject, conversation_id, turn_id, call_id,"
                + " capability, tool_name, status, source_type, arguments, result, started_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, 'cap', 'tool', 'SUCCEEDED', 'TOOL_RESULT', ?, ?,"
                + " TIMESTAMPADD(DAY, -?, CURRENT_TIMESTAMP(6)), CURRENT_TIMESTAMP(6))",
                id, scope.tenantId(), scope.subject(), c, turnId, id, arguments, result, ageDays);
    }

    private void age(String table, String createdColumn, int days) {
        jdbc.update("UPDATE " + table + " SET " + createdColumn + " = TIMESTAMPADD(DAY, -?, CURRENT_TIMESTAMP(6))",
                days);
    }

    private Map<String, Object> messageRow(String id) {
        return jdbc.queryForMap("SELECT payload, payload_codec, payload_tier, payload_blob, payload_sha256"
                + " FROM chat_messages WHERE id = ?", id);
    }

    private String storedText(Field field, String id) {
        var table = field.table();
        return fixture.databaseClient().sql("SELECT " + MessagePayloadCodec.select(field, "x") + " FROM " + table
                        + " x" + MessagePayloadCodec.join(field, "x") + " WHERE x.id = :id")
                .bind("id", id)
                .map(row -> java.util.Optional.ofNullable(MessagePayloadCodec.text(row, field)))
                .one().block().orElse(null);
    }

    private int compressAll() {
        return compressor.compressAll().block();
    }

    private double counter(String name, String... tags) {
        var c = meters.find(name).tags(tags).counter();
        return c == null ? 0 : c.count();
    }

    private static String large(String seed) {
        return (seed + " ").repeat(800);
    }

    // ---- tests ---------------------------------------------------------------------------------------------

    @Test
    void recentMessagesStayPlain() {
        turn(ALICE, "c1", "turn-1", "COMPLETED", 1, "最近的问题", "最近的回答");

        assertThat(compressAll()).isZero();

        var row = messageRow("turn-1-user");
        assertThat(row.get("payload_codec")).isNull();
        assertThat((String) row.get("payload")).contains("最近的问题");
    }

    @Test
    void olderThanThresholdCompressedAndReadTransparently() {
        turn(ALICE, "c1", "turn-1", "COMPLETED", 10, "八天前的问题 TK-42", "八天前的回答");
        turn(ALICE, "c1", "turn-2", "RUNNING", 10, "进行中的问题", "进行中的回答");

        assertThat(compressAll()).isEqualTo(2);

        var row = messageRow("turn-1-user");
        assertThat(row.get("payload")).isNull();
        assertThat(row.get("payload_codec")).isEqualTo(MessagePayloadCodec.CODEC_ZSTD);
        assertThat(row.get("payload_tier")).isEqualTo(MessagePayloadCodec.TIER_WARM);
        var plain = conversations.serialize(LlmClient.Message.user("八天前的问题 TK-42"));
        assertThat((byte[]) row.get("payload_blob")).isEqualTo(MessagePayloadCodec.encode(plain, 3).data());
        assertThat(row.get("payload_sha256")).isEqualTo(MessagePayloadCodec.sha256(plain));
        assertThat(messageRow("turn-2-user").get("payload_codec")).as("only finished turns").isNull();

        assertThat(conversations.assistantText("turn-1").block()).isEqualTo("八天前的回答");
        var turns = new ChunkRepository(fixture.databaseClient(), conversations)
                .turnTexts(ALICE, List.of("c1"), List.of("turn-1")).collectList().block();
        assertThat(turns).singleElement().satisfies(t -> {
            assertThat(t.user()).isEqualTo("八天前的问题 TK-42");
            assertThat(t.answer()).isEqualTo("八天前的回答");
        });
        assertThat(counter("memory.storage.compressed", "tier", "WARM")).isEqualTo(2);
        assertThat(meters.find("memory.storage.compressed.bytes").tags("tier", "WARM").counter().count())
                .isPositive();
        assertThat(meters.find("memory.storage.compression.ratio").summary().count()).isEqualTo(2);
    }

    @Test
    void coldTierRecompressedAtHigherLevel() {
        turn(ALICE, "c1", "turn-1", "COMPLETED", 10, "旧问题", "旧回答");
        compressAll();
        assertThat(messageRow("turn-1-user").get("payload_tier")).isEqualTo(MessagePayloadCodec.TIER_WARM);

        age("chat_messages", "created_at", 100);
        assertThat(compressAll()).isEqualTo(2);

        var row = messageRow("turn-1-user");
        var plain = conversations.serialize(LlmClient.Message.user("旧问题"));
        assertThat(row.get("payload_tier")).isEqualTo(MessagePayloadCodec.TIER_COLD);
        assertThat((byte[]) row.get("payload_blob")).isEqualTo(MessagePayloadCodec.encode(plain, 19).data());
        assertThat(storedText(Field.MESSAGE_PAYLOAD, "turn-1-user")).isEqualTo(plain);
        assertThat(counter("memory.storage.compressed", "tier", "COLD")).isEqualTo(2);
        assertThat(compressAll()).as("nothing left to do").isZero();
    }

    @Test
    void plainTextPastTheColdAgeGoesStraightToTheColdLevel() {
        turn(ALICE, "c1", "turn-1", "COMPLETED", 100, "很旧的问题", "很旧的回答");

        assertThat(compressAll()).isEqualTo(2);

        var plain = conversations.serialize(LlmClient.Message.user("很旧的问题"));
        var row = messageRow("turn-1-user");
        assertThat(row.get("payload_tier")).isEqualTo(MessagePayloadCodec.TIER_COLD);
        assertThat((byte[]) row.get("payload_blob")).isEqualTo(MessagePayloadCodec.encode(plain, 19).data());
    }

    @Test
    void indexAndRecordsNeverCompressed() {
        turn(ALICE, "c1", "turn-1", "COMPLETED", 10, "问题", "回答");
        jdbc.update("INSERT INTO memory_chunks (id, tenant_id, subject, conversation_id, turn_id, from_sequence,"
                + " to_sequence, content, source_types, created_at) VALUES ('k1', 't1', 'alice', 'c1', 'turn-1', 1, 2,"
                + " '用户：问题\n助手：回答', 'USER_INPUT', TIMESTAMPADD(DAY, -10, CURRENT_TIMESTAMP(6)))");
        jdbc.update("INSERT INTO memory_summaries (id, tenant_id, subject, conversation_id, turn_id, from_sequence,"
                + " to_sequence, from_message_id, to_message_id, record_json, search_text, memory_type,"
                + " generator_model, schema_version, created_at) VALUES ('s1', 't1', 'alice', 'c1',"
                + " 'turn-1', 1, 2, 'turn-1-user', 'turn-1-assistant', '{\"k\":1}', '问题 回答', 'EVENT', 'm', 'v1',"
                + " TIMESTAMPADD(DAY, -10, CURRENT_TIMESTAMP(6)))");
        var turnBefore = jdbc.queryForMap("SELECT * FROM chat_turns WHERE id = 'turn-1'");

        compressAll();

        assertThat(jdbc.queryForObject("SELECT content FROM memory_chunks WHERE id = 'k1'", String.class))
                .isEqualTo("用户：问题\n助手：回答");
        assertThat(jdbc.queryForMap("SELECT record_json, search_text FROM memory_summaries WHERE id = 's1'"))
                .containsEntry("record_json", "{\"k\":1}").containsEntry("search_text", "问题 回答");
        assertThat(jdbc.queryForMap("SELECT * FROM chat_turns WHERE id = 'turn-1'")).isEqualTo(turnBefore);
        assertThat(messageRow("turn-1-user").get("payload_codec")).isNotNull();
    }

    @Test
    void toolArgumentsAndResultsCompressedTooAndEmptyFieldsSkipped() {
        turn(ALICE, "c1", "turn-1", "COMPLETED", 10, "问题", "回答");
        toolEvent(ALICE, "c1", "turn-1", "e1", 10, "{\"q\":\"x\"}", "{\"r\":1}");
        toolEvent(ALICE, "c1", "turn-1", "e2", 10, "{\"q\":\"y\"}", null);
        toolEvent(ALICE, "c1", "turn-1", "e3", 1, "{\"q\":\"recent\"}", "{\"r\":3}");

        assertThat(compressAll()).isEqualTo(2 + 3);

        var e1 = jdbc.queryForMap("SELECT * FROM chat_tool_events WHERE id = 'e1'");
        assertThat(e1.get("arguments")).isNull();
        assertThat(e1.get("result")).isNull();
        assertThat(e1.get("arguments_codec")).isEqualTo("zstd");
        assertThat(e1.get("result_codec")).isEqualTo("zstd");
        assertThat(storedText(Field.TOOL_ARGUMENTS, "e1")).isEqualTo("{\"q\":\"x\"}");
        assertThat(storedText(Field.TOOL_RESULT, "e1")).isEqualTo("{\"r\":1}");
        var e2 = jdbc.queryForMap("SELECT * FROM chat_tool_events WHERE id = 'e2'");
        assertThat(e2.get("result_codec")).isNull();
        assertThat(storedText(Field.TOOL_RESULT, "e2")).isNull();
        assertThat(jdbc.queryForObject("SELECT arguments_codec FROM chat_tool_events WHERE id = 'e3'",
                String.class)).isNull();
    }

    @Test
    void duplicateLargeToolResultStoredOnce() {
        var big = large("重复的工具结果");
        turn(ALICE, "c1", "turn-1", "COMPLETED", 10, "问题一", "回答一");
        turn(ALICE, "c2", "turn-2", "COMPLETED", 10, "问题二", "回答二");
        toolEvent(ALICE, "c1", "turn-1", "e1", 10, "{}", big);
        toolEvent(ALICE, "c2", "turn-2", "e2", 10, "{}", big);

        compressAll();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM message_blobs WHERE tenant_id = 't1'", Integer.class))
                .isEqualTo(1);
        for (var id : List.of("e1", "e2")) {
            var row = jdbc.queryForMap("SELECT result, result_codec, result_blob, result_sha256 FROM chat_tool_events"
                    + " WHERE id = ?", id);
            assertThat(row.get("result")).isNull();
            assertThat(row.get("result_codec")).isEqualTo(MessagePayloadCodec.CODEC_REF);
            assertThat(row.get("result_blob")).isNull();
            assertThat(row.get("result_sha256")).isEqualTo(MessagePayloadCodec.sha256(big));
            assertThat(storedText(Field.TOOL_RESULT, id)).isEqualTo(big);
        }
        assertThat(counter("memory.storage.dedupe.hits")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT plain_bytes FROM message_blobs", Integer.class))
                .isEqualTo(big.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    @Test
    void sharedBlobGoesColdWithItsNewestReference() {
        var big = large("共享的大块内容");
        turn(ALICE, "c1", "turn-1", "COMPLETED", 10, "问题", "回答");
        toolEvent(ALICE, "c1", "turn-1", "e1", 10, "{}", big);
        compressAll();
        assertThat(jdbc.queryForObject("SELECT tier FROM message_blobs", String.class)).isEqualTo("WARM");

        age("chat_tool_events", "started_at", 100);
        age("chat_messages", "created_at", 100);
        jdbc.update("UPDATE message_blobs SET newest_ref_at = TIMESTAMPADD(DAY, -100, CURRENT_TIMESTAMP(6))");
        compressAll();

        assertThat(jdbc.queryForObject("SELECT tier FROM message_blobs", String.class)).isEqualTo("COLD");
        assertThat(jdbc.queryForObject("SELECT data FROM message_blobs", byte[].class))
                .isEqualTo(MessagePayloadCodec.encode(big, 19).data());
        assertThat(storedText(Field.TOOL_RESULT, "e1")).isEqualTo(big);
    }

    @Test
    void dedupeNeverCrossesTenants() {
        var big = large("两个租户相同的内容");
        turn(ALICE, "c1", "turn-1", "COMPLETED", 10, "问题", "回答");
        turn(BOB, "c2", "turn-2", "COMPLETED", 10, "问题", "回答");
        toolEvent(ALICE, "c1", "turn-1", "e1", 10, "{}", big);
        toolEvent(BOB, "c2", "turn-2", "e2", 10, "{}", big);

        compressAll();

        assertThat(jdbc.queryForList("SELECT tenant_id FROM message_blobs ORDER BY tenant_id", String.class))
                .containsExactly("t1", "t2");
        assertThat(counter("memory.storage.dedupe.hits")).isZero();
        assertThat(storedText(Field.TOOL_RESULT, "e1")).isEqualTo(big);
        assertThat(storedText(Field.TOOL_RESULT, "e2")).isEqualTo(big);
        jdbc.update("DELETE FROM message_blobs WHERE tenant_id = 't2'");
        assertThat(storedText(Field.TOOL_RESULT, "e1")).as("t1 never reads t2's blob").isEqualTo(big);
    }

    @Test
    void softDeletedMessagesStayCompressedAndReadable() {
        turn(ALICE, "c1", "turn-1", "COMPLETED", 10, "删除前的问题", "删除前的回答");
        turn(ALICE, "c2", "turn-2", "COMPLETED", 10, "被遗忘的问题", "被遗忘的回答");
        jdbc.update("UPDATE sessions SET is_valid = 0 WHERE id = 'c1'");
        jdbc.update("UPDATE chat_messages SET is_valid = 0 WHERE conversation_id = 'c1'");
        jdbc.update("UPDATE chat_turns SET is_valid = 0 WHERE conversation_id = 'c1'");
        jdbc.update("UPDATE chat_turns SET memory_command = 'FORGET' WHERE id = 'turn-2'");

        assertThat(compressAll()).isEqualTo(4);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE conversation_id = 'c1'",
                Integer.class)).as("never physically deleted").isEqualTo(2);
        assertThat(messageRow("turn-1-user").get("payload_codec")).isEqualTo("zstd");
        assertThat(storedText(Field.MESSAGE_PAYLOAD, "turn-1-user"))
                .isEqualTo(conversations.serialize(LlmClient.Message.user("删除前的问题")));
        assertThat(messageRow("turn-2-user").get("payload_codec")).isEqualTo("zstd");
        assertThat(conversations.assistantText("turn-1").block()).as("soft-deleted rows are not served").isNull();
    }

    @Test
    void compressionJobIsIdempotentAndResumable() {
        props.getStorage().setBatch(3);
        for (int i = 1; i <= 4; i++) {
            turn(ALICE, "c1", "turn-" + i, "COMPLETED", 10, "问题" + i, "回答" + i);
        }
        var db = fixture.databaseClient();
        var worker = new MemoryJobWorker(db, () -> false);
        var scanner = new MessageStorageScanner(db, new MemoryJobQueue(db), worker, compressor, props, () -> false);

        assertThat(scanner.scan().block()).isEqualTo(1);
        assertThat(scanner.scan().block()).as("one open job per conversation").isZero();
        assertThat(worker.runPending(10).block()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE payload_codec IS NOT NULL",
                Integer.class)).as("bounded by batch").isEqualTo(3);

        int rounds = 0;
        while (scanner.scan().block() > 0) {
            worker.runPending(10).block();
            assertThat(++rounds).isLessThan(10);
        }
        assertThat(rounds).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_messages WHERE payload_codec IS NULL",
                Integer.class)).isZero();
        var before = jdbc.queryForList("SELECT payload_blob FROM chat_messages ORDER BY id", byte[].class);

        assertThat(compressor.compressConversation(ALICE, "c1", 100, () -> reactor.core.publisher.Mono.just(true))
                .block()).as("idempotent").isZero();
        assertThat(jdbc.queryForList("SELECT payload_blob FROM chat_messages ORDER BY id", byte[].class))
                .containsExactlyElementsOf(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memory_jobs WHERE job_type = ? AND status = 'DONE'",
                Integer.class, JobType.COMPRESS_MESSAGES.name())).isEqualTo(3);
        for (int i = 1; i <= 4; i++) {
            assertThat(conversations.assistantText("turn-" + i).block()).isEqualTo("回答" + i);
        }
    }

    @Test
    void aRowChangedMeanwhileIsLeftForTheNextRun() {
        turn(ALICE, "c1", "turn-1", "COMPLETED", 10, "问题", "回答");
        var once = new java.util.concurrent.atomic.AtomicBoolean();
        compressor.beforeWrite(() -> {
            if (once.compareAndSet(false, true)) {
                jdbc.update("UPDATE chat_messages SET payload = ?, updated_at = TIMESTAMPADD(SECOND, 1,"
                        + " CURRENT_TIMESTAMP(6)) WHERE id = 'turn-1-assistant'",
                        conversations.serialize(LlmClient.Message.assistantText("改过的回答")));
            }
        });

        var changed = compressor.compressConversation(ALICE, "c1", 100, () -> reactor.core.publisher.Mono.just(true))
                .block();

        assertThat(changed).isEqualTo(1);
        assertThat(conversations.assistantText("turn-1").block()).isEqualTo("改过的回答");
        assertThat(compressAll()).isEqualTo(1);
        assertThat(conversations.assistantText("turn-1").block()).isEqualTo("改过的回答");
    }

    @Test
    void writingTextAgainReplacesTheCompressedValue() {
        turn(ALICE, "c1", "turn-1", "COMPLETED", 10, "问题", "回答");
        toolEvent(ALICE, "c1", "turn-1", "e1", 10, "{}", "{\"r\":1}");
        jdbc.update("UPDATE chat_tool_events SET status = 'UNCERTAIN'");
        compressAll();

        conversations.saveAssistantText(new io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket(ALICE, "c1",
                "turn-1", 0), "新的回答").block();
        new io.github.sukimiya.agileagent.memory.ToolEventRepository(fixture.databaseClient())
                .finish(new io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket(ALICE, "c1", "turn-1", 0),
                        "e1", io.github.sukimiya.agileagent.memory.ToolEventRepository.Status.SUCCEEDED,
                        "{\"r\":2}", null)
                .block();

        assertThat(messageRow("turn-1-assistant").get("payload_codec")).isNull();
        assertThat(conversations.assistantText("turn-1").block()).isEqualTo("新的回答");
        assertThat(jdbc.queryForObject("SELECT result_codec FROM chat_tool_events", String.class)).isNull();
        assertThat(storedText(Field.TOOL_RESULT, "e1")).isEqualTo("{\"r\":2}");
        assertThat(storedText(Field.TOOL_ARGUMENTS, "e1")).isEqualTo("{}");
    }

    @Test
    void disabledStorageDoesNothing() {
        props.getStorage().setEnabled(false);
        turn(ALICE, "c1", "turn-1", "COMPLETED", 100, "问题", "回答");
        var db = fixture.databaseClient();
        var scanner = new MessageStorageScanner(db, new MemoryJobQueue(db), new MemoryJobWorker(db, () -> false),
                compressor, props, () -> false);

        assertThat(scanner.scan().block()).isZero();
        assertThat(compressAll()).isZero();
        assertThat(scanner.isAutoStartup()).isFalse();
        assertThat(messageRow("turn-1-user").get("payload_codec")).isNull();
    }

    @Test
    void scanPausesWhileAnotherInstanceIsLive() {
        turn(ALICE, "c1", "turn-1", "COMPLETED", 10, "问题", "回答");
        var db = fixture.databaseClient();
        var scanner = new MessageStorageScanner(db, new MemoryJobQueue(db), new MemoryJobWorker(db, () -> false),
                compressor, props, () -> true);

        assertThat(scanner.scan().block()).isZero();
    }

    @Test
    void ageZeroCompressesAFinishedTurnImmediately() {
        props.getStorage().setCompressAfter(Duration.ZERO);
        props.getStorage().setRecompressAfter(Duration.ofDays(3650));
        turn(ALICE, "c1", "turn-1", "COMPLETED", 0, "刚说的话", "刚给的回答");

        assertThat(compressAll()).isEqualTo(2);
        assertThat(messageRow("turn-1-user").get("payload_tier")).isEqualTo("WARM");

        props.getStorage().setRecompressAfter(Duration.ZERO);
        assertThat(compressAll()).isEqualTo(2);
        assertThat(messageRow("turn-1-user").get("payload_tier")).isEqualTo("COLD");
        assertThat(conversations.assistantText("turn-1").block()).isEqualTo("刚给的回答");
    }
}
