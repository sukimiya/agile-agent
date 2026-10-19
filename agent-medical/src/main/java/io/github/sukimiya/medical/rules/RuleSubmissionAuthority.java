package io.github.sukimiya.medical.rules;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

/**
 * Rule submission/approval authority (Plan 4 spec §6): GLOBAL scope is platform-admin
 * only; TENANT and HOSPITAL scope also allow a tenant admin acting inside their own
 * tenant. Unlike capability packages (whose approval is platform-only), rule approval
 * reuses the same scope check — the spec assigns rule governance to whoever owns the
 * scope. Nulls fail closed.
 */
public final class RuleSubmissionAuthority {

    private RuleSubmissionAuthority() {
    }

    public static boolean maySubmit(IdentityContext identity, MedicalRuleRow draft) {
        return inScope(identity, draft);
    }

    /** Approval authority is scope-based: GLOBAL platform-only, TENANT/HOSPITAL own-tenant admins. */
    public static boolean mayApprove(IdentityContext identity, MedicalRuleRow row) {
        return inScope(identity, row);
    }

    private static boolean inScope(IdentityContext identity, MedicalRuleRow row) {
        if (identity == null || row == null || row.scopeType() == null) {
            return false;
        }
        if (identity.platformAdmin()) {
            return true;
        }
        if (!identity.tenantAdmin()) {
            return false;
        }
        return switch (row.scopeType()) {
            case GLOBAL -> false;
            case TENANT, HOSPITAL -> sameTenant(identity.tenantId(), row.tenantId());
        };
    }

    private static boolean sameTenant(String identityTenantId, String rowTenantId) {
        return identityTenantId != null && !identityTenantId.isBlank()
                && identityTenantId.equals(rowTenantId);
    }
}
