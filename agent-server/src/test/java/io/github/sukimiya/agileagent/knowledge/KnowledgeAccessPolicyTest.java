package io.github.sukimiya.agileagent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.ScopeType;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.Status;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

class KnowledgeAccessPolicyTest {

    private final KnowledgeAccessPolicy policy = new KnowledgeAccessPolicy();

    @Test
    void platformAdminManagesEveryScopeAndStatus() {
        var admin = identity("admin", null, null, true, false, Set.of(), Set.of());

        for (var scope : ScopeType.values()) {
            for (var status : Status.values()) {
                var row = row(scope, status, "tenant-a", "org-a", "someone");
                assertThat(policy.canList(admin, row)).isTrue();
                assertThat(policy.canDownload(admin, row)).isTrue();
                assertThat(policy.canApprove(admin, row)).isTrue();
            }
        }
    }

    @Test
    void tenantAdminManagesSameTenantTenantAndManageableOrgRowsOnly() {
        var admin = identity("tenant-admin", "tenant-a", null, false, true,
                Set.of("org-managed"), Set.of());

        assertManagement(admin, row(ScopeType.TENANT, Status.PENDING, "tenant-a", null, "uploader"), true);
        assertManagement(admin, row(ScopeType.ORG, Status.APPROVED, "tenant-a", "org-managed", "uploader"), true);
        assertManagement(admin, row(ScopeType.ORG, Status.REJECTED, "tenant-a", "org-other", "uploader"), false);
        assertManagement(admin, row(ScopeType.ORG, Status.PENDING, "tenant-b", "org-managed", "uploader"), false);
        assertManagement(admin, row(ScopeType.GLOBAL, Status.APPROVED, null, null, "uploader"), false);
        assertThat(policy.canApprove(admin, row(ScopeType.ORG, Status.PENDING, "tenant-a", "org-managed", "uploader"))).isTrue();
        assertThat(policy.canApprove(admin, row(ScopeType.ORG, Status.PENDING, "tenant-a", "org-other", "uploader"))).isFalse();
    }

    @Test
    void ordinaryUserSeesOnlyOwnRowsWithinCurrentTenant() {
        var user = identity("user-a", "tenant-a", "org-a", false, false, Set.of(), Set.of());
        var own = row(ScopeType.ORG, Status.PENDING, "tenant-a", "org-a", "user-a");

        assertThat(policy.canList(user, own)).isTrue();
        assertThat(policy.canDownload(user, own)).isTrue();
        assertThat(policy.canApprove(user, own)).isFalse();
        assertManagement(user, row(ScopeType.ORG, Status.PENDING, "tenant-a", "org-a", "user-b"), false);
        assertManagement(user, row(ScopeType.ORG, Status.PENDING, "tenant-b", "org-a", "user-a"), false);
        assertManagement(user, row(ScopeType.GLOBAL, Status.APPROVED, null, null, "user-a"), false);
    }

    @Test
    void consumptionRequiresApprovalAndUsesTenantAndReadableOrgIds() {
        var user = identity("user-a", "tenant-a", "org-readable", false, false,
                Set.of("org-manageable"), Set.of("org-readable"));

        assertThat(policy.canConsume(user, row(ScopeType.GLOBAL, Status.APPROVED, null, null, "other"))).isTrue();
        assertThat(policy.canConsume(user, row(ScopeType.GLOBAL, Status.PENDING, null, null, "other"))).isFalse();
        assertThat(policy.canConsume(user, row(ScopeType.TENANT, Status.APPROVED, "tenant-a", null, "other"))).isTrue();
        assertThat(policy.canConsume(user, row(ScopeType.TENANT, Status.APPROVED, "tenant-b", null, "other"))).isFalse();
        assertThat(policy.canConsume(user, row(ScopeType.ORG, Status.APPROVED, "tenant-a", "org-readable", "other"))).isTrue();
        assertThat(policy.canConsume(user, row(ScopeType.ORG, Status.APPROVED, "tenant-a", "org-manageable", "other"))).isFalse();
        assertThat(policy.canConsume(user, row(ScopeType.ORG, Status.APPROVED, "tenant-b", "org-readable", "other"))).isFalse();
        assertThat(policy.canConsume(user, row(ScopeType.ORG, Status.REJECTED, "tenant-a", "org-readable", "other"))).isFalse();
    }

    @Test
    void tenantlessPlatformAdminConsumesOnlyGlobalRows() {
        var admin = identity("platform", null, null, true, false, Set.of(), Set.of("org-a"));

        assertThat(policy.canConsume(admin, row(ScopeType.GLOBAL, Status.APPROVED, null, null, "other"))).isTrue();
        assertThat(policy.canConsume(admin, row(ScopeType.TENANT, Status.APPROVED, "tenant-a", null, "other"))).isFalse();
        assertThat(policy.canConsume(admin, row(ScopeType.ORG, Status.APPROVED, "tenant-a", "org-a", "other"))).isFalse();
    }

    @Test
    void sameOrgIdInAnotherTenantDoesNotGrantAccess() {
        var admin = identity("tenant-admin", "tenant-a", null, false, true,
                Set.of("shared-org-id"), Set.of("shared-org-id"));
        var rowInOtherTenant = row(ScopeType.ORG, Status.APPROVED, "tenant-b", "shared-org-id", "other");

        assertThat(policy.canList(admin, rowInOtherTenant)).isFalse();
        assertThat(policy.canDownload(admin, rowInOtherTenant)).isFalse();
        assertThat(policy.canApprove(admin, rowInOtherTenant)).isFalse();
        assertThat(policy.canConsume(admin, rowInOtherTenant)).isFalse();
    }

    @Test
    void missingTenantOrOrgNeverMatchesNonGlobalScopes() {
        var tenantless = identity("tenantless", null, null, false, true, Set.of("org-a"), Set.of("org-a"));
        var blankTenant = identity("blank", "  ", "org-a", false, true, Set.of("org-a"), Set.of("org-a"));
        var userWithoutOrg = identity("user", "tenant-a", null, false, false, Set.of(), Set.of());

        for (var identity : new IdentityContext[] {tenantless, blankTenant}) {
            assertThat(policy.canConsume(identity, row(ScopeType.TENANT, Status.APPROVED, "tenant-a", null, "other"))).isFalse();
            assertThat(policy.canConsume(identity, row(ScopeType.ORG, Status.APPROVED, "tenant-a", "org-a", "other"))).isFalse();
        }
        assertThat(policy.canConsume(userWithoutOrg,
                row(ScopeType.TENANT, Status.APPROVED, "tenant-a", null, "other"))).isTrue();
        assertThat(policy.canConsume(userWithoutOrg,
                row(ScopeType.ORG, Status.APPROVED, "tenant-a", "org-a", "other"))).isFalse();
        assertThat(policy.canConsume(identity("reader", "tenant-a", null, false, false, Set.of(), Set.of("org-a")),
                row(ScopeType.ORG, Status.APPROVED, "tenant-a", " ", "other"))).isFalse();
        assertThat(policy.canConsume(null, row(ScopeType.GLOBAL, Status.APPROVED, null, null, "other"))).isFalse();
    }

    @Test
    void derivesGlobalTenantAndOrgUploadScopesFromTrustedIdentity() {
        var platform = policy.deriveUploadScope(identity("platform", null, null, true, false, Set.of(), Set.of()));
        assertThat(platform.scopeType()).isEqualTo(ScopeType.GLOBAL);
        assertThat(platform.initialStatus()).isEqualTo(Status.PENDING);
        assertThat(platform.autoApprove()).isTrue();
        assertThat(platform.tenantId()).isNull();
        assertThat(platform.orgId()).isNull();

        var tenantAdmin = policy.deriveUploadScope(identity("tenant-admin", "tenant-a", null, false, true,
                Set.of(), Set.of()));
        assertThat(tenantAdmin.scopeType()).isEqualTo(ScopeType.TENANT);
        assertThat(tenantAdmin.tenantId()).isEqualTo("tenant-a");
        assertThat(tenantAdmin.tenantName()).isEqualTo("Tenant A");
        assertThat(tenantAdmin.orgId()).isNull();
        assertThat(tenantAdmin.autoApprove()).isTrue();

        var user = policy.deriveUploadScope(identity("user", "tenant-a", "org-a", false, false,
                Set.of(), Set.of()));
        assertThat(user.scopeType()).isEqualTo(ScopeType.ORG);
        assertThat(user.initialStatus()).isEqualTo(Status.PENDING);
        assertThat(user.tenantId()).isEqualTo("tenant-a");
        assertThat(user.tenantName()).isEqualTo("Tenant A");
        assertThat(user.orgId()).isEqualTo("org-a");
        assertThat(user.orgName()).isEqualTo("Org A");
        assertThat(user.autoApprove()).isFalse();
    }

    @Test
    void tenantAdminAndOrdinaryUserRequireTheirUploadScopeIdentityBeforeProceeding() {
        assertThatThrownBy(() -> policy.deriveUploadScope(
                identity("tenant-admin", null, null, false, true, Set.of(), Set.of())))
                .isInstanceOf(KnowledgeAccessPolicy.UploadIdentityException.class);
        assertThatThrownBy(() -> policy.deriveUploadScope(
                identity("tenant-admin", "  ", null, false, true, Set.of(), Set.of())))
                .isInstanceOf(KnowledgeAccessPolicy.UploadIdentityException.class);
        assertThatThrownBy(() -> policy.deriveUploadScope(
                identity("user", null, "org-a", false, false, Set.of(), Set.of())))
                .isInstanceOf(KnowledgeAccessPolicy.UploadIdentityException.class);
        assertThatThrownBy(() -> policy.deriveUploadScope(
                identity("user", "tenant-a", " ", false, false, Set.of(), Set.of())))
                .isInstanceOf(KnowledgeAccessPolicy.UploadIdentityException.class);
    }

    @Test
    void identityCollectionsAreImmutableWhenUsedByPolicy() {
        var manageable = new HashSet<>(Set.of("org-a"));
        var readable = new HashSet<>(Set.of("org-b"));
        var identity = identity("admin", "tenant-a", null, false, true, manageable, readable);
        manageable.add("org-b");
        readable.clear();

        assertThat(policy.canApprove(identity, row(ScopeType.ORG, Status.PENDING, "tenant-a", "org-a", "other"))).isTrue();
        assertThat(policy.canApprove(identity, row(ScopeType.ORG, Status.PENDING, "tenant-a", "org-b", "other"))).isFalse();
        assertThat(policy.canConsume(identity, row(ScopeType.ORG, Status.APPROVED, "tenant-a", "org-b", "other"))).isTrue();
    }

    private void assertManagement(IdentityContext identity, KnowledgeDocumentRow row, boolean expected) {
        assertThat(policy.canList(identity, row)).isEqualTo(expected);
        assertThat(policy.canDownload(identity, row)).isEqualTo(expected);
    }

    private static IdentityContext identity(String subject, String tenantId, String orgId,
            boolean platformAdmin, boolean tenantAdmin, Set<String> manageable, Set<String> readable) {
        String tenantName = tenantId == null || tenantId.isBlank() ? null : "Tenant A";
        String orgName = orgId == null || orgId.isBlank() ? null : "Org A";
        return new IdentityContext(subject, subject, tenantId, tenantName, orgId, orgName,
                Set.of(), manageable, readable, platformAdmin, tenantAdmin);
    }

    private static KnowledgeDocumentRow row(ScopeType scope, Status status, String tenant, String org, String uploader) {
        return new KnowledgeDocumentRow("id", "title", "file.md", "/tmp/id.md", status, "summary",
                uploader, java.time.LocalDateTime.now(), null, null, scope, tenant, "Tenant A", org, "Org A");
    }
}
