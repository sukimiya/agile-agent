package io.github.sukimiya.medical.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.hospital.HospitalPackageRow;
import io.github.sukimiya.medical.hospital.HospitalPackageRow.ScopeType;

class PackageSubmissionAuthorityTest {

    private static final IdentityContext PLATFORM_ADMIN = new IdentityContext("admin-1", "平台管理员",
            null, null, null, null, Set.of("ADMIN"), Set.of(), Set.of(), true, false);
    private static final IdentityContext TENANT_ADMIN = new IdentityContext("ta-1", "租户管理员",
            "tenant-1", "租户一", null, null, Set.of("TENANT_ADMIN"), Set.of(), Set.of(), false, true);
    private static final IdentityContext ORG_ADMIN = new IdentityContext("oa-1", "机构管理员",
            "tenant-1", "租户一", "org-1", "机构一", Set.of("ORG_ADMIN"), Set.of("org-1"), Set.of("org-1"),
            false, true);
    private static final IdentityContext USER = new IdentityContext("user-1", "用户一",
            "tenant-1", "租户一", "org-1", "机构一", Set.of("USER"), Set.of(), Set.of("org-1"), false, false);

    private static HospitalPackageRow draft(ScopeType scope, String tenantId, String orgId) {
        return new HospitalPackageRow("pkg-1", "demo", "演示医院", "路由描述",
                HospitalPackageRow.Status.PENDING, 1, "{}", "manual",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                scope, tenantId, tenantId == null ? null : "租户一",
                orgId, orgId == null ? null : "机构一", "seeder", null);
    }

    @Test
    void platformAdminMaySubmitAnyScope() {
        assertThat(PackageSubmissionAuthority.maySubmit(PLATFORM_ADMIN,
                draft(ScopeType.GLOBAL, null, null))).isTrue();
        assertThat(PackageSubmissionAuthority.maySubmit(PLATFORM_ADMIN,
                draft(ScopeType.TENANT, "tenant-9", null))).isTrue();
    }

    @Test
    void tenantAdminMaySubmitOwnTenantScopeOnly() {
        assertThat(PackageSubmissionAuthority.maySubmit(TENANT_ADMIN,
                draft(ScopeType.TENANT, "tenant-1", null))).isTrue();
        assertThat(PackageSubmissionAuthority.maySubmit(TENANT_ADMIN,
                draft(ScopeType.GLOBAL, null, null))).isFalse();
        assertThat(PackageSubmissionAuthority.maySubmit(TENANT_ADMIN,
                draft(ScopeType.TENANT, "tenant-2", null))).isFalse();
    }

    @Test
    void orgScopedDraftRequiresManageableOrg() {
        assertThat(PackageSubmissionAuthority.maySubmit(ORG_ADMIN,
                draft(ScopeType.ORG, "tenant-1", "org-1"))).isTrue();
        assertThat(PackageSubmissionAuthority.maySubmit(ORG_ADMIN,
                draft(ScopeType.ORG, "tenant-1", "org-2"))).isFalse();
        assertThat(PackageSubmissionAuthority.maySubmit(TENANT_ADMIN,
                draft(ScopeType.ORG, "tenant-1", "org-1"))).isFalse();
    }

    @Test
    void regularUsersMayNeverSubmit() {
        assertThat(PackageSubmissionAuthority.maySubmit(USER,
                draft(ScopeType.GLOBAL, null, null))).isFalse();
        assertThat(PackageSubmissionAuthority.maySubmit(USER,
                draft(ScopeType.TENANT, "tenant-1", null))).isFalse();
    }

    @Test
    void nullInputsAreRejectedFailClosed() {
        assertThat(PackageSubmissionAuthority.maySubmit(null, draft(ScopeType.GLOBAL, null, null))).isFalse();
        assertThat(PackageSubmissionAuthority.maySubmit(PLATFORM_ADMIN, null)).isFalse();
        assertThat(PackageSubmissionAuthority.maySubmit(PLATFORM_ADMIN, draft(null, null, null))).isFalse();
    }

    @Test
    void onlyPlatformAdminMayApprove() {
        assertThat(PackageSubmissionAuthority.mayApprove(PLATFORM_ADMIN)).isTrue();
        assertThat(PackageSubmissionAuthority.mayApprove(TENANT_ADMIN)).isFalse();
        assertThat(PackageSubmissionAuthority.mayApprove(USER)).isFalse();
        assertThat(PackageSubmissionAuthority.mayApprove(null)).isFalse();
    }
}
