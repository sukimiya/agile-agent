package io.github.sukimiya.agileagent.medical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import io.github.sukimiya.agileagent.PostgresTestDatabase;

class MedicalMigrationTest {

    @Test
    void v8CreatesHospitalCapabilityPackagesWithUniqueCodeAndScopeIndex() {
        try (var db = PostgresTestDatabase.create("medical_migration_v8")) {
            db.migrate();
            var jdbc = db.jdbc();

            assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = 'public' AND table_name = 'hospital_capability_packages' "
                    + "ORDER BY ordinal_position", String.class))
                    .containsExactly("id", "hospital_code", "hospital_name", "routing_description",
                            "status", "schema_version", "content", "source", "valid_from", "valid_until",
                            "scope_type", "tenant_id", "tenant_name", "org_id", "org_name",
                            "created_by", "created_at",
                            "approval_version", "reviewed_by", "reviewed_at", "rejection_reason",
                            "last_gate_report");
            assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes "
                    + "WHERE tablename = 'hospital_capability_packages'", String.class))
                    .contains("hospital_capability_packages_hospital_code_key",
                            "idx_hospital_packages_scope", "idx_hospital_packages_status");
            assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes "
                    + "WHERE tablename = 'hospital_capability_packages' "
                    + "AND indexname = 'idx_hospital_packages_scope'", String.class))
                    .contains("(tenant_id, org_id, status)");
            assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes "
                    + "WHERE tablename = 'hospital_capability_packages' "
                    + "AND indexname = 'idx_hospital_packages_status'", String.class))
                    .contains("(status, valid_until)");
        }
    }

    @Test
    void v9CreatesMedicalHumanTasksWithStatusAndConversationIndexes() {
        try (var db = PostgresTestDatabase.create("medical_migration_v9")) {
            db.migrate();
            var jdbc = db.jdbc();

            assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = 'public' AND table_name = 'medical_human_tasks' "
                    + "ORDER BY ordinal_position", String.class))
                    .containsExactly("id", "conversation_id", "hospital_code", "tool_name",
                            "arguments_json", "status", "created_by", "created_at", "submission_key");
            assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes "
                    + "WHERE tablename = 'medical_human_tasks'", String.class))
                    .contains("idx_medical_human_tasks_status", "idx_medical_human_tasks_conversation",
                            "ux_medical_human_tasks_submission_key");
        }
    }

    @Test
    void scopeTypeDefaultsToGlobalOnInsertWithoutScopeColumns() {
        try (var db = PostgresTestDatabase.create("medical_migration_default")) {
            db.migrate();
            var jdbc = db.jdbc();
            jdbc.update("INSERT INTO hospital_capability_packages "
                    + "(id, hospital_code, hospital_name, routing_description, status, schema_version, "
                    + "content, source, valid_from, valid_until, created_by) "
                    + "VALUES ('p1', 'demo', 'Demo Hospital', 'demo routing', 'PENDING', 1, '{}', "
                    + "'fixture', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'seeder')");

            assertThat(jdbc.queryForObject(
                    "SELECT scope_type FROM hospital_capability_packages WHERE id = 'p1'", String.class))
                    .isEqualTo("GLOBAL");
            assertThat(jdbc.queryForObject("SELECT column_default FROM information_schema.columns "
                    + "WHERE table_schema = 'public' AND table_name = 'medical_human_tasks' "
                    + "AND column_name = 'created_at'", String.class))
                    .contains("CURRENT_TIMESTAMP");
        }
    }

    @Test
    void v10AddsApprovalFencingColumnsAndSubmissionKeyDedup() {
        try (var db = PostgresTestDatabase.create("medical_migration_v10")) {
            db.migrate();
            var jdbc = db.jdbc();
            jdbc.update("INSERT INTO hospital_capability_packages "
                    + "(id, hospital_code, hospital_name, routing_description, status, schema_version, "
                    + "content, source, valid_from, valid_until, created_by) "
                    + "VALUES ('p1', 'demo', 'Demo Hospital', 'demo routing', 'PENDING', 1, '{}', "
                    + "'manual', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'seeder')");

            assertThat(jdbc.queryForObject(
                    "SELECT approval_version FROM hospital_capability_packages WHERE id = 'p1'", Long.class))
                    .isZero();

            jdbc.update("INSERT INTO medical_human_tasks (id, conversation_id, hospital_code, tool_name, "
                    + "arguments_json, status, created_by) VALUES "
                    + "('t1', 'c1', 'demo', 'requestConsultation', '{}', 'PENDING', 'u1')");
            jdbc.update("INSERT INTO medical_human_tasks (id, conversation_id, hospital_code, tool_name, "
                    + "arguments_json, status, created_by) VALUES "
                    + "('t2', 'c1', 'demo', 'requestConsultation', '{}', 'PENDING', 'u1')");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM medical_human_tasks", Integer.class))
                    .isEqualTo(2);

            jdbc.update("INSERT INTO medical_human_tasks (id, conversation_id, hospital_code, tool_name, "
                    + "arguments_json, status, created_by, submission_key) VALUES "
                    + "('t3', 'c1', 'demo', 'requestConsultation', '{}', 'PENDING', 'u1', 'key-1')");
            assertThatThrownBy(() -> jdbc.update("INSERT INTO medical_human_tasks "
                    + "(id, conversation_id, hospital_code, tool_name, arguments_json, status, created_by, "
                    + "submission_key) VALUES "
                    + "('t4', 'c1', 'demo', 'requestConsultation', '{}', 'PENDING', 'u1', 'key-1')"))
                    .isInstanceOf(DuplicateKeyException.class);

            assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes "
                    + "WHERE tablename = 'medical_human_tasks' "
                    + "AND indexname = 'ux_medical_human_tasks_submission_key'", String.class))
                    .contains("WHERE (submission_key IS NOT NULL)");
        }
    }
}
