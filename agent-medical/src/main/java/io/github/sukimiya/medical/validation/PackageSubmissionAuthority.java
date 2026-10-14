package io.github.sukimiya.medical.validation;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.hospital.HospitalPackageRow;

/**
 * Submission/approval authority predicates (spec §14 resolution 2): platform admins act on
 * everything; tenant admins act inside their own tenant — TENANT drafts directly, ORG drafts
 * only for orgs they manage; GLOBAL drafts are platform-only; approving (turning a green
 * six-gate report into an APPROVED row) is platform-admin only. Nulls fail closed.
 */
public final class PackageSubmissionAuthority {

    private PackageSubmissionAuthority() {
    }

    public static boolean maySubmit(IdentityContext identity, HospitalPackageRow draft) {
        if (identity == null || draft == null || draft.scopeType() == null) {
            return false;
        }
        if (identity.platformAdmin()) {
            return true;
        }
        if (!identity.tenantAdmin()) {
            return false;
        }
        return switch (draft.scopeType()) {
            case GLOBAL -> false;
            case TENANT -> sameTenant(identity.tenantId(), draft.tenantId());
            case ORG -> sameTenant(identity.tenantId(), draft.tenantId())
                    && draft.orgId() != null && identity.manageableOrgIds().contains(draft.orgId());
        };
    }

    public static boolean mayApprove(IdentityContext identity) {
        return identity != null && identity.platformAdmin();
    }

    private static boolean sameTenant(String identityTenantId, String draftTenantId) {
        return identityTenantId != null && !identityTenantId.isBlank()
                && identityTenantId.equals(draftTenantId);
    }
}
