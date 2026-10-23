package io.github.sukimiya.agileagent.memory;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.util.NonBlockingIds;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Single-instance protection (M1 supports exactly one server per deployment namespace). Each instance registers a
 * row in {@code service_instances} and renews it every {@code app.memory.instance-heartbeat-interval} on database
 * time. Another row in the same {@code app.memory.namespace} whose heartbeat is younger than
 * {@code app.memory.instance-stale-after} is a conflict: it is logged at ERROR, reported by the
 * {@code memory.instance.conflict} gauge and the {@code memoryInstance} health component, and
 * {@link TurnLifecycle#accept} refuses new turns with {@code INSTANCE_CONFLICT} (503). Turns already running
 * finish normally. A failed heartbeat keeps the last known state. On stop the instance deregisters so the peer's
 * conflict clears at its next heartbeat; a crashed peer stops counting once its heartbeat is stale.
 */
@Component
public class InstanceGuard implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(InstanceGuard.class);
    private static final Duration RETAIN_STALE_ROWS = Duration.ofDays(1);

    private final DatabaseClient db;
    private final MemoryProperties props;
    private final String instanceId = NonBlockingIds.uuid();
    private final String host;
    private volatile boolean conflict;
    private volatile long peers;
    private volatile Disposable schedule;

    public InstanceGuard(DatabaseClient db, MemoryProperties props, MeterRegistry meters) {
        this.db = db;
        this.props = props;
        var hostname = System.getenv("HOSTNAME");
        this.host = hostname == null || hostname.isBlank() ? null
                : hostname.length() <= 255 ? hostname : hostname.substring(0, 255);
        Gauge.builder("memory.instance.conflict", this, guard -> guard.conflict ? 1 : 0)
                .description("1 while another live server instance shares this deployment namespace")
                .register(meters);
    }

    public String instanceId() {
        return instanceId;
    }

    /** True while another live instance shares this namespace (as of the last successful heartbeat). */
    public boolean conflict() {
        return conflict;
    }

    /** Number of other live instances seen at the last successful heartbeat. */
    public long peers() {
        return peers;
    }

    /** Registers or renews this instance, then re-evaluates the conflict state. Errors propagate. */
    public Mono<Void> heartbeat() {
        var namespace = props.getNamespace();
        var staleMicros = props.getInstanceStaleAfter().toNanos() / 1_000;
        return db.sql("UPDATE service_instances SET heartbeat_at = CURRENT_TIMESTAMP(6), namespace = :ns"
                        + " WHERE instance_id = :id")
                .bind("ns", namespace).bind("id", instanceId)
                .fetch().rowsUpdated()
                .flatMap(n -> n > 0 ? Mono.just(n) : register(namespace))
                .then(db.sql("SELECT COUNT(*) AS n FROM service_instances WHERE namespace = :ns"
                                + " AND instance_id <> :id"
                                + " AND heartbeat_at > TIMESTAMPADD(MICROSECOND, -:staleMicros, CURRENT_TIMESTAMP(6))")
                        .bind("ns", namespace).bind("id", instanceId).bind("staleMicros", staleMicros)
                        .map(row -> TurnRepository.number(row.get("n")))
                        .one())
                .doOnNext(this::update)
                .then(pruneStale(namespace));
    }

    private Mono<Long> register(String namespace) {
        var spec = db.sql("INSERT INTO service_instances (instance_id, namespace, host, started_at, heartbeat_at)"
                        + " VALUES (:id, :ns, :host, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))")
                .bind("id", instanceId).bind("ns", namespace);
        spec = host == null ? spec.bindNull("host", String.class) : spec.bind("host", host);
        return spec.fetch().rowsUpdated();
    }

    /** Rows of crashed instances stop counting once stale; drop them after a day so restarts don't pile up. */
    private Mono<Void> pruneStale(String namespace) {
        return db.sql("DELETE FROM service_instances WHERE namespace = :ns AND instance_id <> :id"
                        + " AND heartbeat_at < TIMESTAMPADD(MICROSECOND, -:retainMicros, CURRENT_TIMESTAMP(6))")
                .bind("ns", namespace).bind("id", instanceId)
                .bind("retainMicros", RETAIN_STALE_ROWS.toNanos() / 1_000)
                .fetch().rowsUpdated().then();
    }

    private void update(long livePeers) {
        var wasConflict = conflict;
        peers = livePeers;
        conflict = livePeers > 0;
        if (conflict) {
            // Logged on every heartbeat while it lasts: an operator must act (scale down to one instance).
            log.error("memory.instance INSTANCE_CONFLICT: {} other live instance(s) in namespace '{}'; refusing new"
                    + " turns until only one instance remains (instanceId={})", livePeers, props.getNamespace(),
                    instanceId);
        } else if (wasConflict) {
            log.warn("memory.instance conflict cleared in namespace '{}'; accepting new turns again (instanceId={})",
                    props.getNamespace(), instanceId);
        }
    }

    @Override
    public void start() {
        Duration every = props.getInstanceHeartbeatInterval();
        schedule = Flux.interval(Duration.ZERO, every)
                .onBackpressureDrop()
                .concatMap(tick -> heartbeat().onErrorResume(e -> {
                    log.warn("memory.instance heartbeat failed, keeping last state (conflict={}): {}", conflict,
                            NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.empty();
                }))
                .subscribe();
    }

    /** Stops heartbeating and deregisters (best effort, bounded) so a peer's conflict clears promptly. */
    @Override
    public void stop() {
        var current = schedule;
        if (current != null) {
            current.dispose();
        }
        schedule = null;
        try {
            db.sql("DELETE FROM service_instances WHERE instance_id = :id").bind("id", instanceId)
                    .fetch().rowsUpdated().block(Duration.ofSeconds(2));
        } catch (RuntimeException e) {
            log.warn("memory.instance deregistration failed (the row goes stale instead): {}",
                    NestedExceptionUtils.getMostSpecificCause(e).toString());
        }
    }

    @Override
    public boolean isRunning() {
        var current = schedule;
        return current != null && !current.isDisposed();
    }

    @Override
    public boolean isAutoStartup() {
        return props.isInstanceGuardEnabled();
    }
}
