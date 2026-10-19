package io.github.sukimiya.agileagent.medical;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.PostgresTestDatabase;
import io.github.sukimiya.medical.rules.MedicalRuleApprovalState;
import io.github.sukimiya.medical.rules.MedicalRuleRepository;
import io.github.sukimiya.medical.rules.MedicalRuleRow;

class MedicalRuleRepositoryTest {

    private static MedicalRuleRow draft(String id, String ruleCode,
            MedicalRuleRow.ScopeType scope, String tenantId, String contentJson) {
        return new MedicalRuleRow(id, ruleCode, "EMERGENCY", scope, tenantId,
                tenantId == null ? null : "租户", null, null,
                scope == MedicalRuleRow.ScopeType.HOSPITAL ? "h1" : null,
                contentJson, "manual", LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1), MedicalRuleRow.Status.PENDING, "admin", null);
    }

    @Test
    void upsertPendingInsertsThenReplacesWithVersionBump() {
        try (var db = PostgresTestDatabase.create("rule_repo")) {
            db.migrate();
            var repo = new MedicalRuleRepository(db.databaseClient());

            var first = repo.upsertPending(draft("r-1", "code-a",
                    MedicalRuleRow.ScopeType.GLOBAL, null, "{\"v\":1}"))
                    .block(Duration.ofSeconds(10));
            assertThat(first.id()).isEqualTo("r-1");
            assertThat(first.status()).isEqualTo(MedicalRuleRow.Status.PENDING);
            assertThat(first.approvalVersion()).isZero();

            repo.saveValidationReport("r-1", "{\"passed\":true}").block(Duration.ofSeconds(10));
            repo.compareAndSetStatus("r-1", 0L, MedicalRuleRow.Status.APPROVED, "admin", null)
                    .block(Duration.ofSeconds(10));

            // Replacement semantics: same rule_code+scope replaces the row, forces PENDING,
            // clears audit columns, bumps the fence. The existing row keeps its id.
            var replacement = repo.upsertPending(draft("r-2", "code-a",
                    MedicalRuleRow.ScopeType.GLOBAL, null, "{\"v\":2}"))
                    .block(Duration.ofSeconds(10));
            assertThat(replacement.id()).isEqualTo("r-1");
            assertThat(replacement.status()).isEqualTo(MedicalRuleRow.Status.PENDING);
            // version sequence: initial insert 0 -> approve CAS bump 1 -> replacement upsert bump 2
            assertThat(replacement.approvalVersion()).isEqualTo(2L);
            assertThat(replacement.lastValidationReport()).isNull();
            assertThat(replacement.reviewedBy()).isNull();
            var row = repo.findById("r-1").block(Duration.ofSeconds(10));
            assertThat(row.contentJson()).isEqualTo("{\"v\":2}");

            // Same rule_code in a DIFFERENT scope coexists (Ruling R1 strictest-merge input).
            var tenant = repo.upsertPending(draft("r-3", "code-a",
                    MedicalRuleRow.ScopeType.TENANT, "t1", "{\"v\":3}"))
                    .block(Duration.ofSeconds(10));
            assertThat(tenant.id()).isEqualTo("r-3");
            assertThat(tenant.approvalVersion()).isZero();
        }
    }

    @Test
    void findForReviewReturnsRowAndStateFromOneSnapshot() {
        try (var db = PostgresTestDatabase.create("rule_repo_review")) {
            db.migrate();
            var repo = new MedicalRuleRepository(db.databaseClient());
            repo.upsertPending(draft("r-1", "code-b", MedicalRuleRow.ScopeType.GLOBAL, null,
                    "{\"v\":1}")).block(Duration.ofSeconds(10));

            var snapshot = repo.findForReview("r-1").block(Duration.ofSeconds(10));
            assertThat(snapshot.row().ruleCode()).isEqualTo("code-b");
            assertThat(snapshot.state().approvalVersion()).isZero();
            assertThat(repo.findForReview("missing").block(Duration.ofSeconds(10))).isNull();

            var state = repo.findApprovalState("r-1").block(Duration.ofSeconds(10));
            assertThat(state.status()).isEqualTo(MedicalRuleRow.Status.PENDING);
        }
    }

    @Test
    void compareAndSetStatusIsVersionFenced() {
        try (var db = PostgresTestDatabase.create("rule_repo_cas")) {
            db.migrate();
            var repo = new MedicalRuleRepository(db.databaseClient());
            repo.upsertPending(draft("r-1", "code-c", MedicalRuleRow.ScopeType.GLOBAL, null,
                    "{}")).block(Duration.ofSeconds(10));

            assertThat(repo.compareAndSetStatus("r-1", 5L,
                    MedicalRuleRow.Status.APPROVED, "admin", null)
                    .block(Duration.ofSeconds(10))).isFalse(); // stale expected version
            assertThat(repo.compareAndSetStatus("r-1", 0L,
                    MedicalRuleRow.Status.APPROVED, "admin", null)
                    .block(Duration.ofSeconds(10))).isTrue();

            var state = repo.findApprovalState("r-1").block(Duration.ofSeconds(10));
            assertThat(state.status()).isEqualTo(MedicalRuleRow.Status.APPROVED);
            assertThat(state.approvalVersion()).isEqualTo(1L);
            assertThat(state.reviewedBy()).isEqualTo("admin");
            assertThat(state.reviewedAt()).isNotNull();

            assertThat(repo.compareAndSetStatus("r-1", 1L, MedicalRuleRow.Status.REVOKED,
                    "admin", "下线").block(Duration.ofSeconds(10))).isTrue();
            var revoked = repo.findApprovalState("r-1").block(Duration.ofSeconds(10));
            assertThat(revoked.status()).isEqualTo(MedicalRuleRow.Status.REVOKED);
            assertThat(revoked.rejectionReason()).isEqualTo("下线");
        }
    }

    @Test
    void findApprovedReturnsOnlyApprovedRowsForEngineAndStore() {
        try (var db = PostgresTestDatabase.create("rule_repo_approved")) {
            db.migrate();
            var repo = new MedicalRuleRepository(db.databaseClient());
            repo.upsertPending(draft("r-1", "code-d", MedicalRuleRow.ScopeType.GLOBAL, null,
                    "{}")).block(Duration.ofSeconds(10));
            repo.upsertPending(draft("r-2", "code-e", MedicalRuleRow.ScopeType.GLOBAL, null,
                    "{}")).block(Duration.ofSeconds(10));
            repo.compareAndSetStatus("r-1", 0L, MedicalRuleRow.Status.APPROVED, "admin", null)
                    .block(Duration.ofSeconds(10));

            var approved = repo.findApproved().collectList().block(Duration.ofSeconds(10));
            // The V11 flagship seed is APPROVED too — it must always be present.
            assertThat(approved).extracting(MedicalRuleRow::id)
                    .contains("r-1", "rule-global-emergency-baseline")
                    .doesNotContain("r-2");
        }
    }
}
