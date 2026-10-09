package io.github.sukimiya.agileagent.spi.identity;

import java.util.Set;

/** Authenticated identity and authorization scope, without OAuth2 credentials. */
public record IdentityContext(
        String subject,
        String displayName,
        String tenantId,
        String tenantName,
        String primaryOrgId,
        String primaryOrgName,
        Set<String> roles,
        Set<String> manageableOrgIds,
        Set<String> readableOrgIds,
        boolean platformAdmin,
        boolean tenantAdmin) {

    public IdentityContext {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        roles = Set.copyOf(roles);
        manageableOrgIds = Set.copyOf(manageableOrgIds);
        readableOrgIds = Set.copyOf(readableOrgIds);
    }
}
