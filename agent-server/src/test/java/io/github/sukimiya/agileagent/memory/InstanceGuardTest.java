package io.github.sukimiya.agileagent.memory;

import static io.github.sukimiya.agileagent.memory.TurnTestSupport.ALICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnCommand;
import io.github.sukimiya.agileagent.util.NonBlockingIds;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Flux;

/**
 * Single-instance protection: database-time heartbeats in {@code service_instances}; another live heartbeat in the
 * same namespace is a conflict that refuses new turns. H2 here; {@code InstanceGuardMySqlIntegrationTest} on MySQL.
 */
class InstanceGuardTest {

    static final Duration WAIT = Duration.ofSeconds(10);

    MemoryTestFixture fixture;
    TurnTestSupport support;
    SimpleMeterRegistry meters;
    InstanceGuard guard;
    final List<InstanceGuard> peers = new ArrayList<>();

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        fixture = newFixture();
        meters = new SimpleMeterRegistry();
        support = TurnTestSupport.create(fixture, p -> p.setNamespace("ns-" + NonBlockingIds.shortUuid()), t -> t,
                meters);
        guard = support.guard;
    }

    @AfterEach
    void tearDown() {
        peers.forEach(InstanceGuard::stop);
        support.close();
    }

    private InstanceGuard peer(String namespace) {
        var props = new MemoryProperties();
        props.setNamespace(namespace);
        var peer = new InstanceGuard(fixture.databaseClient(), props, new SimpleMeterRegistry());
        peers.add(peer);
        return peer;
    }

    private void beat(InstanceGuard g) {
        g.heartbeat().block(WAIT);
    }

    private int rows(String namespace) {
        return fixture.jdbc().queryForObject("SELECT COUNT(*) FROM service_instances WHERE namespace = ?",
                Integer.class, namespace);
    }

    private double conflictGauge() {
        return meters.get("memory.instance.conflict").gauge().value();
    }

    @Test
    void heartbeatRegistersAndRenewsThisInstance() {
        beat(guard);
        var first = fixture.jdbc().queryForMap("SELECT instance_id, heartbeat_at FROM service_instances"
                + " WHERE namespace = ?", support.props.getNamespace());
        beat(guard);

        assertThat(rows(support.props.getNamespace())).isEqualTo(1);
        assertThat(first.get("INSTANCE_ID")).isEqualTo(guard.instanceId());
        assertThat(guard.conflict()).isFalse();
        assertThat(conflictGauge()).isZero();
    }

    @Test
    void secondInstanceInSameNamespaceIsDetected() {
        var logs = capture();
        try {
            var peer = peer(support.props.getNamespace());
            beat(guard);
            beat(peer);
            beat(guard);

            assertThat(guard.conflict()).isTrue();
            assertThat(peer.conflict()).as("both sides see the conflict").isTrue();
            assertThat(conflictGauge()).isEqualTo(1.0);
            assertThat(new InstanceGuardHealthIndicator(guard).health().getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
            assertThat(logs.list).anySatisfy(e -> {
                assertThat(e.getLevel()).isEqualTo(Level.ERROR);
                assertThat(e.getFormattedMessage()).contains("INSTANCE_CONFLICT");
            });
        } finally {
            release(logs);
        }
    }

    @Test
    void conflictRefusesNewTurns() {
        var peer = peer(support.props.getNamespace());
        beat(peer);
        beat(guard);
        var conversation = NonBlockingIds.uuid();

        assertThatThrownBy(() -> support.lifecycle.accept(new TurnCommand(ALICE, conversation, "k1", "hi")).block(WAIT))
                .isInstanceOfSatisfying(MemoryException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INSTANCE_CONFLICT));
        assertThat(support.turnCount(conversation)).isZero();
        assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM sessions WHERE id = ?", Integer.class,
                conversation)).as("nothing is written for a refused turn").isZero();
    }

    @Test
    void runningTurnsFinishNormallyDuringConflict() {
        var conversation = NonBlockingIds.uuid();
        var ticket = support.lifecycle.accept(new TurnCommand(ALICE, conversation, "k1", "hi")).block(WAIT).ticket();
        var peer = peer(support.props.getNamespace());
        beat(peer);
        beat(guard);
        assertThat(guard.conflict()).isTrue();

        support.lifecycle.execute(ticket,
                r -> r.completed("answer", TokenUsage.notObserved()).thenMany(Flux.<String>empty())).blockLast(WAIT);

        assertThat(support.status(ticket.turnId())).isEqualTo("COMPLETED");
        assertThat(support.assistantText(ticket.turnId())).isEqualTo("answer");
    }

    @Test
    void staleHeartbeatIsNotConflict() {
        var peer = peer(support.props.getNamespace());
        beat(peer);
        // The peer stopped heartbeating long ago (e.g. crashed without deregistering).
        fixture.jdbc().update("UPDATE service_instances SET heartbeat_at = TIMESTAMPADD(SECOND, -31,"
                + " CURRENT_TIMESTAMP(6)) WHERE instance_id = ?", peer.instanceId());

        beat(guard);

        assertThat(guard.conflict()).isFalse();
        assertThat(new InstanceGuardHealthIndicator(guard).health().getStatus()).isEqualTo(Status.UP);
        assertThat(support.lifecycle.accept(new TurnCommand(ALICE, NonBlockingIds.uuid(), "k1", "hi")).block(WAIT))
                .isNotNull();
    }

    @Test
    void otherNamespaceIsNotConflict() {
        var other = peer("other-" + NonBlockingIds.shortUuid());
        beat(other);
        beat(guard);

        assertThat(guard.conflict()).isFalse();
        assertThat(other.conflict()).isFalse();
    }

    @Test
    void conflictClearsWhenPeerStops() {
        var logs = capture();
        try {
            var peer = peer(support.props.getNamespace());
            beat(peer);
            beat(guard);
            assertThat(guard.conflict()).isTrue();

            peer.stop();
            beat(guard);

            assertThat(guard.conflict()).isFalse();
            assertThat(conflictGauge()).isZero();
            assertThat(rows(support.props.getNamespace())).as("the stopped peer deregistered").isEqualTo(1);
            assertThat(support.lifecycle.accept(new TurnCommand(ALICE, NonBlockingIds.uuid(), "k1", "hi")).block(WAIT))
                    .isNotNull();
            assertThat(logs.list).anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("conflict cleared"));
        } finally {
            release(logs);
        }
    }

    @Test
    void failedHeartbeatKeepsTheLastKnownState() {
        var peer = peer(support.props.getNamespace());
        beat(peer);
        beat(guard);
        fixture.jdbc().execute("ALTER TABLE service_instances RENAME TO service_instances_unavailable");
        try {
            assertThatThrownBy(() -> beat(guard)).isNotNull();
            assertThat(guard.conflict()).as("an unreadable registry does not clear a known conflict").isTrue();
        } finally {
            fixture.jdbc().execute("ALTER TABLE service_instances_unavailable RENAME TO service_instances");
        }
    }

    private static ListAppender<ILoggingEvent> capture() {
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(InstanceGuard.class)).addAppender(appender);
        return appender;
    }

    private static void release(ListAppender<ILoggingEvent> appender) {
        ((Logger) LoggerFactory.getLogger(InstanceGuard.class)).detachAppender(appender);
    }
}
