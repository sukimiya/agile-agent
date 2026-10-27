package io.github.sukimiya.agileagent.memory.recall;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.memory.MemoryMetrics;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.SourceType;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Per-turn recall explanation (M2b Task 5): what the answer prompt of a turn actually contained from memory
 * ({@code memory_recall_events}: kind PROFILE / RELATION / ITEM / CHUNK / SUMMARY, the referenced id, reason,
 * score, rank and source types — never content).
 * <p>
 * Writes go through one managed, bounded in-memory queue (default 1024 turns) drained by a single subscriber,
 * so the turn never waits on, and never fails because of, the explanation: a full queue drops the batch
 * ({@code memory.recall.explain{outcome=dropped}}), a failed write is only counted
 * ({@code outcome=failed}). A later batch for the same turn replaces the earlier one. Pending batches are
 * drained on shutdown.
 */
@Component
public class RecallEventRecorder implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RecallEventRecorder.class);
    static final int DEFAULT_CAPACITY = 1024;

    /** One injected memory reference, rank 1 = first in the prompt. */
    public record Event(String kind, String refId, String reason, Double score, int rank,
            List<SourceType> sourceTypes) {
        public Event {
            sourceTypes = sourceTypes == null ? List.of() : List.copyOf(sourceTypes);
        }
    }

    /** A stored event; {@code sourceTypes} comma-separated. */
    public record StoredEvent(String kind, String refId, String reason, Double score, int rank,
            String sourceTypes) {}

    private record Batch(MemoryScope scope, String conversationId, String turnId, List<Event> events) {}

    private final DatabaseClient db;
    private final MemoryMetrics metrics;
    private final Sinks.Many<Batch> queue;
    private final AtomicInteger pending = new AtomicInteger();
    private final Disposable drainer;

    @Autowired
    public RecallEventRecorder(DatabaseClient db, MemoryMetrics metrics) {
        this(db, metrics, DEFAULT_CAPACITY);
    }

    public RecallEventRecorder(DatabaseClient db, MemoryMetrics metrics, int capacity) {
        this.db = db;
        this.metrics = metrics;
        this.queue = Sinks.many().unicast().onBackpressureBuffer(new ArrayBlockingQueue<>(Math.max(1, capacity)));
        this.drainer = queue.asFlux()
                .concatMap(batch -> write(batch)
                        .doOnSuccess(v -> count("written"))
                        .onErrorResume(e -> {
                            count("failed");
                            log.warn("memory.recall.explain write failed turnId={} errorType={}", batch.turnId(),
                                    e.getClass().getSimpleName());
                            return Mono.empty();
                        })
                        .doFinally(s -> pending.decrementAndGet()), 1)
                .subscribe();
    }

    /**
     * Queues the explanation of one turn's answer prompt; never blocks or throws. Events without a reference id
     * are skipped. An empty list still replaces an earlier explanation of the turn.
     */
    public void submit(TurnTicket ticket, List<Event> events) {
        var kept = events.stream().filter(e -> e.refId() != null && !e.refId().isBlank()).toList();
        var batch = new Batch(ticket.scope(), ticket.conversationId(), ticket.turnId(), kept);
        pending.incrementAndGet();
        Sinks.EmitResult result;
        synchronized (queue) {
            result = queue.tryEmitNext(batch);
        }
        if (result.isFailure()) {
            pending.decrementAndGet();
            count("dropped");
            log.warn("memory.recall.explain dropped turnId={} result={}", ticket.turnId(), result);
        }
    }

    /** The turn's events in prompt order; empty for another owner. */
    public Flux<StoredEvent> forTurn(MemoryScope scope, String conversationId, String turnId) {
        return db.sql("SELECT kind, ref_id, reason, score, `rank`, source_types FROM memory_recall_events"
                        + " WHERE tenant_id = :tenantId AND subject = :subject AND conversation_id = :c"
                        + " AND turn_id = :turnId AND is_valid = 1 ORDER BY `rank`")
                .bind("tenantId", scope.tenantId()).bind("subject", scope.subject()).bind("c", conversationId)
                .bind("turnId", turnId)
                .map((row, meta) -> {
                    var score = row.get("score");
                    var rank = row.get("rank");
                    return new StoredEvent(row.get("kind", String.class), row.get("ref_id", String.class),
                            row.get("reason", String.class), score == null ? null : ((Number) score).doubleValue(),
                            rank == null ? 0 : ((Number) rank).intValue(), row.get("source_types", String.class));
                })
                .all();
    }

    /** Completes when every queued batch has been written (or failed), or after {@code timeout}. Tests/shutdown. */
    public Mono<Void> flush(Duration timeout) {
        return Flux.interval(Duration.ZERO, Duration.ofMillis(10))
                .filter(t -> pending.get() == 0)
                .next()
                .timeout(timeout, Mono.empty())
                .then();
    }

    int pending() {
        return pending.get();
    }

    private Mono<Void> write(Batch batch) {
        var delete = db.sql("UPDATE memory_recall_events SET is_valid = 0 WHERE tenant_id = :tenantId"
                        + " AND subject = :subject AND turn_id = :turnId AND is_valid = 1")
                .bind("tenantId", batch.scope().tenantId()).bind("subject", batch.scope().subject())
                .bind("turnId", batch.turnId())
                .fetch().rowsUpdated();
        return delete.thenMany(Flux.fromIterable(batch.events()).concatMap(e -> insert(batch, e))).then();
    }

    private Mono<Long> insert(Batch batch, Event e) {
        var spec = db.sql("INSERT INTO memory_recall_events (id, tenant_id, subject, conversation_id, turn_id, kind,"
                        + " ref_id, reason, score, `rank`, source_types, created_at) VALUES (:id, :tenantId, :subject,"
                        + " :c, :turnId, :kind, :ref, :reason, :score, :rank, :sources, CURRENT_TIMESTAMP(6))")
                .bind("id", NonBlockingIds.uuid()).bind("tenantId", batch.scope().tenantId())
                .bind("subject", batch.scope().subject()).bind("c", batch.conversationId())
                .bind("turnId", batch.turnId()).bind("kind", e.kind()).bind("ref", e.refId())
                .bind("rank", e.rank())
                .bind("sources", e.sourceTypes().stream().map(Enum::name).collect(Collectors.joining(",")));
        spec = e.reason() == null ? spec.bindNull("reason", String.class) : spec.bind("reason", e.reason());
        spec = e.score() == null ? spec.bindNull("score", Double.class) : spec.bind("score", e.score());
        return spec.fetch().rowsUpdated();
    }

    private void count(String outcome) {
        if (metrics != null) {
            metrics.recallExplain(outcome);
        }
    }

    /** Stops accepting batches and drains what is queued (bounded wait). */
    @Override
    public void close() {
        synchronized (queue) {
            queue.tryEmitComplete();
        }
        flush(Duration.ofSeconds(5)).block();
        drainer.dispose();
    }
}
