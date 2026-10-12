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
}
