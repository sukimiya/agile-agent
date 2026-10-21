package io.github.sukimiya.agileagent.memory;

import static io.github.sukimiya.agileagent.memory.TurnTestSupport.ALICE;
import static io.github.sukimiya.agileagent.memory.TurnTestSupport.ALICE_OTHER_TENANT;
import static io.github.sukimiya.agileagent.memory.TurnTestSupport.command;
import static io.github.sukimiya.agileagent.memory.TurnTestSupport.newConversationId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryModel.AcceptedTurn;
import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Acceptance, idempotency, execution and terminal mapping on H2. {@link TurnConcurrencyIntegrationTest} re-runs
 * every case on a real MySQL with independent connections.
 */
class TurnLifecycleTest {

    static final Duration WAIT = Duration.ofSeconds(10);

    protected TurnTestSupport support;
    protected TurnLifecycle lifecycle;
    protected final AtomicInteger modelCalls = new AtomicInteger();

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUpLifecycle() {
        support = TurnTestSupport.create(newFixture(),
                p -> p.setLeaseRenewInterval(Duration.ofMillis(100)), t -> t);
        lifecycle = support.lifecycle;
    }

    @AfterEach
    void closeSupport() {
        support.close();
    }

    /** Fake model: streams the answer as two deltas and reports Completed with usage. */
    Function<TurnRecorder, Flux<String>> answering(String answer) {
        return recorder -> Flux.defer(() -> {
            modelCalls.incrementAndGet();
            var half = answer.length() / 2;
            return Flux.just(answer.substring(0, half), answer.substring(half))
                    .concatMap(d -> recorder.delta(d).thenReturn(d))
                    .concatWith(recorder.completed(answer, new TokenUsage(true, 3, 4, 7)).then(Mono.empty()));
        });
    }

    AcceptedTurn accept(TurnCommand command) {
        return lifecycle.accept(command).block(WAIT);
    }

    List<String> run(AcceptedTurn accepted, Function<TurnRecorder, Flux<String>> operation) {
        return lifecycle.execute(accepted.ticket(), operation).collectList().block(WAIT);
    }

    static void assertCode(Throwable e, ErrorCode code) {
        assertThat(e).isInstanceOfSatisfying(MemoryException.class, m -> assertThat(m.code()).isEqualTo(code));
    }

    @Test
    void acceptPersistsUserMessageAndAcceptedTurnBeforeExecution() {
        var conversation = newConversationId();

        var accepted = accept(command(conversation, "k1", "你好"));

        assertThat(accepted.replay()).isFalse();
        assertThat(accepted.ticket().scope()).isEqualTo(ALICE);
        assertThat(support.status(accepted.ticket().turnId())).isEqualTo("ACCEPTED");
        assertThat(support.messageCount(conversation, "user")).isEqualTo(1);
        assertThat(support.activeTurn(conversation)).isEqualTo(accepted.ticket().turnId());
        assertThat(support.revision(conversation)).isEqualTo(1);
    }

    @Test
    void completedTurnStoresAnswerUsageAndReleasesTheConversation() {
        var conversation = newConversationId();
        var accepted = accept(command(conversation, "k1", "hi"));

        assertThat(run(accepted, answering("hello world"))).containsExactly("hello", " world");

        var turnId = accepted.ticket().turnId();
        assertThat(support.status(turnId)).isEqualTo("COMPLETED");
        assertThat(support.assistantText(turnId)).isEqualTo("hello world");
        assertThat(support.activeTurn(conversation)).isNull();
        assertThat(support.fixture.jdbc().queryForObject(
                "SELECT total_tokens FROM chat_turns WHERE id = ?", Long.class, turnId)).isEqualTo(7);
        assertThat(accept(command(conversation, "k2", "next")).replay()).isFalse();
    }

    @Test
    void sameKeyDoesNotDuplicateUserMessage() {
        var conversation = newConversationId();
        var first = accept(command(conversation, "k1", "hi"));
        run(first, answering("hello"));

        var again = accept(command(conversation, "k1", "hi"));

        assertThat(again.replay()).isTrue();
        assertThat(again.ticket().turnId()).isEqualTo(first.ticket().turnId());
        assertThat(again.answer()).isEqualTo("hello");
        assertThat(again.usage()).isEqualTo(new TokenUsage(true, 3, 4, 7));
        assertThat(support.messageCount(conversation, "user")).isEqualTo(1);
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void sameKeyWhileActiveReturnsInProgressWithoutSecondMessage() {
        var conversation = newConversationId();
        var first = accept(command(conversation, "k1", "hi"));

        assertThatThrownBy(() -> accept(command(conversation, "k1", "hi"))).satisfies(e -> {
            assertCode(e, ErrorCode.TURN_IN_PROGRESS);
            assertThat(((MemoryException) e).turnId()).isEqualTo(first.ticket().turnId());
        });
        assertThat(support.messageCount(conversation, "user")).isEqualTo(1);
    }

    @Test
    void differentTextForSameKeyConflicts() {
        var conversation = newConversationId();
        run(accept(command(conversation, "k1", "hi")), answering("hello"));

        assertThatThrownBy(() -> accept(command(conversation, "k1", "something else")))
                .satisfies(e -> assertCode(e, ErrorCode.IDEMPOTENCY_CONFLICT));
        assertThat(support.messageCount(conversation, "user")).isEqualTo(1);
    }

    @Test
    void repeatedFailedRequestIsTerminated() {
        var conversation = newConversationId();
        var accepted = accept(command(conversation, "k1", "hi"));
        assertThatThrownBy(() -> run(accepted, r -> Flux.error(new IllegalStateException("model down"))))
                .hasMessageContaining("model down");

        assertThatThrownBy(() -> accept(command(conversation, "k1", "hi")))
                .satisfies(e -> assertCode(e, ErrorCode.TURN_TERMINATED));
    }

    @Test
    void activeConflictCarriesTurnId() {
        var conversation = newConversationId();
        var first = accept(command(conversation, "k1", "hi"));

        assertThatThrownBy(() -> accept(command(conversation, "k2", "another"))).satisfies(e -> {
            assertCode(e, ErrorCode.TURN_IN_PROGRESS);
            assertThat(((MemoryException) e).turnId()).isEqualTo(first.ticket().turnId());
        });
        assertThat(support.turnCount(conversation)).isEqualTo(1);
    }

    @Test
    void onlyOneConcurrentTurnAccepted() throws InterruptedException {
        var conversation = newConversationId();
        var results = concurrently(8, i -> command(conversation, "key-" + i, "parallel " + i));

        assertThat(results.stream().filter(r -> r instanceof AcceptedTurn)).hasSize(1);
        assertThat(results.stream().filter(r -> !(r instanceof AcceptedTurn)))
                .allSatisfy(e -> assertCode((Throwable) e, ErrorCode.TURN_IN_PROGRESS));
        assertThat(support.turnCount(conversation)).isEqualTo(1);
        assertThat(support.messageCount(conversation, "user")).isEqualTo(1);
    }

    @Test
    void concurrentRetriesOfOneKeyAreAcceptedOnce() throws InterruptedException {
        var conversation = newConversationId();
        var results = concurrently(8, i -> command(conversation, "same-key", "same text"));

        assertThat(results.stream().filter(r -> r instanceof AcceptedTurn)).hasSize(1);
        assertThat(results.stream().filter(r -> !(r instanceof AcceptedTurn)))
                .allSatisfy(e -> assertCode((Throwable) e, ErrorCode.TURN_IN_PROGRESS));
        assertThat(support.messageCount(conversation, "user")).isEqualTo(1);
    }

    @Test
    void foreignScopeCannotAcceptIntoAConversation() {
        var conversation = newConversationId();
        accept(command(conversation, "k1", "hi"));

        assertThatThrownBy(() -> accept(new TurnCommand(ALICE_OTHER_TENANT, conversation, "k9", "hi")))
                .satisfies(e -> assertCode(e, ErrorCode.CONVERSATION_NOT_FOUND));
        assertThat(support.turnCount(conversation)).isEqualTo(1);
    }

    @Test
    void invalidIdempotencyKeyIsRejected() {
        var conversation = newConversationId();
        for (var key : new String[] {"", "has space", "k".repeat(129), "键"}) {
            assertThatThrownBy(() -> accept(command(conversation, key, "hi")))
                    .satisfies(e -> assertCode(e, ErrorCode.INVALID_IDEMPOTENCY_KEY));
        }
        accept(command(conversation, "Abc-1._:z", "hi"));
    }

    @Test
    void oversizedUserMessageIsRejectedWithoutRows() {
        var conversation = newConversationId();
        var text = "a".repeat(support.props.getMaxMessageBytes() + 1);

        assertThatThrownBy(() -> accept(command(conversation, "k1", text)))
                .satisfies(e -> assertCode(e, ErrorCode.MESSAGE_TOO_LARGE));
        assertThat(support.turnCount(conversation)).isZero();
    }

    @Test
    void terminalStateCannotRegress() {
        var conversation = newConversationId();
        var accepted = accept(command(conversation, "k1", "hi"));
        run(accepted, answering("hello"));
        var ticket = accepted.ticket();

        assertThat(support.turns.markTerminal(ticket, MemoryModel.TurnStatus.FAILED).block(WAIT)).isFalse();
        assertThat(support.finalizer.finish(ticket, MemoryModel.TurnStatus.CANCELLED, "x").block(WAIT)).isFalse();
        assertThatThrownBy(() -> run(accepted, answering("again")))
                .satisfies(e -> assertCode(e, ErrorCode.TURN_TERMINATED));
        assertThat(support.status(ticket.turnId())).isEqualTo("COMPLETED");
        assertThat(support.assistantText(ticket.turnId())).isEqualTo("hello");
        assertThat(modelCalls).hasValue(1);
    }

    @Test
    void doubleSubscriptionRunsModelOnce() {
        var conversation = newConversationId();
        var accepted = accept(command(conversation, "k1", "hi"));
        var gate = new CountDownLatch(1);
        Function<TurnRecorder, Flux<String>> slow = recorder -> Flux.defer(() -> {
            modelCalls.incrementAndGet();
            return recorder.delta("partial").thenReturn("partial").flux()
                    .concatWith(Mono.fromRunnable(gate::countDown).then(Mono.never()));
        });
        var first = lifecycle.execute(accepted.ticket(), slow).subscribeOn(Schedulers.boundedElastic()).subscribe();
        try {
            await().atMost(WAIT).until(() -> gate.getCount() == 0);

            assertThatThrownBy(() -> run(accepted, slow)).satisfies(e -> {
                assertCode(e, ErrorCode.TURN_IN_PROGRESS);
                assertThat(((MemoryException) e).turnId()).isEqualTo(accepted.ticket().turnId());
            });
            assertThat(modelCalls).hasValue(1);
        } finally {
            first.dispose();
        }
    }

    @Test
    void unsubscribeMarksCancelled() {
        var conversation = newConversationId();
        var accepted = accept(command(conversation, "k1", "hi"));
        Function<TurnRecorder, Flux<String>> streaming = recorder -> Flux.concat(
                recorder.delta("部分回答").thenReturn("部分回答"), Mono.never());

        assertThat(lifecycle.execute(accepted.ticket(), streaming).take(1).collectList().block(WAIT))
                .containsExactly("部分回答");

        var turnId = accepted.ticket().turnId();
        await().atMost(WAIT).until(() -> "CANCELLED".equals(support.status(turnId)));
        assertThat(support.assistantText(turnId)).isEqualTo("部分回答");
        assertThat(support.activeTurn(conversation)).isNull();
    }

    @Test
    void executionErrorMarksFailed() {
        var conversation = newConversationId();
        var accepted = accept(command(conversation, "k1", "hi"));

        assertThatThrownBy(() -> run(accepted, recorder -> recorder.delta("half").thenReturn("half").flux()
                .concatWith(Flux.error(new IllegalStateException("model down")))))
                .hasMessageContaining("model down");

        var turnId = accepted.ticket().turnId();
        assertThat(support.status(turnId)).isEqualTo("FAILED");
        assertThat(support.assistantText(turnId)).isEqualTo("half");
        assertThat(support.activeTurn(conversation)).isNull();
    }

    @Test
    void missingCompletedIsFailure() {
        var conversation = newConversationId();
        var accepted = accept(command(conversation, "k1", "hi"));

        assertThatThrownBy(() -> run(accepted, recorder -> recorder.delta("cut").thenReturn("cut").flux()))
                .isInstanceOf(TurnLifecycle.IncompleteTurnException.class);

        assertThat(support.status(accepted.ticket().turnId())).isEqualTo("FAILED");
    }

    @Test
    void lostFenceMarksInterrupted() {
        var conversation = newConversationId();
        var accepted = accept(command(conversation, "k1", "hi"));
        var turnId = accepted.ticket().turnId();
        var started = new CountDownLatch(1);
        var errors = new ArrayList<Throwable>();
        var cancelledUpstream = new AtomicInteger();
        Function<TurnRecorder, Flux<String>> streaming = recorder -> Flux.concat(
                recorder.delta("a").thenReturn("a"),
                Mono.<String>fromRunnable(started::countDown).then(Mono.never()))
                .doOnCancel(cancelledUpstream::incrementAndGet);
        var subscription = lifecycle.execute(accepted.ticket(), streaming)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(x -> { }, errors::add);
        try {
            await().atMost(WAIT).until(() -> started.getCount() == 0);
            support.expireLease(turnId);
            assertThat(support.recovery.runOnce().block(WAIT)).isEqualTo(1);

            await().atMost(WAIT).until(() -> !errors.isEmpty());
            assertThat(errors.get(0)).isInstanceOf(TurnLifecycle.ExecutionRightLostException.class);
            assertThat(cancelledUpstream).hasValue(1);
            assertThat(support.status(turnId)).isEqualTo("INTERRUPTED");
        } finally {
            subscription.dispose();
        }
    }

    /** Runs {@code n} accepts at once on separate threads; returns each AcceptedTurn or Throwable. */
    protected List<Object> concurrently(int n, java.util.function.IntFunction<TurnCommand> commands)
            throws InterruptedException {
        var start = new CountDownLatch(1);
        var results = java.util.Collections.synchronizedList(new ArrayList<Object>());
        var threads = new ArrayList<Thread>();
        for (int i = 0; i < n; i++) {
            var command = commands.apply(i);
            var thread = new Thread(() -> {
                try {
                    start.await();
                    results.add(lifecycle.accept(command).block(WAIT));
                } catch (Throwable e) {
                    results.add(e);
                }
            });
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        for (var thread : threads) {
            thread.join(WAIT.toMillis() * 2);
        }
        assertThat(results).hasSize(n);
        return results;
    }
}
