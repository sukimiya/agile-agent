package io.github.sukimiya.agileagent.medical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.PostgresTestDatabase;
import io.github.sukimiya.medical.hospital.HospitalPackageRepository;
import io.github.sukimiya.medical.hospital.HospitalPackageRow;

class HospitalPackageRepositoryTest {

    private static HospitalPackageRow row(String id, String hospitalCode, HospitalPackageRow.Status status,
            HospitalPackageRow.ScopeType scope, String tenantId, String orgId) {
        return new HospitalPackageRow(id, hospitalCode, "医院-" + hospitalCode, "路由描述-" + hospitalCode,
                status, 1, "{\"schemaVersion\":1,\"tools\":[]}", "manual",
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 12, 31, 23, 59),
                scope, tenantId, tenantId == null ? null : "租户", orgId, orgId == null ? null : "机构",
                "admin", null);
    }

    @Test
    void insertsAndReadsBackEveryBoundColumn() {
        try (var db = PostgresTestDatabase.create("hospital_repo_rw")) {
            db.migrate();
            var repo = new HospitalPackageRepository(db.databaseClient());
            var original = row("pkg-1", "demo", HospitalPackageRow.Status.APPROVED,
                    HospitalPackageRow.ScopeType.TENANT, "tenant-1", null);

            repo.insert(original).block(Duration.ofSeconds(10));

            var found = repo.findById("pkg-1").block(Duration.ofSeconds(10));
            assertThat(found.id()).isEqualTo("pkg-1");
            assertThat(found.hospitalCode()).isEqualTo("demo");
            assertThat(found.hospitalName()).isEqualTo("医院-demo");
            assertThat(found.routingDescription()).isEqualTo("路由描述-demo");
            assertThat(found.status()).isEqualTo(HospitalPackageRow.Status.APPROVED);
            assertThat(found.schemaVersion()).isEqualTo(1);
            assertThat(found.content()).isEqualTo("{\"schemaVersion\":1,\"tools\":[]}");
            assertThat(found.source()).isEqualTo("manual");
            assertThat(found.validFrom()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
            assertThat(found.validUntil()).isEqualTo(LocalDateTime.of(2026, 12, 31, 23, 59));
            assertThat(found.scopeType()).isEqualTo(HospitalPackageRow.ScopeType.TENANT);
            assertThat(found.tenantId()).isEqualTo("tenant-1");
            assertThat(found.tenantName()).isEqualTo("租户");
            assertThat(found.orgId()).isNull();
            assertThat(found.orgName()).isNull();
            assertThat(found.createdBy()).isEqualTo("admin");
            assertThat(found.createdAt()).isNotNull();
            assertThat(repo.findById("missing").block(Duration.ofSeconds(10))).isNull();
        }
    }

    @Test
    void findByHospitalCodeUsesTheUniqueIndex() {
        try (var db = PostgresTestDatabase.create("hospital_repo_code")) {
            db.migrate();
            var repo = new HospitalPackageRepository(db.databaseClient());
            repo.insert(row("pkg-1", "demo", HospitalPackageRow.Status.APPROVED,
                    HospitalPackageRow.ScopeType.GLOBAL, null, null)).block(Duration.ofSeconds(10));

            var found = repo.findByHospitalCode("demo").block(Duration.ofSeconds(10));

            assertThat(found.id()).isEqualTo("pkg-1");
            assertThat(repo.findByHospitalCode("nope").block(Duration.ofSeconds(10))).isNull();
        }
    }

    @Test
    void findApprovedReturnsOnlyApprovedOrderedByHospitalCode() {
        try (var db = PostgresTestDatabase.create("hospital_repo_approved")) {
            db.migrate();
            var repo = new HospitalPackageRepository(db.databaseClient());
            repo.insert(row("pkg-b", "bbb", HospitalPackageRow.Status.APPROVED,
                    HospitalPackageRow.ScopeType.GLOBAL, null, null)).block(Duration.ofSeconds(10));
            repo.insert(row("pkg-a", "aaa", HospitalPackageRow.Status.APPROVED,
                    HospitalPackageRow.ScopeType.GLOBAL, null, null)).block(Duration.ofSeconds(10));
            repo.insert(row("pkg-c", "ccc", HospitalPackageRow.Status.PENDING,
                    HospitalPackageRow.ScopeType.GLOBAL, null, null)).block(Duration.ofSeconds(10));

            var approved = repo.findApproved().collectList().block(Duration.ofSeconds(10));

            assertThat(approved).extracting(HospitalPackageRow::hospitalCode).containsExactly("aaa", "bbb");
        }
    }

    @Test
    void updateStatusFlipsTheGateAndReportsRowCount() {
        try (var db = PostgresTestDatabase.create("hospital_repo_status")) {
            db.migrate();
            var repo = new HospitalPackageRepository(db.databaseClient());
            repo.insert(row("pkg-1", "demo", HospitalPackageRow.Status.APPROVED,
                    HospitalPackageRow.ScopeType.GLOBAL, null, null)).block(Duration.ofSeconds(10));

            var updated = repo.updateStatus("pkg-1", HospitalPackageRow.Status.REVOKED)
                    .block(Duration.ofSeconds(10));

            assertThat(updated).isEqualTo(1L);
            assertThat(repo.findById("pkg-1").block(Duration.ofSeconds(10)).status())
                    .isEqualTo(HospitalPackageRow.Status.REVOKED);
            assertThat(repo.updateStatus("missing", HospitalPackageRow.Status.REVOKED)
                    .block(Duration.ofSeconds(10))).isZero();
        }
    }

    @Test
    void duplicateHospitalCodeIsRejectedByTheUniqueConstraint() {
        try (var db = PostgresTestDatabase.create("hospital_repo_dup")) {
            db.migrate();
            var repo = new HospitalPackageRepository(db.databaseClient());
            repo.insert(row("pkg-1", "demo", HospitalPackageRow.Status.APPROVED,
                    HospitalPackageRow.ScopeType.GLOBAL, null, null)).block(Duration.ofSeconds(10));

            try {
                repo.insert(row("pkg-2", "demo", HospitalPackageRow.Status.PENDING,
                        HospitalPackageRow.ScopeType.GLOBAL, null, null)).block(Duration.ofSeconds(10));
                fail("duplicate hospital_code must be rejected");
            } catch (RuntimeException expected) {
                // 具体异常类型（DataAccessException 家族）由 r2dbc 转译，不做类型断言
            }

            assertThat(db.jdbc().queryForObject(
                    "SELECT COUNT(*) FROM hospital_capability_packages", Integer.class)).isEqualTo(1);
        }
    }
}
