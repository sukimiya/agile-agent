package io.github.sukimiya.agileagent.memory.summary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.MemoryTestFixture;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobQueue;

/**
 * The idle trigger (D19): a conversation with no new message for {@code idle-timeout} (defaults 1m / 30m) and no
 * active turn, with completed turns after the record position, gets one SUMMARIZE job per idle period (dedupe key
 * conversation + last completed sequence). H2 here; {@link IdleConversationScannerMySqlIntegrationTest} on MySQL.
 */
class IdleConversationScannerTest {

    static final Duration WAIT = Duration.ofSeconds(10);
    static final MemoryScope ALICE = MemoryTestStack.ALICE;

    MemoryTestStack stack;
    String conversation;
    final AtomicBoolean conflict = new AtomicBoolean();
    IdleConversationScanner scanner;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        stack = MemoryTestStack.create(newFixture(), p -> p.getSummary().setEnabled(true), t -> t,
                MemoryTestStack.testRedis());
        conversation = MemoryTestStack.newConversationId();
        scanner = new IdleConversationScanner(stack.fixture().databaseClient(),
                new MemoryJobQueue(stack.fixture().databaseClient()), stack.props(), stack.metrics(), conflict::get);
    }

    @AfterEach
    void tearDown() {
        stack.close();
    }

    private long completedTurn(String conversationId, String question) {
        var ticket = stack.accept(conversationId, question).ticket();
        assertThat(stack.finalizer().finish(ticket, TurnStatus.COMPLETED, "回答").block(WAIT)).isTrue();
        return lastSequence(conversationId);
    }

    private long lastSequence(String conversationId) {
        return ((Number) stack.rows("SELECT MAX(sequence) AS s FROM chat_messages WHERE conversation_id = ?",
                conversationId).getFirst().get("S")).longValue();
    }

    /** Moves every turn of the conversation {@code minutes} into the past (database time). */
    private void age(String conversationId, int minutes) {
        stack.sql("UPDATE chat_turns SET created_at = TIMESTAMPADD(MINUTE, ?, created_at),"
                + " updated_at = TIMESTAMPADD(MINUTE, ?, updated_at),"
                + " finished_at = TIMESTAMPADD(MINUTE, ?, finished_at) WHERE conversation_id = ?",
                -minutes, -minutes, -minutes, conversationId);
    }

    private int scan() {
        return scanner.scan().block(WAIT);
    }

    private List<Map<String, Object>> jobs() {
        return stack.rows("SELECT dedupe_key, payload, status, conversation_id, tenant_id, subject FROM memory_jobs"
                + " WHERE job_type = 'SUMMARIZE' ORDER BY created_at");
    }

    @Test
    void idleScanEnqueuesOnlyAfterTimeoutAndOncePerIdlePeriod() {
        completedTurn(conversation, "问题一");
        long last = completedTurn(conversation, "问题二");

        // Not idle yet: the last message is fresh (default idle-timeout is 1 minute).
        assertThat(scan()).isZero();

        age(conversation, 1);
        assertThat(scan()).isEqualTo(1);
        assertThat(jobs()).singleElement().satisfies(job -> assertThat(job)
                .containsEntry("DEDUPE_KEY", "summarize:" + conversation + ":" + last)
                .containsEntry("PAYLOAD", "{\"throughSequence\":" + last + "}")
                .containsEntry("STATUS", "PENDING").containsEntry("CONVERSATION_ID", conversation)
                .containsEntry("TENANT_ID", ALICE.tenantId()).containsEntry("SUBJECT", ALICE.subject()));
        assertThat(stack.meters().find("memory.summary.backlog").gauge().value()).isEqualTo(2.0);

        // The same idle period is enqueued once.
        assertThat(scan()).isZero();
        assertThat(jobs()).hasSize(1);

        // A new turn starts a new period; once idle again it gets its own job.
        long next = completedTurn(conversation, "问题三");
        assertThat(scan()).isZero();
        age(conversation, 31);
        assertThat(scan()).isEqualTo(1);
        assertThat(jobs()).extracting(j -> j.get("DEDUPE_KEY")).containsExactly(
                "summarize:" + conversation + ":" + last, "summarize:" + conversation + ":" + next);
    }

    @Test
    void activeRecentlyFailedFullyRecordedOrDeletedConversationNotScanned() {
        // Active turn: not done.
        completedTurn(conversation, "问题一");
        stack.accept(conversation, "进行中的问题");
        age(conversation, 60);
        assertThat(scan()).isZero();

        // A recent failed turn is still a new message: not idle.
        var failing = MemoryTestStack.newConversationId();
        completedTurn(failing, "问题一");
        age(failing, 60);
        var failed = stack.accept(failing, "失败的问题").ticket();
        assertThat(stack.finalizer().finish(failed, TurnStatus.FAILED, null).block(WAIT)).isTrue();
        assertThat(scan()).isZero();

        // Everything already recorded.
        var recorded = MemoryTestStack.newConversationId();
        long last = completedTurn(recorded, "问题一");
        age(recorded, 60);
        stack.sql("INSERT INTO memory_index_progress (conversation_id, tenant_id, subject, indexed_through_sequence,"
                + " summarized_through_sequence, updated_at) VALUES (?, ?, ?, 0, ?, CURRENT_TIMESTAMP(6))",
                recorded, ALICE.tenantId(), ALICE.subject(), last);
        assertThat(scan()).isZero();

        // Deleted.
        var deleted = MemoryTestStack.newConversationId();
        completedTurn(deleted, "问题一");
        age(deleted, 60);
        stack.sql("UPDATE sessions SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?", deleted);
        assertThat(scan()).isZero();
        assertThat(jobs()).isEmpty();
    }

    @Test
    void disabledOrConflictedScannerDoesNothing() {
        completedTurn(conversation, "问题一");
        age(conversation, 60);

        conflict.set(true);
        assertThat(scan()).isZero();
        conflict.set(false);

        var off = new MemoryProperties();
        var disabled = new IdleConversationScanner(stack.fixture().databaseClient(),
                new MemoryJobQueue(stack.fixture().databaseClient()), off, stack.metrics(), () -> false);
        assertThat(off.getSummary().isEnabled()).isFalse();
        assertThat(disabled.isAutoStartup()).isFalse();
        assertThat(disabled.scan().block(WAIT)).isZero();
        assertThat(jobs()).isEmpty();

        assertThat(off.getSummary().getIdleTimeout()).isEqualTo(Duration.ofMinutes(1));
        assertThat(off.getSummary().getIdleDeadline()).isEqualTo(Duration.ofMinutes(30));
        assertThat(off.getSummary().getIdleScanInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(off.getSummary().effectiveIdleCutoff()).isEqualTo(Duration.ofMinutes(1));
        assertThat(off.getSummary().getMaxInvalidAttempts()).isEqualTo(3);
        assertThat(scanner.isAutoStartup()).isEqualTo(stack.props().isRecoveryEnabled());
        assertThat(scan()).isEqualTo(1);
    }

    @Test
    void idleTimeoutIsConfigurable() {
        stack.props().getSummary().setIdleTimeout(Duration.ofMinutes(5));
        completedTurn(conversation, "问题一");
        age(conversation, 6);

        assertThat(scan()).isEqualTo(1);
    }

    @Test
    void idleDeadlineCapsALongerTimeout() {
        // Misconfigured long timeout still cannot delay past the 30m deadline (effective = min).
        stack.props().getSummary().setIdleTimeout(Duration.ofHours(2));
        stack.props().getSummary().setIdleDeadline(Duration.ofMinutes(30));
        assertThat(stack.props().getSummary().effectiveIdleCutoff()).isEqualTo(Duration.ofMinutes(30));

        completedTurn(conversation, "问题一");
        age(conversation, 29);
        assertThat(scan()).isZero();
        age(conversation, 2);
        assertThat(scan()).isEqualTo(1);
    }
}
