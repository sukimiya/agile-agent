package io.github.sukimiya.agileagent.memory;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.context.SmartLifecycle;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;

import io.r2dbc.spi.ConnectionFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Fallback for turns nobody finishes: accepted but never subscribed, process exit, finalizer failure or timeout.
 * Every active turn whose database-time lease has expired is marked INTERRUPTED with an incremented fence (so the
 * old executor can no longer write) and the session revision is bumped. Runs at startup and then every
 * {@code app.memory.recovery-scan-interval}; terminal turns are never touched.
 */
@Component
public class ExpiredTurnRecovery implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ExpiredTurnRecovery.class);
    private static final int BATCH = 100;

    private final TurnRepository turns;
    private final ConversationRepository conversations;
    private final MemoryProperties props;
    private final TransactionalOperator transactions;
    private volatile Disposable schedule;

    public ExpiredTurnRecovery(ConnectionFactory connectionFactory, TurnRepository turns,
            ConversationRepository conversations, MemoryProperties props) {
        this.turns = turns;
        this.conversations = conversations;
        this.props = props;
        this.transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
    }

    /** One scan; emits how many turns were interrupted. */
    public Mono<Integer> runOnce() {
        return turns.findExpired(BATCH)
                .concatMap(row -> transactions.transactional(turns.interruptExpired(row.id(), row.fence())
                        .flatMap(done -> done
                                ? conversations.bumpRevision(row.conversationId()).thenReturn(1)
                                : Mono.just(0))))
                .reduce(0, Integer::sum);
    }

    @Override
    public void start() {
        Duration every = props.getRecoveryScanInterval();
        schedule = Flux.interval(Duration.ZERO, every)
                .onBackpressureDrop()
                .concatMap(tick -> runOnce().onErrorResume(e -> {
                    log.warn("memory.recovery scan failed: {}", NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.just(0);
                }))
                .subscribe(count -> {
                    if (count > 0) {
                        log.info("memory.recovery interrupted {} expired turn(s)", count);
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
