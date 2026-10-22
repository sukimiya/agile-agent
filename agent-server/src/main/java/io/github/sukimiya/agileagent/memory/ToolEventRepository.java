package io.github.sukimiya.agileagent.memory;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import reactor.core.publisher.Mono;

/**
 * {@code chat_tool_events} access. One row per (turn, call id): a call id repeated inside the same turn updates
 * its row, while the same call id in another turn is a different event. Start records are fenced on the owning
 * turn (current fence, active, unexpired lease) so a stale executor cannot record, and therefore cannot run, a
 * tool. Outcome and uncertainty updates are scoped to tenant, subject and turn but not fenced: what happened to
 * a call that was already issued is recorded even after the execution right moved on.
 */
@Repository
public class ToolEventRepository {

    public enum Status { STARTED, SUCCEEDED, FAILED, UNCERTAIN }

    private static final String TURN_OWNED = "EXISTS (SELECT 1 FROM chat_turns t WHERE t.id = :turnId"
            + " AND t.fence = :fence AND t.status IN " + TurnRepository.ACTIVE
            + " AND t.lease_expires_at > CURRENT_TIMESTAMP(6) AND t.tenant_id = :tenantId AND t.subject = :subject)";
    private static final String SCOPED = "turn_id = :turnId AND call_id = :callId AND tenant_id = :tenantId"
            + " AND subject = :subject";

    private final DatabaseClient db;

    public ToolEventRepository(DatabaseClient db) {
        this.db = db;
    }

    /**
     * Records that a call is about to execute (status STARTED, outcome cleared). Returns false when the ticket no
     * longer owns the turn; nothing is written then and the tool must not run.
     */
    public Mono<Boolean> upsertStarted(TurnTicket ticket, String capability, String toolName, String callId,
            String arguments, SourceType source) {
        var update = db.sql("UPDATE chat_tool_events SET capability = :capability, tool_name = :toolName,"
                        + " status = 'STARTED', source_type = :source, arguments = :arguments, result = NULL,"
                        + " error_type = NULL, started_at = CURRENT_TIMESTAMP(6), updated_at = CURRENT_TIMESTAMP(6),"
                        + " finished_at = NULL WHERE " + SCOPED + " AND " + TURN_OWNED)
                .bind("capability", capability).bind("toolName", toolName).bind("source", source.name())
                .bind("callId", callId).bind("fence", ticket.fence());
        update = bindScope(nullable(update, "arguments", arguments), ticket);
        return update.fetch().rowsUpdated().flatMap(updated -> updated > 0 ? Mono.just(true) : insert(ticket,
                capability, toolName, callId, arguments, source));
    }

    private Mono<Boolean> insert(TurnTicket ticket, String capability, String toolName, String callId,
            String arguments, SourceType source) {
        var insert = db.sql("INSERT INTO chat_tool_events (id, tenant_id, subject, conversation_id, turn_id, call_id,"
                        + " capability, tool_name, status, source_type, arguments, started_at, updated_at)"
                        + " SELECT :id, :tenantId, :subject, :conversationId, :turnId, :callId, :capability, :toolName,"
                        + " 'STARTED', :source, :arguments, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)"
                        + " FROM chat_turns WHERE id = :turnId AND " + TURN_OWNED)
                .bind("id", NonBlockingIds.uuid()).bind("conversationId", ticket.conversationId())
                .bind("callId", callId).bind("capability", capability).bind("toolName", toolName)
                .bind("source", source.name()).bind("fence", ticket.fence());
        insert = bindScope(nullable(insert, "arguments", arguments), ticket);
        return insert.fetch().rowsUpdated().map(n -> n == 1);
    }

    /** Records the outcome of a STARTED (or already UNCERTAIN) call; false when there is no such open call. */
    public Mono<Boolean> finish(TurnTicket ticket, String callId, Status status, String result, String errorType) {
        if (status != Status.SUCCEEDED && status != Status.FAILED) {
            return Mono.error(new IllegalArgumentException("not an outcome: " + status));
        }
        var spec = db.sql("UPDATE chat_tool_events SET status = :status, result = :result, error_type = :errorType,"
                        + " finished_at = CURRENT_TIMESTAMP(6), updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE " + SCOPED + " AND status IN ('STARTED', 'UNCERTAIN')")
                .bind("status", status.name()).bind("callId", callId);
        spec = bindScope(nullable(nullable(spec, "result", result), "errorType", errorType), ticket);
        return spec.fetch().rowsUpdated().map(n -> n > 0);
    }

    /** Marks one open call UNCERTAIN: it was issued but its outcome is unknown. Recorded outcomes are kept. */
    public Mono<Boolean> markUncertain(TurnTicket ticket, String callId) {
        var spec = db.sql("UPDATE chat_tool_events SET status = 'UNCERTAIN', updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE " + SCOPED + " AND status = 'STARTED'")
                .bind("callId", callId);
        return bindScope(spec, ticket).fetch().rowsUpdated().map(n -> n > 0);
    }

    /**
     * Marks every still-open call of a turn UNCERTAIN. Runs inside each terminal transition of the turn (finish,
     * cancel, interruption, recovery), so no call is left looking as if it were still running.
     */
    public static Mono<Long> markOpenUncertain(DatabaseClient db, String turnId) {
        return db.sql("UPDATE chat_tool_events SET status = 'UNCERTAIN', updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE turn_id = :turnId AND status = 'STARTED'")
                .bind("turnId", turnId).fetch().rowsUpdated();
    }

    private static DatabaseClient.GenericExecuteSpec bindScope(DatabaseClient.GenericExecuteSpec spec, TurnTicket ticket) {
        return spec.bind("turnId", ticket.turnId()).bind("tenantId", ticket.scope().tenantId())
                .bind("subject", ticket.scope().subject());
    }

    private static DatabaseClient.GenericExecuteSpec nullable(DatabaseClient.GenericExecuteSpec spec, String name,
            String value) {
        return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
    }
}
