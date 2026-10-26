package io.github.sukimiya.agileagent.memory;

import static io.github.sukimiya.agileagent.memory.TurnTestSupport.ALICE;
import static io.github.sukimiya.agileagent.memory.TurnTestSupport.command;
import static io.github.sukimiya.agileagent.memory.TurnTestSupport.newConversationId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.jobs.MemoryJobQueue;
import reactor.core.publisher.Mono;

/**
 * M2a schema (Flyway V10 plus the vendor file V10_1) and the COMPLETED-turn index job on H2 ({@code MODE=MySQL}).
 * {@link M2aSchemaMySqlIntegrationTest} runs the same assertions on MySQL 8.4, where the vendor file also adds
 * the ngram FULLTEXT indexes.
 */
class M2aSchemaTest {

    static final Duration WAIT = Duration.ofSeconds(10);
    static final List<String> M2A_TABLES = List.of("memory_summaries", "memory_chunks", "memory_index_progress",
            "user_memory_settings");

    protected MemoryTestFixture fixture;
    protected TurnTestSupport support;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    /** FULLTEXT index names on the M2a tables; H2 has none (recall falls back to LIKE there). */
    protected Set<String> fulltextIndexes() {
        return Set.copyOf(fixture.jdbc().queryForList(
                "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE INDEX_NAME LIKE 'FT_%'", String.class));
    }

    protected Set<String> expectedFulltextIndexes() {
        return Set.of();
    }

    @BeforeEach
    void setUp() {
        fixture = newFixture();
        support = TurnTestSupport.create(fixture);
    }

    @AfterEach
    void tearDown() {
        support.close();
    }

    private JdbcTemplate jdbc() {
        return fixture.jdbc();
    }

    private int count(String sql, Object... args) {
        return jdbc().queryForObject(sql, Integer.class, args);
    }

    @Test
    void v10AppliesOnH2AndMySql() {
        var applied = java.util.Arrays.stream(Flyway.configure().dataSource(fixture.dataSource()).load().info().applied())
                .filter(m -> m.getState().isApplied() && !m.getState().isFailed())
                .map(m -> m.getVersion().getVersion()).toList();
        assertThat(applied).contains("10", "10.1");

        jdbc().update("INSERT INTO memory_summaries (id, tenant_id, subject, conversation_id, turn_id, from_sequence,"
                + " to_sequence, from_message_id, to_message_id, record_json, search_text, memory_type, has_decision,"
                + " source_type, generator_model, schema_version, version, created_at) VALUES ('s1', 't', 'alice', 'c1',"
                + " 'turn-1', 1, 2, 'm1', 'm2', '{}', '摘要', 'FACT', FALSE, 'MODEL_SUMMARY', 'record-model',"
                + " 'memory-record-v1', 1, CURRENT_TIMESTAMP(6))");
        jdbc().update("INSERT INTO memory_chunks (id, tenant_id, subject, conversation_id, turn_id, from_sequence,"
                + " to_sequence, content, source_types, created_at) VALUES ('k1', 't', 'alice', 'c1', 'turn-1', 1, 2,"
                + " '片段', 'USER_INPUT,ASSISTANT', CURRENT_TIMESTAMP(6))");
        jdbc().update("INSERT INTO memory_index_progress (conversation_id, tenant_id, subject, indexed_through_sequence,"
                + " summarized_through_sequence, updated_at) VALUES ('c1', 't', 'alice', 2, 0, CURRENT_TIMESTAMP(6))");
        jdbc().update("INSERT INTO user_memory_settings (tenant_id, subject, updated_at)"
                + " VALUES ('t', 'alice', CURRENT_TIMESTAMP(6))");
        for (var table : M2A_TABLES) {
            assertThat(count("SELECT COUNT(*) FROM " + table)).as(table).isEqualTo(1);
        }
        var settings = jdbc().queryForMap("SELECT cross_conversation_enabled, revision FROM user_memory_settings");
        assertThat(settings.values()).containsExactly(false, 0L);

        assertThatThrownBy(() -> jdbc().update("INSERT INTO memory_summaries (id, tenant_id, subject,"
                + " conversation_id, turn_id, from_sequence, to_sequence, from_message_id, to_message_id, record_json,"
                + " search_text, memory_type, has_decision, source_type, generator_model, schema_version, version,"
                + " created_at) VALUES ('s2', 't', 'alice', 'c1', 'turn-1', 1, 2, 'm1', 'm2', '{}', 'again', 'EVENT',"
                + " TRUE, 'MODEL_SUMMARY', 'm', 'memory-record-v1', 1, CURRENT_TIMESTAMP(6))"))
                .as("one record per turn and schema version").isInstanceOf(DataIntegrityViolationException.class);
        var progress = jdbc().queryForMap("SELECT summary_invalid_turn_id, summary_invalid_attempts"
                + " FROM memory_index_progress");
        assertThat(progress.values()).containsExactly(null, 0);

        jdbc().update("INSERT INTO llm_provider_preset (id, name, base_url, model, created_by_subject, created_at,"
                + " updated_at) VALUES ('p1', 'n', 'https://x', 'm', 'admin', CURRENT_TIMESTAMP(6),"
                + " CURRENT_TIMESTAMP(6))");
        var preset = jdbc().queryForMap("SELECT context_window_tokens, max_output_tokens FROM llm_provider_preset");
        assertThat(preset.values()).containsOnlyNulls();
    }

    @Test
    void vendorFulltextOnlyOnMySql() {
        assertThat(fulltextIndexes()).isEqualTo(expectedFulltextIndexes());
    }

    @Test
    void identityColumnsAreCaseSensitive() {
        jdbc().update("INSERT INTO user_memory_settings (tenant_id, subject, updated_at)"
                + " VALUES ('t', 'alice', CURRENT_TIMESTAMP(6))");
        jdbc().update("INSERT INTO user_memory_settings (tenant_id, subject, updated_at)"
                + " VALUES ('t', 'Alice', CURRENT_TIMESTAMP(6))");
        jdbc().update("INSERT INTO user_memory_settings (tenant_id, subject, updated_at)"
                + " VALUES ('T', 'alice', CURRENT_TIMESTAMP(6))");
        jdbc().update("INSERT INTO memory_index_progress (conversation_id, tenant_id, subject, indexed_through_sequence,"
                + " summarized_through_sequence, updated_at) VALUES ('conv', 't', 'alice', 0, 0, CURRENT_TIMESTAMP(6))");
        jdbc().update("INSERT INTO memory_index_progress (conversation_id, tenant_id, subject, indexed_through_sequence,"
                + " summarized_through_sequence, updated_at) VALUES ('CONV', 't', 'alice', 0, 0, CURRENT_TIMESTAMP(6))");
        insertJob("j1", "INDEX_CHUNKS", "key");
        insertJob("j2", "INDEX_CHUNKS", "KEY");

        assertThat(count("SELECT COUNT(*) FROM user_memory_settings WHERE subject = 'alice'")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM memory_index_progress WHERE conversation_id = 'conv'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM memory_jobs WHERE dedupe_key = 'key'")).isEqualTo(1);
        assertThatThrownBy(() -> insertJob("j3", "INDEX_CHUNKS", "key"))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertJob("j4", "SUMMARIZE", "key");
        insertJob("j5", "INDEX_CHUNKS", null);
        insertJob("j6", "INDEX_CHUNKS", null);
    }

    private void insertJob(String id, String type, String dedupeKey) {
        jdbc().update("INSERT INTO memory_jobs (id, tenant_id, subject, conversation_id, job_type, status, attempts,"
                + " dedupe_key, available_at, created_at, updated_at) VALUES (?, 't', 'alice', 'c', ?, 'PENDING', 0, ?,"
                + " CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", id, type, dedupeKey);
    }

    private List<java.util.Map<String, Object>> indexJobs(String conversationId) {
        return jdbc().queryForList("SELECT tenant_id, subject, dedupe_key, payload, status FROM memory_jobs"
                + " WHERE conversation_id = ? AND job_type = 'INDEX_CHUNKS'", conversationId);
    }

    @Test
    void completedTurnEnqueuesIndexJobInSameTransaction() {
        var conversation = newConversationId();
        var ticket = support.lifecycle.accept(command(conversation, "k1", "你好")).block(WAIT).ticket();
        assertThat(support.finalizer.finish(ticket, TurnStatus.COMPLETED, "答复").block(WAIT)).isTrue();

        var jobs = indexJobs(conversation);
        assertThat(jobs).hasSize(1);
        var job = jobs.getFirst();
        assertThat(job.get("TENANT_ID")).isEqualTo(ALICE.tenantId());
        assertThat(job.get("SUBJECT")).isEqualTo(ALICE.subject());
        assertThat(job.get("DEDUPE_KEY")).isEqualTo(ticket.turnId());
        assertThat((String) job.get("PAYLOAD")).contains(ticket.turnId());
        assertThat(job.get("STATUS")).isEqualTo("PENDING");

        // A repeated finish lost the turn already: no second job.
        assertThat(support.finalizer.finish(ticket, TurnStatus.COMPLETED, "again").block(WAIT)).isFalse();
        assertThat(indexJobs(conversation)).hasSize(1);
    }

    @Test
    void nonCompletedTurnsEnqueueNoIndexJob() {
        for (var status : List.of(TurnStatus.CANCELLED, TurnStatus.FAILED, TurnStatus.INTERRUPTED)) {
            var conversation = newConversationId();
            var ticket = support.lifecycle.accept(command(conversation, "k-" + status, "hi")).block(WAIT).ticket();
            assertThat(support.finalizer.finish(ticket, status, "partial").block(WAIT)).isTrue();
            assertThat(indexJobs(conversation)).as(status.name()).isEmpty();
        }
    }

    @Test
    void rolledBackTerminalWriteLeavesNoIndexJob() {
        var conversations = spy(support.conversations);
        doReturn(Mono.error(new IllegalStateException("revision write failed")))
                .when(conversations).bumpRevision(anyString());
        var finalizer = new TurnFinalizer(fixture.connectionFactory(), support.turns, conversations, support.props,
                support.metrics, new MemoryJobQueue(fixture.databaseClient()));
        try {
            var conversation = newConversationId();
            var ticket = support.lifecycle.accept(command(conversation, "k1", "hi")).block(WAIT).ticket();

            assertThat(finalizer.finish(ticket, TurnStatus.COMPLETED, "答复").block(WAIT)).isFalse();

            assertThat(support.status(ticket.turnId())).isEqualTo("ACCEPTED");
            assertThat(indexJobs(conversation)).isEmpty();
        } finally {
            finalizer.destroy();
        }
    }

    /** Pending M1 cleanup jobs keep working after the job type is renamed by V10. */
    @Test
    void v10RenamesPendingM1CleanupJobs() {
        org.junit.jupiter.api.Assumptions.assumeTrue(expectedFulltextIndexes().isEmpty(),
                "H2 only: needs its own database stopped at V9");
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:m2a-upgrade-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        var locations = new String[] {"classpath:db/migration", "classpath:db/vendor/h2"};
        Flyway.configure().dataSource(dataSource).locations(locations).target("9").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO memory_jobs (id, tenant_id, subject, conversation_id, job_type, status, attempts,"
                + " available_at, created_at, updated_at) VALUES ('old', 't', 'alice', 'c', 'DELETE_CONVERSATION',"
                + " 'PENDING', 2, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))");

        Flyway.configure().dataSource(dataSource).locations(locations).load().migrate();

        assertThat(jdbc.queryForMap("SELECT job_type, attempts, dedupe_key FROM memory_jobs WHERE id = 'old'").values())
                .containsExactly("CONVERSATION_CLEANUP", 2, null);
    }
}
