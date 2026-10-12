package io.github.sukimiya.medical.hospital;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

/**
 * Single source of hospital package scope decisions (spec §4 租户/机构 scope), mirroring the
 * knowledge module's canConsume semantics: consumable only when APPROVED and the caller's
 * identity reaches the package scope. Stateless; registered as a @Bean, never scanned.
 */
public final class HospitalPackageAccessPolicy {

    public boolean canConsume(IdentityContext identity, HospitalPackageRow row) {
        if (!authenticated(identity) || row == null || row.status() != HospitalPackageRow.Status.APPROVED) {
            return false;
        }
        return switch (row.scopeType()) {
            case GLOBAL -> true;
            case TENANT -> sameTenant(identity.tenantId(), row.tenantId());
            case ORG -> sameTenant(identity.tenantId(), row.tenantId())
                    && hasText(row.orgId()) && identity.readableOrgIds().contains(row.orgId());
        };
    }

    private static boolean authenticated(IdentityContext identity) {
        return identity != null && hasText(identity.subject());
    }

    private static boolean sameTenant(String identityTenantId, String rowTenantId) {
        return hasText(identityTenantId) && hasText(rowTenantId) && identityTenantId.equals(rowTenantId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
