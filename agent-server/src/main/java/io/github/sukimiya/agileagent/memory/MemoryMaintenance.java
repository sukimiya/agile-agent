package io.github.sukimiya.agileagent.memory;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;

import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.jobs.JobType;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobQueue;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobWorker;
import io.github.sukimiya.agileagent.service.FileStorageService;
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
 * <li>The {@link JobType#CONVERSATION_CLEANUP} handler, run by {@link MemoryJobWorker} from {@link #runPending}:
 * retryable physical cleanup of messages, tool records, the cached window (replaced by a cache tombstone that
 * refuses later refills) and the conversation's local attachment directory. Failed jobs are retried with
 * backoff; jobs left RUNNING by a dead process are reclaimed after their lease.</li>
 * </ol>
 * The tombstone row (id, owner, deletion time; no content) is kept permanently so neither background work nor a
 * cache rebuild nor a reused id can bring the conversation back. Runs at startup and every
 * {@code app.memory.cleanup-scan-interval} when {@code app.memory.recovery-enabled} is set.
 */
@Component
public class MemoryMaintenance implements SmartLifecycle {

    static final String DELETED_ERROR = "CONVERSATION_DELETED";
    /** {@code memory_jobs.dedupe_key} of a conversation's cleanup job: prefix plus conversation id. */
    static final String CLEANUP_KEY_PREFIX = "cleanup:";

    private static final Logger log = LoggerFactory.getLogger(MemoryMaintenance.class);
    /**
     * The protected deletion list: one INFO line per committed deletion, a JSON object with {@code event},
     * {@code tenantId}, {@code subject}, {@code conversationId}, {@code deletedAt} (database time, as stored in
     * {@code sessions.deleted_at}) and {@code revision}. Never message content, titles, paths or secrets. Operators
     * ship this logger to a log store outside the database's failure domain and replay it after a restore.
     */
    static final String AUDIT_LOGGER = "memory.deletion-audit";
    static final String AUDIT_EVENT = "CONVERSATION_DELETED";
    private static final Logger audit = LoggerFactory.getLogger(AUDIT_LOGGER);
    private static final com.fasterxml.jackson.databind.ObjectMapper AUDIT_JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();
    private static final int BATCH = 50;
    private static final int DELETE_ATTEMPTS = 4;

    private final DatabaseClient db;
    private final HotMemoryCache cache;
    private final FileStorageService files;
    private final MemoryProperties props;
    private final MemoryMetrics metrics;
    private final TransactionalOperator transactions;
    private final MemoryJobQueue jobs;
    private final MemoryJobWorker worker;
    private volatile Disposable schedule;

    /** Standalone wiring (tests): its own worker, which never pauses for an instance conflict. */
    public MemoryMaintenance(ConnectionFactory connectionFactory, DatabaseClient db, HotMemoryCache cache,
            FileStorageService files, MemoryProperties props, MemoryMetrics metrics) {
        this(connectionFactory, db, cache, files, props, metrics, new MemoryJobWorker(db, () -> false));
    }

    /** Registers the {@link JobType#CONVERSATION_CLEANUP} handler on the shared worker. */
    @Autowired
    public MemoryMaintenance(ConnectionFactory connectionFactory, DatabaseClient db, HotMemoryCache cache,
            FileStorageService files, MemoryProperties props, MemoryMetrics metrics, MemoryJobWorker worker) {
        this.metrics = metrics;
        this.db = db;
        this.cache = cache;
        this.files = files;
        this.props = props;
        this.transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
        this.jobs = new MemoryJobQueue(db);
        this.worker = worker;
        worker.register(JobType.CONVERSATION_CLEANUP, job -> cleanup(job.scope(), job.conversationId()));
    }

    /** Outcome of the tombstone transaction; {@code deletedAt} is null when it was already deleted (a repeat). */
    private record Tombstone(java.time.LocalDateTime deletedAt, long revision) {
        static final Tombstone REPEAT = new Tombstone(null, 0);

        boolean newlyDeleted() {
            return deletedAt != null;
        }
    }

    /**
     * Deletes a conversation owned by {@code scope}. Completes for a live or already deleted own conversation
     * (idempotent); fails with {@code INVALID_CONVERSATION_ID} for a non-canonical id and
     * {@code CONVERSATION_NOT_FOUND} for an unknown, foreign or legacy one.
     */
    public Mono<Void> deleteConversation(MemoryScope scope, String conversationId) {
        return Mono.defer(() -> {
            ConversationIds.validate(conversationId);
            // Turns interrupted by the attempt that committed (reset per attempt, read after commit).
            var interrupted = new java.util.concurrent.atomic.AtomicLong();
            return transactions.transactional(Mono.defer(() -> {
                        interrupted.set(0);
                        return tombstone(scope, conversationId, interrupted);
                    }))
                    .retryWhen(Retry.max(DELETE_ATTEMPTS - 1).filter(MemoryMaintenance::isTransient))
                    .onErrorMap(e -> !(e instanceof MemoryException),
                            e -> new MemoryException(ErrorCode.STORAGE_UNAVAILABLE, e))
                    .doOnNext(result -> metrics.terminal(MemoryModel.TurnStatus.INTERRUPTED, interrupted.get()))
                    // Emitted after commit, once per actual transition; repeats and rolled-back attempts log nothing.
                    .doOnNext(result -> {
                        if (result.newlyDeleted()) {
                            auditDeletion(scope, conversationId, result);
                        }
                    })
                    // Best effort right away; the cleanup job writes the cache tombstone again and must succeed.
                    .flatMap(result -> result.newlyDeleted()
                            ? cache.tombstone(scope, conversationId).onErrorResume(e -> {
                                log.warn("memory.delete cache tombstone deferred to cleanup: {}",
                                        NestedExceptionUtils.getMostSpecificCause(e).toString());
                                return Mono.empty();
                            })
                            : Mono.empty());
        });
    }

    private static void auditDeletion(MemoryScope scope, String conversationId, Tombstone result) {
        var line = new java.util.LinkedHashMap<String, Object>();
        line.put("event", AUDIT_EVENT);
        line.put("tenantId", scope.tenantId());
        line.put("subject", scope.subject());
        line.put("conversationId", conversationId);
        line.put("deletedAt", result.deletedAt().toString());
        line.put("revision", result.revision());
        try {
            audit.info(AUDIT_JSON.writeValueAsString(line));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Unreachable for strings and numbers; never fail a committed delete over its audit line.
            log.error("memory.delete audit line could not be written conversationId={}", conversationId);
        }
    }

    /** The committed tombstone of this call, or {@link Tombstone#REPEAT} when the owner had already deleted it. */
    private Mono<Tombstone> tombstone(MemoryScope scope, String conversationId,
            java.util.concurrent.atomic.AtomicLong interrupted) {
        return db.sql("UPDATE sessions SET deleted_at = CURRENT_TIMESTAMP(6), title = '', revision = revision + 1,"
                        + " updated_at = CURRENT_TIMESTAMP(6) WHERE id = :id AND tenant_id = :tenantId"
                        + " AND user_id = :subject AND deleted_at IS NULL")
                .bind("id", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .fetch().rowsUpdated()
                .flatMap(n -> n == 1
                        ? revokeTurns(scope, conversationId, interrupted).then(enqueue(scope, conversationId))
                                .then(readTombstone(conversationId))
                        : alreadyDeletedByOwner(scope, conversationId));
    }

    /** Reads the deletion time and revision written by this transaction (database time). */
    private Mono<Tombstone> readTombstone(String conversationId) {
        return db.sql("SELECT deleted_at, revision FROM sessions WHERE id = :id")
                .bind("id", conversationId)
                .map(row -> new Tombstone(row.get("deleted_at", java.time.LocalDateTime.class),
                        TurnRepository.number(row.get("revision"))))
                .one();
    }

    private Mono<Tombstone> alreadyDeletedByOwner(MemoryScope scope, String conversationId) {
        return db.sql("SELECT COUNT(*) AS n FROM sessions WHERE id = :id AND tenant_id = :tenantId"
                        + " AND user_id = :subject AND deleted_at IS NOT NULL")
                .bind("id", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .map(row -> TurnRepository.number(row.get("n")))
                .one()
                .flatMap(n -> n > 0 ? Mono.just(Tombstone.REPEAT)
                        : Mono.error(new MemoryException(ErrorCode.CONVERSATION_NOT_FOUND)));
    }

    /** Interrupts active turns (fence + 1, slot released) and marks their open tool calls UNCERTAIN. */
    private Mono<Void> revokeTurns(MemoryScope scope, String conversationId,
            java.util.concurrent.atomic.AtomicLong interrupted) {
        var turns = db.sql("UPDATE chat_turns SET status = 'INTERRUPTED', active_conversation_id = NULL,"
                        + " fence = fence + 1, lease_expires_at = NULL, error_code = '" + DELETED_ERROR + "',"
                        + " finished_at = CURRENT_TIMESTAMP(6), updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE conversation_id = :c AND tenant_id = :tenantId AND subject = :subject"
                        + " AND status IN " + TurnRepository.ACTIVE)
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .fetch().rowsUpdated()
                .doOnNext(interrupted::set);
        var tools = db.sql("UPDATE chat_tool_events SET status = 'UNCERTAIN', updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE conversation_id = :c AND tenant_id = :tenantId AND subject = :subject"
                        + " AND status = 'STARTED'")
                .bind("c", conversationId).bind("tenantId", scope.tenantId()).bind("subject", scope.subject())
                .fetch().rowsUpdated();
        return turns.then(tools).then();
    }

    /** One cleanup job per conversation: the tombstone transition happens once, so the key never repeats. */
    private Mono<Void> enqueue(MemoryScope scope, String conversationId) {
        return jobs.enqueue(scope, conversationId, JobType.CONVERSATION_CLEANUP, CLEANUP_KEY_PREFIX + conversationId,
                null).then();
    }

    /**
     * Idempotency-Key retention: terminal turns accepted longer ago than {@code app.memory.idempotency-key-retention}
     * (default 24 h, database time) release their key, so it can be used again; the turn and its messages stay.
     * Acceptance also treats such a key as absent, so the result never depends on when this scan ran.
     */
    public Mono<Long> releaseExpiredIdempotencyKeys() {
        long retentionMicros = Math.max(1, props.getIdempotencyKeyRetention().toNanos() / 1_000);
        return db.sql("UPDATE chat_turns SET idempotency_key = NULL, updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE idempotency_key IS NOT NULL AND status NOT IN " + TurnRepository.ACTIVE
                        + " AND created_at <= TIMESTAMPADD(MICROSECOND, -:retentionMicros, CURRENT_TIMESTAMP(6))")
                .bind("retentionMicros", retentionMicros)
                .fetch().rowsUpdated();
    }

    /**
     * Runs up to {@code limit} due jobs of every type registered on the worker (here: conversation cleanup) one
     * after another; emits how many completed.
     */
    public Mono<Integer> runPending(int limit) {
        return worker.runPending(limit);
    }

    /** Idempotent physical cleanup; refuses to touch a conversation that is not tombstoned. */
    private Mono<Void> cleanup(MemoryScope scope, String c) {
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
                .concatMap(tick -> releaseExpiredIdempotencyKeys().onErrorResume(e -> {
                    log.warn("memory.idempotency key cleanup failed: {}",
                            NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.just(0L);
                }).then(runPending(BATCH).onErrorResume(e -> {
                    log.warn("memory.jobs scan failed: {}",
                            NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.just(0);
                })))
                .subscribe(count -> {
                    if (count > 0) {
                        log.info("memory.jobs completed {} job(s)", count);
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
