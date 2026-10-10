package io.github.sukimiya.agileagent.auth;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.ForbiddenException;
import org.springframework.stereotype.Service;

/** Central authorization decisions derived only from the authenticated identity. */
@Service
public class AuthorizationService {

    public void requirePlatformAdmin(IdentityContext identity) {
        if (identity == null || !identity.platformAdmin()) {
            throw new ForbiddenException();
        }
    }

    public String requireTenant(IdentityContext identity) {
        if (identity == null || identity.tenantId() == null || identity.tenantId().isBlank()) {
            throw new ForbiddenException();
        }
        return identity.tenantId();
    }

    public void requireTenantAdmin(IdentityContext identity) {
        requireTenant(identity);
        if (!identity.tenantAdmin()) {
            throw new ForbiddenException();
        }
    }

    public boolean canManageOrg(IdentityContext identity, String orgId) {
        if (orgId == null || orgId.isBlank()) {
            return false;
        }
        if (identity == null || !hasTenant(identity)) {
            return false;
        }
        if (identity.platformAdmin()) {
            return true;
        }
        return identity.manageableOrgIds().contains(orgId);
    }

    public boolean canReadOrg(IdentityContext identity, String orgId) {
        if (orgId == null || orgId.isBlank()) {
            return false;
        }
        if (identity == null || !hasTenant(identity)) {
            return false;
        }
        if (identity.platformAdmin()) {
            return true;
        }
        return identity.manageableOrgIds().contains(orgId) || identity.readableOrgIds().contains(orgId);
    }

    private static boolean hasTenant(IdentityContext identity) {
        return identity.tenantId() != null && !identity.tenantId().isBlank();
    }
}
