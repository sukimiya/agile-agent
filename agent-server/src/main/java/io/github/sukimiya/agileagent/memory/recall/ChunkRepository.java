package io.github.sukimiya.agileagent.memory.recall;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.github.sukimiya.agileagent.memory.ConversationRepository;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reads for recall over {@code memory_chunks} and the original turns. Every read is scoped to the owner
 * {@code (tenantId, subject)} and joins the live session, so a tombstoned conversation's chunks and turns are
 * unusable from the moment of deletion, before cleanup removes them (the index may lag, recall never does).
 */
@Repository
public class ChunkRepository {

    static final String LIVE_SESSION = " JOIN sessions se ON se.id = %s.conversation_id"
            + " AND se.tenant_id = %<s.tenant_id AND se.user_id = %<s.subject AND se.is_valid = 1"
            + " AND %<s.is_valid = 1";

    /** One completed turn as recall sees it: the user message and the final answer, with its message range. */
    public record TurnText(String turnId, String conversationId, long fromSequence, long toSequence,
            long answerSequence, String user, String answer, LocalDateTime finishedAt) {

        /** The original turn as indexed and injected: {@code 用户：…\n助手：…}. */
        public String content() {
            return answer == null ? "用户：" + user : "用户：" + user + "\n助手：" + answer;
        }
    }

    /** A chunk row. */
    public record ChunkRow(String id, String turnId, String conversationId, long fromSequence, long toSequence,
            String content) {}

    private final DatabaseClient db;
    private final ConversationRepository conversations;

    public ChunkRepository(DatabaseClient db, ConversationRepository conversations) {
        this.db = db;
        this.conversations = conversations;
    }

    DatabaseClient db() {
        return db;
    }

    /**
     * The completed turns among {@code turnIds} that belong to the owner's live {@code conversationIds}; turns
     * that are not completed, foreign or deleted are absent. Order of {@code turnIds} is not kept.
     */
    public Flux<TurnText> turnTexts(MemoryScope scope, List<String> conversationIds, List<String> turnIds) {
        if (conversationIds.isEmpty() || turnIds.isEmpty()) {
            return Flux.empty();
        }
        return db.sql("SELECT m.turn_id, m.conversation_id, m.sequence, m.role, m.payload, t.finished_at"
                        + " FROM chat_messages m JOIN chat_turns t ON t.id = m.turn_id" + LIVE_SESSION.formatted("m")
                        + " WHERE m.tenant_id = :tenantId AND m.subject = :subject AND m.conversation_id IN (:ids)"
                        + " AND m.turn_id IN (:turns) AND t.status = 'COMPLETED' AND t.tenant_id = m.tenant_id"
                        + " AND t.subject = m.subject ORDER BY m.turn_id, m.sequence")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("ids", List.copyOf(conversationIds)).bind("turns", List.copyOf(turnIds))
                .map(row -> new MessageRow(row.get("turn_id", String.class), row.get("conversation_id", String.class),
                        number(row.get("sequence")), row.get("role", String.class),
                        row.get("payload", String.class), row.get("finished_at", LocalDateTime.class)))
                .all()
                .collectList()
                .flatMapIterable(this::toTurns);
    }

    private record MessageRow(String turnId, String conversationId, long sequence, String role, String payload,
            LocalDateTime finishedAt) {}

    private List<TurnText> toTurns(List<MessageRow> rows) {
        var byTurn = new LinkedHashMap<String, List<MessageRow>>();
        rows.forEach(r -> byTurn.computeIfAbsent(r.turnId(), k -> new ArrayList<>()).add(r));
        var out = new ArrayList<TurnText>();
        for (var messages : byTurn.values()) {
            String user = null;
            String answer = null;
            long answerSequence = -1;
            for (var row : messages) {
                var message = conversations.deserialize(row.payload());
                if ("user".equals(row.role()) && user == null) {
                    user = message.content();
                } else if ("assistant".equals(row.role()) && message.content() != null
                        && !message.content().isBlank()
                        && (message.toolCalls() == null || message.toolCalls().isEmpty())) {
                    answer = message.content();
                    answerSequence = row.sequence();
                }
            }
            if (user == null) {
                continue;
            }
            var first = messages.getFirst();
            var last = messages.getLast();
            out.add(new TurnText(first.turnId(), first.conversationId(), first.sequence(), last.sequence(),
                    answerSequence < 0 ? last.sequence() : answerSequence, user, answer, first.finishedAt()));
        }
        return out;
    }

    /** Chunks of the owner's live conversations whose codes include {@code code} (exact element match). */
    public Flux<ChunkRow> withEntity(MemoryScope scope, List<String> conversationIds, String code, int limit) {
        if (conversationIds.isEmpty()) {
            return Flux.empty();
        }
        var element = "%" + escapeLike("\"" + code + "\"") + "%";
        return db.sql("SELECT c.id, c.turn_id, c.conversation_id, c.from_sequence, c.to_sequence, c.content"
                        + " FROM memory_chunks c" + LIVE_SESSION.formatted("c")
                        + " WHERE c.tenant_id = :tenantId AND c.subject = :subject AND c.conversation_id IN (:ids)"
                        + " AND c.entities_json LIKE :element ESCAPE '!' ORDER BY c.to_sequence DESC LIMIT :limit")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("ids", List.copyOf(conversationIds)).bind("element", element).bind("limit", limit)
                .map(ChunkRepository::chunk)
                .all();
    }

    static ChunkRow chunk(io.r2dbc.spi.Readable row) {
        return new ChunkRow(row.get("id", String.class), row.get("turn_id", String.class),
                row.get("conversation_id", String.class), number(row.get("from_sequence")),
                number(row.get("to_sequence")), row.get("content", String.class));
    }

    /**
     * Completed turns of the owner's live conversation without chunks yet (the index lags), newest first, at most
     * {@code limit}.
     */
    public Flux<String> unindexedTurns(MemoryScope scope, String conversationId, int limit) {
        return db.sql("SELECT t.id FROM chat_turns t" + LIVE_SESSION.formatted("t")
                        + " WHERE t.conversation_id = :c AND t.tenant_id = :tenantId AND t.subject = :subject"
                        + " AND t.status = 'COMPLETED' AND NOT EXISTS (SELECT 1 FROM memory_chunks k"
                        + " WHERE k.turn_id = t.id) ORDER BY t.created_at DESC, t.id DESC LIMIT :limit")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("limit", limit)
                .map(row -> row.get("id", String.class))
                .all();
    }

    /** Whether any of the owner's live {@code conversationIds} has a completed turn without chunks. */
    public Mono<Boolean> anyUnindexed(MemoryScope scope, List<String> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Mono.just(false);
        }
        return db.sql("SELECT t.id FROM chat_turns t" + LIVE_SESSION.formatted("t")
                        + " WHERE t.conversation_id IN (:ids) AND t.tenant_id = :tenantId AND t.subject = :subject"
                        + " AND t.status = 'COMPLETED' AND NOT EXISTS (SELECT 1 FROM memory_chunks k"
                        + " WHERE k.turn_id = t.id) LIMIT 1")
                .bind("ids", List.copyOf(conversationIds)).bind("tenantId", scope.tenantId())
                .bind("subject", scope.subject())
                .map(row -> row.get("id", String.class))
                .all().hasElements();
    }

    /** The owner's other live conversations, most recently updated first, at most {@code limit}. */
    public Flux<String> otherLiveConversations(MemoryScope scope, String currentId, int limit) {
        return db.sql("SELECT id FROM sessions WHERE tenant_id = :tenantId AND user_id = :subject"
                        + " AND is_valid = 1 AND id <> :current ORDER BY updated_at DESC, id LIMIT :limit")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject()).bind("current", currentId)
                .bind("limit", limit)
                .map(row -> row.get("id", String.class))
                .all();
    }

    static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    static long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
