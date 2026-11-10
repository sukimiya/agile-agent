package io.github.sukimiya.agileagent.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class OAuth2PropertiesTest {

    @Test
    void bindsAllFieldsFromPrefixOauth2() {
        var source = new MapConfigurationPropertySource(java.util.Map.of(
                "oauth2.authorization-uri", "https://idp.example.com/oauth2/authorize",
                "oauth2.token-uri", "https://idp.example.com/oauth2/token",
                "oauth2.user-info-uri", "https://idp.example.com/oauth2/userinfo",
                "oauth2.revocation-uri", "https://idp.example.com/oauth2/revoke",
                "oauth2.client-id", "agile-agent",
                "oauth2.client-secret", "s3cr3t",
                "oauth2.redirect-uri", "http://localhost:8088/api/auth/oauth2/callback",
                "oauth2.scope", "openid profile",
                "oauth2.user-type-claim", "userType",
                "oauth2.frontend-redirect-uri", "http://localhost:5173/login/callback"));

        var props = new Binder(source).bind("oauth2", OAuth2Properties.class).get();

        assertThat(props.authorizationUri()).isEqualTo("https://idp.example.com/oauth2/authorize");
        assertThat(props.tokenUri()).isEqualTo("https://idp.example.com/oauth2/token");
        assertThat(props.userInfoUri()).isEqualTo("https://idp.example.com/oauth2/userinfo");
        assertThat(props.revocationUri()).isEqualTo("https://idp.example.com/oauth2/revoke");
        assertThat(props.clientId()).isEqualTo("agile-agent");
        assertThat(props.clientSecret()).isEqualTo("s3cr3t");
        assertThat(props.redirectUri()).isEqualTo("http://localhost:8088/api/auth/oauth2/callback");
        assertThat(props.scope()).isEqualTo("openid profile");
        assertThat(props.userTypeClaim()).isEqualTo("userType");
        assertThat(props.frontendRedirectUri()).isEqualTo("http://localhost:5173/login/callback");
        assertThat(props.claims()).isEqualTo(new OAuth2ClaimProperties());
        assertThat(props.roles()).isEqualTo(new OAuth2Properties.Roles());
    }

    @Test
    void bindsCustomClaimPointersAndAdminRoleNames() {
        var source = new MapConfigurationPropertySource(java.util.Map.ofEntries(
                java.util.Map.entry("oauth2.claims.subject", "/account/id"),
                java.util.Map.entry("oauth2.claims.display-name", "/profile/fullName"),
                java.util.Map.entry("oauth2.claims.tenant-id", "/org/tenant/id"),
                java.util.Map.entry("oauth2.claims.tenant-name", "/org/tenant/name"),
                java.util.Map.entry("oauth2.claims.primary-org-id", "/org/primary/id"),
                java.util.Map.entry("oauth2.claims.primary-org-name", "/org/primary/name"),
                java.util.Map.entry("oauth2.claims.roles", "/access/roles"),
                java.util.Map.entry("oauth2.claims.manageable-org-ids", "/access/manageable"),
                java.util.Map.entry("oauth2.claims.readable-org-ids", "/access/readable"),
                java.util.Map.entry("oauth2.roles.platform-admin", "root_operator"),
                java.util.Map.entry("oauth2.roles.tenant-admin", "tenant_owner")));

        var props = new Binder(source).bind("oauth2", OAuth2Properties.class).get();

        assertThat(props.claims()).isEqualTo(new OAuth2ClaimProperties(
                "/account/id", "/profile/fullName", "/org/tenant/id", "/org/tenant/name",
                "/org/primary/id", "/org/primary/name", "/access/roles", "/access/manageable", "/access/readable"));
        assertThat(props.roles()).isEqualTo(new OAuth2Properties.Roles("root_operator", "tenant_owner"));
    }

    @Test
    void bindsGitHubCompatibilitySettingsFromCommaSeparatedValues() {
        var source = new MapConfigurationPropertySource(java.util.Map.ofEntries(
                java.util.Map.entry("oauth2.default-access-token-ttl", "8h"),
                java.util.Map.entry("oauth2.user-agent", "agile-agent-test"),
                java.util.Map.entry("oauth2.claims.subject", "/id"),
                java.util.Map.entry("oauth2.claims.subject-prefix", "github:"),
                java.util.Map.entry("oauth2.claims.display-name-fallback", "/login"),
                java.util.Map.entry("oauth2.claim-defaults.enabled", "true"),
                java.util.Map.entry("oauth2.claim-defaults.tenant-id", "users.example.test"),
                java.util.Map.entry("oauth2.claim-defaults.roles", "user"),
                java.util.Map.entry("oauth2.claim-defaults.manageable-org-ids", ""),
                java.util.Map.entry("oauth2.claim-defaults.readable-org-ids", "users.example.test"),
                java.util.Map.entry("oauth2.admin-grant.match-claim", "/id"),
                java.util.Map.entry("oauth2.admin-grant.match-values", "1, 2"),
                java.util.Map.entry("oauth2.admin-grant.roles", "platform_admin,tenant_admin")));

        var props = new Binder(source).bind("oauth2", OAuth2Properties.class).get();

        assertThat(props.defaultAccessTokenTtl()).isEqualTo(java.time.Duration.ofHours(8));
        assertThat(props.userAgent()).isEqualTo("agile-agent-test");
        assertThat(props.claims().subjectPrefix()).isEqualTo("github:");
        assertThat(props.claims().displayNameFallback()).isEqualTo("/login");
        assertThat(props.claimDefaults().enabled()).isTrue();
        assertThat(props.claimDefaults().tenantId()).isEqualTo("users.example.test");
        assertThat(props.claimDefaults().roles()).containsExactly("user");
        assertThat(props.claimDefaults().manageableOrgIds()).isEmpty();
        assertThat(props.claimDefaults().readableOrgIds()).containsExactly("users.example.test");
        assertThat(props.adminGrant().enabled()).isTrue();
        assertThat(props.adminGrant().matchValues()).containsExactly("1", "2");
        assertThat(props.adminGrant().roles()).containsExactly("platform_admin", "tenant_admin");
    }

    @Test
    void blankCompatibilitySettingsLeaveDefaultsAndAdminGrantDisabled() {
        var source = new MapConfigurationPropertySource(java.util.Map.ofEntries(
                java.util.Map.entry("oauth2.default-access-token-ttl", "1h"),
                java.util.Map.entry("oauth2.user-agent", "agile-agent"),
                java.util.Map.entry("oauth2.claims.subject-prefix", ""),
                java.util.Map.entry("oauth2.claims.display-name-fallback", ""),
                java.util.Map.entry("oauth2.claim-defaults.enabled", "false"),
                java.util.Map.entry("oauth2.claim-defaults.tenant-id", ""),
                java.util.Map.entry("oauth2.claim-defaults.roles", ""),
                java.util.Map.entry("oauth2.admin-grant.match-claim", ""),
                java.util.Map.entry("oauth2.admin-grant.match-values", "")));

        var props = new Binder(source).bind("oauth2", OAuth2Properties.class).get();

        assertThat(props.claims()).isEqualTo(new OAuth2ClaimProperties());
        assertThat(props.claimDefaults()).isEqualTo(new OAuth2Properties.ClaimDefaults());
        assertThat(props.adminGrant().enabled()).isFalse();
    }
}
