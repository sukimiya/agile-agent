package io.github.sukimiya.agileagent.memory.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryMetrics;
import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;

/**
 * Per-tenant daily consolidation token quota and concurrency (M2b Task 2, D11). H2 here;
 * {@link ConsolidationQuotaMySqlIntegrationTest} re-runs on MySQL 8.4 (including concurrent reserves).
 */
class ConsolidationQuotaTest {

    static final Duration WAIT = Duration.ofSeconds(15);
    static final MemoryScope ALICE = new MemoryScope("tenant-a", "alice");
    static final MemoryScope BOB = new MemoryScope("tenant-b", "bob");

    protected MemoryTestFixture fixture;
    MemoryProperties props;
    MemoryMetrics metrics;
    ConsolidationQuota quota;
    MemoryJobQueue queue;
    MemoryJobWorker worker;
    SimpleMeterRegistry meters;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        fixture = newFixture();
        props = new MemoryProperties();
        props.getConsolidation().setEnabled(true);
        props.getConsolidation().setDailyTokenLimit(1_000);
        props.getConsolidation().setMaxConcurrentPerTenant(1);
        props.getConsolidation().setReserveTokens(100);
        meters = new SimpleMeterRegistry();
        metrics = new MemoryMetrics(meters);
        metrics.enableConsolidation(props.getConsolidation().isEnabled());
        quota = new ConsolidationQuota(fixture.databaseClient(), props, metrics);
        queue = new MemoryJobQueue(fixture.databaseClient());
        worker = new MemoryJobWorker(fixture.databaseClient(), () -> false, quota, props, metrics);
    }

    private long usage(String tenantId) {
        Long v = fixture.jdbc().queryForObject(
                "SELECT tokens FROM memory_token_usage WHERE tenant_id = ? AND usage_date = CURRENT_DATE",
                Long.class, tenantId);
        return v == null ? 0 : v;
    }

    @Test
    void overQuotaPostponesWithoutDroppingEvents() {
        assertThat(quota.reserve(ALICE.tenantId(), 1_000).block(WAIT)).isTrue();
        assertThat(queue.enqueue(ALICE, "conv-1", JobType.SUMMARIZE, "sum-1", "{\"throughSequence\":1}")
                .block(WAIT)).isTrue();
        worker.register(JobType.SUMMARIZE, job -> Mono.error(new AssertionError("must not run when over quota")));
        assertThat(worker.runPending(10).block(WAIT)).isZero();

        var row = fixture.jdbc().queryForMap(
                "SELECT status, attempts, available_at > TIMESTAMPADD(HOUR, 12, CURRENT_TIMESTAMP(6)) AS postponed"
                        + " FROM memory_jobs WHERE dedupe_key = 'sum-1'");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(((Number) row.get("attempts")).intValue()).isZero();
        assertThat(row.get("postponed")).isIn(true, Boolean.TRUE, 1, 1L);
        assertThat(meters.find("memory.consolidation.quota.postponed").counter().count()).isEqualTo(1.0);
    }

    @Test
    void quotaIsPerTenant() {
        assertThat(quota.reserve(ALICE.tenantId(), 1_000).block(WAIT)).isTrue();
        assertThat(quota.reserve(ALICE.tenantId(), 1).block(WAIT)).isFalse();
        assertThat(quota.reserve(BOB.tenantId(), 500).block(WAIT)).isTrue();
        assertThat(usage(ALICE.tenantId())).isEqualTo(1_000);
        assertThat(usage(BOB.tenantId())).isEqualTo(500);
    }

    @Test
    void concurrentReservationsDoNotOverspend() throws Exception {
        int threads = 20;
        long each = 100;
        props.getConsolidation().setDailyTokenLimit(1_000);
        var ok = new AtomicInteger();
        var latch = new CountDownLatch(threads);
        List<Thread> started = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            var t = new Thread(() -> {
                try {
                    if (Boolean.TRUE.equals(quota.reserve(ALICE.tenantId(), each).block(WAIT))) {
                        ok.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
            started.add(t);
            t.start();
        }
        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        for (var t : started) {
            t.join(5_000);
        }
        assertThat(ok.get()).isEqualTo(10);
        assertThat(usage(ALICE.tenantId())).isEqualTo(1_000);
    }

    @Test
    void actualUsageSettlesReservation() {
        assertThat(quota.reserve(ALICE.tenantId(), 800).block(WAIT)).isTrue();
        assertThat(usage(ALICE.tenantId())).isEqualTo(800);
        quota.settle(ALICE.tenantId(), 800, new TokenUsage(true, 40, 60, 100)).block(WAIT);
        assertThat(usage(ALICE.tenantId())).isEqualTo(100);
        // Not observed: keep the reserved amount (already charged).
        assertThat(quota.reserve(ALICE.tenantId(), 50).block(WAIT)).isTrue();
        quota.settle(ALICE.tenantId(), 50, TokenUsage.notObserved()).block(WAIT);
        assertThat(usage(ALICE.tenantId())).isEqualTo(150);
    }

    @Test
    void perTenantConcurrencyLimitHonored() {
        assertThat(quota.tryAcquire(ALICE.tenantId())).isTrue();
        assertThat(quota.tryAcquire(ALICE.tenantId())).isFalse();
        assertThat(quota.tryAcquire(BOB.tenantId())).as("other tenant unaffected").isTrue();
        quota.release(ALICE.tenantId());
        assertThat(quota.tryAcquire(ALICE.tenantId())).isTrue();
        quota.release(ALICE.tenantId());
        quota.release(BOB.tenantId());
    }

    @Test
    void backlogMetricRegisteredOnlyWhenEnabled() {
        assertThat(meters.find("memory.consolidation.backlog").gauge()).isNotNull();
        assertThat(meters.find("memory.feature.enabled").tag("feature", "consolidation").gauge().value())
                .isEqualTo(1.0);
        assertThat(metrics.consolidationBacklog().availability()).isEqualTo(MemoryMetrics.Availability.REPORTED);

        var offMeters = new SimpleMeterRegistry();
        var offMetrics = new MemoryMetrics(offMeters);
        // default consolidation.enabled=false → backlog not registered, feature gauge 0
        assertThat(offMeters.find("memory.consolidation.backlog").meter()).isNull();
        assertThat(offMetrics.consolidationBacklog().availability()).isEqualTo(MemoryMetrics.Availability.DISABLED);
        assertThat(offMeters.find("memory.feature.enabled").tag("feature", "consolidation").gauge().value())
                .isZero();

        offMetrics.enableConsolidation(true);
        offMetrics.consolidationBacklog(3);
        assertThat(offMeters.find("memory.consolidation.backlog").gauge().value()).isEqualTo(3.0);
        assertThat(offMeters.find("memory.feature.enabled").tag("feature", "consolidation").gauge().value())
                .isEqualTo(1.0);
    }

    @Test
    void tenantQuotaOverrideRaisesLimit() {
        fixture.jdbc().update(
                "INSERT INTO memory_tenant_quotas (tenant_id, daily_token_limit, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP(6))",
                ALICE.tenantId(), 2_000L);
        assertThat(quota.reserve(ALICE.tenantId(), 1_500).block(WAIT)).isTrue();
        assertThat(quota.reserve(ALICE.tenantId(), 600).block(WAIT)).isFalse();
        assertThat(usage(ALICE.tenantId())).isEqualTo(1_500);
    }

    @Test
    void workerReservesThenSettlesOnLlmJob() {
        worker.register(JobType.SUMMARIZE, job ->
                quota.settle(job.scope().tenantId(), props.getConsolidation().getReserveTokens(),
                        new TokenUsage(true, 10, 15, 25)).then());
        assertThat(queue.enqueue(ALICE, "conv-1", JobType.SUMMARIZE, "sum-ok", "{\"throughSequence\":1}")
                .block(WAIT)).isTrue();
        assertThat(worker.runPending(5).block(WAIT)).isEqualTo(1);
        assertThat(usage(ALICE.tenantId())).isEqualTo(25);
        var status = fixture.jdbc().queryForObject("SELECT status FROM memory_jobs WHERE dedupe_key = 'sum-ok'",
                String.class);
        assertThat(status).isEqualTo("DONE");
    }
}
