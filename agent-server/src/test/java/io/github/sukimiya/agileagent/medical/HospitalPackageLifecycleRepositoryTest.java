package io.github.sukimiya.agileagent.medical;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.PostgresTestDatabase;
import io.github.sukimiya.medical.hospital.HospitalPackageRepository;
import io.github.sukimiya.medical.hospital.HospitalPackageRow;

/**
 * V10 lifecycle plumbing on real PostgreSQL: replacement-semantics upsert, version-fenced
 * CAS transitions, and the approval-state projection (spec §14 resolution 2).
 */
class HospitalPackageLifecycleRepositoryTest {

    private static HospitalPackageRow draft(String id, String code, String name) {
        return new HospitalPackageRow(id, code, name, name + "路由描述",
                HospitalPackageRow.Status.APPROVED, 1, "{\"schemaVersion\":1,\"tools\":[]}", "manual",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30),
                HospitalPackageRow.ScopeType.GLOBAL, null, null, null, null, "seeder", null);
    }

    @Test
    void upsertPendingInsertsNewRowAtVersionZeroForcingPending() {
        try (var db = PostgresTestDatabase.create("lifecycle_repo_insert")) {
            db.migrate();
            var repo = new HospitalPackageRepository(db.databaseClient());

            var state = repo.upsertPending(draft("pkg-1", "demo", "演示医院"))
                    .block(Duration.ofSeconds(10));

            assertThat(state.id()).isEqualTo("pkg-1");
            // The draft claims APPROVED; replacement semantics must force it back to PENDING.
            assertThat(state.status()).isEqualTo(HospitalPackageRow.Status.PENDING);
            assertThat(state.approvalVersion()).isZero();
            assertThat(state.reviewedBy()).isNull();
            assertThat(state.reviewedAt()).isNull();
            assertThat(state.rejectionReason()).isNull();
            assertThat(state.lastGateReport()).isNull();
            assertThat(db.jdbc().queryForObject(
                    "SELECT created_at FROM hospital_capability_packages WHERE id = 'pkg-1'",
                    LocalDateTime.class)).isNotNull();
        }
    }

    @Test
    void upsertPendingReplacesContentBumpsVersionAndClearsAudit() {
        try (var db = PostgresTestDatabase.create("lifecycle_repo_replace")) {
            db.migrate();
            var repo = new HospitalPackageRepository(db.databaseClient());
            repo.upsertPending(draft("pkg-1", "demo", "演示医院")).block(Duration.ofSeconds(10));
            db.jdbc().update("UPDATE hospital_capability_packages SET status = 'APPROVED', "
                    + "reviewed_by = 'admin', reviewed_at = CURRENT_TIMESTAMP, "
                    + "last_gate_report = '{\"passed\":true}' WHERE id = 'pkg-1'");

            var state = repo.upsertPending(draft("pkg-2", "demo", "演示医院 v2"))
                    .block(Duration.ofSeconds(10));

            // Conflict on hospital_code keeps the original row id and bumps the fence,
            // invalidating the stale approval over replaced content (TOCTOU guard).
            assertThat(state.id()).isEqualTo("pkg-1");
            assertThat(state.approvalVersion()).isEqualTo(1L);
            assertThat(state.status()).isEqualTo(HospitalPackageRow.Status.PENDING);
            assertThat(state.reviewedBy()).isNull();
            assertThat(state.reviewedAt()).isNull();
            assertThat(state.lastGateReport()).isNull();
            assertThat(db.jdbc().queryForObject(
                    "SELECT hospital_name FROM hospital_capability_packages WHERE hospital_code = 'demo'",
                    String.class)).isEqualTo("演示医院 v2");
            assertThat(db.jdbc().queryForObject(
                    "SELECT COUNT(*) FROM hospital_capability_packages", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    void compareAndSetStatusOnlyAppliesAtExpectedVersion() {
        try (var db = PostgresTestDatabase.create("lifecycle_repo_cas")) {
            db.migrate();
            var repo = new HospitalPackageRepository(db.databaseClient());
            repo.upsertPending(draft("pkg-1", "demo", "演示医院")).block(Duration.ofSeconds(10));

            assertThat(repo.compareAndSetStatus("pkg-1", 0L, HospitalPackageRow.Status.APPROVED,
                    "admin", null).block(Duration.ofSeconds(10))).isTrue();
            var approved = repo.findApprovalState("pkg-1").block(Duration.ofSeconds(10));
            assertThat(approved.status()).isEqualTo(HospitalPackageRow.Status.APPROVED);
            assertThat(approved.approvalVersion()).isEqualTo(1L);
            assertThat(approved.reviewedBy()).isEqualTo("admin");
            assertThat(approved.reviewedAt()).isNotNull();

            // A second writer still holding version 0 loses the race.
            assertThat(repo.compareAndSetStatus("pkg-1", 0L, HospitalPackageRow.Status.REVOKED,
                    "intruder", "late revoke").block(Duration.ofSeconds(10))).isFalse();
            assertThat(repo.findApprovalState("pkg-1").block(Duration.ofSeconds(10)).status())
                    .isEqualTo(HospitalPackageRow.Status.APPROVED);

            assertThat(repo.compareAndSetStatus("pkg-1", 1L, HospitalPackageRow.Status.REVOKED,
                    "admin", "合作终止").block(Duration.ofSeconds(10))).isTrue();
            var revoked = repo.findApprovalState("pkg-1").block(Duration.ofSeconds(10));
            assertThat(revoked.status()).isEqualTo(HospitalPackageRow.Status.REVOKED);
            assertThat(revoked.approvalVersion()).isEqualTo(2L);
            assertThat(revoked.rejectionReason()).isEqualTo("合作终止");

            assertThat(repo.compareAndSetStatus("missing", 0L, HospitalPackageRow.Status.APPROVED,
                    "admin", null).block(Duration.ofSeconds(10))).isFalse();
        }
    }

    @Test
    void findApprovalStateMissingIsEmpty() {
        try (var db = PostgresTestDatabase.create("lifecycle_repo_missing")) {
            db.migrate();
            var repo = new HospitalPackageRepository(db.databaseClient());

            assertThat(repo.findApprovalState("missing").block(Duration.ofSeconds(10))).isNull();
        }
    }

    @Test
    void findForReviewReturnsRowAndStateFromOneSnapshot() {
        try (var db = PostgresTestDatabase.create("lifecycle_repo_snapshot")) {
            db.migrate();
            var repo = new HospitalPackageRepository(db.databaseClient());
            repo.upsertPending(draft("pkg-1", "demo", "演示医院")).block(Duration.ofSeconds(10));
            repo.saveGateReport("pkg-1", "{\"passed\":true,\"skippedGates\":[]}")
                    .block(Duration.ofSeconds(10));

            var snapshot = repo.findForReview("pkg-1").block(Duration.ofSeconds(10));

            assertThat(snapshot).isNotNull();
            assertThat(snapshot.row().id()).isEqualTo("pkg-1");
            assertThat(snapshot.row().hospitalCode()).isEqualTo("demo");
            assertThat(snapshot.row().hospitalName()).isEqualTo("演示医院");
            assertThat(snapshot.row().status()).isEqualTo(HospitalPackageRow.Status.PENDING);
            assertThat(snapshot.row().content()).isEqualTo("{\"schemaVersion\":1,\"tools\":[]}");
            assertThat(snapshot.row().createdBy()).isEqualTo("seeder");
            assertThat(snapshot.state().id()).isEqualTo("pkg-1");
            assertThat(snapshot.state().status()).isEqualTo(HospitalPackageRow.Status.PENDING);
            assertThat(snapshot.state().approvalVersion()).isZero();
            assertThat(snapshot.state().reviewedBy()).isNull();
            assertThat(snapshot.state().lastGateReport())
                    .isEqualTo("{\"passed\":true,\"skippedGates\":[]}");

            assertThat(repo.findForReview("missing-id").block(Duration.ofSeconds(10))).isNull();
        }
    }

    @Test
    void saveGateReportRoundTripsWithoutBumpingVersion() {
        try (var db = PostgresTestDatabase.create("lifecycle_repo_report")) {
            db.migrate();
            var repo = new HospitalPackageRepository(db.databaseClient());
            repo.upsertPending(draft("pkg-1", "demo", "演示医院")).block(Duration.ofSeconds(10));

            repo.saveGateReport("pkg-1", "{\"passed\":true,\"skippedGates\":[]}")
                    .block(Duration.ofSeconds(10));

            var state = repo.findApprovalState("pkg-1").block(Duration.ofSeconds(10));
            assertThat(state.lastGateReport()).isEqualTo("{\"passed\":true,\"skippedGates\":[]}");
            assertThat(state.approvalVersion()).isZero();
        }
    }
}
