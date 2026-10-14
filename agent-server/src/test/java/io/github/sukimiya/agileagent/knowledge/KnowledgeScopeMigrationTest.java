package io.github.sukimiya.agileagent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;

import io.github.sukimiya.agileagent.PostgresTestDatabase;

class KnowledgeScopeMigrationTest {

    @Test
    void upgradesEveryV2KnowledgeStateToGlobalAndIsIdempotent() {
        try (var db = PostgresTestDatabase.create("knowledge_scope_v2")) {
            db.flyway().table(historyTable()).baselineOnMigrate(false).target("2").load().migrate();
            var jdbc = db.jdbc();
            jdbc.update("INSERT INTO knowledge_documents (id, title, original_filename, temp_path, status, summary, uploader_user_id, uploaded_at) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                    "pending", "pending doc", "pending.md", "/tmp/pending.md", "PENDING", "pending summary", "user-1");
            jdbc.update("INSERT INTO knowledge_documents (id, title, original_filename, temp_path, status, summary, uploader_user_id, uploaded_at) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                    "approved", "approved doc", "approved.md", "/tmp/approved.md", "APPROVED", "approved summary", "user-2");
            jdbc.update("INSERT INTO knowledge_documents (id, title, original_filename, temp_path, status, summary, uploader_user_id, uploaded_at) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                    "rejected", "rejected doc", "rejected.md", "/tmp/rejected.md", "REJECTED", "rejected summary", "user-3");

            db.flyway().table(historyTable()).baselineOnMigrate(false).load().migrate();

            assertThat(jdbc.queryForList("SELECT id FROM knowledge_documents ORDER BY id", String.class))
                    .containsExactly("approved", "pending", "rejected");
            assertThat(jdbc.queryForList("SELECT scope_type FROM knowledge_documents ORDER BY id", String.class))
                    .containsExactly("GLOBAL", "GLOBAL", "GLOBAL");
            assertThat(jdbc.queryForList("SELECT tenant_id, tenant_name, org_id, org_name FROM knowledge_documents"))
                    .hasSize(3).allSatisfy(row -> assertThat(row.values()).containsOnlyNulls());
            assertThat(jdbc.queryForList("SELECT approval_owner, approval_claimed_at, approval_version "
                    + "FROM knowledge_documents"))
                    .hasSize(3).allSatisfy(row -> {
                        assertThat(row.get("approval_owner")).isNull();
                        assertThat(row.get("approval_claimed_at")).isNull();
                        assertThat(row.get("approval_version")).isEqualTo(0L);
                    });
            assertThat(jdbc.queryForList("SELECT status FROM knowledge_documents ORDER BY id", String.class))
                    .containsExactly("APPROVED", "PENDING", "REJECTED");
            assertThat(jdbc.queryForList("SELECT summary FROM knowledge_documents ORDER BY id", String.class))
                    .containsExactly("approved summary", "pending summary", "rejected summary");
            assertThat(jdbc.queryForList("SELECT \"version\" FROM \"" + historyTable()
                    + "\" WHERE \"success\" = TRUE AND \"type\" = 'SQL' ORDER BY \"installed_rank\"",
                    String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");

            db.flyway().table(historyTable()).baselineOnMigrate(false).load().migrate();

            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"" + historyTable()
                    + "\" WHERE \"success\" = TRUE AND \"type\" = 'SQL' AND \"version\" = '7'", Integer.class))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_documents", Integer.class)).isEqualTo(3);
        }
    }

    @Test
    void scopeIndexUsesTenantThenOrganizationThenStatus() {
        try (var db = PostgresTestDatabase.create("knowledge_scope_index")) {
            db.flyway().table(historyTable()).baselineOnMigrate(false).load().migrate();
            var jdbc = db.jdbc();

            var indexDef = jdbc.queryForObject(
                    "SELECT indexdef FROM pg_indexes WHERE tablename = 'knowledge_documents' "
                            + "AND indexname = 'idx_knowledge_documents_scope'",
                    String.class);

            assertThat(indexDef).contains("(tenant_id, org_id, status)");
            assertThat(jdbc.queryForList(
                    "SELECT indexname FROM pg_indexes WHERE tablename = 'knowledge_documents'",
                    String.class)).contains("idx_knowledge_documents_status");
        }
    }

    @Test
    void v6ChecksumRemainsStableAndV7AddsOwnerVersionLeaseAfterV6WasApplied() {
        try (var db = PostgresTestDatabase.create("knowledge_approval_claim")) {
            db.flyway().table(historyTable()).baselineOnMigrate(false).target("6").load().migrate();
            var jdbc = db.jdbc();

            jdbc.update("INSERT INTO knowledge_documents "
                    + "(id, title, original_filename, temp_path, status, summary, uploader_user_id, uploaded_at, "
                    + "reviewed_by_user_id, reviewed_at, scope_type) "
                    + "VALUES (?, ?, ?, ?, 'APPROVED', ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, 'GLOBAL')",
                    "legacy-approved", "title", "legacy.md", "/tmp/legacy.md", "summary", "uploader", "reviewer");

            assertThat(jdbc.queryForObject("SELECT \"checksum\" FROM \"" + historyTable()
                    + "\" WHERE \"version\" = '6'", Integer.class)).isEqualTo(-1183775273);
            assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = 'public' AND table_name = 'knowledge_documents' AND column_name IN "
                    + "('approval_owner', 'approval_claimed_at', 'approval_version')", String.class)).isEmpty();

            db.flyway().table(historyTable()).baselineOnMigrate(false).load().migrate();

            assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = 'public' AND table_name = 'knowledge_documents' AND column_name IN "
                    + "('approval_owner', 'approval_claimed_at', 'approval_version') ORDER BY column_name",
                    String.class)).containsExactly("approval_claimed_at", "approval_owner", "approval_version");
            assertThat(jdbc.queryForMap("SELECT approval_owner, approval_version FROM knowledge_documents "
                    + "WHERE id = 'legacy-approved'"))
                    .containsEntry("approval_owner", "reviewer")
                    .containsEntry("approval_version", 0L);
            insertLegacyRow(jdbc, "one");
            assertThat(jdbc.queryForObject("SELECT approval_version FROM knowledge_documents WHERE id = 'one'",
                    Long.class)).isEqualTo(0L);
        }
    }

    private static void insertLegacyRow(JdbcTemplate jdbc, String id) {
        jdbc.update("INSERT INTO knowledge_documents "
                + "(id, title, original_filename, temp_path, status, summary, uploader_user_id, uploaded_at) "
                + "VALUES (?, ?, ?, ?, 'PENDING', '', 'uploader', CURRENT_TIMESTAMP)",
                id, "title", id + ".md", "/tmp/" + id + ".md");
    }

    private static String historyTable() {
        return "l7" + "_skill_flyway_schema_history";
    }
}
