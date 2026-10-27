package io.github.sukimiya.agileagent.memory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Basic M1 memory metrics (Micrometer). Every meter records only events that happened:
 * <ul>
 * <li>{@code memory.turn.accepted{replay}}: newly accepted turns ({@code false}) and completed replays
 * ({@code true}); refused requests are not counted.</li>
 * <li>{@code memory.turn.terminal{status}}: terminal transitions actually written, by COMPLETED / FAILED /
 * CANCELLED / INTERRUPTED, from the finalizer, the recovery scan, accept-time lease expiry and deletion.</li>
 * <li>{@code memory.checkpoint.latency}: fenced checkpoint write latency.</li>
 * <li>{@code memory.cache.requests{result}}: newest-page reads served from the cache ({@code hit}) or from the
 * database ({@code miss}, including a stale or unavailable cache).</li>
 * <li>{@code memory.finalizer.failures}: terminal writes that failed, timed out or were rejected by a full queue
 * (a ticket that lost its turn is not a failure).</li>
 * <li>{@code memory.instance.conflict}: registered by {@link InstanceGuard} (0/1).</li>
 * <li>{@code memory.history.injected.messages} / {@code memory.history.injected.tokens}: earlier messages (and
 * their estimated tokens) injected as prompt history per executed turn. This is history injection, not long-term
 * memory recall, which M1 does not implement. From M2a only the history that fits the answer call's budget is
 * counted, once per turn.</li>
 * <li>{@code memory.context.history.truncated{purpose}}: model calls whose budgeted context left out older
 * completed turns.</li>
 * <li>{@code memory.context.estimate.ratio{purpose}}: provider {@code prompt_tokens} over the raw heuristic estimate
 * of the same call, for tuning {@code app.memory.context.estimate-factor}.</li>
 * </ul>
 * Not implemented in M1, so never reported as a number: Recall ({@code memory.recall.*}: not applicable, never
 * 0 ms or 0 items) and consolidation ({@code memory.consolidation.backlog}: disabled until enabled, never a backlog of 0 while off).
 * {@code memory.feature.enabled{feature}} states that explicitly (0 = not enabled).
 */
@Component
public class MemoryMetrics {

    public enum Availability { REPORTED, NOT_APPLICABLE, DISABLED }

    /** One metric as a report shows it; {@code value} is null unless the metric is actually reported. */
    public record Reading(String name, Availability availability, Double value, String note) {
        public String display() {
            return switch (availability) {
                case NOT_APPLICABLE -> "不适用";
                case DISABLED -> "功能未启用";
                case REPORTED -> value == null ? "无数据" : formatNumber(value);
            };
        }

        private static String formatNumber(double v) {
            return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.3f", v);
        }
    }

    private final MeterRegistry meters;
    private final Counter acceptedNew;
    private final Counter acceptedReplay;
    private final Map<TurnStatus, Counter> terminal = new LinkedHashMap<>();
    private final Timer checkpointLatency;
    private final Counter cacheHit;
    private final Counter cacheMiss;
    private final Counter finalizerFailures;
    private final DistributionSummary injectedMessages;
    private final DistributionSummary injectedTokens;
    private final java.util.concurrent.atomic.AtomicLong summaryBacklog = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicInteger recallFeature = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger consolidationFeature = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicLong consolidationBacklog = new java.util.concurrent.atomic.AtomicLong();
    private volatile boolean consolidationBacklogRegistered;
    private volatile Timer recallLatency;
    private volatile DistributionSummary recallItems;
    private volatile Counter recallCoverageLimited;

    public MemoryMetrics(MeterRegistry meters) {
        this.meters = meters;
        this.acceptedNew = Counter.builder("memory.turn.accepted").tag("replay", "false")
                .description("Newly accepted turns").register(meters);
        this.acceptedReplay = Counter.builder("memory.turn.accepted").tag("replay", "true")
                .description("Completed turns replayed for a repeated Idempotency-Key").register(meters);
        for (var status : List.of(TurnStatus.COMPLETED, TurnStatus.FAILED, TurnStatus.CANCELLED,
                TurnStatus.INTERRUPTED)) {
            terminal.put(status, Counter.builder("memory.turn.terminal").tag("status", status.name())
                    .description("Terminal turn states written").register(meters));
        }
        this.checkpointLatency = Timer.builder("memory.checkpoint.latency")
                .description("Fenced streaming checkpoint write latency").register(meters);
        this.cacheHit = Counter.builder("memory.cache.requests").tag("result", "hit")
                .description("Newest history page served from the Redis cache").register(meters);
        this.cacheMiss = Counter.builder("memory.cache.requests").tag("result", "miss")
                .description("Newest history page read from the database").register(meters);
        this.finalizerFailures = Counter.builder("memory.finalizer.failures")
                .description("Terminal writes that failed, timed out or were rejected").register(meters);
        this.injectedMessages = DistributionSummary.builder("memory.history.injected.messages")
                .description("Earlier messages injected as prompt history per turn (not long-term recall)")
                .register(meters);
        this.injectedTokens = DistributionSummary.builder("memory.history.injected.tokens")
                .baseUnit("estimated_tokens")
                .description("Estimated tokens of injected prompt history per turn (heuristic, not a tokenizer)")
                .register(meters);
        Gauge.builder("memory.feature.enabled", recallFeature, java.util.concurrent.atomic.AtomicInteger::get)
                .tag("feature", "recall")
                .description("1 when the memory feature is implemented and enabled").register(meters);
        Gauge.builder("memory.feature.enabled", consolidationFeature, java.util.concurrent.atomic.AtomicInteger::get)
                .tag("feature", "consolidation")
                .description("1 when consolidation (quota / judgement backlog) is enabled")
                .register(meters);
    }

    public void accepted(boolean replay) {
        (replay ? acceptedReplay : acceptedNew).increment();
    }

    public void terminal(TurnStatus status) {
        terminal(status, 1);
    }

    public void terminal(TurnStatus status, long count) {
        var counter = terminal.get(status);
        if (counter != null && count > 0) {
            counter.increment(count);
        }
    }

    public void checkpointLatency(Duration latency) {
        checkpointLatency.record(latency);
    }

    public void cacheHit() {
        cacheHit.increment();
    }

    public void cacheMiss() {
        cacheMiss.increment();
    }

    public void finalizerFailure() {
        finalizerFailures.increment();
    }

    public void historyInjected(List<LlmClient.Message> history) {
        injectedMessages.record(history.size());
        injectedTokens.record(estimateTokens(history));
    }

    /** A model call whose context left out older completed turns (M2a budget; summaries arrive in Task 4). */
    public void historyTruncated(String purpose) {
        Counter.builder("memory.context.history.truncated").tag("purpose", purpose)
                .description("Model calls whose budgeted context left out older completed turns")
                .register(meters).increment();
    }


    /**
     * Per-turn recall explanation writes ({@code memory.recall.explain}, {@code outcome}: {@code written},
     * {@code failed} or {@code dropped} when the bounded queue is full). Never fails the turn.
     */
    public void recallExplain(String outcome) {
        Counter.builder("memory.recall.explain").tag("outcome", outcome)
                .description("Per-turn recall explanation writes").register(meters).increment();
    }

    /** Derived index rows physically purged after their retention ({@code kind}: ITEM, CHUNK or SUMMARY). */
    /**
     * In-chat forget / restore classification calls ({@code memory.command.classified}, {@code outcome}: FORGET,
     * RESTORE, NONE or FAILED). Only inputs that pass the keyword prefilter are classified.
     */
    public void commandClassified(String outcome) {
        Counter.builder("memory.command.classified").tag("outcome", outcome == null ? "UNKNOWN" : outcome)
                .description("Forget / restore intent classification calls").register(meters).increment();
    }

    /** Items made INACTIVE by the periodic decay pass ({@code memory.decay.inactivated}; never deleted). */
    public void decayInactivated(long count) {
        if (count > 0) {
            Counter.builder("memory.decay.inactivated")
                    .description("Non-user memory items made INACTIVE after long without recall")
                    .register(meters).increment(count);
        }
    }

    /** Recall records marked expired by the periodic purge job (never deleted). */
    public void recallRecordsExpired(long count) {
        if (count > 0) {
            Counter.builder("memory.recall.records.expired")
                    .description("Recall records marked expired; they no longer count as references")
                    .register(meters).increment(count);
        }
    }

    public void purged(String kind, long count) {
        if (count > 0) {
            Counter.builder("memory.purge.purged").tag("kind", kind)
                    .description("Derived memory index rows purged after retention").register(meters).increment(count);
        }
    }

    /** A long-term memory item was written by judgement (M2b Task 3). */
    public void judgementWritten() {
        Counter.builder("memory.judgement.written").description("Long-term memory items written by judgement")
                .register(meters).increment();
    }

    /** A judgement action was rejected by the validator; {@code reason} is a short code. */
    public void judgementRejected(String reason) {
        Counter.builder("memory.judgement.rejected").tag("reason", reason == null ? "UNKNOWN" : reason)
                .description("Judgement actions discarded by provenance / anti-poison checks")
                .register(meters).increment();
    }

    /** A judgement attempt failed (model / timeout / invalid JSON). */
    public void judgementFailed(String reason) {
        Counter.builder("memory.judgement.failures").tag("reason", reason == null ? "UNKNOWN" : reason)
                .description("Judgement attempts that failed")
                .register(meters).increment();
    }

    /** A turn memory record was written (M2a Task 4). */
    public void summaryGenerated() {
        Counter.builder("memory.summary.generated").description("Turn memory records written")
                .register(meters).increment();
    }

    /**
     * A record attempt failed; {@code reason}: MODEL_MISSING, MODEL_ERROR, TIMEOUT or INVALID_OUTPUT (the model's
     * answer was not a valid memory-record-v1 object, D23).
     */
    public void summaryFailed(String reason) {
        Counter.builder("memory.summary.failures").tag("reason", reason)
                .description("Turn record attempts that failed (retried, or skipped after repeated invalid output)")
                .register(meters).increment();
    }

    /** A turn was skipped without a record after repeated invalid output (D23). */
    public void summarySkipped() {
        Counter.builder("memory.summary.skipped").description("Turns skipped after repeated invalid record output")
                .register(meters).increment();
    }

    /** Time from a turn's completion to the moment its record was built (database clock). */
    public void summaryLag(Duration lag) {
        Timer.builder("memory.summary.lag").description("Turn completion to record written")
                .register(meters).record(lag.isNegative() ? Duration.ZERO : lag);
    }

    /**
     * Completed turns without a record in the conversations the last idle scan found (bounded by the scan batch),
     * registered by the scanner only when records are enabled, so a disabled feature never reports a 0 backlog.
     */
    public void summaryBacklog(long turns) {
        summaryBacklog.set(turns);
        Gauge.builder("memory.summary.backlog", summaryBacklog, java.util.concurrent.atomic.AtomicLong::get)
                .description("Completed turns waiting for a record, as of the last idle scan").register(meters);
    }

    /** Provider {@code prompt_tokens} divided by the raw (unscaled) estimate of the same call. */
    public void estimateRatio(String purpose, double ratio) {
        DistributionSummary.builder("memory.context.estimate.ratio").tag("purpose", purpose)
                .description("Provider prompt tokens over the heuristic estimate, per model call")
                .register(meters).record(ratio);
    }

    /**
     * Registers the recall meters and sets {@code memory.feature.enabled{feature=recall}} to 1; called by the
     * retriever when recall is enabled. Until then recall readings are not applicable (never 0 ms or 0 items).
     */
    public synchronized void enableRecall() {
        if (recallLatency == null) {
            recallItems = DistributionSummary.builder("memory.recall.items")
                    .description("Trusted recall candidates per question (long-term recall, not history injection)")
                    .register(meters);
            recallCoverageLimited = Counter.builder("memory.recall.coverage.limited")
                    .description("Recalls where some turns in scope were neither indexed nor scanned").register(meters);
            recallLatency = Timer.builder("memory.recall.latency").description("Recall latency per question")
                    .register(meters);
        }
        recallFeature.set(1);
    }

    /** One recall: its latency, trusted candidates and whether coverage was limited. */
    public void recalled(Duration latency, int items, boolean coverageLimited) {
        var timer = recallLatency;
        if (timer == null) {
            return;
        }
        timer.record(latency);
        recallItems.record(items);
        if (coverageLimited) {
            recallCoverageLimited.increment();
        }
    }

    /** Mean recall latency once recall is enabled; not applicable before (never 0 ms). */
    public Reading recallLatency() {
        var timer = recallLatency;
        if (timer == null) {
            return new Reading("memory.recall.latency", Availability.NOT_APPLICABLE, null, "Recall 未启用");
        }
        return new Reading("memory.recall.latency", Availability.REPORTED,
                timer.count() == 0 ? null : timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS), null);
    }

    /** Long-term recall items, counted separately from history injection; not applicable before recall is enabled. */
    public Reading recallItems() {
        var items = recallItems;
        if (items == null) {
            return new Reading("memory.recall.items", Availability.NOT_APPLICABLE, null, "Recall 未启用");
        }
        return new Reading("memory.recall.items", Availability.REPORTED, items.totalAmount(), null);
    }

    /**
     * Turns on consolidation feature gauge and allows {@link #consolidationBacklog(long)} to register the backlog
     * meter. Call when {@code app.memory.consolidation.enabled=true} (M2b Task 2).
     */
    public synchronized void enableConsolidation(boolean enabled) {
        consolidationFeature.set(enabled ? 1 : 0);
        if (enabled && !consolidationBacklogRegistered) {
            Gauge.builder("memory.consolidation.backlog", consolidationBacklog,
                            java.util.concurrent.atomic.AtomicLong::get)
                    .description("LLM consolidation jobs waiting (including quota-postponed)")
                    .register(meters);
            consolidationBacklogRegistered = true;
        }
    }

    /** Sets the consolidation backlog gauge (only has effect after {@link #enableConsolidation(true)}). */
    public void consolidationBacklog(long jobs) {
        consolidationBacklog.set(jobs);
        if (consolidationFeature.get() == 1 && !consolidationBacklogRegistered) {
            enableConsolidation(true);
        }
    }

    /** Increments the backlog gauge by {@code delta} when consolidation is enabled. */
    public void consolidationBacklogDelta(long delta) {
        if (consolidationFeature.get() == 1) {
            consolidationBacklog(consolidationBacklog.addAndGet(delta));
        }
    }

    /** A due LLM job was postponed to the next day because the tenant was over its daily token budget. */
    public void consolidationQuotaPostponed() {
        Counter.builder("memory.consolidation.quota.postponed")
                .description("LLM consolidation jobs postponed to the next day for daily token quota")
                .register(meters).increment();
    }

    /**
     * Pending / postponed LLM consolidation jobs when the feature is enabled; otherwise disabled (never a backlog
     * of 0 while off).
     */
    public Reading consolidationBacklog() {
        if (consolidationFeature.get() == 0) {
            return new Reading("memory.consolidation.backlog", Availability.DISABLED, null, "整理未启用");
        }
        return new Reading("memory.consolidation.backlog", Availability.REPORTED,
                (double) consolidationBacklog.get(), null);
    }

    /** All basic metrics as report readings, in a stable order. */
    public Map<String, Reading> snapshot() {
        var out = new LinkedHashMap<String, Reading>();
        put(out, reported("memory.turn.accepted{replay=false}", acceptedNew.count()));
        put(out, reported("memory.turn.accepted{replay=true}", acceptedReplay.count()));
        terminal.forEach((status, counter) ->
                put(out, reported("memory.turn.terminal{status=" + status.name() + "}", counter.count())));
        put(out, reported("memory.checkpoint.latency.count", checkpointLatency.count()));
        put(out, new Reading("memory.checkpoint.latency.mean_ms", Availability.REPORTED,
                checkpointLatency.count() == 0 ? null : checkpointLatency.mean(java.util.concurrent.TimeUnit.MILLISECONDS),
                null));
        put(out, reported("memory.cache.requests{result=hit}", cacheHit.count()));
        put(out, reported("memory.cache.requests{result=miss}", cacheMiss.count()));
        put(out, reported("memory.finalizer.failures", finalizerFailures.count()));
        var conflict = meters.find("memory.instance.conflict").gauge();
        put(out, new Reading("memory.instance.conflict", Availability.REPORTED,
                conflict == null ? null : conflict.value(), conflict == null ? "实例保护未注册" : null));
        put(out, reported("memory.history.injected.messages.total", injectedMessages.totalAmount()));
        put(out, reported("memory.history.injected.turns", injectedMessages.count()));
        put(out, reported("memory.history.injected.tokens.total", injectedTokens.totalAmount()));
        put(out, recallItems());
        put(out, recallLatency());
        put(out, consolidationBacklog());
        return out;
    }

    private static Reading reported(String name, double value) {
        return new Reading(name, Availability.REPORTED, value, null);
    }

    private static void put(Map<String, Reading> out, Reading reading) {
        out.put(reading.name(), reading);
    }

    /**
     * Rough token estimate (not a tokenizer): one token per CJK character, one per four other non-space
     * characters. Used only to describe prompt size in metrics and eval baselines.
     */
    /** The M1 heuristic; implemented by {@link io.github.sukimiya.agileagent.memory.context.TokenEstimator}. */
    public static long estimateTokens(String text) {
        return io.github.sukimiya.agileagent.memory.context.TokenEstimator.estimate(text);
    }

    public static long estimateTokens(List<LlmClient.Message> messages) {
        return io.github.sukimiya.agileagent.memory.context.TokenEstimator.estimate(messages);
    }
}
