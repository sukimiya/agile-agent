package io.github.sukimiya.agileagent.medical;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.PostgresTestDatabase;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRepository;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRow;

class MedicalHumanTaskRepositoryTest {

    @Test
    void insertsAndReadsBackATask() {
        try (var db = PostgresTestDatabase.create("human_task_repo")) {
            db.migrate();
            var repo = new MedicalHumanTaskRepository(db.databaseClient());
            var row = new MedicalHumanTaskRow("task-1", "conv-1", "demo", "requestConsultation",
                    "{\"keyword\":\"cardiology\"}", MedicalHumanTaskRow.TaskStatus.PENDING, "user-1", null);

            repo.insert(row).block(Duration.ofSeconds(10));

            var found = repo.findById("task-1").block(Duration.ofSeconds(10));
            assertThat(found.id()).isEqualTo("task-1");
            assertThat(found.conversationId()).isEqualTo("conv-1");
            assertThat(found.hospitalCode()).isEqualTo("demo");
            assertThat(found.toolName()).isEqualTo("requestConsultation");
            assertThat(found.argumentsJson()).isEqualTo("{\"keyword\":\"cardiology\"}");
            assertThat(found.status()).isEqualTo(MedicalHumanTaskRow.TaskStatus.PENDING);
            assertThat(found.createdBy()).isEqualTo("user-1");
            assertThat(found.createdAt()).isNotNull();
            assertThat(repo.findById("missing").block(Duration.ofSeconds(10))).isNull();
        }
    }

    @Test
    void insertOrFindExistingDeduplicatesOnSubmissionKey() {
        try (var db = PostgresTestDatabase.create("human_task_repo_dedup")) {
            db.migrate();
            var repo = new MedicalHumanTaskRepository(db.databaseClient());
            var first = new MedicalHumanTaskRow("task-1", "conv-1", "demo", "requestConsultation",
                    "{}", MedicalHumanTaskRow.TaskStatus.PENDING, "user-1", null);
            var second = new MedicalHumanTaskRow("task-2", "conv-1", "demo", "requestConsultation",
                    "{}", MedicalHumanTaskRow.TaskStatus.PENDING, "user-1", null);

            var inserted = repo.insertOrFindExisting(first, "key-1").block(Duration.ofSeconds(10));
            var duplicate = repo.insertOrFindExisting(second, "key-1").block(Duration.ofSeconds(10));

            assertThat(inserted.id()).isEqualTo("task-1");
            // The retry never inserts: it returns the ORIGINAL row, keeping one human task.
            assertThat(duplicate.id()).isEqualTo("task-1");
            assertThat(db.jdbc().queryForObject("SELECT COUNT(*) FROM medical_human_tasks", Integer.class))
                    .isEqualTo(1);
            assertThat(db.jdbc().queryForObject(
                    "SELECT submission_key FROM medical_human_tasks WHERE id = 'task-1'", String.class))
                    .isEqualTo("key-1");
        }
    }

    @Test
    void nullSubmissionKeyAllowsDuplicates() {
        try (var db = PostgresTestDatabase.create("human_task_repo_nullkey")) {
            db.migrate();
            var repo = new MedicalHumanTaskRepository(db.databaseClient());
            var first = new MedicalHumanTaskRow("task-1", "conv-1", "demo", "requestConsultation",
                    "{}", MedicalHumanTaskRow.TaskStatus.PENDING, "user-1", null);
            var second = new MedicalHumanTaskRow("task-2", "conv-1", "demo", "requestConsultation",
                    "{}", MedicalHumanTaskRow.TaskStatus.PENDING, "user-1", null);

            repo.insertOrFindExisting(first, null).block(Duration.ofSeconds(10));
            repo.insertOrFindExisting(second, null).block(Duration.ofSeconds(10));

            assertThat(db.jdbc().queryForObject("SELECT COUNT(*) FROM medical_human_tasks", Integer.class))
                    .isEqualTo(2);
        }
    }

    @Test
    void countAndDeleteByConversationIdSupportSimulationCleanup() {
        try (var db = PostgresTestDatabase.create("human_task_repo_conv")) {
            db.migrate();
            var repo = new MedicalHumanTaskRepository(db.databaseClient());
            repo.insert(new MedicalHumanTaskRow("task-1", "conv-sim", "demo", "requestConsultation",
                    "{}", MedicalHumanTaskRow.TaskStatus.PENDING, "user-1", null)).block(Duration.ofSeconds(10));
            repo.insert(new MedicalHumanTaskRow("task-2", "conv-sim", "demo", "requestConsultation",
                    "{}", MedicalHumanTaskRow.TaskStatus.PENDING, "user-1", null)).block(Duration.ofSeconds(10));
            repo.insert(new MedicalHumanTaskRow("task-3", "conv-other", "demo", "requestConsultation",
                    "{}", MedicalHumanTaskRow.TaskStatus.PENDING, "user-1", null)).block(Duration.ofSeconds(10));

            assertThat(repo.countByConversationId("conv-sim").block(Duration.ofSeconds(10))).isEqualTo(2L);
            assertThat(repo.deleteByConversationId("conv-sim").block(Duration.ofSeconds(10))).isEqualTo(2L);
            assertThat(repo.countByConversationId("conv-sim").block(Duration.ofSeconds(10))).isZero();
            assertThat(repo.countByConversationId("conv-other").block(Duration.ofSeconds(10))).isEqualTo(1L);
        }
    }
}
