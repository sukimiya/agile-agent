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
 * 0 ms or 0 items) and consolidation ({@code memory.consolidation.backlog}: feature disabled, never a backlog of 0).
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
        for (var feature : List.of("recall", "consolidation")) {
            Gauge.builder("memory.feature.enabled", () -> 0).tag("feature", feature)
                    .description("1 when the memory feature is implemented and enabled; M1 has neither")
                    .register(meters);
        }
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

    /** Recall is not implemented in M1: not applicable, never 0 ms. */
    public Reading recallLatency() {
        return new Reading("memory.recall.latency", Availability.NOT_APPLICABLE, null, "Recall 在 M2a 实现");
    }

    /** Long-term memory recall items, counted separately from history injection; not applicable in M1. */
    public Reading recallItems() {
        return new Reading("memory.recall.items", Availability.NOT_APPLICABLE, null, "Recall 在 M2a 实现");
    }

    /** No consolidation jobs exist in M1 (deletion jobs are maintenance, not consolidation): disabled, not 0. */
    public Reading consolidationBacklog() {
        return new Reading("memory.consolidation.backlog", Availability.DISABLED, null, "M2 才启用整理任务");
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
