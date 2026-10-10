package io.github.sukimiya.agileagent.mockoauth;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@Import(OAuth2ControllerTest.TestClockConfiguration.class)
class OAuth2ControllerTest {
    private static final String CLIENT_ID = "local-agent";
    private static final String CLIENT_SECRET = "local-secret";
    private static final String REDIRECT = "http://localhost:8088/api/auth/oauth2/callback";
    private static final String STATE = "state-123";
    private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Autowired
    WebTestClient client;

    @Autowired
    TokenStore tokens;

    @Autowired
    Clock mockOAuth2Clock;

    @Autowired
    ObjectMapper objectMapper;

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry properties) {
        properties.add("mock.oauth2.client-id", () -> CLIENT_ID);
        properties.add("mock.oauth2.client-secret", () -> CLIENT_SECRET);
        properties.add("mock.oauth2.redirect-uris", () -> REDIRECT);
    }

    @TestConfiguration
    static class TestClockConfiguration {
        @Bean
        @Primary
        Clock testMockOAuth2Clock() {
            return new MutableClock(Instant.now());
        }
    }

    @Test
    void authorizeRendersEscapedChooserAndPostReturnsOneUseCodeToExactRedirect() {
        String page = client.get().uri(authorizeUri(REDIRECT, STATE, CHALLENGE, "S256"))
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
        assertTrue(page.contains("Platform Admin"));
        assertTrue(page.contains("Tenant A Admin"));
        assertTrue(page.contains("Tenant A User"));
        assertTrue(page.contains("Tenant B Admin"));
        assertTrue(page.contains("Tenant B User"));
        assertFalse(page.contains(CLIENT_SECRET));

        String callback = authorizePost(REDIRECT, STATE, CHALLENGE, "S256", "platform-admin");
        assertTrue(callback.startsWith(REDIRECT + "?code="));
        assertTrue(callback.contains("&state=" + STATE));
        String code = queryValue(callback, "code");

        String access = exchangeCode(code, VERIFIER, CLIENT_ID, CLIENT_SECRET);
        assertFalse(access.isBlank());
        tokenForm("grant_type", "authorization_code", "code", code, "redirect_uri", REDIRECT,
                "client_id", CLIENT_ID, "client_secret", CLIENT_SECRET, "code_verifier", VERIFIER)
                .expectStatus().isBadRequest().expectBody(String.class).value(body -> {
                    assertTrue(body.contains("invalid_grant"));
                    assertFalse(body.contains(code));
                    assertFalse(body.contains(VERIFIER));
                    assertFalse(body.contains(access));
                });
    }

    @Test
    void authorizeRejectsRedirectUriPrefixHostSuffixAndNormalizationVariants() {
        for (String candidate : new String[]{
                REDIRECT + ".attacker.example",
                "http://localhost:8088.evil.example/api/auth/oauth2/callback",
                "http://localhost:8088/api/auth/oauth2/callback/extra",
                "http://localhost:8088/api/auth/../auth/oauth2/callback",
                "HTTP://localhost:8088/api/auth/oauth2/callback"
        }) {
            client.get().uri(authorizeUri(candidate, STATE, CHALLENGE, "S256"))
                    .exchange().expectStatus().isBadRequest().expectHeader().doesNotExist("Location");
            client.post().uri("/oauth2/authorize").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(authorizeForm(candidate, STATE, CHALLENGE, "S256", "tenant-a-user")))
                    .exchange().expectStatus().isBadRequest().expectHeader().doesNotExist("Location");
        }
    }

    @Test
    void authorizeRevalidatesPostParametersAndRejectsMissingStatePkceOrSubject() {
        for (LinkedMultiValueMap<String, String> form : List.of(
                authorizeForm(REDIRECT, "", CHALLENGE, "S256", "tenant-a-user"),
                authorizeForm(REDIRECT, STATE, "", "S256", "tenant-a-user"),
                authorizeForm(REDIRECT, STATE, CHALLENGE, "plain", "tenant-a-user"),
                authorizeForm(REDIRECT, STATE, CHALLENGE, "S256", "unknown-subject"),
                tamperedAuthorizeForm("response_type", "token"),
                tamperedAuthorizeForm("client_id", "other-client"))) {
            client.post().uri("/oauth2/authorize").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form)).exchange()
                    .expectStatus().isBadRequest().expectHeader().doesNotExist("Location");
        }
    }

    @Test
    void authorizeRejectsInvalidGetParametersWithoutRedirecting() {
        client.get().uri(authorizeUri(REDIRECT, "", CHALLENGE, "S256"))
                .exchange().expectStatus().isBadRequest().expectHeader().doesNotExist("Location");
        client.get().uri("/oauth2/authorize?response_type=token&client_id=" + CLIENT_ID
                        + "&redirect_uri=" + REDIRECT + "&state=" + STATE
                        + "&code_challenge=" + CHALLENGE + "&code_challenge_method=S256")
                .exchange().expectStatus().isBadRequest().expectHeader().doesNotExist("Location");
    }

    @Test
    void tokenEndpointAcceptsOnlyFormAndMapsInvalidClientAndGrantErrorsSafely() {
        client.post().uri("/oauth2/token").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"grant_type\":\"authorization_code\"}")
                .exchange().expectStatus().is4xxClientError();

        tokenForm("grant_type", "unsupported", "client_id", CLIENT_ID, "client_secret", CLIENT_SECRET)
                .expectStatus().isBadRequest().expectBody().jsonPath("$.error").isEqualTo("unsupported_grant_type");
        tokenForm("grant_type", "refresh_token", "refresh_token", "no-such-token",
                "client_id", CLIENT_ID, "client_secret", CLIENT_SECRET)
                .expectStatus().isBadRequest().expectBody().jsonPath("$.error").isEqualTo("invalid_grant");
        tokenForm("grant_type", "refresh_token", "refresh_token", "secret-refresh-value",
                "client_id", "wrong-client", "client_secret", CLIENT_SECRET)
                .expectStatus().isUnauthorized().expectBody(String.class).value(body -> {
                    assertTrue(body.contains("invalid_client"));
                    assertFalse(body.contains("secret-refresh-value"));
                    assertFalse(body.contains(CLIENT_SECRET));
                });
        tokenForm("grant_type", "refresh_token", "refresh_token", "secret-refresh-value",
                "client_id", CLIENT_ID, "client_secret", "wrong-secret")
                .expectStatus().isUnauthorized().expectBody(String.class).value(body -> {
                    assertTrue(body.contains("invalid_client"));
                    assertFalse(body.contains("secret-refresh-value"));
                    assertFalse(body.contains("wrong-secret"));
                });
        tokenForm("grant_type", "refresh_token", "refresh_token", "secret-refresh-value",
                "client_id", CLIENT_ID)
                .expectStatus().isUnauthorized().expectBody().jsonPath("$.error").isEqualTo("invalid_client");
    }

    @Test
    void codeExchangeRejectsWrongVerifierAndWrongRedirectWithoutConsumingValidCode() {
        String code = queryValue(authorizePost(REDIRECT, STATE, CHALLENGE, "S256", "tenant-a-user"), "code");
        tokenForm("grant_type", "authorization_code", "code", code, "redirect_uri", REDIRECT,
                "client_id", CLIENT_ID, "client_secret", CLIENT_SECRET, "code_verifier", "wrong")
                .expectStatus().isBadRequest().expectBody().jsonPath("$.error").isEqualTo("invalid_grant");
        tokenForm("grant_type", "authorization_code", "code", code,
                "redirect_uri", REDIRECT + ".evil", "client_id", CLIENT_ID,
                "client_secret", CLIENT_SECRET, "code_verifier", VERIFIER)
                .expectStatus().isBadRequest().expectBody().jsonPath("$.error").isEqualTo("invalid_grant");
        tokenForm("grant_type", "authorization_code", "code", code, "redirect_uri", REDIRECT,
                "client_id", CLIENT_ID, "client_secret", CLIENT_SECRET)
                .expectStatus().isBadRequest().expectBody(String.class).value(body -> {
                    assertTrue(body.contains("invalid_grant"));
                    assertFalse(body.contains(code));
                    assertFalse(body.contains(CLIENT_SECRET));
                });
        exchangeCode(code, VERIFIER, CLIENT_ID, CLIENT_SECRET);
    }

    @Test
    void refreshRotationAndReplayRevokesTheWholeFamily() {
        TokenStore.TokenPair first = tokens.issue("tenant-a-user");
        TokenStore.TokenPair rotated = refresh(first.refreshToken());
        assertFalse(first.refreshToken().equals(rotated.refreshToken()));
        tokenForm("grant_type", "refresh_token", "refresh_token", first.refreshToken(),
                "client_id", CLIENT_ID, "client_secret", CLIENT_SECRET)
                .expectStatus().isBadRequest().expectBody(String.class).value(body -> {
                    assertTrue(body.contains("invalid_grant"));
                    assertFalse(body.contains(first.refreshToken()));
                    assertFalse(body.contains(rotated.refreshToken()));
                });
        client.get().uri("/oauth2/userinfo").headers(h -> h.setBearerAuth(rotated.accessToken()))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void userinfoRequiresValidBearerAndReturnsDefaultClaims() {
        client.get().uri("/oauth2/userinfo").exchange().expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody(String.class).value(body -> assertFalse(body.contains("\"error\"")));
        client.get().uri("/oauth2/userinfo").headers(h -> h.setBearerAuth("invalid-access-token"))
                .exchange().expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
        TokenStore.TokenPair pair = tokens.issue("tenant-a-user");
        client.get().uri("/oauth2/userinfo").headers(h -> h.setBearerAuth(pair.accessToken()))
                .exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.sub").isEqualTo("tenant-a-user")
                .jsonPath("$.tenant_id").isEqualTo("tenant-a")
                .jsonPath("$.primary_org.id").isEqualTo("tenant-a-team-1")
                .jsonPath("$.roles[0]").exists()
                .jsonPath("$.manageable_org_ids").isArray()
                .jsonPath("$.readable_org_ids").isArray();
    }

    @Test
    void userinfoRejectsExpiredAndRevokedAccessTokens() {
        TokenStore.TokenPair expired = tokens.issue("tenant-a-user");
        ((MutableClock) mockOAuth2Clock).advanceSeconds(3600);
        client.get().uri("/oauth2/userinfo").headers(h -> h.setBearerAuth(expired.accessToken()))
                .exchange().expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
        TokenStore.TokenPair revoked = tokens.issue("tenant-a-user");
        tokens.revoke(revoked.refreshToken());
        client.get().uri("/oauth2/userinfo").headers(h -> h.setBearerAuth(revoked.accessToken()))
                .exchange().expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
    }

    @Test
    void revokeIsIdempotentForUnknownTokenAndRevokesTokenFamily() {
        client.post().uri("/oauth2/revoke").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("token", "unknown-secret-token")
                        .with("client_id", CLIENT_ID).with("client_secret", CLIENT_SECRET))
                .exchange().expectStatus().is2xxSuccessful();
        TokenStore.TokenPair pair = tokens.issue("tenant-a-user");
        client.post().uri("/oauth2/revoke").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("token", pair.accessToken())
                        .with("client_id", CLIENT_ID).with("client_secret", CLIENT_SECRET))
                .exchange().expectStatus().is2xxSuccessful();
        client.get().uri("/oauth2/userinfo").headers(h -> h.setBearerAuth(pair.accessToken()))
                .exchange().expectStatus().isUnauthorized();
        tokenForm("grant_type", "refresh_token", "refresh_token", pair.refreshToken(),
                "client_id", CLIENT_ID, "client_secret", CLIENT_SECRET)
                .expectStatus().isBadRequest().expectBody().jsonPath("$.error").isEqualTo("invalid_grant");
    }

    private String authorizePost(String redirect, String state, String challenge, String method, String subject) {
        return client.post().uri("/oauth2/authorize").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(authorizeForm(redirect, state, challenge, method, subject)))
                .exchange().expectStatus().is3xxRedirection().expectHeader().value("Location", location -> {
                    assertTrue(location.startsWith(redirect + "?code="));
                    assertTrue(location.contains("&state=" + state));
                }).returnResult(String.class).getResponseHeaders().getLocation().toString();
    }

    private WebTestClient.ResponseSpec tokenForm(String... fields) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        for (int i = 0; i < fields.length; i += 2) {
            form.add(fields[i], fields[i + 1]);
        }
        return client.post().uri("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form)).exchange();
    }

    private String exchangeCode(String code, String verifier, String clientId, String secret) {
        var response = tokenForm("grant_type", "authorization_code", "code", code,
                "redirect_uri", REDIRECT, "client_id", clientId, "client_secret", secret,
                "code_verifier", verifier).expectStatus().isOk().expectBody()
                .jsonPath("$.token_type").isEqualTo("Bearer")
                .jsonPath("$.expires_in").isEqualTo(3600).jsonPath("$.refresh_token").exists()
                .jsonPath("$.access_token").exists().returnResult();
        assertNoStoreHeaders(response);
        byte[] body = response.getResponseBodyContent();
        try {
            return objectMapper.readTree(body).path("access_token").asText();
        } catch (Exception exception) {
            throw new AssertionError("Token response was not valid JSON", exception);
        }
    }

    private TokenStore.TokenPair refresh(String refreshToken) {
        var response = tokenForm("grant_type", "refresh_token", "refresh_token", refreshToken,
                "client_id", CLIENT_ID, "client_secret", CLIENT_SECRET).expectStatus().isOk()
                .expectBody().jsonPath("$.token_type").isEqualTo("Bearer")
                .jsonPath("$.expires_in").isEqualTo(3600).jsonPath("$.refresh_token").exists()
                .jsonPath("$.access_token").exists().returnResult();
        assertNoStoreHeaders(response);
        byte[] body = response.getResponseBodyContent();
        try {
            var payload = objectMapper.readTree(body);
            return new TokenStore.TokenPair(payload.path("access_token").asText(),
                    payload.path("refresh_token").asText(), mockOAuth2Clock.instant().plusSeconds(3600));
        } catch (Exception exception) {
            throw new AssertionError("Token response was not valid JSON", exception);
        }
    }

    private static void assertNoStoreHeaders(EntityExchangeResult<byte[]> response) {
        assertEquals("no-store", response.getResponseHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals("no-cache", response.getResponseHeaders().getFirst(HttpHeaders.PRAGMA));
    }

    private static LinkedMultiValueMap<String, String> authorizeForm(String redirect, String state,
            String challenge, String method, String subject) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("response_type", "code");
        form.add("client_id", CLIENT_ID);
        form.add("redirect_uri", redirect);
        form.add("state", state);
        form.add("code_challenge", challenge);
        form.add("code_challenge_method", method);
        form.add("subject", subject);
        return form;
    }

    private static LinkedMultiValueMap<String, String> tamperedAuthorizeForm(String name, String value) {
        LinkedMultiValueMap<String, String> form = authorizeForm(REDIRECT, STATE, CHALLENGE, "S256", "tenant-a-user");
        form.set(name, value);
        return form;
    }

    private static String authorizeUri(String redirect, String state, String challenge, String method) {
        return "/oauth2/authorize?response_type=code&client_id=" + CLIENT_ID + "&redirect_uri=" + redirect
                + "&state=" + state + "&code_challenge=" + challenge
                + "&code_challenge_method=" + method;
    }

    private static String queryValue(String uri, String name) {
        for (String pair : uri.substring(uri.indexOf('?') + 1).split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts[0].equals(name)) {
                return java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("Missing query parameter: " + name);
    }
}
