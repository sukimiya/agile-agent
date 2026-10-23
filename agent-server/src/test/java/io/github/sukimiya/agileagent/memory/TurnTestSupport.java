package io.github.sukimiya.agileagent.memory;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.memory.MemoryModel.TurnCommand;
import io.github.sukimiya.agileagent.util.NonBlockingIds;

/** Wires the Task 2 lifecycle components against one test database and offers row-level probes. */
final class TurnTestSupport implements AutoCloseable {

    static final MemoryScope ALICE = new MemoryScope("tenant-1", "alice");
    static final MemoryScope ALICE_OTHER_TENANT = new MemoryScope("tenant-2", "alice");

    final MemoryTestFixture fixture;
    final MemoryProperties props;
    final ObjectMapper mapper = new ObjectMapper();
    final TurnRepository turns;
    final ConversationRepository conversations;
    final TurnFinalizer finalizer;
    final TurnLifecycle lifecycle;
    final ExpiredTurnRecovery recovery;
    final InstanceGuard guard;
    final MeterRegistry meters;
    final MemoryMetrics metrics;

    private TurnTestSupport(MemoryTestFixture fixture, Consumer<MemoryProperties> tune,
            UnaryOperator<TurnRepository> decorateTurns, MeterRegistry meters) {
        this.fixture = fixture;
        this.props = new MemoryProperties();
        props.setFinalizerTimeout(Duration.ofSeconds(5));
        tune.accept(props);
        this.meters = meters;
        this.metrics = new MemoryMetrics(meters);
        var cf = fixture.connectionFactory();
        this.turns = decorateTurns.apply(new TurnRepository(fixture.databaseClient(), props));
        this.conversations = new ConversationRepository(fixture.databaseClient(), mapper);
        this.finalizer = new TurnFinalizer(cf, turns, conversations, props, metrics,
                new io.github.sukimiya.agileagent.memory.jobs.MemoryJobQueue(fixture.databaseClient()));
        // Never started here: conflict() stays false unless a test drives heartbeat() itself.
        this.guard = new InstanceGuard(fixture.databaseClient(), props, meters);
        this.lifecycle = new TurnLifecycle(cf, turns, conversations, finalizer, props, guard, metrics);
        this.recovery = new ExpiredTurnRecovery(cf, turns, conversations, props, metrics);
    }

    static TurnTestSupport create(MemoryTestFixture fixture) {
        return new TurnTestSupport(fixture, p -> { }, t -> t, new SimpleMeterRegistry());
    }

    static TurnTestSupport create(MemoryTestFixture fixture, Consumer<MemoryProperties> tune,
            UnaryOperator<TurnRepository> decorateTurns) {
        return new TurnTestSupport(fixture, tune, decorateTurns, new SimpleMeterRegistry());
    }

    static TurnTestSupport create(MemoryTestFixture fixture, Consumer<MemoryProperties> tune,
            UnaryOperator<TurnRepository> decorateTurns, MeterRegistry meters) {
        return new TurnTestSupport(fixture, tune, decorateTurns, meters);
    }

    static String newConversationId() {
        return NonBlockingIds.uuid();
    }

    static TurnCommand command(String conversationId, String key, String text) {
        return new TurnCommand(ALICE, conversationId, key, text);
    }

    String status(String turnId) {
        return fixture.jdbc().queryForObject("SELECT status FROM chat_turns WHERE id = ?", String.class, turnId);
    }

    long fence(String turnId) {
        return fixture.jdbc().queryForObject("SELECT fence FROM chat_turns WHERE id = ?", Long.class, turnId);
    }

    int turnCount(String conversationId) {
        return fixture.jdbc().queryForObject("SELECT COUNT(*) FROM chat_turns WHERE conversation_id = ?",
                Integer.class, conversationId);
    }

    int messageCount(String conversationId, String role) {
        return fixture.jdbc().queryForObject("SELECT COUNT(*) FROM chat_messages WHERE conversation_id = ? AND role = ?",
                Integer.class, conversationId, role);
    }

    String activeTurn(String conversationId) {
        var ids = fixture.jdbc().queryForList("SELECT id FROM chat_turns WHERE active_conversation_id = ?",
                String.class, conversationId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    String assistantText(String turnId) {
        return conversations.assistantText(turnId).block(Duration.ofSeconds(5));
    }

    long revision(String conversationId) {
        return fixture.jdbc().queryForObject("SELECT revision FROM sessions WHERE id = ?", Long.class, conversationId);
    }

    /** Moves the lease into the past, as if the executor stopped renewing long ago. */
    void expireLease(String turnId) {
        fixture.jdbc().update("UPDATE chat_turns SET lease_expires_at = TIMESTAMPADD(SECOND, -600, CURRENT_TIMESTAMP(6))"
                + " WHERE id = ?", turnId);
    }

    @Override
    public void close() {
        guard.stop();
        finalizer.destroy();
    }
}
