package io.github.sukimiya.agileagent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataAccessException;

class KnowledgeScopeMigrationTest {

    @Test
    void upgradesEveryV2KnowledgeStateToGlobalAndIsIdempotent() {
        var dbName = "knowledge-scope-v2-" + System.nanoTime();
        var dataSource = dataSource(dbName);
        flyway(dataSource, "2").migrate();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO knowledge_documents (id, title, original_filename, temp_path, status, summary, uploader_user_id, uploaded_at) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                "pending", "pending doc", "pending.md", "/tmp/pending.md", "PENDING", "pending summary", "user-1");
        jdbc.update("INSERT INTO knowledge_documents (id, title, original_filename, temp_path, status, summary, uploader_user_id, uploaded_at) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                "approved", "approved doc", "approved.md", "/tmp/approved.md", "APPROVED", "approved summary", "user-2");
        jdbc.update("INSERT INTO knowledge_documents (id, title, original_filename, temp_path, status, summary, uploader_user_id, uploaded_at) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                "rejected", "rejected doc", "rejected.md", "/tmp/rejected.md", "REJECTED", "rejected summary", "user-3");

        flyway(dataSource).migrate();

        assertThat(jdbc.queryForList("SELECT id FROM knowledge_documents ORDER BY id", String.class))
                .containsExactly("approved", "pending", "rejected");
        assertThat(jdbc.queryForList("SELECT scope_type FROM knowledge_documents ORDER BY id", String.class))
                .containsExactly("GLOBAL", "GLOBAL", "GLOBAL");
        assertThat(jdbc.queryForList("SELECT tenant_id, tenant_name, org_id, org_name FROM knowledge_documents"))
                .hasSize(3).allSatisfy(row -> assertThat(row.values()).containsOnlyNulls());
        assertThat(jdbc.queryForList("SELECT approval_owner, approval_claimed_at, approval_version "
                + "FROM knowledge_documents"))
                .hasSize(3).allSatisfy(row -> {
                    assertThat(row.get("APPROVAL_OWNER")).isNull();
                    assertThat(row.get("APPROVAL_CLAIMED_AT")).isNull();
                    assertThat(row.get("APPROVAL_VERSION")).isEqualTo(0L);
                });
        assertThat(jdbc.queryForList("SELECT status FROM knowledge_documents ORDER BY id", String.class))
                .containsExactly("APPROVED", "PENDING", "REJECTED");
        assertThat(jdbc.queryForList("SELECT summary FROM knowledge_documents ORDER BY id", String.class))
                .containsExactly("approved summary", "pending summary", "rejected summary");
        assertThat(jdbc.queryForList("SELECT \"version\" FROM \"" + historyTable()
                + "\" WHERE \"success\" = TRUE AND \"type\" = 'SQL' ORDER BY \"installed_rank\"",
                String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");

        flyway(dataSource).migrate();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"" + historyTable()
                + "\" WHERE \"success\" = TRUE AND \"type\" = 'SQL' AND \"version\" = '7'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_documents", Integer.class)).isEqualTo(3);
    }

    @Test
    void scopeIndexUsesTenantThenOrganizationThenStatus() {
        var dataSource = dataSource("knowledge-scope-index-" + System.nanoTime());
        flyway(dataSource).migrate();
        var jdbc = new JdbcTemplate(dataSource);

        var indexedColumns = jdbc.queryForList("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.INDEX_COLUMNS "
                + "WHERE TABLE_NAME = 'KNOWLEDGE_DOCUMENTS' AND INDEX_NAME = 'IDX_KNOWLEDGE_DOCUMENTS_SCOPE' "
                + "ORDER BY ORDINAL_POSITION", String.class);

        assertThat(indexedColumns).containsExactly("TENANT_ID", "ORG_ID", "STATUS");
        assertThat(jdbc.queryForList("SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES "
                + "WHERE TABLE_NAME = 'KNOWLEDGE_DOCUMENTS'", String.class))
                .contains("IDX_KNOWLEDGE_DOCUMENTS_STATUS");
    }

    @Test
    void v6ChecksumRemainsStableAndV7AddsOwnerVersionLeaseAfterV6WasApplied() {
        var dataSource = dataSource("knowledge-approval-claim-" + System.nanoTime());
        var v6 = flyway(dataSource, "6");
        v6.migrate();
        var jdbc = new JdbcTemplate(dataSource);

        jdbc.update("INSERT INTO knowledge_documents "
                + "(id, title, original_filename, temp_path, status, summary, uploader_user_id, uploaded_at, "
                + "reviewed_by_user_id, reviewed_at, scope_type) "
                + "VALUES (?, ?, ?, ?, 'APPROVED', ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, 'GLOBAL')",
                "legacy-approved", "title", "legacy.md", "/tmp/legacy.md", "summary", "uploader", "reviewer");

        assertThat(jdbc.queryForObject("SELECT \"checksum\" FROM \"" + historyTable()
                + "\" WHERE \"version\" = '6'", Integer.class)).isEqualTo(-1183775273);
        assertThat(jdbc.queryForList("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = 'KNOWLEDGE_DOCUMENTS' AND COLUMN_NAME IN "
                + "('APPROVAL_OWNER', 'APPROVAL_CLAIMED_AT', 'APPROVAL_VERSION')", String.class)).isEmpty();

        flyway(dataSource).migrate();

        assertThat(jdbc.queryForList("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = 'KNOWLEDGE_DOCUMENTS' AND COLUMN_NAME IN "
                + "('APPROVAL_OWNER', 'APPROVAL_CLAIMED_AT', 'APPROVAL_VERSION') ORDER BY COLUMN_NAME",
                String.class)).containsExactly("APPROVAL_CLAIMED_AT", "APPROVAL_OWNER", "APPROVAL_VERSION");
        assertThat(jdbc.queryForMap("SELECT approval_owner, approval_version FROM knowledge_documents "
                + "WHERE id = 'legacy-approved'"))
                .containsEntry("APPROVAL_OWNER", "reviewer")
                .containsEntry("APPROVAL_VERSION", 0L);
        insertLegacyRow(jdbc, "one");
        assertThat(jdbc.queryForObject("SELECT approval_version FROM knowledge_documents WHERE id = 'one'",
                Long.class)).isEqualTo(0L);
    }

    private static void insertLegacyRow(JdbcTemplate jdbc, String id) {
        jdbc.update("INSERT INTO knowledge_documents "
                + "(id, title, original_filename, temp_path, status, summary, uploader_user_id, uploaded_at) "
                + "VALUES (?, ?, ?, ?, 'PENDING', '', 'uploader', CURRENT_TIMESTAMP)",
                id, "title", id + ".md", "/tmp/" + id + ".md");
    }

    private static DriverManagerDataSource dataSource(String dbName) {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        return dataSource;
    }

    private static Flyway flyway(DriverManagerDataSource dataSource) {
        return flyway(dataSource, null);
    }

    private static Flyway flyway(DriverManagerDataSource dataSource, String target) {
        var configuration = Flyway.configure().dataSource(dataSource)
                .table(historyTable())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private static String historyTable() {
        return "l7" + "_skill_flyway_schema_history";
    }
}
