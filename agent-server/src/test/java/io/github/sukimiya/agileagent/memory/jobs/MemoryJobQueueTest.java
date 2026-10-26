package io.github.sukimiya.agileagent.memory.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

import io.github.sukimiya.agileagent.memory.InstanceGuard;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;

/**
 * Typed durable jobs: deduplicated enqueue (also inside a caller's transaction), dispatch by {@link JobType},
 * lease, backoff, and no claiming while the single-instance guard reports a conflict. H2 here;
 * {@link MemoryJobQueueMySqlIntegrationTest} runs the same tests on MySQL 8.4.
 */
class MemoryJobQueueTest {

    static final Duration WAIT = Duration.ofSeconds(10);
    static final MemoryScope ALICE = new MemoryScope("tenant-1", "alice");

    protected MemoryTestFixture fixture;
    MemoryJobQueue queue;
    MemoryJobWorker worker;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        fixture = newFixture();
        queue = new MemoryJobQueue(fixture.databaseClient());
        worker = new MemoryJobWorker(fixture.databaseClient(), () -> false);
    }

    private boolean enqueue(JobType type, String dedupeKey) {
        return queue.enqueue(ALICE, "conv-1", type, dedupeKey, "{\"k\":\"" + dedupeKey + "\"}").block(WAIT);
    }

    private int count(String sql, Object... args) {
        return fixture.jdbc().queryForObject(sql, Integer.class, args);
    }

    private java.util.Map<String, Object> job(String dedupeKey) {
        return fixture.jdbc().queryForMap("SELECT status, attempts, last_error, lease_owner,"
                + " available_at > TIMESTAMPADD(SECOND, 20, CURRENT_TIMESTAMP(6)) AS backed_off"
                + " FROM memory_jobs WHERE dedupe_key = ?", dedupeKey);
    }

    private void makeDue(String dedupeKey) {
        fixture.jdbc().update("UPDATE memory_jobs SET available_at = TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6))"
                + " WHERE dedupe_key = ?", dedupeKey);
    }

    @Test
    void duplicateDedupeKeyIsIgnored() {
        assertThat(enqueue(JobType.INDEX_CHUNKS, "turn-1")).isTrue();
        assertThat(enqueue(JobType.INDEX_CHUNKS, "turn-1")).isFalse();
        assertThat(enqueue(JobType.SUMMARIZE, "turn-1")).as("dedupe is per job type").isTrue();
        assertThat(count("SELECT COUNT(*) FROM memory_jobs WHERE dedupe_key = 'turn-1'")).isEqualTo(2);

        // A duplicate inside a caller's transaction neither fails nor poisons it.
        var tx = TransactionalOperator.create(new R2dbcTransactionManager(fixture.connectionFactory()));
        var both = tx.transactional(queue.enqueue(ALICE, "conv-1", JobType.INDEX_CHUNKS, "turn-1", null)
                .zipWith(queue.enqueue(ALICE, "conv-1", JobType.INDEX_CHUNKS, "turn-2", null))).block(WAIT);
        assertThat(both.getT1()).isFalse();
        assertThat(both.getT2()).isTrue();
        assertThat(count("SELECT COUNT(*) FROM memory_jobs WHERE dedupe_key = 'turn-2'")).isEqualTo(1);

        var stored = fixture.jdbc().queryForMap("SELECT tenant_id, subject, conversation_id, job_type, status,"
                + " attempts, payload FROM memory_jobs WHERE dedupe_key = 'turn-1' AND job_type = 'INDEX_CHUNKS'");
        assertThat(stored.values()).containsExactly("tenant-1", "alice", "conv-1", "INDEX_CHUNKS", "PENDING", 0,
                "{\"k\":\"turn-1\"}");
    }

    @Test
    void enqueueRollsBackWithItsTransaction() {
        var tx = TransactionalOperator.create(new R2dbcTransactionManager(fixture.connectionFactory()));
        var failing = tx.transactional(queue.enqueue(ALICE, "conv-1", JobType.INDEX_CHUNKS, "turn-9", null)
                .then(Mono.error(new IllegalStateException("caller failed"))));
        assertThatThrownBy(() -> failing.block(WAIT)).hasMessageContaining("caller failed");
        assertThat(count("SELECT COUNT(*) FROM memory_jobs")).isZero();
        assertThat(enqueue(JobType.INDEX_CHUNKS, "turn-9")).as("the key was never taken").isTrue();
    }

    @Test
    void workerDispatchesByTypeAndPassesTheJob() {
        enqueue(JobType.INDEX_CHUNKS, "turn-1");
        enqueue(JobType.SUMMARIZE, "turn-1");
        var seen = new CopyOnWriteArrayList<ClaimedJob>();
        worker.register(JobType.INDEX_CHUNKS, job -> Mono.fromRunnable(() -> seen.add(job)));

        assertThat(worker.runPending(10).block(WAIT)).isEqualTo(1);

        assertThat(seen).singleElement().satisfies(job -> {
            assertThat(job.type()).isEqualTo(JobType.INDEX_CHUNKS);
            assertThat(job.scope()).isEqualTo(ALICE);
            assertThat(job.conversationId()).isEqualTo("conv-1");
            assertThat(job.dedupeKey()).isEqualTo("turn-1");
            assertThat(job.payload()).isEqualTo("{\"k\":\"turn-1\"}");
            assertThat(job.attempts()).isEqualTo(1);
        });
        assertThat(count("SELECT COUNT(*) FROM memory_jobs WHERE job_type = 'INDEX_CHUNKS' AND status = 'DONE'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM memory_jobs WHERE job_type = 'SUMMARIZE' AND status = 'PENDING'"
                + " AND attempts = 0")).as("a type without a handler is never claimed").isEqualTo(1);
        assertThatThrownBy(() -> worker.register(JobType.INDEX_CHUNKS, job -> Mono.empty()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void jobFailureKeepsProgressAndBacksOff() {
        fixture.jdbc().update("INSERT INTO memory_index_progress (conversation_id, tenant_id, subject,"
                + " indexed_through_sequence, summarized_through_sequence, updated_at)"
                + " VALUES ('conv-1', 'tenant-1', 'alice', 6, 0, CURRENT_TIMESTAMP(6))");
        enqueue(JobType.INDEX_CHUNKS, "turn-1");
        var calls = new AtomicInteger();
        worker.register(JobType.INDEX_CHUNKS, job -> calls.incrementAndGet() == 1
                ? Mono.error(new IllegalStateException("model at /secret/path down"))
                : Mono.empty());

        assertThat(worker.runPending(10).block(WAIT)).isZero();

        var failed = job("turn-1");
        assertThat(failed.get("STATUS")).isEqualTo("PENDING");
        assertThat(((Number) failed.get("ATTEMPTS")).intValue()).isEqualTo(1);
        assertThat((String) failed.get("LAST_ERROR")).isEqualTo("IllegalStateException");
        assertThat(failed.get("LEASE_OWNER")).isNull();
        assertThat(failed.get("BACKED_OFF")).as("first backoff is 30 s").isIn(true, 1, 1L);
        assertThat(count("SELECT indexed_through_sequence FROM memory_index_progress WHERE conversation_id = 'conv-1'"))
                .isEqualTo(6);
        assertThat(worker.runPending(10).block(WAIT)).as("not due during backoff").isZero();
        assertThat(calls).hasValue(1);

        makeDue("turn-1");
        assertThat(worker.runPending(10).block(WAIT)).isEqualTo(1);
        assertThat(job("turn-1").get("STATUS")).isEqualTo("DONE");
        assertThat(((Number) job("turn-1").get("ATTEMPTS")).intValue()).isEqualTo(2);
        assertThat(job("turn-1").get("LAST_ERROR")).isNull();
    }

    @Test
    void renewLeaseExtendsOnlyTheHeldRunningJob() {
        queue.enqueue(ALICE, "conv-1", JobType.INDEX_CHUNKS, "renew", null).block(WAIT);
        var renewed = new java.util.concurrent.CopyOnWriteArrayList<Boolean>();
        var leases = new java.util.concurrent.CopyOnWriteArrayList<java.time.LocalDateTime>();
        java.util.function.Supplier<java.time.LocalDateTime> lease = () -> fixture.jdbc().queryForObject(
                "SELECT lease_expires_at FROM memory_jobs WHERE dedupe_key = 'renew'", java.time.LocalDateTime.class);
        var other = new MemoryJobWorker(fixture.databaseClient(), () -> false);
        worker.register(JobType.INDEX_CHUNKS, job -> Mono.fromRunnable(() -> leases.add(lease.get()))
                .then(Mono.delay(Duration.ofMillis(20)))
                .then(worker.renewLease(job)).doOnNext(renewed::add)
                .then(other.renewLease(job)).doOnNext(renewed::add)
                .then(Mono.fromRunnable(() -> leases.add(lease.get()))));

        assertThat(worker.runPending(10).block(WAIT)).isEqualTo(1);

        // The holder extends its own lease; another instance cannot.
        assertThat(renewed).containsExactly(true, false);
        assertThat(leases.get(1)).isAfter(leases.get(0));
        // Finished jobs have no lease to renew.
        var done = new ClaimedJob(fixture.jdbc().queryForObject("SELECT id FROM memory_jobs WHERE dedupe_key = 'renew'",
                String.class), JobType.INDEX_CHUNKS, ALICE, "conv-1", "renew", null, 1);
        assertThat(worker.renewLease(done).block(WAIT)).isFalse();
    }

    @Test
    void abandonedRunningJobIsReclaimedAfterItsLease() {
        enqueue(JobType.INDEX_CHUNKS, "turn-1");
        fixture.jdbc().update("UPDATE memory_jobs SET status = 'RUNNING', lease_owner = 'dead', attempts = 1,"
                + " lease_expires_at = TIMESTAMPADD(SECOND, 60, CURRENT_TIMESTAMP(6))");
        worker.register(JobType.INDEX_CHUNKS, job -> Mono.empty());

        assertThat(worker.runPending(10).block(WAIT)).as("lease still valid").isZero();
        fixture.jdbc().update("UPDATE memory_jobs SET lease_expires_at = TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6))");
        assertThat(worker.runPending(10).block(WAIT)).isEqualTo(1);
        assertThat(((Number) job("turn-1").get("ATTEMPTS")).intValue()).isEqualTo(2);
    }

    @Test
    void workerSkipsClaimingDuringInstanceConflict() {
        var props = new MemoryProperties();
        var guard = new InstanceGuard(fixture.databaseClient(), props, new SimpleMeterRegistry());
        var peer = new InstanceGuard(fixture.databaseClient(), props, new SimpleMeterRegistry());
        peer.heartbeat().block(WAIT);
        guard.heartbeat().block(WAIT);
        assertThat(guard.conflict()).isTrue();
        var guarded = new MemoryJobWorker(fixture.databaseClient(), guard);
        var ran = new CopyOnWriteArrayList<JobType>();
        for (var type : List.of(JobType.CONVERSATION_CLEANUP, JobType.INDEX_CHUNKS)) {
            guarded.register(type, job -> Mono.fromRunnable(() -> ran.add(job.type())));
            queue.enqueue(ALICE, "conv-1", type, "k-" + type, null).block(WAIT);
        }

        assertThat(guarded.runPending(10).block(WAIT)).isZero();
        assertThat(ran).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM memory_jobs WHERE status = 'PENDING' AND attempts = 0"))
                .as("nothing claimed, no attempt used").isEqualTo(2);

        fixture.jdbc().update("DELETE FROM service_instances WHERE instance_id = ?", peer.instanceId());
        guard.heartbeat().block(WAIT);
        assertThat(guard.conflict()).isFalse();
        assertThat(guarded.runPending(10).block(WAIT)).isEqualTo(2);
        assertThat(ran).containsExactlyInAnyOrder(JobType.CONVERSATION_CLEANUP, JobType.INDEX_CHUNKS);
    }
}
