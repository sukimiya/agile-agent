package io.github.sukimiya.agileagent.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.ForbiddenException;
import org.junit.jupiter.api.Test;

class AuthorizationServiceTest {

    private final AuthorizationService authorization = new AuthorizationService();

    @Test
    void tenantlessPlatformAdminCannotManageOrReadOrganizationResources() {
        var platformAdmin = identity(null, true, false, Set.of(), Set.of());
        var blankTenantPlatformAdmin = identity("  ", true, false, Set.of(), Set.of());

        authorization.requirePlatformAdmin(platformAdmin);
        assertThat(authorization.canManageOrg(platformAdmin, "org-outside-tenant")).isFalse();
        assertThat(authorization.canReadOrg(platformAdmin, "org-outside-tenant")).isFalse();
        assertThat(authorization.canManageOrg(blankTenantPlatformAdmin, "org-outside-tenant")).isFalse();
        assertThat(authorization.canReadOrg(blankTenantPlatformAdmin, "org-outside-tenant")).isFalse();
        assertThat(authorization.canManageOrg(platformAdmin, null)).isFalse();
        assertThat(authorization.canReadOrg(platformAdmin, " ")).isFalse();
        assertThatThrownBy(() -> authorization.requireTenant(platformAdmin))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void tenantBearingPlatformAdminCanManageAndReadAnyNamedOrg() {
        var platformAdmin = identity("tenant-platform", true, false, Set.of(), Set.of());

        assertThat(authorization.canManageOrg(platformAdmin, "org-outside-scopes")).isTrue();
        assertThat(authorization.canReadOrg(platformAdmin, "org-outside-scopes")).isTrue();
    }

    @Test
    void tenantAdminRequiresAndReturnsItsTrustedTenant() {
        var tenantAdmin = identity("tenant-a", false, true, Set.of("org-managed"), Set.of());

        assertThat(authorization.requireTenant(tenantAdmin)).isEqualTo("tenant-a");
        authorization.requireTenantAdmin(tenantAdmin);
        assertThat(authorization.canManageOrg(tenantAdmin, "org-managed")).isTrue();
        assertThat(authorization.canReadOrg(tenantAdmin, "org-managed")).isTrue();
    }

    @Test
    void tenantAdminWithoutTenantAndRegularUserAreDenied() {
        var tenantlessAdmin = identity(null, false, true, Set.of(), Set.of());
        var regularUser = identity("tenant-a", false, false, Set.of(), Set.of("org-readable"));

        assertThatThrownBy(() -> authorization.requireTenant(tenantlessAdmin))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> authorization.requireTenantAdmin(tenantlessAdmin))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> authorization.requireTenantAdmin(regularUser))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> authorization.requirePlatformAdmin(regularUser))
                .isInstanceOf(ForbiddenException.class);
        assertThat(authorization.canManageOrg(regularUser, "org-readable")).isFalse();
    }

    @Test
    void orgPermissionsUseOnlyTrustedManageableAndReadableMembership() {
        var user = identity("tenant-a", false, false,
                Set.of("org-managed"), Set.of("org-readable"));

        assertThat(authorization.canManageOrg(user, "org-managed")).isTrue();
        assertThat(authorization.canManageOrg(user, "org-readable")).isFalse();
        assertThat(authorization.canReadOrg(user, "org-managed")).isTrue();
        assertThat(authorization.canReadOrg(user, "org-readable")).isTrue();
        assertThat(authorization.canManageOrg(user, "org-from-another-tenant")).isFalse();
        assertThat(authorization.canReadOrg(user, "org-from-another-tenant")).isFalse();
        assertThat(authorization.canManageOrg(user, null)).isFalse();
        assertThat(authorization.canReadOrg(user, "")).isFalse();
    }

    private static IdentityContext identity(String tenantId, boolean platformAdmin, boolean tenantAdmin,
                                            Set<String> manageable, Set<String> readable) {
        return new IdentityContext("subject", "User", tenantId, null, null, null,
                Set.of(), manageable, readable, platformAdmin, tenantAdmin);
    }
}
