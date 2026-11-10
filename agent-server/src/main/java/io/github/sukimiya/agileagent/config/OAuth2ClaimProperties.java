package io.github.sukimiya.agileagent.config;

import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * JSON Pointers used to read identity claims from an OAuth2 userinfo response.
 *
 * <p>{@code subjectPrefix} is prepended to the provider subject (e.g. {@code github:}) so ids from
 * different providers cannot collide. {@code displayNameFallback} is read when the display-name
 * claim is missing, null, or blank (e.g. GitHub {@code /login} when {@code /name} is null).
 */
public record OAuth2ClaimProperties(
        String subject,
        String subjectPrefix,
        String displayName,
        String displayNameFallback,
        String tenantId,
        String tenantName,
        String primaryOrgId,
        String primaryOrgName,
        String roles,
        String manageableOrgIds,
        String readableOrgIds) {

    @ConstructorBinding
    public OAuth2ClaimProperties {
        subject = subject == null ? "/sub" : subject;
        subjectPrefix = subjectPrefix == null ? "" : subjectPrefix;
        displayName = displayName == null ? "/name" : displayName;
        displayNameFallback = displayNameFallback == null || displayNameFallback.isBlank() ? null : displayNameFallback;
        tenantId = tenantId == null ? "/tenant_id" : tenantId;
        tenantName = tenantName == null ? "/tenant_name" : tenantName;
        primaryOrgId = primaryOrgId == null ? "/primary_org/id" : primaryOrgId;
        primaryOrgName = primaryOrgName == null ? "/primary_org/name" : primaryOrgName;
        roles = roles == null ? "/roles" : roles;
        manageableOrgIds = manageableOrgIds == null ? "/manageable_org_ids" : manageableOrgIds;
        readableOrgIds = readableOrgIds == null ? "/readable_org_ids" : readableOrgIds;
    }

    /** Pointers only, without a subject prefix or display-name fallback. */
    public OAuth2ClaimProperties(String subject, String displayName, String tenantId, String tenantName,
                                 String primaryOrgId, String primaryOrgName, String roles,
                                 String manageableOrgIds, String readableOrgIds) {
        this(subject, null, displayName, null, tenantId, tenantName, primaryOrgId, primaryOrgName, roles,
                manageableOrgIds, readableOrgIds);
    }

    public OAuth2ClaimProperties() {
        this(null, null, null, null, null, null, null, null, null, null, null);
    }
}
