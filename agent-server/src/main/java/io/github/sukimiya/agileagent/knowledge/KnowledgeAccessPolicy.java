package io.github.sukimiya.agileagent.knowledge;

import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.ScopeType;
import io.github.sukimiya.agileagent.knowledge.KnowledgeDocumentRepository.KnowledgeDocumentRow.Status;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

/** The single source of knowledge scope derivation and access decisions. */
@Component
public class KnowledgeAccessPolicy {

    public record UploadScope(ScopeType scopeType, Status initialStatus, String tenantId, String tenantName,
                              String orgId, String orgName, boolean autoApprove) {}

    public static class UploadIdentityException extends IllegalArgumentException {
        public UploadIdentityException() {
            super("tenant and organization identity are required for this upload");
        }
    }

    public UploadScope deriveUploadScope(IdentityContext identity) {
        if (!authenticated(identity)) {
            throw new UploadIdentityException();
        }
        if (identity.platformAdmin()) {
            return new UploadScope(ScopeType.GLOBAL, Status.PENDING, null, null, null, null, true);
        }
        if (identity.tenantAdmin()) {
            if (!hasText(identity.tenantId())) {
                throw new UploadIdentityException();
            }
            return new UploadScope(ScopeType.TENANT, Status.PENDING, identity.tenantId(),
                    displaySnapshot(identity.tenantName()), null, null, true);
        }
        if (!hasText(identity.tenantId()) || !hasText(identity.primaryOrgId())) {
            throw new UploadIdentityException();
        }
        return new UploadScope(ScopeType.ORG, Status.PENDING, identity.tenantId(),
                displaySnapshot(identity.tenantName()), identity.primaryOrgId(),
                displaySnapshot(identity.primaryOrgName()), false);
    }

    public boolean canList(IdentityContext identity, KnowledgeDocumentRow row) {
        return canManageVisibility(identity, row);
    }

    public boolean canDownload(IdentityContext identity, KnowledgeDocumentRow row) {
        return canManageVisibility(identity, row);
    }

    public boolean canApprove(IdentityContext identity, KnowledgeDocumentRow row) {
        if (!authenticated(identity) || row == null) {
            return false;
        }
        if (identity.platformAdmin()) {
            return true;
        }
        if (!identity.tenantAdmin() || !sameTenant(identity.tenantId(), row.tenantId())) {
            return false;
        }
        return row.scopeType() == ScopeType.TENANT
                || row.scopeType() == ScopeType.ORG && hasText(row.orgId())
                        && identity.manageableOrgIds().contains(row.orgId());
    }

    public boolean canConsume(IdentityContext identity, KnowledgeDocumentRow row) {
        if (!authenticated(identity) || row == null || row.status() != Status.APPROVED) {
            return false;
        }
        return switch (row.scopeType()) {
            case GLOBAL -> true;
            case TENANT -> sameTenant(identity.tenantId(), row.tenantId());
            case ORG -> sameTenant(identity.tenantId(), row.tenantId())
                    && hasText(row.orgId()) && identity.readableOrgIds().contains(row.orgId());
        };
    }

    private boolean canManageVisibility(IdentityContext identity, KnowledgeDocumentRow row) {
        if (!authenticated(identity) || row == null) {
            return false;
        }
        if (identity.platformAdmin()) {
            return true;
        }
        if (identity.tenantAdmin()) {
            if (!sameTenant(identity.tenantId(), row.tenantId())) {
                return false;
            }
            return row.scopeType() == ScopeType.TENANT
                    || row.scopeType() == ScopeType.ORG && hasText(row.orgId())
                            && identity.manageableOrgIds().contains(row.orgId());
        }
        return sameTenant(identity.tenantId(), row.tenantId())
                && identity.subject().equals(row.uploaderUserId());
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

    private static String displaySnapshot(String value) {
        return hasText(value) ? value : null;
    }
}
