package io.github.sukimiya.agileagent.memory;

import static io.github.sukimiya.agileagent.memory.TurnTestSupport.command;
import static io.github.sukimiya.agileagent.memory.TurnTestSupport.newConversationId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import reactor.core.publisher.Flux;

class ExpiredTurnRecoveryTest {

    static final Duration WAIT = Duration.ofSeconds(10);

    TurnTestSupport support;

    @BeforeEach
    void setUp() {
        support = TurnTestSupport.create(MemoryTestFixture.h2());
    }

    @AfterEach
    void close() {
        support.close();
    }

    @Test
    void acceptedWithoutSubscriptionIsRecovered() {
        var conversation = newConversationId();
        var accepted = support.lifecycle.accept(command(conversation, "k1", "hi")).block(WAIT);
        var turnId = accepted.ticket().turnId();
        var fence = support.fence(turnId);
        var revision = support.revision(conversation);

        support.expireLease(turnId);
        assertThat(support.recovery.runOnce().block(WAIT)).isEqualTo(1);

        assertThat(support.status(turnId)).isEqualTo("INTERRUPTED");
        assertThat(support.fence(turnId)).isEqualTo(fence + 1);
        assertThat(support.activeTurn(conversation)).isNull();
        assertThat(support.revision(conversation)).isEqualTo(revision + 1);

        var calls = new AtomicInteger();
        assertThatThrownBy(() -> support.lifecycle.execute(accepted.ticket(), r -> {
            calls.incrementAndGet();
            return Flux.just("late");
        }).blockLast(WAIT)).isInstanceOfSatisfying(MemoryException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.TURN_TERMINATED));
        assertThat(calls).hasValue(0);
        assertThat(support.lifecycle.accept(command(conversation, "k2", "again")).block(WAIT).replay()).isFalse();
    }

    @Test
    void liveLeasesAndTerminalTurnsAreLeftAlone() {
        var live = newConversationId();
        var liveTurn = support.lifecycle.accept(command(live, "k1", "hi")).block(WAIT).ticket().turnId();
        var done = newConversationId();
        var doneTicket = support.lifecycle.accept(command(done, "k1", "hi")).block(WAIT).ticket();
        support.lifecycle.execute(doneTicket, r -> r.completed("ok", TokenUsage.notObserved()).thenMany(Flux.<String>empty()))
                .blockLast(WAIT);
        support.expireLease(doneTicket.turnId());

        assertThat(support.recovery.runOnce().block(WAIT)).isZero();

        assertThat(support.status(liveTurn)).isEqualTo("ACCEPTED");
        assertThat(support.status(doneTicket.turnId())).isEqualTo("COMPLETED");
    }

    @Test
    void acceptTakesOverAnExpiredActiveTurnInsteadOfReporting409() {
        var conversation = newConversationId();
        var stale = support.lifecycle.accept(command(conversation, "k1", "hi")).block(WAIT).ticket().turnId();
        support.expireLease(stale);

        var next = support.lifecycle.accept(command(conversation, "k2", "again")).block(WAIT);

        assertThat(support.status(stale)).isEqualTo("INTERRUPTED");
        assertThat(support.activeTurn(conversation)).isEqualTo(next.ticket().turnId());
    }
}
