package io.github.sukimiya.agileagent.memory.jobs;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.memory.InstanceGuard;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import reactor.core.publisher.Mono;

/**
 * Claims due jobs from {@code memory_jobs} and runs them with the handler registered for their {@link JobType}.
 * <ul>
 * <li>A job is due when PENDING with {@code available_at} reached, or RUNNING with an expired lease (its process
 * died). Claiming sets a 5 minute lease and counts an attempt.</li>
 * <li>A handler failure puts the job back to PENDING with exponential backoff (30 s doubling, at most 30 min) and
 * stores only the error type, since messages can carry paths. Handlers are idempotent and keep their own progress,
 * so a retry repeats no committed work.</li>
 * <li>Jobs of a type without a handler are never claimed; they wait, attempts unused, until one is registered.</li>
 * <li>Nothing is claimed while the single-instance guard reports a peer in the same namespace.</li>
 * </ul>
 * Not scheduled itself: {@code MemoryMaintenance} drives {@link #runPending} on its scan interval.
 */
@Component
public class MemoryJobWorker {

    private static final Logger log = LoggerFactory.getLogger(MemoryJobWorker.class);
    static final Duration JOB_LEASE = Duration.ofMinutes(5);
    static final Duration FIRST_BACKOFF = Duration.ofSeconds(30);
    static final Duration MAX_BACKOFF = Duration.ofMinutes(30);
    private static final String DUE = "((status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP(6))"
            + " OR (status = 'RUNNING' AND lease_expires_at <= CURRENT_TIMESTAMP(6)))";

    private final DatabaseClient db;
    private final BooleanSupplier conflict;
    private final Map<JobType, Function<ClaimedJob, Mono<Void>>> handlers = new EnumMap<>(JobType.class);
    private final String instanceId = NonBlockingIds.uuid();

    @Autowired
    public MemoryJobWorker(DatabaseClient db, InstanceGuard guard) {
        this(db, guard::conflict);
    }

    /** {@code conflict} answers whether claiming must pause (another live instance in the namespace). */
    public MemoryJobWorker(DatabaseClient db, BooleanSupplier conflict) {
        this.db = db;
        this.conflict = conflict;
    }

    /** Registers the handler of one job type; a type has at most one handler. */
    public synchronized void register(JobType type, Function<ClaimedJob, Mono<Void>> handler) {
        if (handlers.putIfAbsent(type, handler) != null) {
            throw new IllegalStateException("a handler is already registered for " + type);
        }
    }

    private synchronized Map<JobType, Function<ClaimedJob, Mono<Void>>> snapshot() {
        return Map.copyOf(handlers);
    }

    /** Runs up to {@code limit} due jobs of the registered types one after another; emits how many completed. */
    public Mono<Integer> runPending(int limit) {
        return Mono.defer(() -> {
            var current = snapshot();
            if (current.isEmpty() || conflict.getAsBoolean()) {
                return Mono.just(0);
            }
            var types = current.keySet().stream().map(t -> "'" + t.name() + "'")
                    .sorted().collect(Collectors.joining(", ", "(", ")"));
            return db.sql("SELECT id, job_type, tenant_id, subject, conversation_id, dedupe_key, payload, attempts"
                            + " FROM memory_jobs WHERE job_type IN " + types + " AND " + DUE
                            + " ORDER BY available_at LIMIT :limit")
                    .bind("limit", limit)
                    .map(row -> new ClaimedJob(row.get("id", String.class),
                            JobType.valueOf(row.get("job_type", String.class)),
                            new MemoryScope(row.get("tenant_id", String.class), row.get("subject", String.class)),
                            row.get("conversation_id", String.class), row.get("dedupe_key", String.class),
                            row.get("payload", String.class),
                            ((Number) row.get("attempts")).intValue() + 1))
                    .all()
                    .concatMap(job -> conflict.getAsBoolean() ? Mono.just(0)
                            : claim(job).flatMap(claimed -> claimed ? run(job, current.get(job.type())) : Mono.just(0)))
                    .reduce(0, Integer::sum);
        });
    }

    private Mono<Boolean> claim(ClaimedJob job) {
        return db.sql("UPDATE memory_jobs SET status = 'RUNNING', lease_owner = :owner, lease_expires_at ="
                        + " TIMESTAMPADD(MICROSECOND, :leaseMicros, CURRENT_TIMESTAMP(6)), attempts = attempts + 1,"
                        + " updated_at = CURRENT_TIMESTAMP(6) WHERE id = :id AND " + DUE)
                .bind("owner", instanceId).bind("leaseMicros", JOB_LEASE.toNanos() / 1_000).bind("id", job.id())
                .fetch().rowsUpdated().map(n -> n == 1);
    }

    private Mono<Integer> run(ClaimedJob job, Function<ClaimedJob, Mono<Void>> handler) {
        return Mono.defer(() -> handler.apply(job))
                .then(Mono.defer(() -> finish(job)))
                .onErrorResume(e -> {
                    log.warn("memory.job failed jobId={} type={} attempt={} errorType={}", job.id(), job.type(),
                            job.attempts(), e.getClass().getSimpleName());
                    return retryLater(job, e).thenReturn(0);
                });
    }

    private Mono<Integer> finish(ClaimedJob job) {
        return db.sql("UPDATE memory_jobs SET status = 'DONE', lease_owner = NULL, lease_expires_at = NULL,"
                        + " last_error = NULL, updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE id = :id AND status = 'RUNNING' AND lease_owner = :owner")
                .bind("id", job.id()).bind("owner", instanceId)
                .fetch().rowsUpdated().map(n -> n == 1 ? 1 : 0);
    }

    /** Back to PENDING with exponential backoff; only the error type is stored. */
    private Mono<Void> retryLater(ClaimedJob job, Throwable error) {
        return db.sql("UPDATE memory_jobs SET status = 'PENDING', lease_owner = NULL, lease_expires_at = NULL,"
                        + " available_at = TIMESTAMPADD(MICROSECOND, :delayMicros, CURRENT_TIMESTAMP(6)),"
                        + " last_error = :error, updated_at = CURRENT_TIMESTAMP(6)"
                        + " WHERE id = :id AND status = 'RUNNING' AND lease_owner = :owner")
                .bind("delayMicros", backoff(job.attempts()).toNanos() / 1_000)
                .bind("error", errorType(error))
                .bind("id", job.id()).bind("owner", instanceId)
                .fetch().rowsUpdated()
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
}
