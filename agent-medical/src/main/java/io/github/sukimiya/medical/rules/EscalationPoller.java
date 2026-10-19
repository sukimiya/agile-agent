package io.github.sukimiya.medical.rules;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive SLA poller (spec §9, ruling 5): a {@code Flux.interval} loop calling
 * {@link EscalationService#tick()} — the idempotent full-scan escalation plus stale-claim
 * recovery routine. The per-tick {@code onErrorResume} is load-bearing: without it a single
 * errored tick would terminate the interval for the lifetime of the process. Registered via
 * {@code @Bean(initMethod = "start", destroyMethod = "stop")} so it lives and dies with the
 * application context; start()/stop() are synchronized and idempotent.
 */
public final class EscalationPoller {

    private static final Logger log = LoggerFactory.getLogger(EscalationPoller.class);

    private final EscalationService escalation;
    private final Duration interval;
    private Disposable subscription;

    public EscalationPoller(EscalationService escalation, Duration interval) {
        this.escalation = escalation;
        this.interval = interval;
    }

    public synchronized void start() {
        if (subscription != null) {
            return;
        }
        subscription = Flux.interval(interval, interval)
                .concatMap(tick -> escalation.tick()
                        .onErrorResume(error -> {
                            log.warn("Escalation poller tick failed: {}", error.toString());
                            return Mono.empty();
                        }))
                .subscribe();
        log.info("Escalation poller started (interval={})", interval);
    }

    public synchronized void stop() {
        if (subscription == null) {
            return;
        }
        subscription.dispose();
        subscription = null;
        log.info("Escalation poller stopped");
    }
}
