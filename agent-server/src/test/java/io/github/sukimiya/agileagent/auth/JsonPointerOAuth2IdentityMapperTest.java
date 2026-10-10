package io.github.sukimiya.agileagent.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.config.OAuth2ClaimProperties;
import io.github.sukimiya.agileagent.config.OAuth2Properties;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import org.junit.jupiter.api.Test;

class JsonPointerOAuth2IdentityMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void mapsDefaultUserInfoClaimsAndConfiguredAdminRoles() throws Exception {
        JsonNode userInfo = JSON.readTree("""
                {
                  "sub": "user-7",
                  "name": "Lin",
                  "tenant_id": "tenant-1",
                  "tenant_name": "Tenant One",
                  "primary_org": {"id": "org-primary", "name": "Primary Org"},
                  "roles": ["platform_admin", "tenant_admin", "reader"],
                  "manageable_org_ids": ["org-a", "org-b"],
                  "readable_org_ids": ["org-b", "org-c"]
                }
                """);

        IdentityContext identity = new JsonPointerOAuth2IdentityMapper(properties()).map(userInfo);

        assertThat(identity.subject()).isEqualTo("user-7");
        assertThat(identity.displayName()).isEqualTo("Lin");
        assertThat(identity.tenantId()).isEqualTo("tenant-1");
        assertThat(identity.tenantName()).isEqualTo("Tenant One");
        assertThat(identity.primaryOrgId()).isEqualTo("org-primary");
        assertThat(identity.primaryOrgName()).isEqualTo("Primary Org");
        assertThat(identity.roles()).containsExactlyInAnyOrder("platform_admin", "tenant_admin", "reader");
        assertThat(identity.manageableOrgIds()).containsExactlyInAnyOrder("org-a", "org-b");
        assertThat(identity.readableOrgIds()).containsExactlyInAnyOrder("org-b", "org-c");
        assertThat(identity.platformAdmin()).isTrue();
        assertThat(identity.tenantAdmin()).isTrue();
    }

    @Test
    void mapsCustomNestedPointers() throws Exception {
        var props = properties(new OAuth2ClaimProperties(
                "/account/id", "/profile/fullName", "/organization/tenant/id", "/organization/tenant/label",
                "/organization/primary/id", "/organization/primary/label", "/authorization/roles",
                "/authorization/manageable", "/authorization/readable"));
        JsonNode userInfo = JSON.readTree("""
                {
                  "account": {"id": "custom-user"},
                  "profile": {"fullName": "Custom Name"},
                  "organization": {
                    "tenant": {"id": "custom-tenant", "label": "Custom Tenant"},
                    "primary": {"id": "custom-org", "label": "Custom Org"}
                  },
                  "authorization": {
                    "roles": ["ops"], "manageable": ["org-x"], "readable": ["org-y"]
                  }
                }
                """);

        IdentityContext identity = new JsonPointerOAuth2IdentityMapper(props).map(userInfo);

        assertThat(identity.subject()).isEqualTo("custom-user");
        assertThat(identity.displayName()).isEqualTo("Custom Name");
        assertThat(identity.tenantId()).isEqualTo("custom-tenant");
        assertThat(identity.tenantName()).isEqualTo("Custom Tenant");
        assertThat(identity.primaryOrgId()).isEqualTo("custom-org");
        assertThat(identity.primaryOrgName()).isEqualTo("Custom Org");
        assertThat(identity.roles()).containsExactly("ops");
        assertThat(identity.manageableOrgIds()).containsExactly("org-x");
        assertThat(identity.readableOrgIds()).containsExactly("org-y");
    }

    @Test
    void rejectsMissingRequiredSubjectWithStableSafeException() throws Exception {
        JsonNode userInfo = JSON.readTree("""
                {"name":"Lin","roles":[],"manageable_org_ids":[],"readable_org_ids":[]}
                """);

        assertThatThrownBy(() -> new JsonPointerOAuth2IdentityMapper(properties()).map(userInfo))
                .isInstanceOf(IdentityMappingException.class)
                .satisfies(exception -> {
                    assertThat(((IdentityMappingException) exception).code()).isEqualTo(IdentityMappingException.CODE);
                    assertThat(exception.getMessage())
                            .contains("IDENTITY_CLAIMS_INVALID", "subject", "/sub")
                            .doesNotContain("Lin");
                });
    }

    @Test
    void doesNotExposeAccessTokenOrUserInfoValuesInMappingException() throws Exception {
        JsonNode userInfo = JSON.readTree("""
                {"sub":"private-user","name":"Private Name","access_token":"secret-access-token",
                 "roles":"malformed-sensitive-value","manageable_org_ids":[],"readable_org_ids":[]}
                """);

        assertThatThrownBy(() -> new JsonPointerOAuth2IdentityMapper(properties()).map(userInfo))
                .isInstanceOf(IdentityMappingException.class)
                .hasMessageContaining("IDENTITY_CLAIMS_INVALID")
                .hasMessageContaining("roles")
                .satisfies(exception -> assertThat(exception.getMessage())
                        .doesNotContain("secret-access-token", "private-user", "Private Name", "malformed-sensitive-value"));
    }

    @Test
    void allowsMissingOptionalTenantAndPrimaryOrganizationClaims() throws Exception {
        JsonNode userInfo = JSON.readTree("""
                {"sub":"user-7","name":"Lin","roles":[],"manageable_org_ids":[],"readable_org_ids":[]}
                """);

        IdentityContext identity = new JsonPointerOAuth2IdentityMapper(properties()).map(userInfo);

        assertThat(identity.tenantId()).isNull();
        assertThat(identity.tenantName()).isNull();
        assertThat(identity.primaryOrgId()).isNull();
        assertThat(identity.primaryOrgName()).isNull();
        assertThat(identity.roles()).isEmpty();
        assertThat(identity.platformAdmin()).isFalse();
        assertThat(identity.tenantAdmin()).isFalse();
    }

    @Test
    void rejectsNonArrayRequiredClaimsAndArrayElementsThatAreNotStrings() throws Exception {
        for (String field : new String[] {"roles", "manageable_org_ids", "readable_org_ids"}) {
            String json = """
                    {"sub":"user-7","name":"Lin","roles":[],"manageable_org_ids":[],"readable_org_ids":[]}
                    """.replace(",\"" + field + "\":[]", "");
            assertInvalid(JSON.readTree(json), field);

            json = """
                    {"sub":"user-7","name":"Lin","roles":[],"manageable_org_ids":[],"readable_org_ids":[]}
                    """.replace("\"" + field + "\":[]", "\"" + field + "\":null");
            assertInvalid(JSON.readTree(json), field);

            json = """
                    {"sub":"user-7","name":"Lin","roles":[],"manageable_org_ids":[],"readable_org_ids":[]}
                    """.replace("\"" + field + "\":[]", "\"" + field + "\":\"unexpected\"");
            assertInvalid(JSON.readTree(json), field);

            json = """
                    {"sub":"user-7","name":"Lin","roles":[],"manageable_org_ids":[],"readable_org_ids":[]}
                    """.replace("\"" + field + "\":[]", "\"" + field + "\":[\"valid\",7]");
            assertInvalid(JSON.readTree(json), field);
        }
    }

    @Test
    void returnsImmutableSets() throws Exception {
        JsonNode userInfo = JSON.readTree("""
                {"sub":"user-7","name":"Lin","roles":["reader"],"manageable_org_ids":["org-a"],"readable_org_ids":["org-b"]}
                """);
        IdentityContext identity = new JsonPointerOAuth2IdentityMapper(properties()).map(userInfo);

        assertThatThrownBy(() -> identity.roles().add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> identity.manageableOrgIds().add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> identity.readableOrgIds().add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void derivesAdminFlagsOnlyFromConfiguredRoleNames() throws Exception {
        var props = properties(new OAuth2ClaimProperties());
        props = new OAuth2Properties(props.authorizationUri(), props.tokenUri(), props.userInfoUri(),
                props.revocationUri(), props.clientId(), props.clientSecret(), props.redirectUri(), props.scope(),
                props.userTypeClaim(), props.frontendRedirectUri(), props.claims(),
                new OAuth2Properties.Roles("root", "org_owner"));
        JsonNode userInfo = JSON.readTree("""
                {"sub":"user-7","name":"Lin","roles":["root","org_owner"],
                 "manageable_org_ids":[],"readable_org_ids":[]}
                """);

        IdentityContext identity = new JsonPointerOAuth2IdentityMapper(props).map(userInfo);

        assertThat(identity.platformAdmin()).isTrue();
        assertThat(identity.tenantAdmin()).isTrue();

        JsonNode defaultRoleNamesOnly = JSON.readTree("""
                {"sub":"user-7","name":"Lin","roles":["platform_admin","tenant_admin"],
                 "manageable_org_ids":[],"readable_org_ids":[]}
                """);
        IdentityContext nonAdmin = new JsonPointerOAuth2IdentityMapper(props).map(defaultRoleNamesOnly);
        assertThat(nonAdmin.platformAdmin()).isFalse();
        assertThat(nonAdmin.tenantAdmin()).isFalse();
    }

    private static void assertInvalid(JsonNode userInfo, String field) {
        assertThatThrownBy(() -> new JsonPointerOAuth2IdentityMapper(properties()).map(userInfo))
                .isInstanceOf(IdentityMappingException.class)
                .hasMessageContaining("IDENTITY_CLAIMS_INVALID")
                .hasMessageContaining(field)
                .satisfies(exception -> assertThat(exception.getMessage())
                        .doesNotContain("user-7", "unexpected"));
    }

    private static OAuth2Properties properties() {
        return properties(new OAuth2ClaimProperties());
    }

    private static OAuth2Properties properties(OAuth2ClaimProperties claims) {
        return new OAuth2Properties(null, null, null, null, null, null, null, null, null, null,
                claims, new OAuth2Properties.Roles("platform_admin", "tenant_admin"));
    }
}
