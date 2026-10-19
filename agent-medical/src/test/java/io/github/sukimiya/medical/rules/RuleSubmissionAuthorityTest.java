package io.github.sukimiya.medical.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

class RuleSubmissionAuthorityTest {

    private static final IdentityContext ADMIN = new IdentityContext("admin-1", "管理员一",
            null, null, null, null, Set.of("PLATFORM_ADMIN"), Set.of(), Set.of(), true, false);
    private static final IdentityContext TENANT_ADMIN = new IdentityContext("tenant-admin-1",
            "租户管理员", "tenant-1", "租户一", null, null, Set.of("TENANT_ADMIN"),
            Set.of(), Set.of(), false, true);
    private static final IdentityContext OTHER_TENANT_ADMIN = new IdentityContext(
            "tenant-admin-2", "他租户管理员", "tenant-2", "租户二", null, null,
            Set.of("TENANT_ADMIN"), Set.of(), Set.of(), false, true);
    private static final IdentityContext USER = new IdentityContext("user-1", "用户一",
            "tenant-1", "租户一", null, null, Set.of("USER"), Set.of(), Set.of(), false, false);

    private static MedicalRuleRow rule(MedicalRuleRow.ScopeType scope, String tenantId) {
        return new MedicalRuleRow("r1", "code-1", "EMERGENCY", scope, tenantId,
                tenantId == null ? null : "租户", null, null,
                scope == MedicalRuleRow.ScopeType.HOSPITAL ? "h1" : null,
                "{}", "manual", LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                MedicalRuleRow.Status.PENDING, "admin", null);
    }

    @Test
    void globalRulesArePlatformAdminOnly() {
        var global = rule(MedicalRuleRow.ScopeType.GLOBAL, null);

        assertThat(RuleSubmissionAuthority.maySubmit(ADMIN, global)).isTrue();
        assertThat(RuleSubmissionAuthority.maySubmit(TENANT_ADMIN, global)).isFalse();
        assertThat(RuleSubmissionAuthority.mayApprove(ADMIN, global)).isTrue();
        assertThat(RuleSubmissionAuthority.mayApprove(TENANT_ADMIN, global)).isFalse();
    }

    @Test
    void tenantAndHospitalRulesAllowOwnTenantAdmin() {
        var tenant = rule(MedicalRuleRow.ScopeType.TENANT, "tenant-1");
        var hospital = rule(MedicalRuleRow.ScopeType.HOSPITAL, "tenant-1");

        assertThat(RuleSubmissionAuthority.maySubmit(TENANT_ADMIN, tenant)).isTrue();
        assertThat(RuleSubmissionAuthority.maySubmit(TENANT_ADMIN, hospital)).isTrue();
        assertThat(RuleSubmissionAuthority.mayApprove(TENANT_ADMIN, hospital)).isTrue();
        assertThat(RuleSubmissionAuthority.maySubmit(OTHER_TENANT_ADMIN, tenant)).isFalse();
        assertThat(RuleSubmissionAuthority.mayApprove(OTHER_TENANT_ADMIN, tenant)).isFalse();
        assertThat(RuleSubmissionAuthority.maySubmit(ADMIN, tenant)).isTrue();
    }

    @Test
    void nullsAndPlainUsersFailClosed() {
        var tenant = rule(MedicalRuleRow.ScopeType.TENANT, "tenant-1");

        assertThat(RuleSubmissionAuthority.maySubmit(null, tenant)).isFalse();
        assertThat(RuleSubmissionAuthority.maySubmit(USER, null)).isFalse();
        assertThat(RuleSubmissionAuthority.maySubmit(USER, tenant)).isFalse();
        assertThat(RuleSubmissionAuthority.mayApprove(USER, tenant)).isFalse();
        assertThat(RuleSubmissionAuthority.mayApprove(null, tenant)).isFalse();
    }
}
