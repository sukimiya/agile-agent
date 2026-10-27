package io.github.sukimiya.agileagent.memory.summary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.memory.ConversationRepository;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.summary.MemoryRecord.Entity;
import io.github.sukimiya.agileagent.memory.summary.MemoryRecord.KeyFact;
import io.github.sukimiya.agileagent.memory.summary.MemoryRecord.MemoryType;
import io.github.sukimiya.agileagent.service.LlmClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Turn memory records in {@code memory_summaries} (one per completed turn, D18) and the record position in
 * {@code memory_index_progress}. Every read is scoped to the owner {@code (tenantId, subject)} and joins the live
 * session, so a tombstoned conversation's records, originals and search hits are unusable from the moment of
 * deletion, before cleanup removes the rows. Records are a search index only: nothing here feeds a prompt.
 */
@Repository
public class SummaryRepository {

    /** Live session of the owner; appended to reads that must not see deleted conversations. */
    private static final String LIVE_SESSION = " JOIN sessions se ON se.id = %s.conversation_id"
            + " AND se.tenant_id = %<s.tenant_id AND se.user_id = %<s.subject AND se.is_valid = 1"
            + " AND %<s.is_valid = 1";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** A search hit: the turn whose record matched, and its relevance (higher is better). */
    public record RecordHit(String conversationId, String turnId, double score) {}

    private final DatabaseClient db;
    private final ConversationRepository conversations;
    private final boolean mysql;

    public SummaryRepository(DatabaseClient db, ConversationRepository conversations) {
        this.db = db;
        this.conversations = conversations;
        this.mysql = db.getConnectionFactory().getMetadata().getName().toLowerCase(Locale.ROOT).contains("mysql");
    }

    /** Current (not superseded) records of the conversation, oldest turn first. */
    public Flux<MemoryRecord> recordsOf(MemoryScope scope, String conversationId) {
        return db.sql("SELECT s.id, s.turn_id, s.from_sequence, s.to_sequence, s.from_message_id, s.to_message_id,"
                        + " s.record_json, s.generator_model, s.schema_version, s.version FROM memory_summaries s"
                        + LIVE_SESSION.formatted("s")
                        + " WHERE s.conversation_id = :c AND s.tenant_id = :tenantId AND s.subject = :subject"
                        + " AND s.superseded_at IS NULL ORDER BY s.from_sequence")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map(row -> parse(row.get("id", String.class), row.get("turn_id", String.class),
                        number(row.get("from_sequence")), number(row.get("to_sequence")),
                        row.get("from_message_id", String.class), row.get("to_message_id", String.class),
                        row.get("record_json", String.class), row.get("generator_model", String.class),
                        row.get("schema_version", String.class), (int) number(row.get("version"))))
                .all();
    }

    /** The original messages of one completed turn, in order: what a record hit expands to. */
    public Flux<LlmClient.Message> originalTurn(MemoryScope scope, String conversationId, String turnId) {
        return db.sql("SELECT m.payload FROM chat_messages m JOIN chat_turns t ON t.id = m.turn_id"
                        + LIVE_SESSION.formatted("m")
                        + " WHERE m.conversation_id = :c AND m.tenant_id = :tenantId AND m.subject = :subject"
                        + " AND m.turn_id = :turn AND t.status = 'COMPLETED' ORDER BY m.sequence")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("turn", turnId)
                .map(row -> conversations.deserialize(row.get("payload", String.class)))
                .all();
    }

    /**
     * Current records of the owner's live {@code conversationIds} whose search text matches {@code query}, best
     * first, at most {@code limit}. MySQL: ngram FULLTEXT, the query as one boolean-mode phrase (D7); H2: substring
     * match (D8). Deterministic exact matching of codes is Task 5's; Task 5 builds recall on this.
     */
    public Flux<RecordHit> search(MemoryScope scope, List<String> conversationIds, String query, int limit) {
        var text = query == null ? "" : query.strip();
        if (conversationIds.isEmpty() || text.isEmpty() || limit <= 0) {
            return Flux.empty();
        }
        var like = "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var phrase = "\"" + text.replace("\"", " ") + "\"";
        var match = "MATCH (s.search_text) AGAINST (:query IN BOOLEAN MODE)";
        var score = mysql ? match : "1";
        var where = mysql ? match + " > 0" : "s.search_text LIKE :query ESCAPE '!'";
        var spec = db.sql("SELECT s.conversation_id, s.turn_id, " + score + " AS score FROM memory_summaries s"
                        + LIVE_SESSION.formatted("s")
                        + " WHERE s.tenant_id = :tenantId AND s.subject = :subject AND s.conversation_id IN (:ids)"
                        + " AND s.superseded_at IS NULL AND " + where
                        + " ORDER BY score DESC, s.to_sequence DESC LIMIT :limit")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("ids", List.copyOf(conversationIds)).bind("query", mysql ? phrase : like)
                .bind("limit", limit);
        return spec.map(row -> new RecordHit(row.get("conversation_id", String.class),
                        row.get("turn_id", String.class), ((Number) row.get("score")).doubleValue()))
                .all();
    }

    /** How far the conversation has records (0 when never). */
    public Mono<Long> summarizedThrough(MemoryScope scope, String conversationId) {
        return db.sql("SELECT summarized_through_sequence AS p FROM memory_index_progress WHERE conversation_id = :c"
                        + " AND tenant_id = :tenantId AND subject = :subject")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map(row -> number(row.get("p")))
                .one()
                .defaultIfEmpty(0L);
    }

    /** Whether the owner's conversation exists and is not deleted. */
    public Mono<Boolean> isLive(MemoryScope scope, String conversationId) {
        return db.sql("SELECT COUNT(*) AS n FROM sessions WHERE id = :c AND tenant_id = :tenantId"
                        + " AND user_id = :subject AND is_valid = 1")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map(row -> number(row.get("n")) == 1)
                .one();
    }

    /**
     * Stores {@code record} and moves the record position from {@code expectedThrough} to the record's end, as
     * one compare-and-set: false (nothing written) when the position moved meanwhile or the conversation is
     * deleted. Run it inside a transaction so the record and the position commit together; the session row is
     * locked first, the same order as the deletion transaction. The version is one above the turn's previous
     * records, which are marked superseded. The invalid-output count is reset.
     */
    public Mono<Boolean> save(MemoryScope scope, String conversationId, MemoryRecord record, long expectedThrough) {
        return lockLiveSession(scope, conversationId)
                .flatMap(live -> !live ? Mono.just(false)
                        : advance(scope, conversationId, expectedThrough, record.toSequence())
                                .flatMap(moved -> !moved ? Mono.just(false)
                                        : insert(scope, conversationId, record).thenReturn(true)));
    }

    /** Moves the position without a record (no completed messages, or a skipped turn); same CAS as save. */
    public Mono<Boolean> skip(MemoryScope scope, String conversationId, long expectedThrough, long through) {
        return lockLiveSession(scope, conversationId)
                .flatMap(live -> !live ? Mono.just(false) : advance(scope, conversationId, expectedThrough, through));
    }

    /**
     * Counts one more invalid model output for {@code turnId} (D23) and emits the turn's consecutive count; a
     * different turn starts again at 1. Empty when the conversation is deleted. Run it inside a transaction.
     */
    public Mono<Integer> recordInvalid(MemoryScope scope, String conversationId, String turnId) {
        return lockLiveSession(scope, conversationId)
                .flatMap(live -> !live ? Mono.<Integer>empty() : ensureProgress(scope, conversationId)
                        // Assignment order matters on MySQL (left to right): the count reads the old turn id.
                        .then(db.sql("UPDATE memory_index_progress SET summary_invalid_attempts = CASE WHEN"
                                        + " summary_invalid_turn_id = :turn THEN summary_invalid_attempts + 1 ELSE 1"
                                        + " END, summary_invalid_turn_id = :turn, updated_at = CURRENT_TIMESTAMP(6)"
                                        + " WHERE conversation_id = :c AND tenant_id = :tenantId AND subject = :subject")
                                .bind("turn", turnId).bind("c", conversationId).bind("tenantId", scope.tenantId())
                                .bind("subject", scope.subject())
                                .fetch().rowsUpdated())
                        .then(db.sql("SELECT summary_invalid_attempts AS n FROM memory_index_progress"
                                        + " WHERE conversation_id = :c AND tenant_id = :tenantId AND subject = :subject")
                                .bind("c", conversationId).bind("tenantId", scope.tenantId())
                                .bind("subject", scope.subject())
                                .map(row -> (int) number(row.get("n"))).one()));
    }

    private Mono<Boolean> lockLiveSession(MemoryScope scope, String conversationId) {
        return db.sql("SELECT id FROM sessions WHERE id = :c AND tenant_id = :tenantId AND user_id = :subject"
                        + " AND is_valid = 1 FOR UPDATE")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map(row -> row.get("id", String.class))
                .all().hasElements();
    }

    private Mono<Void> ensureProgress(MemoryScope scope, String conversationId) {
        return db.sql("INSERT INTO memory_index_progress (conversation_id, tenant_id, subject,"
                        + " indexed_through_sequence, summarized_through_sequence, updated_at)"
                        + " VALUES (:c, :tenantId, :subject, 0, 0, CURRENT_TIMESTAMP(6))")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .fetch().rowsUpdated().then()
                .onErrorResume(SummaryRepository::isDuplicateKey, e -> Mono.empty());
    }

    private Mono<Boolean> advance(MemoryScope scope, String conversationId, long expected, long through) {
        return ensureProgress(scope, conversationId).then(db.sql("UPDATE memory_index_progress SET"
                        + " summarized_through_sequence = :through, summary_invalid_turn_id = NULL,"
                        + " summary_invalid_attempts = 0, updated_at = CURRENT_TIMESTAMP(6) WHERE conversation_id = :c"
                        + " AND tenant_id = :tenantId AND subject = :subject AND summarized_through_sequence = :expected")
                .bind("through", through).bind("expected", expected)
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .fetch().rowsUpdated().map(n -> n == 1));
    }

    private Mono<Void> insert(MemoryScope scope, String conversationId, MemoryRecord record) {
        var ofTurn = " WHERE conversation_id = :c AND turn_id = :turn";
        return db.sql("SELECT COALESCE(MAX(version), 0) AS v FROM memory_summaries" + ofTurn)
                .bind("c", conversationId).bind("turn", record.turnId())
                .map(row -> number(row.get("v"))).one()
                .flatMap(previous -> db.sql("UPDATE memory_summaries SET superseded_at = CURRENT_TIMESTAMP(6)"
                                + ofTurn + " AND superseded_at IS NULL")
                        .bind("c", conversationId).bind("turn", record.turnId())
                        .fetch().rowsUpdated()
                        .then(db.sql("INSERT INTO memory_summaries (id, tenant_id, subject, conversation_id, turn_id,"
                                        + " from_sequence, to_sequence, from_message_id, to_message_id, record_json,"
                                        + " search_text, memory_type, has_decision, source_type, generator_model,"
                                        + " schema_version, version, created_at) VALUES (:id, :tenantId, :subject, :c,"
                                        + " :turn, :from, :to, :fromId, :toId, :json, :search, :type, :decision,"
                                        + " 'MODEL_SUMMARY', :model, :schema, :version, CURRENT_TIMESTAMP(6))")
                                .bind("id", record.id()).bind("tenantId", scope.tenantId())
                                .bind("subject", scope.subject()).bind("c", conversationId)
                                .bind("turn", record.turnId())
                                .bind("from", record.fromSequence()).bind("to", record.toSequence())
                                .bind("fromId", record.fromMessageId()).bind("toId", record.toMessageId())
                                .bind("json", recordJson(record)).bind("search", searchText(record))
                                .bind("type", record.memoryType().name()).bind("decision", record.decision())
                                .bind("model", record.generatorModel()).bind("schema", record.schemaVersion())
                                .bind("version", previous + 1)
                                .fetch().rowsUpdated().then()));
    }

    /** The stored {@code memory-record-v1} JSON, with the server-filled source. */
    static String recordJson(MemoryRecord record) {
        var json = new LinkedHashMap<String, Object>();
        json.put("schema", record.schemaVersion());
        json.put("summary", record.summary());
        json.put("entities", record.entities().stream()
                .map(e -> Map.of("name", e.name(), "type", e.type())).toList());
        json.put("key_facts", record.keyFacts().stream()
                .map(f -> Map.of("text", f.text(), "kind", f.kind())).toList());
        json.put("decision", record.decision());
        json.put("keywords", record.keywords());
        json.put("memory_type", record.memoryType().name());
        var source = new LinkedHashMap<String, Object>();
        source.put("turn_id", record.turnId());
        source.put("from_sequence", record.fromSequence());
        source.put("to_sequence", record.toSequence());
        json.put("source", source);
        try {
            return JSON.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("memory record not serializable", e);
        }
    }

    /** What full-text search looks at: summary, entity names, key facts and keywords, one per line. */
    static String searchText(MemoryRecord record) {
        var lines = new ArrayList<String>();
        lines.add(record.summary());
        record.entities().forEach(e -> lines.add(e.name()));
        record.keyFacts().forEach(f -> lines.add(f.text()));
        lines.addAll(record.keywords());
        return String.join("\n", lines);
    }

    private static MemoryRecord parse(String id, String turnId, long from, long to, String fromId, String toId,
            String json, String model, String schema, int version) {
        try {
            JsonNode node = JSON.readTree(json);
            var entities = new ArrayList<Entity>();
            node.path("entities").forEach(e -> entities.add(new Entity(e.path("name").asText(), e.path("type").asText())));
            var facts = new ArrayList<KeyFact>();
            node.path("key_facts").forEach(f -> facts.add(new KeyFact(f.path("text").asText(), f.path("kind").asText())));
            var keywords = new ArrayList<String>();
            node.path("keywords").forEach(k -> keywords.add(k.asText()));
            return new MemoryRecord(id, turnId, from, to, fromId, toId, node.path("summary").asText(), entities, facts,
                    node.path("decision").asBoolean(), keywords, MemoryType.valueOf(node.path("memory_type").asText()),
                    model, schema, version);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored memory record is not JSON: " + id, e);
        }
    }

    static long number(Object value) {
        return ((Number) value).longValue();
    }

    private static boolean isDuplicateKey(Throwable error) {
        for (var cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }
}
