package io.github.sukimiya.agileagent.memory.jobs;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.memory.MemoryMetrics;
import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import reactor.core.publisher.Mono;

/**
 * Per-tenant daily consolidation token budget and in-process concurrency limit (M2b Task 2, D11).
 * <ul>
 * <li>{@link #reserve} atomically adds an estimate to {@code memory_token_usage} for the database's
 * {@code CURRENT_DATE} (DB session timezone; see operations.md). False when the add would exceed the tenant
 * limit ({@code memory_tenant_quotas} override, else {@code app.memory.consolidation.daily-token-limit}).</li>
 * <li>{@link #settle} replaces the reservation with observed usage, or keeps the reservation when usage was not
 * observed (conservative).</li>
 * <li>{@link #tryAcquire}/{@link #release} bound concurrent LLM consolidation jobs per tenant on this JVM
 * (aligned with the single-instance guard).</li>
 * </ul>
 */
@Component
public class ConsolidationQuota {

    private final DatabaseClient db;
    private final MemoryProperties props;
    private final MemoryMetrics metrics;
    private final ConcurrentHashMap<String, AtomicInteger> inFlight = new ConcurrentHashMap<>();

    public ConsolidationQuota(DatabaseClient db, MemoryProperties props, MemoryMetrics metrics) {
        this.db = db;
        this.props = props;
        this.metrics = metrics;
        metrics.enableConsolidation(props.getConsolidation().isEnabled());
    }

    /** LLM-backed consolidation job types that consume the daily token budget. */
    public static boolean isLlmJob(JobType type) {
        return type == JobType.SUMMARIZE || type == JobType.JUDGE_MEMORY || type == JobType.REBUILD_PROFILE
                || type == JobType.REPLAY_RULES;
    }

    /**
     * Reserves {@code estimatedTokens} against today's budget. Emits true when the reservation stuck;
     * false when it would exceed the limit (no row change beyond ensuring the usage row exists at 0).
     */
    public Mono<Boolean> reserve(String tenantId, long estimatedTokens) {
        if (estimatedTokens <= 0) {
            return Mono.just(true);
        }
        return limitOf(tenantId).flatMap(limit -> ensureRow(tenantId).then(db.sql(
                        "UPDATE memory_token_usage SET tokens = tokens + :est"
                                + " WHERE tenant_id = :tenantId AND usage_date = CURRENT_DATE"
                                + " AND tokens + :est <= :limit")
                .bind("est", estimatedTokens).bind("tenantId", tenantId).bind("limit", limit)
                .fetch().rowsUpdated().map(n -> n == 1)));
    }

    /**
     * Adjusts today's usage after a call: {@code tokens = max(0, tokens - reserved + charged)}, where
     * {@code charged} is {@code actual.totalTokens()} when observed, else {@code reserved}.
     */
    public Mono<Void> settle(String tenantId, long reserved, TokenUsage actual) {
        long charged = actual != null && actual.observed() ? actual.totalTokens() : reserved;
        long delta = charged - reserved;
        if (delta == 0) {
            return Mono.empty();
        }
        return ensureRow(tenantId).then(db.sql(
                        "UPDATE memory_token_usage SET tokens = GREATEST(0, tokens + :delta)"
                                + " WHERE tenant_id = :tenantId AND usage_date = CURRENT_DATE")
                .bind("delta", delta).bind("tenantId", tenantId)
                .fetch().rowsUpdated().then());
    }

    /** Tries to take one concurrency slot for {@code tenantId}. */
    public boolean tryAcquire(String tenantId) {
        int max = props.getConsolidation().getMaxConcurrentPerTenant();
        var counter = inFlight.computeIfAbsent(tenantId, t -> new AtomicInteger());
        while (true) {
            int cur = counter.get();
            if (cur >= max) {
                return false;
            }
            if (counter.compareAndSet(cur, cur + 1)) {
                return true;
            }
        }
    }

    /** Releases one concurrency slot previously acquired for {@code tenantId}. */
    public void release(String tenantId) {
        var counter = inFlight.get(tenantId);
        if (counter == null) {
            return;
        }
        while (true) {
            int cur = counter.get();
            if (cur <= 0) {
                return;
            }
            if (counter.compareAndSet(cur, cur - 1)) {
                return;
            }
        }
    }

    /** Records that a due LLM job was postponed to the next calendar day for quota. */
    public void postponed() {
        metrics.consolidationQuotaPostponed();
    }

    private Mono<Long> limitOf(String tenantId) {
        return db.sql("SELECT daily_token_limit FROM memory_tenant_quotas WHERE tenant_id = :tenantId")
                .bind("tenantId", tenantId)
                .map(row -> ((Number) row.get("daily_token_limit")).longValue())
                .one()
                .defaultIfEmpty(props.getConsolidation().getDailyTokenLimit());
    }

    private Mono<Void> ensureRow(String tenantId) {
        return db.sql("INSERT INTO memory_token_usage (tenant_id, usage_date, tokens) VALUES (:tenantId, CURRENT_DATE, 0)")
                .bind("tenantId", tenantId)
                .fetch().rowsUpdated()
                .onErrorResume(e -> Mono.just(0L)) // duplicate key: row already exists
                .then();
    }
}
