package io.github.sukimiya.agileagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.repository.SessionRepository;
import io.github.sukimiya.agileagent.repository.SessionRepository.SessionRow;
import io.github.sukimiya.agileagent.service.SessionService;

/**
 * Schema and scoped-session contract on H2 ({@code MODE=MySQL}). {@link MemoryMySqlIntegrationTest} runs the
 * same assertions against a real MySQL so the two databases cannot drift apart.
 */
class MemorySchemaTest {

    static final MemoryScope ALICE_T1 = new MemoryScope("tenant-1", "alice");
    static final MemoryScope ALICE_T2 = new MemoryScope("tenant-2", "alice");
    static final MemoryScope ALICE_UPPER_T1 = new MemoryScope("tenant-1", "Alice");
    static final MemoryScope ALICE_UPPER_TENANT = new MemoryScope("TENANT-1", "alice");

    protected MemoryTestFixture fixture;
    protected SessionRepository repository;
    protected SessionService sessions;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUpDatabase() {
        fixture = newFixture();
        repository = new SessionRepository(fixture.databaseClient());
        sessions = new SessionService(repository);
    }

    @Test
    void sameSubjectAcrossTenantsCannotRead() {
        sessions.createOwned("c-t1", ALICE_T1, "租户一").block();
        sessions.createOwned("c-t2", ALICE_T2, "租户二").block();

        assertThat(sessions.findOwned("c-t1", ALICE_T1).blockOptional()).isPresent();
        assertThat(sessions.findOwned("c-t1", ALICE_T2).blockOptional()).isEmpty();
        assertThat(sessions.findOwned("c-t2", ALICE_T1).blockOptional()).isEmpty();
        assertThat(sessions.listForUser(ALICE_T1).map(SessionRow::id).collectList().block()).containsExactly("c-t1");
        assertThat(sessions.listForUser(ALICE_T2).map(SessionRow::id).collectList().block()).containsExactly("c-t2");
    }

    @Test
    void caseDistinctScopesRemainDistinct() {
        sessions.createOwned("c-lower", ALICE_T1, "lower").block();
        sessions.createOwned("c-upper", ALICE_UPPER_T1, "upper").block();
        sessions.createOwned("c-upper-tenant", ALICE_UPPER_TENANT, "upper tenant").block();

        assertThat(sessions.findOwned("c-lower", ALICE_UPPER_T1).blockOptional()).isEmpty();
        assertThat(sessions.findOwned("c-upper", ALICE_T1).blockOptional()).isEmpty();
        assertThat(sessions.findOwned("c-lower", ALICE_UPPER_TENANT).blockOptional()).isEmpty();
        assertThat(sessions.listForUser(ALICE_T1).map(SessionRow::id).collectList().block()).containsExactly("c-lower");
        assertThat(sessions.listForUser(ALICE_UPPER_T1).map(SessionRow::id).collectList().block()).containsExactly("c-upper");
        assertThat(sessions.listForUser(ALICE_UPPER_TENANT).map(SessionRow::id).collectList().block())
                .containsExactly("c-upper-tenant");
    }

    @Test
    void createdSessionCarriesTenantRevisionAndNoDeletion() {
        var row = sessions.createOwned("c1", ALICE_T1, "  第一次对话  ").block();

        assertThat(row.tenantId()).isEqualTo("tenant-1");
        assertThat(row.userId()).isEqualTo("alice");
        assertThat(row.title()).isEqualTo("第一次对话");
        assertThat(row.revision()).isZero();
        assertThat(row.deletedAt()).isNull();
    }

    @Test
    void createOwnedIsIdempotentForTheOwnerAndHidesForeignSessions() {
        sessions.createOwned("c1", ALICE_T1, "mine").block();

        assertThat(sessions.createOwned("c1", ALICE_T1, "again").block().title()).isEqualTo("mine");
        assertThatThrownBy(() -> sessions.createOwned("c1", ALICE_T2, "steal").block())
                .isInstanceOfSatisfying(MemoryException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
        assertThatThrownBy(() -> sessions.createOwned("c1", ALICE_UPPER_T1, "steal").block())
                .isInstanceOfSatisfying(MemoryException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    @Test
    void legacySessionsWithoutTenantAreNotReadByScopedQueries() {
        repository.insert("legacy", "alice", "旧会话").block();

        assertThat(sessions.findOwned("legacy", ALICE_T1).blockOptional()).isEmpty();
        assertThat(sessions.listForUser(ALICE_T1).collectList().block()).isEmpty();
        assertThatThrownBy(() -> sessions.createOwned("legacy", ALICE_T1, "接管").block())
                .isInstanceOfSatisfying(MemoryException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    @Test
    void deletedSessionsAreNotReadByScopedQueries() {
        sessions.createOwned("c1", ALICE_T1, "will be deleted").block();
        fixture.jdbc().update("UPDATE sessions SET deleted_at = ?, is_valid = 0 WHERE id = ?", LocalDateTime.now(), "c1");

        assertThat(sessions.findOwned("c1", ALICE_T1).blockOptional()).isEmpty();
        assertThat(sessions.listForUser(ALICE_T1).collectList().block()).isEmpty();
        assertThatThrownBy(() -> sessions.createOwned("c1", ALICE_T1, "revive").block())
                .isInstanceOfSatisfying(MemoryException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    @Test
    void longMessageBodyIsNotTruncated() {
        var body = "{\"role\":\"user\",\"content\":\"" + "记忆".repeat(40_000) + "\"}";
        assertThat(body.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(64 * 1024 * 3);
        new MemoryProperties().checkMessageSize(body);

        insertTurn("turn-1", "c1", "req-1");
        insertMessage("m1", "turn-1", "c1", 1, body);

        var stored = fixture.jdbc().queryForObject("SELECT payload FROM chat_messages WHERE id = ?", String.class, "m1");
        assertThat(stored).isEqualTo(body);
    }

    @Test
    void messageOverTheApplicationLimitIsRejectedNotTruncated() {
        var props = new MemoryProperties();
        props.setMaxMessageBytes(1024);

        props.checkMessageSize("a".repeat(1024));
        assertThatThrownBy(() -> props.checkMessageSize("记".repeat(342)))
                .isInstanceOfSatisfying(MemoryException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.MESSAGE_TOO_LARGE));
        assertThatThrownBy(() -> props.setMaxMessageBytes(MemoryProperties.MEDIUMTEXT_MAX_BYTES + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void idempotencyKeyIsUniquePerTenantAndSubjectAcrossConversations() {
        insertTurn("turn-1", "c1", "req-1");
        insertTurn("turn-2", "c2", "tenant-1", "bob", "req-1");
        insertTurn("turn-3", "c3", "tenant-2", "alice", "req-1");
        // Requests without a key (and released keys) never collide.
        insertTurn("turn-4", "c1", null);
        insertTurn("turn-5", "c2", null);

        assertThatThrownBy(() -> insertTurn("turn-6", "c2", "req-1")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void idempotencyKeyComparisonIsCaseSensitive() {
        insertTurn("turn-1", "c1", "Req-1");
        insertTurn("turn-2", "c1", "req-1");

        assertThat(fixture.jdbc().queryForObject(
                "SELECT COUNT(*) FROM chat_turns WHERE conversation_id = 'c1' AND idempotency_key = 'req-1'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void atMostOneActiveTurnPerConversation() {
        insertTurn("turn-1", "c1", "req-1");
        fixture.jdbc().update("UPDATE chat_turns SET active_conversation_id = conversation_id WHERE id = 'turn-1'");
        insertTurn("turn-2", "c1", "req-2");

        assertThatThrownBy(() -> fixture.jdbc().update(
                "UPDATE chat_turns SET active_conversation_id = conversation_id WHERE id = 'turn-2'"))
                .isInstanceOf(DataIntegrityViolationException.class);

        fixture.jdbc().update("UPDATE chat_turns SET active_conversation_id = NULL, status = 'COMPLETED' WHERE id = 'turn-1'");
        fixture.jdbc().update("UPDATE chat_turns SET active_conversation_id = conversation_id WHERE id = 'turn-2'");
    }

    @Test
    void sequenceIsUniqueWithinAConversation() {
        insertTurn("turn-1", "c1", "req-1");
        insertMessage("m1", "turn-1", "c1", 1, "{}");
        insertMessage("m2", "turn-1", "c2", 1, "{}");

        assertThatThrownBy(() -> insertMessage("m3", "turn-1", "c1", 1, "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void toolCallIdIsUniqueOnlyWithinATurn() {
        insertTurn("turn-1", "c1", "req-1");
        insertTurn("turn-2", "c1", "req-2");
        insertToolEvent("e1", "turn-1", "call-1");
        insertToolEvent("e2", "turn-2", "call-1");

        assertThatThrownBy(() -> insertToolEvent("e3", "turn-1", "call-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void jobAndInstanceTablesExist() {
        var now = LocalDateTime.now();
        fixture.jdbc().update("INSERT INTO memory_jobs (id, tenant_id, subject, conversation_id, job_type, status, "
                + "available_at, created_at, updated_at) VALUES ('j1', 'tenant-1', 'alice', 'c1', 'CONVERSATION_CLEANUP', "
                + "'PENDING', ?, ?, ?)", now, now, now);
        fixture.jdbc().update("INSERT INTO service_instances (instance_id, namespace, started_at, heartbeat_at) "
                + "VALUES ('i1', 'default', ?, ?)", now, now);

        assertThat(fixture.jdbc().queryForObject("SELECT attempts FROM memory_jobs WHERE id = 'j1'", Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM service_instances", Integer.class)).isEqualTo(1);
    }

    protected void insertTurn(String id, String conversationId, String idempotencyKey) {
        insertTurn(id, conversationId, "tenant-1", "alice", idempotencyKey);
    }

    protected void insertTurn(String id, String conversationId, String tenantId, String subject, String idempotencyKey) {
        var now = LocalDateTime.now();
        fixture.jdbc().update("INSERT INTO chat_turns (id, tenant_id, subject, conversation_id, idempotency_key, request_hash, "
                + "status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 'ACCEPTED', ?, ?)",
                id, tenantId, subject, conversationId, idempotencyKey, "0".repeat(64), now, now);
    }

    protected void insertMessage(String id, String turnId, String conversationId, long sequence, String payload) {
        var now = LocalDateTime.now();
        fixture.jdbc().update("INSERT INTO chat_messages (id, tenant_id, subject, conversation_id, turn_id, sequence, "
                + "role, source_type, payload, created_at, updated_at) VALUES (?, 'tenant-1', 'alice', ?, ?, ?, 'user', "
                + "'USER_INPUT', ?, ?, ?)", id, conversationId, turnId, sequence, payload, now, now);
    }

    protected void insertToolEvent(String id, String turnId, String callId) {
        var now = LocalDateTime.now();
        fixture.jdbc().update("INSERT INTO chat_tool_events (id, tenant_id, subject, conversation_id, turn_id, call_id, "
                + "capability, tool_name, status, source_type, started_at, updated_at) VALUES (?, 'tenant-1', 'alice', "
                + "'c1', ?, ?, 'cap', 'tool', 'STARTED', 'TOOL_RESULT', ?, ?)", id, turnId, callId, now, now);
    }
}
