package io.github.sukimiya.agileagent.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.config.OAuth2ClaimProperties;
import io.github.sukimiya.agileagent.config.OAuth2Properties;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import org.junit.jupiter.api.Test;

/** Claim defaults, subject prefix, display-name fallback, and admin grant for GitHub-style userinfo. */
class GitHubCompatibleIdentityMappingTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ORG = "users.example.test";

    private static final OAuth2ClaimProperties GITHUB_CLAIMS = new OAuth2ClaimProperties(
            "/id", "github:", "/name", "/login", null, null, null, null, null, null, null);

    private static final OAuth2Properties.ClaimDefaults DEFAULTS = new OAuth2Properties.ClaimDefaults(
            true, ORG, "Example Users", ORG, "Example Users",
            List.of("user"), List.of(), List.of(ORG));

    private static final OAuth2Properties.AdminGrant ADMIN = new OAuth2Properties.AdminGrant(
            "/id", List.of("8380581"), List.of("platform_admin", "tenant_admin"), List.of(ORG), List.of(ORG));

    @Test
    void numericSubjectGetsPrefixAndNullNameFallsBackToLogin() throws Exception {
        IdentityContext identity = mapper(DEFAULTS, new OAuth2Properties.AdminGrant()).map(github(42, "octo", null));

        assertThat(identity.subject()).isEqualTo("github:42");
        assertThat(identity.displayName()).isEqualTo("octo");
    }

    @Test
    void blankNameFallsBackAndPresentNameWins() throws Exception {
        var mapper = mapper(DEFAULTS, new OAuth2Properties.AdminGrant());

        assertThat(mapper.map(github(1, "octo", "  ")).displayName()).isEqualTo("octo");
        assertThat(mapper.map(github(1, "octo", "Octo Cat")).displayName()).isEqualTo("Octo Cat");
    }

    @Test
    void ordinaryUserGetsDefaultRoleTenantAndReadOnlyOrg() throws Exception {
        IdentityContext identity = mapper(DEFAULTS, ADMIN).map(github(42, "octo", "Octo"));

        assertThat(identity.roles()).containsExactly("user");
        assertThat(identity.tenantId()).isEqualTo(ORG);
        assertThat(identity.tenantName()).isEqualTo("Example Users");
        assertThat(identity.primaryOrgId()).isEqualTo(ORG);
        assertThat(identity.primaryOrgName()).isEqualTo("Example Users");
        assertThat(identity.manageableOrgIds()).isEmpty();
        assertThat(identity.readableOrgIds()).containsExactly(ORG);
        assertThat(identity.platformAdmin()).isFalse();
        assertThat(identity.tenantAdmin()).isFalse();
    }

    @Test
    void matchingIdentityGetsAdminGrantOnTopOfDefaults() throws Exception {
        IdentityContext identity = mapper(DEFAULTS, ADMIN).map(github(8380581, "sukimiya", null));

        assertThat(identity.subject()).isEqualTo("github:8380581");
        assertThat(identity.roles()).containsExactlyInAnyOrder("user", "platform_admin", "tenant_admin");
        assertThat(identity.manageableOrgIds()).containsExactly(ORG);
        assertThat(identity.readableOrgIds()).containsExactly(ORG);
        assertThat(identity.platformAdmin()).isTrue();
        assertThat(identity.tenantAdmin()).isTrue();
    }

    @Test
    void adminGrantCanMatchTextClaimSuchAsLogin() throws Exception {
        var byLogin = new OAuth2Properties.AdminGrant("/login", List.of("sukimiya"),
                List.of("platform_admin"), List.of(), List.of());

        assertThat(mapper(DEFAULTS, byLogin).map(github(1, "sukimiya", null)).platformAdmin()).isTrue();
        assertThat(mapper(DEFAULTS, byLogin).map(github(2, "someone", null)).platformAdmin()).isFalse();
    }

    @Test
    void providedClaimsWinOverDefaults() throws Exception {
        JsonNode userInfo = JSON.readTree("""
                {"id": 5, "login": "x", "tenant_id": "t-real", "roles": ["reader"],
                 "manageable_org_ids": ["o-m"], "readable_org_ids": ["o-r"]}
                """);

        IdentityContext identity = mapper(DEFAULTS, new OAuth2Properties.AdminGrant()).map(userInfo);

        assertThat(identity.tenantId()).isEqualTo("t-real");
        assertThat(identity.roles()).containsExactly("reader");
        assertThat(identity.manageableOrgIds()).containsExactly("o-m");
        assertThat(identity.readableOrgIds()).containsExactly("o-r");
    }

    @Test
    void withoutDefaultsMissingGitHubClaimsStillFailClosed() throws Exception {
        var mapper = mapper(new OAuth2Properties.ClaimDefaults(), new OAuth2Properties.AdminGrant());

        assertThatThrownBy(() -> mapper.map(github(42, "octo", "Octo")))
                .isInstanceOf(IdentityMappingException.class)
                .hasMessageContaining("roles");
    }

    @Test
    void disabledDefaultsAreIgnoredEvenWhenValuesAreSet() throws Exception {
        var disabled = new OAuth2Properties.ClaimDefaults(false, ORG, null, null, null,
                List.of("user"), List.of(), List.of(ORG));

        assertThatThrownBy(() -> mapper(disabled, new OAuth2Properties.AdminGrant()).map(github(42, "octo", "Octo")))
                .isInstanceOf(IdentityMappingException.class);
    }

    @Test
    void nonIntegralNumericSubjectIsRejected() throws Exception {
        assertThatThrownBy(() -> mapper(DEFAULTS, new OAuth2Properties.AdminGrant())
                .map(JSON.readTree("{\"id\": 1.5, \"login\": \"x\"}")))
                .isInstanceOf(IdentityMappingException.class)
                .hasMessageContaining("subject");
    }

    @Test
    void missingNameAndFallbackStillFails() throws Exception {
        assertThatThrownBy(() -> mapper(DEFAULTS, new OAuth2Properties.AdminGrant())
                .map(JSON.readTree("{\"id\": 1}")))
                .isInstanceOf(IdentityMappingException.class)
                .hasMessageContaining("displayName");
    }

    private static JsonNode github(long id, String login, String name) {
        var node = JSON.createObjectNode().put("id", id).put("login", login);
        if (name == null) {
            node.putNull("name");
        } else {
            node.put("name", name);
        }
        return node;
    }

    private static JsonPointerOAuth2IdentityMapper mapper(OAuth2Properties.ClaimDefaults defaults,
                                                         OAuth2Properties.AdminGrant grant) {
        return new JsonPointerOAuth2IdentityMapper(new OAuth2Properties(null, null, null, null, null, null, null,
                null, null, null, GITHUB_CLAIMS, new OAuth2Properties.Roles(), defaults, grant, null, null));
    }
}
