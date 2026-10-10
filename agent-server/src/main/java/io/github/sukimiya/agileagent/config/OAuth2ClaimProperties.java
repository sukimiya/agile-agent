package io.github.sukimiya.agileagent.config;

import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** JSON Pointers used to read identity claims from an OAuth2 userinfo response. */
public record OAuth2ClaimProperties(
        String subject,
        String displayName,
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
        displayName = displayName == null ? "/name" : displayName;
        tenantId = tenantId == null ? "/tenant_id" : tenantId;
        tenantName = tenantName == null ? "/tenant_name" : tenantName;
        primaryOrgId = primaryOrgId == null ? "/primary_org/id" : primaryOrgId;
        primaryOrgName = primaryOrgName == null ? "/primary_org/name" : primaryOrgName;
        roles = roles == null ? "/roles" : roles;
        manageableOrgIds = manageableOrgIds == null ? "/manageable_org_ids" : manageableOrgIds;
        readableOrgIds = readableOrgIds == null ? "/readable_org_ids" : readableOrgIds;
    }

    public OAuth2ClaimProperties() {
        this(null, null, null, null, null, null, null, null, null);
    }
}
