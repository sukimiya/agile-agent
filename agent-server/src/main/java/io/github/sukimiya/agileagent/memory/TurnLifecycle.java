package io.github.sukimiya.agileagent.memory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;

import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryModel.AcceptedTurn;
import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnCommand;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.memory.TurnRepository.TurnRow;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import io.r2dbc.spi.ConnectionFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Turn acceptance and execution. {@link #accept} durably records the user message and an ACCEPTED turn before
 * any response is committed; {@link #execute} runs the model only while subscribed and maps the outcome to a
 * terminal state: normal completion with Completed → COMPLETED, error → FAILED, lost lease/fence → INTERRUPTED,
 * downstream cancel (client disconnect) → CANCELLED. Execution never continues after the subscriber leaves.
 */
@Service
public class TurnLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TurnLifecycle.class);
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final int ACCEPT_ATTEMPTS = 4;

    /** The turn's lease or fence was lost (recovery, deletion or expiry); the execution has been stopped. */
    public static class ExecutionRightLostException extends RuntimeException {
        public ExecutionRightLostException() {
            super("EXECUTION_RIGHT_LOST");
        }
    }

    /** The upstream ended without a Completed result; the turn is recorded as FAILED. */
    public static class IncompleteTurnException extends RuntimeException {
        public IncompleteTurnException() {
            super("MISSING_COMPLETED");
        }
    }

    private final TurnRepository turns;
    private final ConversationRepository conversations;
    private final TurnFinalizer finalizer;
    private final MemoryProperties props;
    private final TransactionalOperator transactions;
    private final InstanceGuard instanceGuard;
    private final MemoryMetrics metrics;

    public TurnLifecycle(ConnectionFactory connectionFactory, TurnRepository turns,
            ConversationRepository conversations, TurnFinalizer finalizer, MemoryProperties props,
            InstanceGuard instanceGuard, MemoryMetrics metrics) {
        this.instanceGuard = instanceGuard;
        this.metrics = metrics;
        this.turns = turns;
        this.conversations = conversations;
        this.finalizer = finalizer;
        this.props = props;
        this.transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
    }

    /**
     * In one transaction:
     * <ol>
     * <li>With an {@code Idempotency-Key}, look the key up in the caller's (tenant, subject) scope <em>before</em>
     * any conversation id is generated. A different fingerprint is 409 {@code IDEMPOTENCY_CONFLICT}. With the
     * same fingerprint, a completed turn is replayed, an active one is 409 {@code TURN_IN_PROGRESS} with its
     * turnId, any other terminal turn is 409 {@code TURN_TERMINATED}, and a deleted conversation is 404. A
     * terminal turn past the retention releases its key and the request is treated as new.</li>
     * <li>Otherwise: use the given conversation id or generate one, verify or create the owned conversation,
     * take its single active slot and append the user message.</li>
     * </ol>
     * Concurrent acceptances collide on unique constraints (active slot, user key) and are re-evaluated, so at
     * most one is accepted; the others see TURN_IN_PROGRESS with the winner's turnId and leave nothing behind.
     * Identical text alone never deduplicates: without a key every request is new.
     */
    public Mono<AcceptedTurn> accept(TurnCommand command) {
        return Mono.defer(() -> {
            if (command.idempotencyKey() != null && !isValidRequestId(command.idempotencyKey())) {
                return Mono.error(new MemoryException(ErrorCode.INVALID_IDEMPOTENCY_KEY));
            }
            // Single-instance deployment: while another live instance shares the namespace, accept nothing.
            if (instanceGuard.conflict()) {
                return Mono.error(new MemoryException(ErrorCode.INSTANCE_CONFLICT));
            }
            var payload = conversations.serialize(LlmClient.Message.user(command.text()));
            props.checkMessageSize(payload);
            var hash = fingerprint(command);
            // Set by the attempt that interrupted an expired turn; read only after that attempt committed.
            var interruptedExpired = new AtomicBoolean();
            return transactions.transactional(Mono.defer(() -> {
                        interruptedExpired.set(false);
                        return acceptOnce(command, payload, hash, interruptedExpired);
                    }))
                    .retryWhen(Retry.max(ACCEPT_ATTEMPTS - 1).filter(TurnLifecycle::isAcceptRace))
                    .onErrorMap(e -> !(e instanceof MemoryException),
                            e -> new MemoryException(ErrorCode.STORAGE_UNAVAILABLE, unwrap(e)))
                    .doOnNext(accepted -> {
                        if (interruptedExpired.get()) {
                            metrics.terminal(TurnStatus.INTERRUPTED);
                        }
                        metrics.accepted(accepted.replay());
                    });
        });
    }

    /** {@code Idempotency-Key} format: 1–128 ASCII letters, digits or {@code ._:-}. */
    public static boolean isValidRequestId(String requestId) {
        return requestId != null && IDEMPOTENCY_KEY.matcher(requestId).matches();
    }

    private Mono<AcceptedTurn> acceptOnce(TurnCommand command, String payload, String hash,
            AtomicBoolean interruptedExpired) {
        var fresh = Mono.defer(() -> acceptNew(command, payload, hash, interruptedExpired));
        if (command.idempotencyKey() == null) {
            return fresh;
        }
        return turns.findByKey(command.scope(), command.idempotencyKey())
                .flatMap(existing -> existing.keyExpired() && existing.status().terminal()
                        ? turns.releaseExpiredKey(existing.id()).then(Mono.<AcceptedTurn>empty())
                        : resolveExisting(command, existing, hash))
                .switchIfEmpty(fresh);
    }

    private Mono<AcceptedTurn> acceptNew(TurnCommand command, String payload, String hash,
            AtomicBoolean interruptedExpired) {
        // Only reached after the key lookup found nothing: a new conversation id is generated here, never earlier.
        var conversationId = command.conversationId() != null ? command.conversationId() : NonBlockingIds.uuid();
        return conversations.ensureOwnedSession(conversationId, command.scope(), command.text())
                .then(turns.findActive(conversationId))
                .flatMap(active -> active.leaseExpired()
                        ? interruptExpired(active).doOnNext(interruptedExpired::set)
                        : Mono.<Boolean>error(MemoryException.turnInProgress(active.id())))
                .then(Mono.defer(() -> insertTurn(command, conversationId, payload, hash)));
    }

    private Mono<AcceptedTurn> resolveExisting(TurnCommand command, TurnRow existing, String hash) {
        if (!existing.requestHash().equals(hash)) {
            return Mono.error(new MemoryException(ErrorCode.IDEMPOTENCY_CONFLICT));
        }
        // The key's conversation must still be the caller's and not deleted (404, never a replay of deleted text).
        return conversations.ensureOwnedSession(existing.conversationId(), command.scope(), command.text())
                .then(Mono.defer(() -> resolveStatus(existing)));
    }

    private Mono<AcceptedTurn> resolveStatus(TurnRow existing) {
        return switch (existing.status()) {
            case COMPLETED -> conversations.assistantText(existing.id()).defaultIfEmpty("")
                    .map(answer -> new AcceptedTurn(existing.ticket(), true, answer, existing.usage()));
            case ACCEPTED, RUNNING -> Mono.error(MemoryException.turnInProgress(existing.id()));
            default -> Mono.error(new MemoryException(ErrorCode.TURN_TERMINATED));
        };
    }

    private Mono<Boolean> interruptExpired(TurnRow active) {
        return turns.interruptExpired(active.id(), active.fence())
                .flatMap(done -> done ? conversations.bumpRevision(active.conversationId()).thenReturn(true)
                        : Mono.error(MemoryException.turnInProgress(active.id())));
    }

    private Mono<AcceptedTurn> insertTurn(TurnCommand command, String conversationId, String payload, String hash) {
        var turnId = NonBlockingIds.uuid();
        return turns.insertAccepted(turnId, command.scope(), conversationId, command.idempotencyKey(), hash)
                .flatMap(ticket -> conversations.appendMessage(command.scope(), conversationId, turnId,
                                "user", SourceType.USER_INPUT, payload)
                        .then(conversations.bumpRevision(conversationId))
                        .thenReturn(new AcceptedTurn(ticket, false, null, TokenUsage.notObserved())));
    }

    /**
     * Runs {@code operation} for an accepted ticket while subscribed. The ticket is consumed on start, so a
     * second subscription (or a ticket already recovered/terminated) never invokes the operation. The lease is
     * renewed in the background of this subscription; losing it cancels the operation.
     */
    public Flux<String> execute(TurnTicket ticket, Function<TurnRecorder, Flux<String>> operation) {
        return Flux.defer(() -> turns.start(ticket)
                .onErrorMap(e -> !(e instanceof MemoryException),
                        e -> new MemoryException(ErrorCode.STORAGE_UNAVAILABLE, unwrap(e)))
                .flatMapMany(started -> started ? run(ticket, operation) : rejectStart(ticket)));
    }

    private Flux<String> run(TurnTicket ticket, Function<TurnRecorder, Flux<String>> operation) {
        var recorder = new TurnRecorder(ticket, this::checkpoint, props, System::nanoTime);
        var written = new AtomicBoolean();
        var lost = new AtomicBoolean();
        Duration renewEvery = props.getLeaseRenewInterval();
        Mono<Boolean> lostSignal = Flux.interval(renewEvery, renewEvery)
                .onBackpressureDrop()
                .concatMap(tick -> turns.renew(ticket).onErrorResume(e -> {
                    log.warn("memory.turn lease renewal failed turnId={}: {}", ticket.turnId(),
                            NestedExceptionUtils.getMostSpecificCause(e).toString());
                    return Mono.just(true);
                }))
                .filter(owned -> !owned)
                .next()
                .doOnNext(x -> lost.set(true));

        return Flux.defer(() -> operation.apply(recorder))
                .takeUntilOther(lostSignal)
                .concatWith(Mono.defer(() -> lost.get() ? Mono.error(new ExecutionRightLostException()) : Mono.empty()))
                .onErrorResume(error -> {
                    var status = error instanceof ExecutionRightLostException ? TurnStatus.INTERRUPTED : TurnStatus.FAILED;
                    return finalizer.finish(ticket, status, recorder.text(), recorder.usage(),
                                    TurnFinalizer.defaultErrorCode(status))
                            .doOnNext(written::set)
                            .then(Mono.error(error));
                })
                .concatWith(Mono.defer(() -> complete(ticket, recorder, written)))
                .doOnCancel(() -> {
                    if (!written.get()) {
                        finalizer.enqueue(ticket, TurnStatus.CANCELLED, recorder.text());
                    }
                });
    }

    private Mono<String> complete(TurnTicket ticket, TurnRecorder recorder, AtomicBoolean written) {
        if (!recorder.isCompleted()) {
            return finalizer.finish(ticket, TurnStatus.FAILED, recorder.text(), recorder.usage(), "MISSING_COMPLETED")
                    .doOnNext(written::set)
                    .then(Mono.error(new IncompleteTurnException()));
        }
        if (finalizer.exceedsLimit(recorder.text())) {
            // The answer cannot be stored untruncated: FAILED without the body, and the stream reports it.
            return finalizer.finish(ticket, TurnStatus.FAILED, null, recorder.usage(), ErrorCode.MESSAGE_TOO_LARGE.name())
                    .doOnNext(written::set)
                    .then(Mono.error(new MemoryException(ErrorCode.MESSAGE_TOO_LARGE)));
        }
        return finalizer.finish(ticket, TurnStatus.COMPLETED, recorder.text(), recorder.usage(), null)
                .doOnNext(written::set)
                .flatMap(ok -> ok ? Mono.<String>empty()
                        : Mono.error(new MemoryException(ErrorCode.STORAGE_UNAVAILABLE)));
    }

    private Flux<String> rejectStart(TurnTicket ticket) {
        return turns.findById(ticket.turnId())
                .flatMapMany(row -> Flux.<String>error(row.status() == TurnStatus.RUNNING
                        ? MemoryException.turnInProgress(row.id())
                        : new MemoryException(ErrorCode.TURN_TERMINATED)))
                .switchIfEmpty(Flux.error(new MemoryException(ErrorCode.TURN_TERMINATED)));
    }

    /** Fenced checkpoint: renews the lease and saves the text in one transaction; false when not the owner. */
    private Mono<Boolean> checkpoint(TurnTicket ticket, String text) {
        return Mono.defer(() -> {
            long start = System.nanoTime();
            return transactions.transactional(turns.renew(ticket)
                            .flatMap(owned -> owned ? conversations.saveAssistantText(ticket, text).thenReturn(true)
                                    : Mono.just(false)))
                    .doOnNext(owned -> {
                        if (owned) {
                            metrics.checkpointLatency(Duration.ofNanos(System.nanoTime() - start));
                        }
                    });
        });
    }

    private static boolean isAcceptRace(Throwable e) {
        var cause = unwrap(e);
        return cause instanceof DataIntegrityViolationException || cause instanceof TransientDataAccessException;
    }

    private static Throwable unwrap(Throwable e) {
        return e instanceof DataAccessException || e.getCause() == null ? e : e.getCause();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper FINGERPRINT_JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * Request fingerprint: SHA-256 (hex) of a canonical JSON object with the endpoint, the client-supplied
     * conversationId ({@code null} when absent, never the generated one), the message content and the attachment
     * ids. The key itself is not part of it.
     */
    static String fingerprint(TurnCommand command) {
        var canonical = new java.util.LinkedHashMap<String, Object>();
        canonical.put("v", 1);
        canonical.put("endpoint", command.endpoint());
        canonical.put("conversationId", command.conversationId());
        canonical.put("content", command.text());
        canonical.put("attachmentIds", command.attachmentIds());
        try {
            return sha256(FINGERPRINT_JSON.writeValueAsString(canonical));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(String text) {
        return HexFormat.of().formatHex(digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
