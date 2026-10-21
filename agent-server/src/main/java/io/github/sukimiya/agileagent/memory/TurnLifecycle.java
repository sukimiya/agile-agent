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

    public TurnLifecycle(ConnectionFactory connectionFactory, TurnRepository turns,
            ConversationRepository conversations, TurnFinalizer finalizer, MemoryProperties props) {
        this.turns = turns;
        this.conversations = conversations;
        this.finalizer = finalizer;
        this.props = props;
        this.transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
    }

    /**
     * In one transaction: verify or create the owned conversation, resolve the request id (replay a completed
     * turn, 409 for an active/terminated one or different text), take the conversation's single active slot and
     * append the user message. Concurrent acceptances collide on unique constraints and are re-evaluated, so at
     * most one is accepted; the others see TURN_IN_PROGRESS with the winner's turnId.
     */
    public Mono<AcceptedTurn> accept(TurnCommand command) {
        return Mono.defer(() -> {
            if (!IDEMPOTENCY_KEY.matcher(command.requestId()).matches()) {
                return Mono.error(new MemoryException(ErrorCode.INVALID_IDEMPOTENCY_KEY));
            }
            var payload = conversations.serialize(LlmClient.Message.user(command.text()));
            props.checkMessageSize(payload);
            var hash = sha256(command.text());
            return transactions.transactional(Mono.defer(() -> acceptOnce(command, payload, hash)))
                    .retryWhen(Retry.max(ACCEPT_ATTEMPTS - 1).filter(TurnLifecycle::isAcceptRace))
                    .onErrorMap(e -> !(e instanceof MemoryException),
                            e -> new MemoryException(ErrorCode.STORAGE_UNAVAILABLE, unwrap(e)));
        });
    }

    private Mono<AcceptedTurn> acceptOnce(TurnCommand command, String payload, String hash) {
        var conversationId = command.conversationId();
        return conversations.ensureOwnedSession(conversationId, command.scope(), command.text())
                .then(turns.findByRequest(conversationId, command.requestId()))
                .flatMap(existing -> resolveExisting(existing, hash))
                .switchIfEmpty(Mono.defer(() -> turns.findActive(conversationId)
                        .flatMap(active -> active.leaseExpired()
                                ? interruptExpired(active)
                                : Mono.<Boolean>error(MemoryException.turnInProgress(active.id())))
                        .then(Mono.defer(() -> insertTurn(command, payload, hash)))));
    }

    private Mono<AcceptedTurn> resolveExisting(TurnRow existing, String hash) {
        if (!existing.requestHash().equals(hash)) {
            return Mono.error(new MemoryException(ErrorCode.IDEMPOTENCY_CONFLICT));
        }
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

    private Mono<AcceptedTurn> insertTurn(TurnCommand command, String payload, String hash) {
        var turnId = NonBlockingIds.uuid();
        return turns.insertAccepted(turnId, command.scope(), command.conversationId(), command.requestId(), hash)
                .flatMap(ticket -> conversations.appendMessage(command.scope(), command.conversationId(), turnId,
                                "user", SourceType.USER_INPUT, payload)
                        .then(conversations.bumpRevision(command.conversationId()))
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
        return transactions.transactional(turns.renew(ticket)
                .flatMap(owned -> owned ? conversations.saveAssistantText(ticket, text).thenReturn(true)
                        : Mono.just(false)));
    }

    private static boolean isAcceptRace(Throwable e) {
        var cause = unwrap(e);
        return cause instanceof DataIntegrityViolationException || cause instanceof TransientDataAccessException;
    }

    private static Throwable unwrap(Throwable e) {
        return e instanceof DataAccessException || e.getCause() == null ? e : e.getCause();
    }

    static String sha256(String text) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
