package io.github.sukimiya.agileagent.memory;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;

import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.service.FileStorageService;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Conversation deletion in two phases.
 * <ol>
 * <li>{@link #deleteConversation}: one transaction tombstones the session (clears its title, bumps its revision),
 * interrupts any active turn with an incremented fence (error code {@code CONVERSATION_DELETED}; its open tool
 * calls become UNCERTAIN) and registers a durable cleanup job. From commit on, every read, renewal, checkpoint,
 * upload and download sees the tombstone or the stale fence. The session row is locked first, the same order as
 * turn acceptance, so a concurrent accept either fails with 404 or is interrupted by the delete.</li>
 * <li>{@link #runPending}: retryable physical cleanup of messages, tool records, the cached window (replaced by a
 * cache tombstone that refuses later refills) and the conversation's local attachment directory. Failed jobs
 * are retried with backoff; jobs left RUNNING by a dead process are reclaimed after their lease.</li>
 * </ol>
 * The tombstone row (id, owner, deletion time; no content) is kept permanently so neither background work nor a
 * cache rebuild nor a reused id can bring the conversation back. Runs at startup and every
 * {@code app.memory.cleanup-scan-interval} when {@code app.memory.recovery-enabled} is set.
 */
@Component
public class MemoryMaintenance implements SmartLifecycle {

    static final String DELETE_JOB = "DELETE_CONVERSATION";
    static final String DELETED_ERROR = "CONVERSATION_DELETED";

    private static final Logger log = LoggerFactory.getLogger(MemoryMaintenance.class);
    private static final int BATCH = 50;
    private static final int DELETE_ATTEMPTS = 4;
    private static final Duration JOB_LEASE = Duration.ofMinutes(5);
    private static final Duration FIRST_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(30);
    private static final String DUE = "((status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP(6))"
            + " OR (status = 'RUNNING' AND lease_expires_at <= CURRENT_TIMESTAMP(6)))";

    private final DatabaseClient db;
    private final HotMemoryCache cache;
    private final FileStorageService files;
    private final MemoryProperties props;
    private final TransactionalOperator transactions;
    private final String instanceId = NonBlockingIds.uuid();
    private volatile Disposable schedule;

    public MemoryMaintenance(ConnectionFactory connectionFactory, DatabaseClient db, HotMemoryCache cache,
            FileStorageService files, MemoryProperties props) {
        this.db = db;
        this.cache = cache;
        this.files = files;
        this.props = props;
        this.transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
    }

    private record Job(String id, MemoryScope scope, String conversationId) {}

    /**
     * Deletes a conversation owned by {@code scope}. Completes for a live or already deleted own conversation
     * (idempotent); fails with {@code INVALID_CONVERSATION_ID} for a non-canonical id and
     * {@code CONVERSATION_NOT_FOUND} for an unknown, foreign or legacy one.
     */
    public Mono<Void> deleteConversation(MemoryScope scope, String conversationId) {
        return Mono.defer(() -> {
            ConversationIds.validate(conversationId);
            return transactions.transactional(Mono.defer(() -> tombstone(scope, conversationId)))
                    .retryWhen(Retry.max(DELETE_ATTEMPTS - 1).filter(MemoryMaintenance::isTransient))
                    .onErrorMap(e -> !(e instanceof MemoryException),
                            e -> new MemoryException(ErrorCode.STORAGE_UNAVAILABLE, e))
                    // Best effort right away; the cleanup job writes the cache tombstone again and must succeed.
                    .flatMap(newlyDeleted -> newlyDeleted
                            ? cache.tombstone(scope, conversationId).onErrorResume(e -> {
                                log.warn("memory.delete cache tombstone deferred to cleanup: {}",
                                        NestedExceptionUtils.getMostSpecificCause(e).toString());
                                return Mono.empty();
                            })
                            : Mono.empty());
        });
    }

    /** True when this call deleted the conversation, false when it was already deleted by the same owner. */
    private Mono<Boolean> tombstone(MemoryScope scope, String conversationId) {
        return db.sql("UPDATE sessions SET deleted_at = CURRENT_TIMESTAMP(6), title = '', revision = revision + 1,"
                        + " updated_at = CURRENT_TIMESTAMP(6) WHERE id = :id AND tenant_id = :tenantId"
                        + " AND user_id = :subject AND deleted_at IS NULL")
                .bind("id", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .fetch().rowsUpdated()
                .flatMap(n -> n == 1
                        ? revokeTurns(scope, conversationId).then(enqueue(scope, conversationId)).thenReturn(true)
                        : alreadyDeletedByOwner(scope, conversationId));
    }

    private Mono<Boolean> alreadyDeletedByOwner(MemoryScope scope, String conversationId) {
        return db.sql("SELECT COUNT(*) AS n FROM sessions WHERE id = :id AND tenant_id = :tenantId"
                        + " AND user_id = :subject AND deleted_at IS NOT NULL")
                .bind("id", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map(row -> TurnRepository.number(row.get("n")))
                .one()
                .flatMap(n -> n > 0 ? Mono.just(false)
                        : Mono.error(new MemoryException(ErrorCode.CONVERSATION_NOT_FOUND)));
    }

    /** Interrupts active turns (fence + 1, slot released) and marks their open tool calls UNCERTAIN. */
    private Mono<Void> revokeTurns(MemoryScope scope, String conversationId) {
        var turns = db.sql("UPDATE chat_turns SET status = 'INTERRUPTED', active_conversation_id = NULL,"
                        + " fence = fence + 1, lease_expires_at = NULL, error_code = '" + DELETED_ERROR + "',"
                        + " finished_at = CURRENT_TIMESTAMP(6), updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE conversation_id = :c AND tenant_id = :tenantId AND subject = :subject"
                        + " AND status IN " + TurnRepository.ACTIVE)
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .fetch().rowsUpdated();
        var tools = db.sql("UPDATE chat_tool_events SET status = 'UNCERTAIN', updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE conversation_id = :c AND tenant_id = :tenantId AND subject = :subject"
                        + " AND status = 'STARTED'")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .fetch().rowsUpdated();
        return turns.then(tools).then();
    }

    private Mono<Void> enqueue(MemoryScope scope, String conversationId) {
        return db.sql("INSERT INTO memory_jobs (id, tenant_id, subject, conversation_id, job_type, status, attempts,"
                        + " available_at, created_at, updated_at) VALUES (:id, :tenantId, :subject, :c, '"
                        + DELETE_JOB + "', 'PENDING', 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6),"
                        + " CURRENT_TIMESTAMP(6))")
                .bind("id", NonBlockingIds.uuid()).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .bind("c", conversationId)
                .fetch().rowsUpdated().then();
    }

    /** Runs up to {@code limit} due cleanup jobs one after another; emits how many completed. */
    public Mono<Integer> runPending(int limit) {
        return db.sql("SELECT id, tenant_id, subject, conversation_id FROM memory_jobs WHERE job_type = '"
                        + DELETE_JOB + "' AND " + DUE + " ORDER BY available_at LIMIT :limit")
                .bind("limit", limit)
                .map(row -> new Job(row.get("id", String.class),
                        new MemoryScope(row.get("tenant_id", String.class), row.get("subject", String.class)),
                        row.get("conversation_id", String.class)))
                .all()
                .concatMap(job -> claim(job).flatMap(claimed -> claimed ? runClaimed(job) : Mono.just(0)))
                .reduce(0, Integer::sum);
    }

    private Mono<Boolean> claim(Job job) {
        return db.sql("UPDATE memory_jobs SET status = 'RUNNING', lease_owner = :owner, lease_expires_at ="
                        + " TIMESTAMPADD(MICROSECOND, :leaseMicros, CURRENT_TIMESTAMP(6)), attempts = attempts + 1,"
                        + " updated_at = CURRENT_TIMESTAMP(6) WHERE id = :id AND " + DUE)
                .bind("owner", instanceId).bind("leaseMicros", JOB_LEASE.toNanos() / 1_000).bind("id", job.id())
                .fetch().rowsUpdated().map(n -> n == 1);
    }

    private Mono<Integer> runClaimed(Job job) {
        return cleanup(job)
                .then(Mono.defer(() -> finishJob(job)))
                .onErrorResume(e -> {
                    log.warn("memory.delete cleanup failed jobId={} errorType={}", job.id(),
                            e.getClass().getSimpleName());
                    return retryLater(job, e).thenReturn(0);
                });
    }

    /** Idempotent physical cleanup; refuses to touch a conversation that is not tombstoned. */
    private Mono<Void> cleanup(Job job) {
        var scope = job.scope();
        var c = job.conversationId();
        return db.sql("SELECT COUNT(*) AS n FROM sessions WHERE id = :c AND tenant_id = :tenantId"
                        + " AND user_id = :subject AND deleted_at IS NOT NULL")
                .bind("c", c).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map(row -> TurnRepository.number(row.get("n")))
                .one()
                .flatMap(n -> n == 1 ? Mono.empty()
                        : Mono.error(new IllegalStateException("cleanup target is not a deleted conversation")))
                .then(scopedDelete("chat_messages", scope, c))
                .then(scopedDelete("chat_tool_events", scope, c))
                .then(Mono.defer(() -> cache.tombstone(scope, c)))
                .then(Mono.defer(() -> files.deleteConversationFiles(c)));
    }

    private Mono<Long> scopedDelete(String table, MemoryScope scope, String conversationId) {
        return Mono.defer(() -> db.sql("DELETE FROM " + table + " WHERE conversation_id = :c"
                        + " AND tenant_id = :tenantId AND subject = :subject")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .fetch().rowsUpdated());
    }

    private Mono<Integer> finishJob(Job job) {
        return db.sql("UPDATE memory_jobs SET status = 'DONE', lease_owner = NULL, lease_expires_at = NULL,"
                        + " last_error = NULL, updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE id = :id AND status = 'RUNNING' AND lease_owner = :owner")
                .bind("id", job.id()).bind("owner", instanceId)
                .fetch().rowsUpdated().map(n -> n == 1 ? 1 : 0);
    }

    /** Back to PENDING with exponential backoff; only the error type is stored (messages can carry paths). */
    private Mono<Void> retryLater(Job job, Throwable error) {
        return db.sql("SELECT attempts FROM memory_jobs WHERE id = :id")
                .bind("id", job.id())
                .map(row -> TurnRepository.number(row.get("attempts")))
                .one()
                .defaultIfEmpty(1L)
                .flatMap(attempts -> db.sql("UPDATE memory_jobs SET status = 'PENDING', lease_owner = NULL,"
                                + " lease_expires_at = NULL, available_at = TIMESTAMPADD(MICROSECOND, :delayMicros,"
                                + " CURRENT_TIMESTAMP(6)), last_error = :error, updated_at = CURRENT_TIMESTAMP(6)"
                                + " WHERE id = :id AND status = 'RUNNING' AND lease_owner = :owner")
                        .bind("delayMicros", backoff(attempts).toNanos() / 1_000)
                        .bind("error", errorType(error))
                        .bind("id", job.id()).bind("owner", instanceId)
                        .fetch().rowsUpdated())
                .then()
                .onErrorResume(e -> Mono.empty()); // the RUNNING lease expires and the job is reclaimed
    }

    static Duration backoff(long attempts) {
        var exponent = Math.max(0, Math.min(attempts - 1, 10));
        var delay = FIRST_BACKOFF.multipliedBy(1L << exponent);
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private static String errorType(Throwable error) {
        var name = error.getClass().getSimpleName();
        return name.length() <= 255 ? name : name.substring(0, 255);
    }

    private static boolean isTransient(Throwable e) {
        var cause = e;
        while (cause != null) {
            if (cause instanceof TransientDataAccessException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Override
    public void start() {
        Duration every = props.getCleanupScanInterval();
        schedule = Flux.interval(Duration.ZERO, every)
                .onBackpressureDrop()
                .concatMap(tick -> runPending(BATCH).onErrorResume(e -> {
                    log.warn("memory.delete cleanup scan failed: {}",
                            NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.just(0);
                }))
                .subscribe(count -> {
                    if (count > 0) {
                        log.info("memory.delete cleaned up {} deleted conversation(s)", count);
                    }
                });
    }

    @Override
    public void stop() {
        var current = schedule;
        if (current != null) {
            current.dispose();
        }
        schedule = null;
    }

    @Override
    public boolean isRunning() {
        var current = schedule;
        return current != null && !current.isDisposed();
    }

    @Override
    public boolean isAutoStartup() {
        return props.isRecoveryEnabled();
    }
}
