package io.github.sukimiya.agileagent.memory;

import static io.github.sukimiya.agileagent.memory.TurnTestSupport.command;
import static io.github.sukimiya.agileagent.memory.TurnTestSupport.newConversationId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import reactor.core.publisher.Mono;

class TurnFinalizerTest {

    static final Duration WAIT = Duration.ofSeconds(10);

    TurnTestSupport support;

    @AfterEach
    void close() {
        if (support != null) {
            support.close();
        }
    }

    @Test
    void finishWritesTerminalStatePartialTextAndRevision() {
        support = TurnTestSupport.create(MemoryTestFixture.h2());
        var conversation = newConversationId();
        var ticket = support.lifecycle.accept(command(conversation, "k1", "hi")).block(WAIT).ticket();
        var revision = support.revision(conversation);

        assertThat(support.finalizer.finish(ticket, TurnStatus.CANCELLED, "部分").block(WAIT)).isTrue();

        assertThat(support.status(ticket.turnId())).isEqualTo("CANCELLED");
        assertThat(support.assistantText(ticket.turnId())).isEqualTo("部分");
        assertThat(support.activeTurn(conversation)).isNull();
        assertThat(support.revision(conversation)).isEqualTo(revision + 1);
        assertThat(support.finalizer.succeeded()).isEqualTo(1);
    }

    @Test
    void expiredFenceCannotWrite() {
        support = TurnTestSupport.create(MemoryTestFixture.h2());
        var conversation = newConversationId();
        var ticket = support.lifecycle.accept(command(conversation, "k1", "hi")).block(WAIT).ticket();
        var staleFence = new TurnTicket(ticket.scope(), ticket.conversationId(), ticket.turnId(), ticket.fence() - 1);

        assertThat(support.turns.renew(staleFence).block(WAIT)).isFalse();
        assertThat(support.finalizer.finish(staleFence, TurnStatus.COMPLETED, "stale").block(WAIT)).isFalse();
        assertThat(support.turns.renew(ticket).block(WAIT)).isTrue();

        support.expireLease(ticket.turnId());

        assertThat(support.turns.renew(ticket).block(WAIT)).isFalse();
        assertThat(support.finalizer.finish(ticket, TurnStatus.COMPLETED, "late").block(WAIT)).isFalse();
        assertThat(support.status(ticket.turnId())).isEqualTo("ACCEPTED");
        assertThat(support.assistantText(ticket.turnId())).isNull();
        assertThat(support.finalizer.failed()).isEqualTo(2);
    }

    @Test
    void finalizerTimeoutFallsBackToRecoveryScan() {
        support = TurnTestSupport.create(MemoryTestFixture.h2(),
                p -> p.setFinalizerTimeout(Duration.ofMillis(200)),
                turns -> {
                    var hanging = spy(turns);
                    doReturn(Mono.never()).when(hanging)
                            .markTerminal(any(TurnTicket.class), any(TurnStatus.class), any(TokenUsage.class), any());
                    return hanging;
                });
        var conversation = newConversationId();
        var ticket = support.lifecycle.accept(command(conversation, "k1", "hi")).block(WAIT).ticket();

        assertThat(support.finalizer.finish(ticket, TurnStatus.CANCELLED, "partial").block(WAIT)).isFalse();
        assertThat(support.finalizer.failed()).isEqualTo(1);
        assertThat(support.status(ticket.turnId())).isEqualTo("ACCEPTED");

        support.expireLease(ticket.turnId());
        assertThat(support.recovery.runOnce().block(WAIT)).isEqualTo(1);

        assertThat(support.status(ticket.turnId())).isEqualTo("INTERRUPTED");
        assertThat(support.activeTurn(conversation)).isNull();
    }

    @Test
    void enqueuedFinishIsWrittenByTheManagedSubscription() {
        support = TurnTestSupport.create(MemoryTestFixture.h2());
        var conversation = newConversationId();
        var ticket = support.lifecycle.accept(command(conversation, "k1", "hi")).block(WAIT).ticket();

        assertThat(support.finalizer.enqueue(ticket, TurnStatus.CANCELLED, "queued")).isTrue();

        await().atMost(WAIT).until(() -> "CANCELLED".equals(support.status(ticket.turnId())));
        assertThat(support.assistantText(ticket.turnId())).isEqualTo("queued");
    }
}
