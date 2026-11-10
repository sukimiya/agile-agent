package io.github.sukimiya.agileagent.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties("oauth2")
public record OAuth2Properties(
        String authorizationUri,
        String tokenUri,
        String userInfoUri,
        String revocationUri,
        String clientId,
        String clientSecret,
        String redirectUri,
        String scope,
        String userTypeClaim,
        String frontendRedirectUri,
        OAuth2ClaimProperties claims,
        Roles roles,
        ClaimDefaults claimDefaults,
        AdminGrant adminGrant,
        Duration defaultAccessTokenTtl,
        String userAgent) {

    static final Duration DEFAULT_ACCESS_TOKEN_TTL = Duration.ofHours(1);
    static final String DEFAULT_USER_AGENT = "agile-agent";

    @ConstructorBinding
    public OAuth2Properties {
        claims = claims == null ? new OAuth2ClaimProperties() : claims;
        roles = roles == null ? new Roles() : roles;
        claimDefaults = claimDefaults == null ? new ClaimDefaults() : claimDefaults;
        adminGrant = adminGrant == null ? new AdminGrant() : adminGrant;
        if (defaultAccessTokenTtl == null || defaultAccessTokenTtl.isNegative() || defaultAccessTokenTtl.isZero()) {
            defaultAccessTokenTtl = DEFAULT_ACCESS_TOKEN_TTL;
        }
        userAgent = userAgent == null || userAgent.isBlank() ? DEFAULT_USER_AGENT : userAgent;
    }

    /** Provider endpoints and client settings only; identity mapping uses its defaults. */
    public OAuth2Properties(
            String authorizationUri,
            String tokenUri,
            String userInfoUri,
            String revocationUri,
            String clientId,
            String clientSecret,
            String redirectUri,
            String scope,
            String userTypeClaim,
            String frontendRedirectUri) {
        this(authorizationUri, tokenUri, userInfoUri, revocationUri, clientId, clientSecret, redirectUri,
                scope, userTypeClaim, frontendRedirectUri, null, null);
    }

    /** Provider settings plus claim pointers and admin role names; no claim defaults or admin grant. */
    public OAuth2Properties(
            String authorizationUri,
            String tokenUri,
            String userInfoUri,
            String revocationUri,
            String clientId,
            String clientSecret,
            String redirectUri,
            String scope,
            String userTypeClaim,
            String frontendRedirectUri,
            OAuth2ClaimProperties claims,
            Roles roles) {
        this(authorizationUri, tokenUri, userInfoUri, revocationUri, clientId, clientSecret, redirectUri,
                scope, userTypeClaim, frontendRedirectUri, claims, roles, null, null, null, null);
    }

    public record Roles(String platformAdmin, String tenantAdmin) {
        @ConstructorBinding
        public Roles {
            platformAdmin = platformAdmin == null ? "platform_admin" : platformAdmin;
            tenantAdmin = tenantAdmin == null ? "tenant_admin" : tenantAdmin;
        }

        public Roles() {
            this(null, null);
        }
    }

    /**
     * Values used when the provider's userinfo lacks a claim (for providers such as GitHub that
     * carry no tenant, role, or org data). Disabled by default so a missing required claim still
     * fails login for OIDC providers that are expected to send it.
     */
    public record ClaimDefaults(
            boolean enabled,
            String tenantId,
            String tenantName,
            String primaryOrgId,
            String primaryOrgName,
            List<String> roles,
            List<String> manageableOrgIds,
            List<String> readableOrgIds) {

        @ConstructorBinding
        public ClaimDefaults {
            tenantId = blankToNull(tenantId);
            tenantName = blankToNull(tenantName);
            primaryOrgId = blankToNull(primaryOrgId);
            primaryOrgName = blankToNull(primaryOrgName);
            roles = cleanList(roles);
            manageableOrgIds = cleanList(manageableOrgIds);
            readableOrgIds = cleanList(readableOrgIds);
        }

        public ClaimDefaults() {
            this(false, null, null, null, null, null, null, null);
        }
    }

    /**
     * Extra roles and org access granted to identities whose claim at {@code matchClaim} (a JSON
     * Pointer into userinfo) equals one of {@code matchValues}. Disabled when either is unset.
     * Prefer an immutable provider id (GitHub {@code /id}) over a renameable login.
     */
    public record AdminGrant(
            String matchClaim,
            List<String> matchValues,
            List<String> roles,
            List<String> manageableOrgIds,
            List<String> readableOrgIds) {

        @ConstructorBinding
        public AdminGrant {
            matchClaim = blankToNull(matchClaim);
            matchValues = cleanList(matchValues);
            roles = cleanList(roles);
            manageableOrgIds = cleanList(manageableOrgIds);
            readableOrgIds = cleanList(readableOrgIds);
        }

        public AdminGrant() {
            this(null, null, null, null, null);
        }

        public boolean enabled() {
            return matchClaim != null && !matchValues.isEmpty();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> cleanList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
