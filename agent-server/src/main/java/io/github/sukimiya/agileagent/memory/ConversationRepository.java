package io.github.sukimiya.agileagent.memory;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.repository.SessionRepository;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.SessionService;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import reactor.core.publisher.Mono;

/**
 * Conversation-side writes used inside turn transactions: owned session, message rows with per-conversation
 * sequence, and the session revision. Sequence allocation is safe because only the conversation's single active
 * turn appends, and {@code (conversation_id, sequence)} is unique as a backstop.
 */
@Repository
public class ConversationRepository {

    private final DatabaseClient db;
    private final ObjectMapper mapper;
    private final SessionRepository sessions;

    public ConversationRepository(DatabaseClient db, ObjectMapper mapper) {
        this.db = db;
        this.mapper = mapper;
        this.sessions = new SessionRepository(db);
    }

    /**
     * Ensures a live session owned by {@code scope}: creates it when the id is unused, otherwise fails with
     * {@link ErrorCode#CONVERSATION_NOT_FOUND} if the id belongs to anyone else, a legacy row or a deleted session.
     * A concurrent creator surfaces as a duplicate-key error for the caller to retry.
     */
    public Mono<Void> ensureOwnedSession(String conversationId, MemoryScope scope, String firstMessage) {
        // One read decides ownership, so a concurrent create by the same owner is never mistaken for a foreign row.
        return sessions.findById(conversationId)
                .map(row -> row.ownedBy(scope))
                .switchIfEmpty(Mono.defer(() -> sessions.insertScoped(conversationId, scope,
                        SessionService.title(firstMessage)).thenReturn(true)))
                .flatMap(owned -> owned ? Mono.<Void>empty()
                        : Mono.<Void>error(new MemoryException(ErrorCode.CONVERSATION_NOT_FOUND)));
    }

    public Mono<Void> bumpRevision(String conversationId) {
        return db.sql("UPDATE sessions SET revision = revision + 1, updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE id = :id AND deleted_at IS NULL")
                .bind("id", conversationId).fetch().rowsUpdated().then();
    }

    /** Serializes a message to the stored JSON form. */
    public String serialize(LlmClient.Message message) {
        try {
            return mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("message not serializable", e);
        }
    }

    public Mono<Void> appendMessage(MemoryScope scope, String conversationId, String turnId, String role,
            SourceType source, String payloadJson) {
        return db.sql("INSERT INTO chat_messages (id, tenant_id, subject, conversation_id, turn_id, sequence, role,"
                        + " source_type, payload, created_at, updated_at) SELECT :id, :tenantId, :subject, :c, :turnId,"
                        + " COALESCE(MAX(sequence), 0) + 1, :role, :source, :payload, CURRENT_TIMESTAMP(6),"
                        + " CURRENT_TIMESTAMP(6) FROM chat_messages WHERE conversation_id = :c")
                .bind("id", NonBlockingIds.uuid()).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("c", conversationId).bind("turnId", turnId).bind("role", role).bind("source", source.name())
                .bind("payload", payloadJson)
                .fetch().rowsUpdated().then();
    }

    /** Creates or replaces the turn's assistant message (checkpoints and terminal text). */
    public Mono<Void> saveAssistantText(TurnTicket ticket, String text) {
        var payload = serialize(LlmClient.Message.assistantText(text));
        return db.sql("UPDATE chat_messages SET payload = :payload, updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE turn_id = :turnId AND role = 'assistant'")
                .bind("payload", payload).bind("turnId", ticket.turnId())
                .fetch().rowsUpdated()
                .flatMap(updated -> updated > 0 ? Mono.empty()
                        : appendMessage(ticket.scope(), ticket.conversationId(), ticket.turnId(), "assistant",
                                SourceType.ASSISTANT, payload));
    }

    /** Stored assistant text of a turn, or empty when none was saved. */
    public Mono<String> assistantText(String turnId) {
        return db.sql("SELECT payload FROM chat_messages WHERE turn_id = :turnId AND role = 'assistant'")
                .bind("turnId", turnId)
                .map(row -> row.get("payload", String.class))
                .one()
                .map(payload -> deserialize(payload).content());
    }

    /** Parses a stored message payload. */
    public LlmClient.Message deserialize(String payload) {
        try {
            return mapper.readValue(payload, LlmClient.Message.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored message is not valid JSON", e);
        }
    }
}
