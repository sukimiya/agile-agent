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
                            "arguments_json", "status", "created_by", "created_at", "submission_key",
                            "task_kind", "priority", "sla_deadline", "escalation_level",
                            "claimed_by", "claimed_at", "completed_at", "resolution_note");
            assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes "
                    + "WHERE tablename = 'medical_human_tasks'", String.class))
                    .contains("idx_medical_human_tasks_status", "idx_medical_human_tasks_conversation",
                            "ux_medical_human_tasks_submission_key", "ux_medical_human_tasks_open_ecall");
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

    @Test
    void v11CreatesRuleAndEventTablesExtendsTasksAndSeedsTheFlagshipRule() {
        try (var db = PostgresTestDatabase.create("medical_migration_v11")) {
            db.migrate();
            var jdbc = db.jdbc();

            assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = 'public' AND table_name = 'medical_rules' "
                    + "ORDER BY ordinal_position", String.class))
                    .containsExactly("id", "rule_code", "rule_kind", "scope_type", "tenant_id",
                            "tenant_name", "org_id", "org_name", "hospital_code", "scope_key",
                            "content_json", "source", "valid_from", "valid_until", "status",
                            "approval_version", "reviewed_by", "reviewed_at", "rejection_reason",
                            "last_validation_report", "created_by", "created_at");
            assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                    + "WHERE table_schema = 'public' AND table_name = 'medical_task_events' "
                    + "ORDER BY ordinal_position", String.class))
                    .containsExactly("id", "task_id", "event_type", "level", "actor", "detail",
                            "created_at");
            assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes "
                    + "WHERE tablename = 'medical_task_events'", String.class))
                    .contains("idx_medical_task_events_task");

            // One open E-call per conversation, enforced by the partial unique index.
            String openEcall = "INSERT INTO medical_human_tasks (id, conversation_id, "
                    + "hospital_code, tool_name, arguments_json, status, created_by, task_kind, "
                    + "priority, sla_deadline, escalation_level) VALUES "
                    + "(?, 'c-e', 'ecall', 'submitTriage', '{}', 'PENDING', 'u1', 'ECALL', 100, "
                    + "CURRENT_TIMESTAMP, 0)";
            jdbc.update(openEcall, "e1");
            assertThatThrownBy(() -> jdbc.update(openEcall, "e2"))
                    .isInstanceOf(DuplicateKeyException.class);
            jdbc.update("UPDATE medical_human_tasks SET status = 'COMPLETED' WHERE id = 'e1'");
            jdbc.update(openEcall, "e3"); // slot freed by completion

            // Legacy inserts keep NORMAL defaults.
            jdbc.update("INSERT INTO medical_human_tasks (id, conversation_id, hospital_code, "
                    + "tool_name, arguments_json, status, created_by) VALUES "
                    + "('n1', 'c-n', 'demo', 'requestConsultation', '{}', 'PENDING', 'u1')");
            assertThat(jdbc.queryForObject("SELECT task_kind FROM medical_human_tasks "
                    + "WHERE id = 'n1'", String.class)).isEqualTo("NORMAL");
            assertThat(jdbc.queryForObject("SELECT escalation_level FROM medical_human_tasks "
                    + "WHERE id = 'n1'", Integer.class)).isZero();

            // Ruling R1: same rule_code coexists across scopes; same code+scope conflicts.
            String rule = "INSERT INTO medical_rules (id, rule_code, rule_kind, scope_type, "
                    + "tenant_id, tenant_name, hospital_code, content_json, source, valid_from, "
                    + "valid_until, status, created_by) VALUES (?, 'code-x', 'EMERGENCY', ?, ?, ?, "
                    + "NULL, '{}', 'manual', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'PENDING', "
                    + "'u1')";
            jdbc.update(rule, "r1", "TENANT", "t1", "租户");
            assertThatThrownBy(() -> jdbc.update(rule, "r2", "TENANT", "t1", "租户"))
                    .isInstanceOf(DuplicateKeyException.class);
            jdbc.update("INSERT INTO medical_rules (id, rule_code, rule_kind, scope_type, "
                    + "content_json, source, valid_from, valid_until, status, created_by) VALUES "
                    + "('r3', 'code-x', 'EMERGENCY', 'GLOBAL', '{}', 'manual', CURRENT_TIMESTAMP, "
                    + "CURRENT_TIMESTAMP, 'PENDING', 'u1')");

            // Flagship seed: an empty database already carries the safety baseline (spec §4).
            var seed = jdbc.queryForMap("SELECT rule_code, rule_kind, scope_type, status, source, "
                    + "content_json FROM medical_rules WHERE id = 'rule-global-emergency-baseline'");
            assertThat(seed).containsEntry("rule_code", "global-emergency-baseline")
                    .containsEntry("rule_kind", "EMERGENCY")
                    .containsEntry("scope_type", "GLOBAL")
                    .containsEntry("status", "APPROVED")
                    .containsEntry("source", "migration");
            assertThat((String) seed.get("content_json"))
                    .contains("急性心肌梗死").contains("stroke")
                    .contains("\"slaSeconds\":0")
                    .contains("我们已立即为您联系专事专办医疗协调专员");
        }
    }
}
