package io.github.sukimiya.agileagent.llmconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.PostgresTestDatabase;

class LlmMigrationTest {

    @Test
    void upgradesExplicitV2DatabaseWithoutLosingExistingRows() {
        try (var db = PostgresTestDatabase.create("llm_migration_v2")) {
            db.flyway().table(historyTable()).baselineOnMigrate(false).target("2").load().migrate();
            var jdbc = db.jdbc();
            jdbc.update("INSERT INTO sessions (id, user_id, title, created_at, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    "session-v2", "user-v2", "legacy session");
            jdbc.update("INSERT INTO knowledge_documents (id, title, original_filename, temp_path, status, uploader_user_id, uploaded_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                    "doc-v2", "legacy doc", "legacy.md", "/tmp/legacy.md", "PENDING", "user-v2");

            db.flyway().table(historyTable()).baselineOnMigrate(false).load().migrate();

            assertThat(jdbc.queryForObject("SELECT title FROM sessions WHERE id = 'session-v2'", String.class))
                    .isEqualTo("legacy session");
            assertThat(jdbc.queryForObject("SELECT title FROM knowledge_documents WHERE id = 'doc-v2'", String.class))
                    .isEqualTo("legacy doc");
            assertThat(jdbc.queryForList(
                    "SELECT \"version\" FROM \"" + historyTable() + "\" WHERE \"success\" = TRUE AND \"type\" = 'SQL' ORDER BY \"installed_rank\"",
                    String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
            assertThat(jdbc.queryForObject("SELECT scope_type FROM knowledge_documents WHERE id = 'doc-v2'", String.class))
                    .isEqualTo("GLOBAL");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_documents WHERE id = 'doc-v2' AND tenant_id IS NULL AND org_id IS NULL", Integer.class))
                    .isEqualTo(1);
            jdbc.update("INSERT INTO tenant_llm_config (id, tenant_id, preset_id, api_key_encrypted, created_at, updated_at) VALUES ('cfg-a', 'tenant-a', 'missing-preset', 'secret', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            assertThatThrownBy(() -> jdbc.update("INSERT INTO tenant_llm_config (id, tenant_id, preset_id, api_key_encrypted, created_at, updated_at) VALUES ('cfg-b', 'tenant-a', 'missing-preset', 'secret', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"))
                    .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        }
    }

    @Test
    void emptyDatabaseMigratesThroughV10() {
        try (var db = PostgresTestDatabase.create("llm_migration_empty")) {
            db.flyway().table(historyTable()).baselineOnMigrate(false).load().migrate();

            var jdbc = db.jdbc();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"" + historyTable() + "\" WHERE \"success\" = TRUE AND \"type\" = 'SQL'", Integer.class))
                    .isEqualTo(10);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = 'public' AND table_name IN ('chat_hints', 'llm_provider_preset', 'tenant_llm_config')",
                    Integer.class)).isEqualTo(3);
        }
    }

    private static String historyTable() {
        return "l7" + "_skill_flyway_schema_history";
    }

}
