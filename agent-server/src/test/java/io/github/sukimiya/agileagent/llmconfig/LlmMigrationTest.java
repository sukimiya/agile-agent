package io.github.sukimiya.agileagent.llmconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LlmMigrationTest {

    @Test
    void upgradesExplicitV2DatabaseWithoutLosingExistingRows() {
        var dbName = "llm-migration-v2-" + System.nanoTime();
        var dataSource = dataSource(dbName);
        var v2 = flyway(dataSource, "2");
        v2.migrate();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO sessions (id, user_id, title, created_at, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "session-v2", "user-v2", "legacy session");
        jdbc.update("INSERT INTO knowledge_documents (id, title, original_filename, temp_path, status, uploader_user_id, uploaded_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                "doc-v2", "legacy doc", "legacy.md", "/tmp/legacy.md", "PENDING", "user-v2");

        flyway(dataSource).migrate();

        assertThat(jdbc.queryForObject("SELECT title FROM sessions WHERE id = 'session-v2'", String.class))
                .isEqualTo("legacy session");
        assertThat(jdbc.queryForObject("SELECT title FROM knowledge_documents WHERE id = 'doc-v2'", String.class))
                .isEqualTo("legacy doc");
        assertThat(jdbc.queryForList(
                "SELECT \"version\" FROM \"" + historyTable() + "\" WHERE \"success\" = TRUE AND \"type\" = 'SQL' ORDER BY \"installed_rank\"",
                String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(jdbc.queryForObject("SELECT scope_type FROM knowledge_documents WHERE id = 'doc-v2'", String.class))
                .isEqualTo("GLOBAL");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_documents WHERE id = 'doc-v2' AND tenant_id IS NULL AND org_id IS NULL", Integer.class))
                .isEqualTo(1);
        jdbc.update("INSERT INTO tenant_llm_config (id, tenant_id, preset_id, api_key_encrypted, created_at, updated_at) VALUES ('cfg-a', 'tenant-a', 'missing-preset', 'secret', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tenant_llm_config (id, tenant_id, preset_id, api_key_encrypted, created_at, updated_at) VALUES ('cfg-b', 'tenant-a', 'missing-preset', 'secret', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void emptyDatabaseMigratesThroughLatestVersion() {
        var dbName = "llm-migration-empty-" + System.nanoTime();
        var dataSource = dataSource(dbName);

        flyway(dataSource).migrate();

        var jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"" + historyTable() + "\" WHERE \"success\" = TRUE AND \"type\" = 'SQL'", Integer.class))
                .isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME IN ('CHAT_HINTS', 'LLM_PROVIDER_PRESET', 'TENANT_LLM_CONFIG')", Integer.class))
                .isEqualTo(3);
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
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static String historyTable() {
        return "l7" + "_skill_flyway_schema_history";
    }

}
