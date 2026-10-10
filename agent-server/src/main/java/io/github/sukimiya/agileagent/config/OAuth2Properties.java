package io.github.sukimiya.agileagent.config;

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
        Roles roles) {

    @ConstructorBinding
    public OAuth2Properties {
        claims = claims == null ? new OAuth2ClaimProperties() : claims;
        roles = roles == null ? new Roles() : roles;
    }

    /** Keeps existing programmatic clients source-compatible while adding claim configuration. */
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
                scope, userTypeClaim, frontendRedirectUri, new OAuth2ClaimProperties(), new Roles());
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
}
