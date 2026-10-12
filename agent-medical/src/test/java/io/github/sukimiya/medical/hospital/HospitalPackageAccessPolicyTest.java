package io.github.sukimiya.medical.hospital;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

class HospitalPackageAccessPolicyTest {

    private final HospitalPackageAccessPolicy policy = new HospitalPackageAccessPolicy();

    private static IdentityContext identity(String tenantId, Set<String> readableOrgIds) {
        return new IdentityContext("user-1", "用户一", tenantId, "租户一", "org-1", "机构一",
                Set.of("USER"), Set.of(), readableOrgIds, false, false);
    }

    private static HospitalPackageRow row(HospitalPackageRow.Status status, HospitalPackageRow.ScopeType scope,
            String tenantId, String orgId) {
        return new HospitalPackageRow("pkg-1", "demo", "演示医院", "演示医院路由描述", status, 1, "{}",
                "manual", LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 12, 31, 23, 59),
                scope, tenantId, tenantId == null ? null : "租户一", orgId, orgId == null ? null : "机构一",
                "admin", LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    void globalApprovedIsConsumableByAnyAuthenticatedIdentity() {
        var global = row(HospitalPackageRow.Status.APPROVED, HospitalPackageRow.ScopeType.GLOBAL, null, null);

        assertThat(policy.canConsume(identity("tenant-1", Set.of()), global)).isTrue();
        assertThat(policy.canConsume(identity(null, Set.of()), global)).isTrue();
    }

    @Test
    void tenantPackageRequiresSameTenant() {
        var tenantPkg = row(HospitalPackageRow.Status.APPROVED, HospitalPackageRow.ScopeType.TENANT,
                "tenant-1", null);

        assertThat(policy.canConsume(identity("tenant-1", Set.of()), tenantPkg)).isTrue();
        assertThat(policy.canConsume(identity("tenant-2", Set.of()), tenantPkg)).isFalse();
        assertThat(policy.canConsume(identity(null, Set.of()), tenantPkg)).isFalse();
    }

    @Test
    void orgPackageRequiresSameTenantAndReadableOrg() {
        var orgPkg = row(HospitalPackageRow.Status.APPROVED, HospitalPackageRow.ScopeType.ORG,
                "tenant-1", "org-9");

        assertThat(policy.canConsume(identity("tenant-1", Set.of("org-9")), orgPkg)).isTrue();
        assertThat(policy.canConsume(identity("tenant-1", Set.of("org-8")), orgPkg)).isFalse();
        assertThat(policy.canConsume(identity("tenant-2", Set.of("org-9")), orgPkg)).isFalse();
    }

    @Test
    void pendingOrRevokedIsNeverConsumable() {
        var pending = row(HospitalPackageRow.Status.PENDING, HospitalPackageRow.ScopeType.GLOBAL, null, null);
        var revoked = row(HospitalPackageRow.Status.REVOKED, HospitalPackageRow.ScopeType.GLOBAL, null, null);

        assertThat(policy.canConsume(identity("tenant-1", Set.of()), pending)).isFalse();
        assertThat(policy.canConsume(identity("tenant-1", Set.of()), revoked)).isFalse();
    }

    @Test
    void nullRowOrUnauthenticatedIdentityIsRefused() {
        var global = row(HospitalPackageRow.Status.APPROVED, HospitalPackageRow.ScopeType.GLOBAL, null, null);

        assertThat(policy.canConsume(identity("tenant-1", Set.of()), null)).isFalse();
        assertThat(policy.canConsume(null, global)).isFalse();
        // 注：blank-subject 身份在构造上不可能——agent-spi 的 IdentityContext 强制 subject 非空白；
        // 生产代码的 authenticated() 仍镜像 KnowledgeAccessPolicy 保留该防御分支，但无法从公共 API 触达。
    }
}
