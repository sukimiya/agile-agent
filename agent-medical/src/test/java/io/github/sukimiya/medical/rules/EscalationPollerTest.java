package io.github.sukimiya.medical.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

/**
 * The poller is a reactive interval loop (spec ruling 5): start() is idempotent, a failing
 * tick never terminates the loop (per-tick onErrorResume is load-bearing — without it one
 * errored tick would cancel the whole interval), and stop() freezes further invocations.
 * Tests use bounded polling instead of fixed sleeps to avoid timing flakiness.
 */
class EscalationPollerTest {

    @Test
    void pollsTheServiceOnTheConfiguredInterval() throws Exception {
        var escalation = mock(EscalationService.class);
        when(escalation.tick()).thenReturn(Mono.empty());
        var poller = new EscalationPoller(escalation, Duration.ofMillis(20));

        poller.start();
        awaitTicks(escalation, 2);
        poller.stop();

        int invocationsAfterStop = mockingDetails(escalation).getInvocations().size();
        Thread.sleep(150);
        assertThat(mockingDetails(escalation).getInvocations().size())
                .as("stop() disposes the interval — no further ticks")
                .isEqualTo(invocationsAfterStop);
    }

    @Test
    void aFailingTickDoesNotKillThePoller() throws Exception {
        var escalation = mock(EscalationService.class);
        when(escalation.tick()).thenReturn(Mono.error(new IllegalStateException("boom")));
        var poller = new EscalationPoller(escalation, Duration.ofMillis(20));

        poller.start();
        try {
            awaitTicks(escalation, 2);
        } finally {
            poller.stop();
        }
        verify(escalation, atLeast(2)).tick();
    }

    @Test
    void startIsIdempotentAndStopIsSafeWhenNeverStarted() throws Exception {
        var escalation = mock(EscalationService.class);
        when(escalation.tick()).thenReturn(Mono.empty());
        var poller = new EscalationPoller(escalation, Duration.ofMillis(20));

        poller.stop(); // never started — must not throw
        poller.start();
        poller.start(); // idempotent — still exactly one interval subscription
        awaitTicks(escalation, 2);
        poller.stop();
        poller.stop(); // double stop — must not throw

        Thread.sleep(100);
        assertThat(mockingDetails(escalation).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("tick"))
                .count())
                .as("ticks stop after stop(); double start() created only one loop")
                .isBetween(2L, 50L);
        verify(escalation, never()).escalateOverdue();
    }

    /** Bounded retry loop: waits until tick() was invoked at least {@code count} times. */
    private static void awaitTicks(EscalationService escalation, int count) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                verify(escalation, atLeast(count)).tick();
                return;
            } catch (AssertionError retry) {
                Thread.sleep(20);
            }
        }
        verify(escalation, atLeast(count)).tick(); // final attempt — fails the test with a message
    }
}
