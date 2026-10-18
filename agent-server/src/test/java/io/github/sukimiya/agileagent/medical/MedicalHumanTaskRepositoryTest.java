package io.github.sukimiya.agileagent.medical;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

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
            var row = MedicalHumanTaskRow.normal("task-1", "conv-1", "demo", "requestConsultation",
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
            var first = MedicalHumanTaskRow.normal("task-1", "conv-1", "demo", "requestConsultation",
                    "{}", MedicalHumanTaskRow.TaskStatus.PENDING, "user-1", null);
            var second = MedicalHumanTaskRow.normal("task-2", "conv-1", "demo", "requestConsultation",
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
            var first = MedicalHumanTaskRow.normal("task-1", "conv-1", "demo", "requestConsultation",
                    "{}", MedicalHumanTaskRow.TaskStatus.PENDING, "user-1", null);
            var second = MedicalHumanTaskRow.normal("task-2", "conv-1", "demo", "requestConsultation",
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
            repo.insert(MedicalHumanTaskRow.normal("task-1", "conv-sim", "demo", "requestConsultation",
                    "{}", MedicalHumanTaskRow.TaskStatus.PENDING, "user-1", null)).block(Duration.ofSeconds(10));
            repo.insert(MedicalHumanTaskRow.normal("task-2", "conv-sim", "demo", "requestConsultation",
                    "{}", MedicalHumanTaskRow.TaskStatus.PENDING, "user-1", null)).block(Duration.ofSeconds(10));
            repo.insert(MedicalHumanTaskRow.normal("task-3", "conv-other", "demo", "requestConsultation",
                    "{}", MedicalHumanTaskRow.TaskStatus.PENDING, "user-1", null)).block(Duration.ofSeconds(10));

            assertThat(repo.countByConversationId("conv-sim").block(Duration.ofSeconds(10))).isEqualTo(2L);
            assertThat(repo.deleteByConversationId("conv-sim").block(Duration.ofSeconds(10))).isEqualTo(2L);
            assertThat(repo.countByConversationId("conv-sim").block(Duration.ofSeconds(10))).isZero();
            assertThat(repo.countByConversationId("conv-other").block(Duration.ofSeconds(10))).isEqualTo(1L);
        }
    }

    @Test
    void insertEcallOrFindExistingKeepsOneOpenEcallPerConversation() {
        try (var db = PostgresTestDatabase.create("human_task_repo_ecall")) {
            db.migrate();
            var repo = new MedicalHumanTaskRepository(db.databaseClient());
            var first = MedicalHumanTaskRow.ecall("ecall-1", "conv-e", "{\"ruleCode\":\"r\"}",
                    "user-1", 100, LocalDateTime.now().plusSeconds(30));

            var inserted = repo.insertEcallOrFindExisting(first).block(Duration.ofSeconds(10));
            var duplicate = repo.insertEcallOrFindExisting(MedicalHumanTaskRow.ecall("ecall-2",
                    "conv-e", "{\"ruleCode\":\"r\"}", "user-1", 50,
                    LocalDateTime.now())).block(Duration.ofSeconds(10));

            assertThat(inserted.id()).isEqualTo("ecall-1");
            assertThat(duplicate.id()).isEqualTo("ecall-1"); // idempotent reuse, never a second row
            assertThat(db.jdbc().queryForObject("SELECT COUNT(*) FROM medical_human_tasks",
                    Integer.class)).isEqualTo(1);
            var open = repo.findOpenEcallByConversationId("conv-e").block(Duration.ofSeconds(10));
            assertThat(open.taskKind()).isEqualTo(MedicalHumanTaskRow.TaskKind.ECALL);
            assertThat(open.priority()).isEqualTo(100);
            assertThat(open.escalationLevel()).isZero();
            assertThat(open.slaDeadline()).isNotNull();

            // Completing frees the slot for a future E-call.
            repo.completeFromPending("ecall-1", "已线下处理", LocalDateTime.now())
                    .block(Duration.ofSeconds(10));
            assertThat(repo.findOpenEcallByConversationId("conv-e")
                    .block(Duration.ofSeconds(10))).isNull();
            var next = repo.insertEcallOrFindExisting(MedicalHumanTaskRow.ecall("ecall-3",
                    "conv-e", "{}", "user-1", 100, LocalDateTime.now()))
                    .block(Duration.ofSeconds(10));
            assertThat(next.id()).isEqualTo("ecall-3");
        }
    }

    @Test
    void claimIsAtomicCompareAndSet() {
        try (var db = PostgresTestDatabase.create("human_task_repo_claim")) {
            db.migrate();
            var repo = new MedicalHumanTaskRepository(db.databaseClient());
            repo.insertEcallOrFindExisting(MedicalHumanTaskRow.ecall("ecall-1", "conv-c", "{}",
                    "user-1", 100, LocalDateTime.now())).block(Duration.ofSeconds(10));

            assertThat(repo.claim("ecall-1", "ops-1", LocalDateTime.now())
                    .block(Duration.ofSeconds(10))).isTrue();
            // Second claim loses the race — CAS conditions no longer hold.
            assertThat(repo.claim("ecall-1", "ops-2", LocalDateTime.now())
                    .block(Duration.ofSeconds(10))).isFalse();
            assertThat(repo.claim("missing", "ops-1", LocalDateTime.now())
                    .block(Duration.ofSeconds(10))).isFalse();

            var row = repo.findById("ecall-1").block(Duration.ofSeconds(10));
            assertThat(row.status()).isEqualTo(MedicalHumanTaskRow.TaskStatus.CLAIMED);
            assertThat(row.claimedBy()).isEqualTo("ops-1");
            assertThat(row.claimedAt()).isNotNull();
        }
    }

    @Test
    void completeVariantsEnforceStateAndOwnership() {
        try (var db = PostgresTestDatabase.create("human_task_repo_complete")) {
            db.migrate();
            var repo = new MedicalHumanTaskRepository(db.databaseClient());
            var now = LocalDateTime.now();
            repo.insert(MedicalHumanTaskRow.normal("normal-1", "conv-n", "demo",
                    "requestConsultation", "{}", MedicalHumanTaskRow.TaskStatus.PENDING,
                    "user-1", null)).block(Duration.ofSeconds(10));
            repo.insertEcallOrFindExisting(MedicalHumanTaskRow.ecall("ecall-1", "conv-e", "{}",
                    "user-1", 100, now)).block(Duration.ofSeconds(10));

            // NORMAL: PENDING → COMPLETED directly (hospital-confirmation desk entry, C04).
            assertThat(repo.completeFromPending("normal-1", "医院已确认", now)
                    .block(Duration.ofSeconds(10))).isTrue();
            assertThat(repo.completeFromPending("normal-1", "重复", now)
                    .block(Duration.ofSeconds(10))).isFalse();
            var completed = repo.findById("normal-1").block(Duration.ofSeconds(10));
            assertThat(completed.status()).isEqualTo(MedicalHumanTaskRow.TaskStatus.COMPLETED);
            assertThat(completed.completedAt()).isNotNull();
            assertThat(completed.resolutionNote()).isEqualTo("医院已确认");

            // ECALL: owner completion requires CLAIMED + claimed_by match.
            assertThat(repo.completeClaimedByOwner("ecall-1", "ops-9", "x", now)
                    .block(Duration.ofSeconds(10))).isFalse(); // still PENDING
            repo.claim("ecall-1", "ops-1", now).block(Duration.ofSeconds(10));
            assertThat(repo.completeClaimedByOwner("ecall-1", "ops-2", "x", now)
                    .block(Duration.ofSeconds(10))).isFalse(); // wrong owner
            assertThat(repo.completeClaimedByOwner("ecall-1", "ops-1", "专员已联系患者", now)
                    .block(Duration.ofSeconds(10))).isTrue();
            // Admin override variant only applies while CLAIMED.
            repo.insertEcallOrFindExisting(MedicalHumanTaskRow.ecall("ecall-2", "conv-e2", "{}",
                    "user-1", 100, now)).block(Duration.ofSeconds(10));
            assertThat(repo.completeClaimedAny("ecall-2", "x", now)
                    .block(Duration.ofSeconds(10))).isFalse(); // PENDING, not CLAIMED
        }
    }

    @Test
    void escalateAndReleaseAreFencedCasUpdates() {
        try (var db = PostgresTestDatabase.create("human_task_repo_cas")) {
            db.migrate();
            var repo = new MedicalHumanTaskRepository(db.databaseClient());
            var now = LocalDateTime.now();
            repo.insertEcallOrFindExisting(MedicalHumanTaskRow.ecall("ecall-1", "conv-s", "{}",
                    "user-1", 100, now.minusSeconds(1))).block(Duration.ofSeconds(10));

            assertThat(repo.escalate("ecall-1", 0, 1, now.plusSeconds(300))
                    .block(Duration.ofSeconds(10))).isTrue();
            assertThat(repo.escalate("ecall-1", 0, 1, now.plusSeconds(300))
                    .block(Duration.ofSeconds(10))).isFalse(); // stale expected level
            var escalated = repo.findById("ecall-1").block(Duration.ofSeconds(10));
            assertThat(escalated.escalationLevel()).isEqualTo(1);
            assertThat(escalated.slaDeadline()).isAfter(now);

            // Overdue scan sees PENDING rows past their deadline only.
            assertThat(repo.findOverdueOpenEcalls(now.plusSeconds(600))
                    .collectList().block(Duration.ofSeconds(10)))
                    .extracting(MedicalHumanTaskRow::id).containsExactly("ecall-1");
            assertThat(repo.findOverdueOpenEcalls(now)
                    .collectList().block(Duration.ofSeconds(10))).isEmpty();

            // Stale-claim recovery: claim, then release fenced on the exact claimed_at.
            repo.claim("ecall-1", "ops-1", now).block(Duration.ofSeconds(10));
            var claimed = repo.findById("ecall-1").block(Duration.ofSeconds(10));
            assertThat(repo.findClaimedBefore(now.plusSeconds(10))
                    .collectList().block(Duration.ofSeconds(10)))
                    .extracting(MedicalHumanTaskRow::id).containsExactly("ecall-1");
            assertThat(repo.releaseClaim("ecall-1", claimed.claimedAt().minusSeconds(1))
                    .block(Duration.ofSeconds(10))).isFalse(); // fence mismatch
            assertThat(repo.releaseClaim("ecall-1", claimed.claimedAt())
                    .block(Duration.ofSeconds(10))).isTrue();
            var released = repo.findById("ecall-1").block(Duration.ofSeconds(10));
            assertThat(released.status()).isEqualTo(MedicalHumanTaskRow.TaskStatus.PENDING);
            assertThat(released.claimedBy()).isNull();
            assertThat(released.claimedAt()).isNull();
        }
    }
}
