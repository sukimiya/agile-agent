package io.github.sukimiya.agileagent.memory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;

import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

/**
 * Managed terminal writes. {@link #finish} is a subscribed, time-bounded, fenced transaction (terminal status,
 * last text, session revision) returning whether it was written. Cancellation paths cannot subscribe in-chain,
 * so they {@link #enqueue} onto a bounded queue drained by one long-lived subscription owned by this bean,
 * never a fire-and-forget write from {@code doFinally}. Any failure, timeout or full queue leaves the turn
 * active; {@link ExpiredTurnRecovery} then marks it INTERRUPTED after the lease expires.
 */
@Component
public class TurnFinalizer implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TurnFinalizer.class);
    private static final int QUEUE_CAPACITY = 1024;

    private record Request(TurnTicket ticket, TurnStatus status, String text, TokenUsage usage, String errorCode) {}

    private final TurnRepository turns;
    private final ConversationRepository conversations;
    private final MemoryProperties props;
    private final TransactionalOperator transactions;
    private final Sinks.Many<Request> queue = Sinks.many().unicast()
            .onBackpressureBuffer(Queues.<Request>get(QUEUE_CAPACITY).get());
    private final CountDownLatch drained = new CountDownLatch(1);
    private final Disposable worker;
    private final AtomicLong succeeded = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    public TurnFinalizer(ConnectionFactory connectionFactory, TurnRepository turns,
            ConversationRepository conversations, MemoryProperties props) {
        this.turns = turns;
        this.conversations = conversations;
        this.props = props;
        this.transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
        this.worker = queue.asFlux()
                .concatMap(r -> finish(r.ticket(), r.status(), r.text(), r.usage(), r.errorCode()))
                .doFinally(signal -> drained.countDown())
                .subscribe(written -> { }, e -> log.error("memory.finalizer queue stopped", e));
    }

    public Mono<Boolean> finish(TurnTicket ticket, TurnStatus status, String partialText) {
        return finish(ticket, status, partialText, TokenUsage.notObserved(), defaultErrorCode(status));
    }

    /**
     * Writes the terminal state and the last text in one fenced transaction within the finalizer timeout.
     * Emits false (never an error) when the ticket lost the turn, the turn is already terminal, or the write
     * failed or timed out.
     */
    public Mono<Boolean> finish(TurnTicket ticket, TurnStatus status, String text, TokenUsage usage, String errorCode) {
        return Mono.defer(() -> {
            var storedText = text == null || text.isEmpty() ? null : text;
            var finalStatus = status;
            var finalCode = errorCode;
            if (storedText != null && exceedsLimit(storedText)) {
                storedText = null;
                finalCode = MemoryException.ErrorCode.MESSAGE_TOO_LARGE.name();
                if (finalStatus == TurnStatus.COMPLETED) {
                    finalStatus = TurnStatus.FAILED;
                }
            }
            var textToSave = storedText;
            var write = turns.markTerminal(ticket, finalStatus, usage, finalCode)
                    .flatMap(owned -> !owned ? Mono.just(false)
                            : (textToSave == null ? Mono.<Void>empty() : conversations.saveAssistantText(ticket, textToSave))
                                    .then(conversations.bumpRevision(ticket.conversationId()))
                                    .thenReturn(true));
            return transactions.transactional(write);
        })
                .timeout(props.getFinalizerTimeout())
                .onErrorResume(e -> {
                    log.warn("memory.finalizer write failed turnId={} status={}: {}", ticket.turnId(), status,
                            NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.just(false);
                })
                .doOnNext(written -> (written ? succeeded : failed).incrementAndGet());
    }

    /** Queues a terminal write from a cancellation path; false (and counted as failed) when the queue is full. */
    public boolean enqueue(TurnTicket ticket, TurnStatus status, String text) {
        var result = queue.tryEmitNext(new Request(ticket, status, text, TokenUsage.notObserved(),
                defaultErrorCode(status)));
        int spins = 0;
        while (result == Sinks.EmitResult.FAIL_NON_SERIALIZED && spins++ < 1000) {
            Thread.onSpinWait();
            result = queue.tryEmitNext(new Request(ticket, status, text, TokenUsage.notObserved(),
                    defaultErrorCode(status)));
        }
        if (result.isFailure()) {
            failed.incrementAndGet();
            log.warn("memory.finalizer queue rejected turnId={} status={} result={}; recovery scan will interrupt it",
                    ticket.turnId(), status, result);
            return false;
        }
        return true;
    }

    public long succeeded() {
        return succeeded.get();
    }

    public long failed() {
        return failed.get();
    }

    /** Stops accepting work and waits briefly for queued terminal writes to drain. */
    @Override
    public void destroy() {
        queue.tryEmitComplete();
        try {
            if (!drained.await(props.getFinalizerTimeout().multipliedBy(2).toMillis(), TimeUnit.MILLISECONDS)) {
                worker.dispose();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            worker.dispose();
        }
    }

    private boolean exceedsLimit(String text) {
        try {
            props.checkMessageSize(conversations.serialize(io.github.sukimiya.agileagent.service.LlmClient.Message
                    .assistantText(text)));
            return false;
        } catch (MemoryException e) {
            return true;
        }
    }

    static String defaultErrorCode(TurnStatus status) {
        return switch (status) {
            case CANCELLED -> "CLIENT_CANCELLED";
            case INTERRUPTED -> "EXECUTION_RIGHT_LOST";
            case FAILED -> "EXECUTION_FAILED";
            default -> null;
        };
    }
}
