package io.github.sukimiya.agileagent.memory.items;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.memory.MemoryMetrics;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Periodic decay inactivation (M2b Task 6): ACTIVE, valid items with a non-user assertion ({@code INFERRED} or
 * none) whose {@code COALESCE(last_recalled_at, created_at)} is at least {@code app.memory.decay.inactive-after}
 * old (database time) become INACTIVE through {@link MemoryItemService#inactivateDecayed} (revision + 1, audited
 * {@code DECAY_INACTIVATE} by {@value #ACTOR}, profile snapshot re-rendered). Never deletes. Runs every
 * {@code interval} when {@code app.memory.decay.enabled} and {@code app.memory.recovery-enabled} are set.
 */
@Component
public class MemoryDecay implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MemoryDecay.class);
    static final String ACTOR = "system:decay";
    static final String ACTION = "DECAY_INACTIVATE";

    private final DatabaseClient db;
    private final MemoryProperties props;
    private final MemoryMetrics metrics;
    private final MemoryItemService itemService;
    private volatile Disposable schedule;

    public MemoryDecay(DatabaseClient db, MemoryProperties props, MemoryMetrics metrics,
            MemoryItemService itemService) {
        this.db = db;
        this.props = props;
        this.metrics = metrics;
        this.itemService = itemService;
    }

    private record Stale(String id, String tenantId, String subject) {}

    /** One pass: inactivates every stale item, in batches of {@code batch}; emits how many changed. */
    public Mono<Integer> inactivateStale() {
        return pass(0).doOnNext(n -> {
            if (metrics != null) {
                metrics.decayInactivated(n);
            }
            if (n > 0) {
                log.info("memory.decay inactivated={}", n);
            }
        });
    }

    private Mono<Integer> pass(int total) {
        return batch().flatMap(found -> found.isEmpty() ? Mono.just(total) : apply(found)
                // A batch that changed nothing (every row raced) ends the pass instead of looping.
                .flatMap(n -> n == 0 ? Mono.just(total) : pass(total + n)));
    }

    private Mono<Integer> apply(List<Stale> found) {
        var byOwner = new java.util.LinkedHashMap<MemoryScope, List<String>>();
        for (var s : found) {
            byOwner.computeIfAbsent(new MemoryScope(s.tenantId(), s.subject()), k -> new java.util.ArrayList<>())
                    .add(s.id());
        }
        return Flux.fromIterable(byOwner.entrySet())
                .concatMap(e -> itemService.inactivateDecayed(e.getKey(), e.getValue(), ACTOR, ACTION,
                        ageCondition()))
                .map(List::size)
                .reduce(0, Integer::sum);
    }

    /** SQL condition (on {@code memory_items}) of an item that decay may inactivate now. */
    private String ageCondition() {
        long seconds = Math.max(1, props.getDecay().getInactiveAfter().toSeconds());
        return "status = 'ACTIVE' AND is_valid = 1 AND (assertion IS NULL OR assertion NOT IN ('USER_STATED',"
                + " 'USER_CONFIRMED')) AND COALESCE(last_recalled_at, created_at) <= TIMESTAMPADD(SECOND, -"
                + seconds + ", CURRENT_TIMESTAMP(6))";
    }

    private Mono<List<Stale>> batch() {
        return db.sql("SELECT id, tenant_id, subject FROM memory_items WHERE " + ageCondition()
                        + " ORDER BY id LIMIT " + Math.max(1, props.getDecay().getBatch()))
                .map((row, meta) -> new Stale(row.get("id", String.class), row.get("tenant_id", String.class),
                        row.get("subject", String.class)))
                .all()
                .collectList();
    }

    @Override
    public void start() {
        Duration every = props.getDecay().getInterval();
        schedule = Flux.interval(every, every)
                .onBackpressureDrop()
                .concatMap(tick -> inactivateStale().onErrorResume(e -> {
                    log.warn("memory.decay failed: {}", NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.empty();
                }))
                .subscribe();
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
        return props.isRecoveryEnabled() && props.getDecay().isEnabled();
    }
}
