package io.github.sukimiya.agileagent.memory.summary;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.github.sukimiya.agileagent.memory.ConversationRepository;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.service.LlmClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@code memory_summaries} and the summary position in {@code memory_index_progress}. Every read is scoped to the
 * owner {@code (tenantId, subject)} and joins the session, so a tombstoned conversation's summaries and originals
 * are unusable from the moment of deletion, before cleanup removes the rows.
 */
@Repository
public class SummaryRepository {

    /** Live session of the owner; appended to reads that must not see deleted conversations. */
    private static final String LIVE_SESSION = " JOIN sessions se ON se.id = %s.conversation_id"
            + " AND se.tenant_id = %<s.tenant_id AND se.user_id = %<s.subject AND se.deleted_at IS NULL";

    private final DatabaseClient db;
    private final ConversationRepository conversations;

    public SummaryRepository(DatabaseClient db, ConversationRepository conversations) {
        this.db = db;
        this.conversations = conversations;
    }

    /** Current (not superseded) summaries ending before {@code beforeSequence}, oldest first. */
    public Flux<Summary> latestCovering(MemoryScope scope, String conversationId, long beforeSequence) {
        return db.sql("SELECT s.id, s.from_sequence, s.to_sequence, s.from_message_id, s.to_message_id, s.content,"
                        + " s.generator_model, s.prompt_version, s.version FROM memory_summaries s"
                        + LIVE_SESSION.formatted("s")
                        + " WHERE s.conversation_id = :c AND s.tenant_id = :tenantId AND s.subject = :subject"
                        + " AND s.superseded_at IS NULL AND s.to_sequence < :before ORDER BY s.from_sequence")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("before", beforeSequence)
                .map(row -> new Summary(row.get("id", String.class), number(row.get("from_sequence")),
                        number(row.get("to_sequence")), row.get("from_message_id", String.class),
                        row.get("to_message_id", String.class), row.get("content", String.class),
                        row.get("generator_model", String.class), row.get("prompt_version", String.class),
                        (int) number(row.get("version"))))
                .all();
    }

    /** The original messages a summary covers: messages of completed turns in its range, in order. */
    public Flux<LlmClient.Message> originalMessages(MemoryScope scope, String conversationId, Summary summary) {
        return db.sql("SELECT m.payload FROM chat_messages m JOIN chat_turns t ON t.id = m.turn_id"
                        + LIVE_SESSION.formatted("m")
                        + " WHERE m.conversation_id = :c AND m.tenant_id = :tenantId AND m.subject = :subject"
                        + " AND t.status = 'COMPLETED' AND m.sequence BETWEEN :from AND :to ORDER BY m.sequence")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("from", summary.fromSequence()).bind("to", summary.toSequence())
                .map(row -> conversations.deserialize(row.get("payload", String.class)))
                .all();
    }

    /** How far the conversation is summarized (0 when never). */
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
                        + " AND user_id = :subject AND deleted_at IS NULL")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map(row -> number(row.get("n")) == 1)
                .one();
    }

    /**
     * Stores {@code summary} and moves the summary position from {@code expectedThrough} to its end, as one
     * compare-and-set: false (nothing written) when the position moved meanwhile or the conversation is deleted.
     * Run it inside a transaction so the summary and the position commit together; the session row is locked
     * first, the same order as the deletion transaction. The version is one above the range's previous ones,
     * which are marked superseded.
     */
    public Mono<Boolean> save(MemoryScope scope, String conversationId, Summary summary, long expectedThrough) {
        return lockLiveSession(scope, conversationId)
                .flatMap(live -> !live ? Mono.just(false)
                        : advance(scope, conversationId, expectedThrough, summary.toSequence())
                                .flatMap(moved -> !moved ? Mono.just(false)
                                        : insert(scope, conversationId, summary).thenReturn(true)));
    }

    /** Moves the position without a summary (a range with no completed messages); same CAS as {@link #save}. */
    public Mono<Boolean> skip(MemoryScope scope, String conversationId, long expectedThrough, long through) {
        return lockLiveSession(scope, conversationId)
                .flatMap(live -> !live ? Mono.just(false) : advance(scope, conversationId, expectedThrough, through));
    }

    private Mono<Boolean> lockLiveSession(MemoryScope scope, String conversationId) {
        return db.sql("SELECT id FROM sessions WHERE id = :c AND tenant_id = :tenantId AND user_id = :subject"
                        + " AND deleted_at IS NULL FOR UPDATE")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map(row -> row.get("id", String.class))
                .all().hasElements();
    }

    private Mono<Boolean> advance(MemoryScope scope, String conversationId, long expected, long through) {
        Mono<Void> ensureRow = db.sql("INSERT INTO memory_index_progress (conversation_id, tenant_id, subject,"
                        + " indexed_through_sequence, summarized_through_sequence, updated_at)"
                        + " VALUES (:c, :tenantId, :subject, 0, 0, CURRENT_TIMESTAMP(6))")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .fetch().rowsUpdated().then()
                .onErrorResume(SummaryRepository::isDuplicateKey, e -> Mono.empty());
        return ensureRow.then(db.sql("UPDATE memory_index_progress SET summarized_through_sequence = :through,"
                        + " updated_at = CURRENT_TIMESTAMP(6) WHERE conversation_id = :c AND tenant_id = :tenantId"
                        + " AND subject = :subject AND summarized_through_sequence = :expected")
                .bind("through", through).bind("expected", expected)
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .fetch().rowsUpdated().map(n -> n == 1));
    }

    private Mono<Void> insert(MemoryScope scope, String conversationId, Summary summary) {
        var range = " WHERE conversation_id = :c AND from_sequence = :from AND to_sequence = :to";
        return db.sql("SELECT COALESCE(MAX(version), 0) AS v FROM memory_summaries" + range)
                .bind("c", conversationId).bind("from", summary.fromSequence()).bind("to", summary.toSequence())
                .map(row -> number(row.get("v"))).one()
                .flatMap(previous -> db.sql("UPDATE memory_summaries SET superseded_at = CURRENT_TIMESTAMP(6)"
                                + range + " AND superseded_at IS NULL")
                        .bind("c", conversationId).bind("from", summary.fromSequence())
                        .bind("to", summary.toSequence())
                        .fetch().rowsUpdated()
                        .then(db.sql("INSERT INTO memory_summaries (id, tenant_id, subject, conversation_id,"
                                        + " from_sequence, to_sequence, from_message_id, to_message_id, content,"
                                        + " source_type, generator_model, prompt_version, version, created_at)"
                                        + " VALUES (:id, :tenantId, :subject, :c, :from, :to, :fromId, :toId,"
                                        + " :content, 'MODEL_SUMMARY', :model, :prompt, :version,"
                                        + " CURRENT_TIMESTAMP(6))")
                                .bind("id", summary.id()).bind("tenantId", scope.tenantId())
                                .bind("subject", scope.subject()).bind("c", conversationId)
                                .bind("from", summary.fromSequence()).bind("to", summary.toSequence())
                                .bind("fromId", summary.fromMessageId()).bind("toId", summary.toMessageId())
                                .bind("content", summary.content()).bind("model", summary.generatorModel())
                                .bind("prompt", summary.promptVersion()).bind("version", previous + 1)
                                .fetch().rowsUpdated().then()));
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
