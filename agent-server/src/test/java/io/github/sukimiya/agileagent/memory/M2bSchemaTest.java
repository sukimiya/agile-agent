package io.github.sukimiya.agileagent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.sukimiya.agileagent.memory.items.ItemStatus;
import io.github.sukimiya.agileagent.memory.items.MemoryItem;
import io.github.sukimiya.agileagent.memory.items.MemoryItemRepository;
import io.github.sukimiya.agileagent.memory.jobs.JobType;

/**
 * M2b schema (Flyway V11) and owner-scoped item storage on H2 ({@code MODE=MySQL}).
 * {@link M2bSchemaMySqlIntegrationTest} runs the same assertions on MySQL 8.4.
 */
class M2bSchemaTest {

    static final Duration WAIT = Duration.ofSeconds(10);
    static final List<String> M2B_TABLES = List.of("memory_items", "memory_item_sources", "user_memory_profiles",
            "memory_entities", "memory_relations", "memory_recall_events", "memory_token_usage",
            "memory_tenant_quotas");

    protected MemoryTestFixture fixture;

    protected MemoryTestFixture newFixture() {
        return MemoryTestFixture.h2();
    }

    @BeforeEach
    void setUp() {
        fixture = newFixture();
    }

    @AfterEach
    void tearDown() {
        // In-memory H2 / MySQL IT fixture: no shared pool to close (see M2aSchemaTest).
    }

    private JdbcTemplate jdbc() {
        return fixture.jdbc();
    }

    @Test
    void v11AppliesOnH2AndMySql() {
        var applied = java.util.Arrays.stream(Flyway.configure().dataSource(fixture.dataSource()).load().info().applied())
                .filter(m -> m.getState().isApplied() && !m.getState().isFailed())
                .map(m -> m.getVersion().getVersion()).toList();
        assertThat(applied).contains("10", "10.1", "11");

        jdbc().update("INSERT INTO memory_items (id, tenant_id, subject, conversation_id, item_type, content, status,"
                + " assertion, importance, confidence, rules_version, judge_model, reason, revision, created_at,"
                + " updated_at) VALUES ('i1', 't', 'alice', NULL, 'preference', '喜欢绿茶', 'ACTIVE', 'USER_STATED',"
                + " 0.5, 0.9, 1, 'judge-model', 'said so', 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))");
        jdbc().update("INSERT INTO memory_item_sources (item_id, message_id, conversation_id, source_type,"
                + " evidence_excerpt) VALUES ('i1', 'm1', 'c1', 'USER_INPUT', '我喜欢绿茶')");
        jdbc().update("INSERT INTO user_memory_profiles (tenant_id, subject, content, version, built_through,"
                + " updated_at) VALUES ('t', 'alice', '概要', 1, 0, CURRENT_TIMESTAMP(6))");
        jdbc().update("INSERT INTO memory_entities (id, tenant_id, subject, name, entity_type, normalized_key)"
                + " VALUES ('e1', 't', 'alice', '绿茶', 'THING', '绿茶')");
        jdbc().update("INSERT INTO memory_relations (id, tenant_id, subject, from_entity, relation, to_entity,"
                + " item_id, status) VALUES ('r1', 't', 'alice', 'e1', 'LIKES', 'e1', 'i1', 'ACTIVE')");
        jdbc().update("INSERT INTO memory_recall_events (id, tenant_id, subject, conversation_id, turn_id, kind,"
                + " ref_id, reason, score, `rank`, source_types, created_at) VALUES ('re1', 't', 'alice', 'c1', 'turn-1',"
                + " 'ITEM', 'i1', 'ENTITY_EXACT', 1.0, 1, 'USER_INPUT', CURRENT_TIMESTAMP(6))");
        jdbc().update("INSERT INTO memory_token_usage (tenant_id, usage_date, tokens) VALUES ('t', CURRENT_DATE, 100)");
        jdbc().update("INSERT INTO memory_tenant_quotas (tenant_id, daily_token_limit, updated_at)"
                + " VALUES ('t', 200000, CURRENT_TIMESTAMP(6))");

        for (var table : M2B_TABLES) {
            assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).as(table).isEqualTo(1);
        }

        assertThatThrownBy(() -> jdbc().update("INSERT INTO memory_item_sources (item_id, message_id,"
                + " conversation_id, source_type, evidence_excerpt) VALUES ('i1', 'm1', 'c1', 'USER_INPUT', 'dup')"))
                .as("one source message per item").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc().update("INSERT INTO memory_entities (id, tenant_id, subject, name,"
                + " entity_type, normalized_key) VALUES ('e2', 't', 'alice', '绿茶2', 'THING', '绿茶')"))
                .as("normalized_key unique per scope").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc().update("INSERT INTO memory_token_usage (tenant_id, usage_date, tokens)"
                + " VALUES ('t', CURRENT_DATE, 1)"))
                .as("one usage row per tenant and day").isInstanceOf(DataIntegrityViolationException.class);

        assertThat(Set.of(JobType.values())).extracting(Enum::name)
                .contains("JUDGE_MEMORY", "REBUILD_PROFILE", "REPLAY_RULES");

        var props = new MemoryProperties();
        assertThat(props.getSummary().getIdleTimeout()).isEqualTo(Duration.ofMinutes(1));
        assertThat(props.getSummary().getIdleDeadline()).isEqualTo(Duration.ofMinutes(30));
        assertThat(props.getSummary().getIdleScanInterval()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void itemsScopedByTenantAndSubjectCaseSensitive() {
        var repo = new MemoryItemRepository(fixture.databaseClient());
        var alice = new MemoryScope("tenant-1", "Alice");
        var aliceLower = new MemoryScope("tenant-1", "alice");
        var otherTenant = new MemoryScope("tenant-2", "Alice");

        var item = MemoryItem.newItem(alice, null, "preference", "喜欢绿茶", ItemStatus.ACTIVE,
                MemoryItem.Assertion.USER_STATED, 0.5, 0.9, 1, "model", "said so");
        assertThat(repo.save(item).block(WAIT)).isTrue();

        assertThat(repo.findById(alice, item.id()).block(WAIT)).isPresent()
                .get().extracting(MemoryItem::content).isEqualTo("喜欢绿茶");
        assertThat(repo.findById(aliceLower, item.id()).block(WAIT)).isEmpty();
        assertThat(repo.findById(otherTenant, item.id()).block(WAIT)).isEmpty();

        var statuses = jdbc().queryForList("SELECT status FROM memory_items", String.class);
        assertThat(statuses).containsExactly("ACTIVE");
        assertThat(ItemStatus.valueOf("SUPERSEDED")).isEqualTo(ItemStatus.SUPERSEDED);
        assertThat(ItemStatus.valueOf("INACTIVE")).isEqualTo(ItemStatus.INACTIVE);
    }
}
