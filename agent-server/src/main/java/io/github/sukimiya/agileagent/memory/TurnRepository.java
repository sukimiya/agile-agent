package io.github.sukimiya.agileagent.memory;

import java.time.Duration;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@code chat_turns} access. Leases use database time only. Every executor write is a compare-and-set on
 * (id, fence, active status, unexpired lease), so a stale executor can never overwrite a newer owner or a
 * terminal state; recovery and deletion move execution rights by incrementing the fence.
 */
@Repository
public class TurnRepository {

    static final String ACTIVE = "('ACCEPTED', 'RUNNING')";
    private static final String COLUMNS = "id, tenant_id, subject, conversation_id, request_id, request_hash, status,"
            + " fence, usage_observed, prompt_tokens, completion_tokens, total_tokens,"
            + " CASE WHEN lease_expires_at IS NULL OR lease_expires_at <= CURRENT_TIMESTAMP(6) THEN 1 ELSE 0 END"
            + " AS lease_expired";
    private static final String LEASE_VALID = "lease_expires_at > CURRENT_TIMESTAMP(6)";
    private static final String NEW_LEASE = "TIMESTAMPADD(MICROSECOND, :leaseMicros, CURRENT_TIMESTAMP(6))";

    private final DatabaseClient db;
    private final MemoryProperties props;
    private final String instanceId = NonBlockingIds.uuid();

    public TurnRepository(DatabaseClient db, MemoryProperties props) {
        this.db = db;
        this.props = props;
    }

    /** One turn row as read by acceptance and recovery. */
    public record TurnRow(String id, MemoryScope scope, String conversationId, String requestId, String requestHash,
            TurnStatus status, long fence, boolean leaseExpired, TokenUsage usage) {
        TurnTicket ticket() {
            return new TurnTicket(scope, conversationId, id, fence);
        }
    }

    public Mono<TurnRow> findById(String turnId) {
        return db.sql("SELECT " + COLUMNS + " FROM chat_turns WHERE id = :id")
                .bind("id", turnId).map(TurnRepository::mapRow).one();
    }

    public Mono<TurnRow> findByRequest(String conversationId, String requestId) {
        return db.sql("SELECT " + COLUMNS + " FROM chat_turns WHERE conversation_id = :c AND request_id = :r")
                .bind("c", conversationId).bind("r", requestId).map(TurnRepository::mapRow).one();
    }

    public Mono<TurnRow> findActive(String conversationId) {
        return db.sql("SELECT " + COLUMNS + " FROM chat_turns WHERE active_conversation_id = :c")
                .bind("c", conversationId).map(TurnRepository::mapRow).one();
    }

    /** Active turns whose lease has expired, oldest first. */
    public Flux<TurnRow> findExpired(int limit) {
        return db.sql("SELECT " + COLUMNS + " FROM chat_turns WHERE status IN " + ACTIVE
                        + " AND lease_expires_at <= CURRENT_TIMESTAMP(6) ORDER BY lease_expires_at LIMIT :limit")
                .bind("limit", limit).map(TurnRepository::mapRow).all();
    }

    /**
     * Inserts an ACCEPTED turn holding the conversation's active slot with fence 1 and a fresh lease. The unique
     * constraints reject a second active turn and a reused request id.
     */
    public Mono<TurnTicket> insertAccepted(String turnId, MemoryScope scope, String conversationId, String requestId,
            String requestHash) {
        return db.sql("INSERT INTO chat_turns (id, tenant_id, subject, conversation_id, request_id, request_hash, status,"
                        + " active_conversation_id, fence, lease_owner, lease_expires_at, created_at, updated_at)"
                        + " VALUES (:id, :tenantId, :subject, :c, :r, :hash, 'ACCEPTED', :c, 1, :owner, " + NEW_LEASE
                        + ", CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))")
                .bind("id", turnId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("c", conversationId).bind("r", requestId).bind("hash", requestHash)
                .bind("owner", instanceId).bind("leaseMicros", leaseMicros())
                .fetch().rowsUpdated()
                .thenReturn(new TurnTicket(scope, conversationId, turnId, 1));
    }

    /** ACCEPTED → RUNNING for the ticket's fence; consumes the ticket, so a second subscription cannot start. */
    public Mono<Boolean> start(TurnTicket ticket) {
        return update("UPDATE chat_turns SET status = 'RUNNING', lease_owner = :owner, lease_expires_at = " + NEW_LEASE
                + ", updated_at = CURRENT_TIMESTAMP(6) WHERE id = :id AND fence = :fence AND status = 'ACCEPTED' AND "
                + LEASE_VALID, ticket);
    }

    /** Extends the lease only while the ticket's fence is current, the turn active and the lease unexpired. */
    public Mono<Boolean> renew(TurnTicket ticket) {
        return update("UPDATE chat_turns SET lease_owner = :owner, lease_expires_at = " + NEW_LEASE
                + ", updated_at = CURRENT_TIMESTAMP(6) WHERE id = :id AND fence = :fence AND status IN " + ACTIVE
                + " AND " + LEASE_VALID, ticket);
    }

    public Mono<Boolean> markTerminal(TurnTicket ticket, TurnStatus status) {
        return markTerminal(ticket, status, TokenUsage.notObserved(), null);
    }

    /**
     * Fenced transition of an active turn with a live lease into a terminal state; releases the conversation's
     * active slot. Returns false when the ticket no longer owns the turn or the turn is already terminal.
     */
    public Mono<Boolean> markTerminal(TurnTicket ticket, TurnStatus status, TokenUsage usage, String errorCode) {
        if (!status.terminal()) {
            return Mono.error(new IllegalArgumentException("not a terminal status: " + status));
        }
        var spec = db.sql("UPDATE chat_turns SET status = :status, active_conversation_id = NULL, lease_expires_at = NULL,"
                        + " usage_observed = :observed, prompt_tokens = :prompt, completion_tokens = :completion,"
                        + " total_tokens = :total, error_code = :errorCode, finished_at = CURRENT_TIMESTAMP(6),"
                        + " updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE id = :id AND fence = :fence AND status IN " + ACTIVE + " AND " + LEASE_VALID)
                .bind("status", status.name())
                .bind("observed", usage.observed())
                .bind("prompt", usage.promptTokens())
                .bind("completion", usage.completionTokens())
                .bind("total", usage.totalTokens())
                .bind("id", ticket.turnId())
                .bind("fence", ticket.fence());
        spec = errorCode == null ? spec.bindNull("errorCode", String.class) : spec.bind("errorCode", errorCode);
        return spec.fetch().rowsUpdated().flatMap(n -> closeOpenToolCalls(n, ticket.turnId()));
    }

    /**
     * Recovery: marks an active turn INTERRUPTED and increments its fence, provided it still has the observed
     * fence and its lease has expired. Never touches terminal turns.
     */
    public Mono<Boolean> interruptExpired(String turnId, long fence) {
        return db.sql("UPDATE chat_turns SET status = 'INTERRUPTED', active_conversation_id = NULL, fence = fence + 1,"
                        + " lease_expires_at = NULL, error_code = 'LEASE_EXPIRED', finished_at = CURRENT_TIMESTAMP(6),"
                        + " updated_at = CURRENT_TIMESTAMP(6) WHERE id = :id AND fence = :fence AND status IN " + ACTIVE
                        + " AND lease_expires_at <= CURRENT_TIMESTAMP(6)")
                .bind("id", turnId).bind("fence", fence)
                .fetch().rowsUpdated().flatMap(n -> closeOpenToolCalls(n, turnId));
    }

    /**
     * After a terminal transition (n == 1), tool calls of the turn that never recorded an outcome become
     * UNCERTAIN in the same transaction: they were issued, but whether they took effect is unknown.
     */
    private Mono<Boolean> closeOpenToolCalls(long n, String turnId) {
        return n == 1 ? ToolEventRepository.markOpenUncertain(db, turnId).thenReturn(true) : Mono.just(false);
    }

    private Mono<Boolean> update(String sql, TurnTicket ticket) {
        return db.sql(sql)
                .bind("owner", instanceId).bind("leaseMicros", leaseMicros())
                .bind("id", ticket.turnId()).bind("fence", ticket.fence())
                .fetch().rowsUpdated().map(n -> n == 1);
    }

    private long leaseMicros() {
        Duration lease = props.getLeaseDuration();
        return Math.max(1, lease.toNanos() / 1_000);
    }

    private static TurnRow mapRow(Readable row) {
        return new TurnRow(
                row.get("id", String.class),
                new MemoryScope(row.get("tenant_id", String.class), row.get("subject", String.class)),
                row.get("conversation_id", String.class),
                row.get("request_id", String.class),
                row.get("request_hash", String.class),
                TurnStatus.valueOf(row.get("status", String.class)),
                number(row.get("fence")),
                number(row.get("lease_expired")) == 1,
                new TokenUsage(bool(row.get("usage_observed")), number(row.get("prompt_tokens")),
                        number(row.get("completion_tokens")), number(row.get("total_tokens"))));
    }

    static long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    static boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && ((Number) value).intValue() != 0;
    }
}
