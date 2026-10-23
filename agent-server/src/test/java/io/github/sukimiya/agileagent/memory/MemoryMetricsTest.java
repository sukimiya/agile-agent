package io.github.sukimiya.agileagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnCommand;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Basic memory metrics are recorded where the events happen, and only for events that really happened. */
class MemoryMetricsTest {

    private static final Duration WAIT = Duration.ofSeconds(10);

    private MemoryTestStack stack = MemoryTestStack.h2();

    @AfterEach
    void tearDown() {
        stack.close();
    }

    @Test
    void acceptAndTerminalOutcomesAreCountedByStatus() {
        var conversation = MemoryTestStack.newConversationId();
        var completed = stack.accept(MemoryTestStack.ALICE, conversation, "k1", "你好");
        run(completed, recorder -> recorder.completed("答", new TokenUsage(true, 1, 1, 2)).thenMany(Flux.empty()));
        stack.accept(MemoryTestStack.ALICE, conversation, "k1", "你好");
        var failed = stack.accept(conversation, "第二个");
        assertThatThrownBy(() -> run(failed, recorder -> Flux.error(new IllegalStateException("boom"))))
                .isInstanceOf(IllegalStateException.class);
        var cancelled = stack.accept(conversation, "第三个");
        var subscription = stack.lifecycle().execute(cancelled.ticket(), recorder -> Flux.<String>never()).subscribe();
        await().atMost(WAIT).until(() -> "RUNNING".equals(stack.status(cancelled.ticket().turnId())));
        subscription.dispose();
        await().atMost(WAIT).until(() -> "CANCELLED".equals(stack.status(cancelled.ticket().turnId())));
        var expired = stack.accept(conversation, "第四个");
        stack.expireLease(expired.ticket().turnId());
        assertThat(stack.recoverExpired()).isEqualTo(1);

        var meters = stack.meters();
        assertThat(count(meters, "memory.turn.accepted", "replay", "false")).isEqualTo(4);
        assertThat(count(meters, "memory.turn.accepted", "replay", "true")).isEqualTo(1);
        assertThat(count(meters, "memory.turn.terminal", "status", "COMPLETED")).isEqualTo(1);
        assertThat(count(meters, "memory.turn.terminal", "status", "FAILED")).isEqualTo(1);
        await().atMost(WAIT).until(() -> count(meters, "memory.turn.terminal", "status", "CANCELLED") == 1);
        assertThat(count(meters, "memory.turn.terminal", "status", "INTERRUPTED")).isEqualTo(1);
    }

    @Test
    void expiredTurnInterruptedOnAcceptIsCountedOnce() {
        var conversation = MemoryTestStack.newConversationId();
        var stale = stack.accept(conversation, "第一个");
        stack.expireLease(stale.ticket().turnId());

        stack.accept(conversation, "第二个");

        assertThat(stack.status(stale.ticket().turnId())).isEqualTo("INTERRUPTED");
        assertThat(count(stack.meters(), "memory.turn.terminal", "status", "INTERRUPTED")).isEqualTo(1);
    }

    @Test
    void refusedAcceptIsNotCounted() {
        var conversation = MemoryTestStack.newConversationId();
        stack.accept(conversation, "第一个");
        assertThatThrownBy(() -> stack.accept(conversation, "第二个")).isInstanceOf(MemoryException.class);

        assertThat(count(stack.meters(), "memory.turn.accepted", "replay", "false")).isEqualTo(1);
    }

    @Test
    void checkpointLatencyIsTimed() {
        stack.close();
        stack = MemoryTestStack.h2(p -> p.setCheckpointBytes(1));
        var accepted = stack.accept(MemoryTestStack.newConversationId(), "写长一点");

        run(accepted, recorder -> recorder.delta("第一段").then(recorder.delta("第二段"))
                .then(recorder.completed("第一段第二段", TokenUsage.notObserved())).thenMany(Flux.empty()));

        var timer = stack.meters().find("memory.checkpoint.latency").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(2);
    }

    @Test
    void cacheHitsAndDatabaseFallbacksAreCounted() {
        var conversation = MemoryTestStack.newConversationId();
        var accepted = stack.accept(conversation, "你好");
        run(accepted, recorder -> recorder.completed("答", TokenUsage.notObserved()).thenMany(Flux.empty()));

        stack.history().page(MemoryTestStack.ALICE, conversation, null, 10).block(WAIT);
        stack.history().page(MemoryTestStack.ALICE, conversation, null, 10).block(WAIT);

        assertThat(count(stack.meters(), "memory.cache.requests", "result", "miss")).isEqualTo(1);
        assertThat(count(stack.meters(), "memory.cache.requests", "result", "hit")).isEqualTo(1);
    }

    @Test
    void finalizerWriteFailuresAreCountedButALostTurnIsNot() {
        var conversation = MemoryTestStack.newConversationId();
        var first = stack.accept(conversation, "第一个");
        stack.revokeFence(first.ticket().turnId());
        assertThat(stack.finalizer().finish(first.ticket(), TurnStatus.COMPLETED, "答").block(WAIT)).isFalse();
        assertThat(count(stack.meters(), "memory.finalizer.failures", null, null)).isZero();

        var second = stack.accept(MemoryTestStack.newConversationId(), "第二个");
        stack.breakTurnStorage();
        assertThat(stack.finalizer().finish(second.ticket(), TurnStatus.COMPLETED, "答").block(WAIT)).isFalse();
        assertThat(count(stack.meters(), "memory.finalizer.failures", null, null)).isEqualTo(1);
        assertThat(count(stack.meters(), "memory.turn.terminal", "status", "COMPLETED")).isZero();
    }

    @Test
    void tokenEstimateCountsCjkPerCharacterAndOtherTextPerFourCharacters() {
        assertThat(MemoryMetrics.estimateTokens("")).isZero();
        assertThat(MemoryMetrics.estimateTokens("你好")).isEqualTo(2);
        assertThat(MemoryMetrics.estimateTokens("abcdefgh")).isEqualTo(2);
        assertThat(MemoryMetrics.estimateTokens("你好abcde")).isEqualTo(4);
    }

    private void run(MemoryModel.AcceptedTurn accepted,
            java.util.function.Function<TurnRecorder, Flux<String>> operation) {
        stack.lifecycle().execute(accepted.ticket(), operation).collectList().block(WAIT);
    }

    private static double count(MeterRegistry meters, String name, String tag, String value) {
        var search = meters.find(name);
        if (tag != null) {
            search = search.tag(tag, value);
        }
        var counter = search.counter();
        return counter == null ? 0 : counter.count();
    }
}
