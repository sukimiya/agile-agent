package io.github.sukimiya.agileagent.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.sukimiya.agileagent.mockoauth.MockOAuth2Application;
import io.github.sukimiya.agileagent.LocalRedisTestServer;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;

/** Exercises the browser-facing authorization flow against two real local Spring Boot applications. */
class MockOAuth2EndToEndTest {

    private static final String CLIENT_ID = "agile-agent-local";
    private static final String CLIENT_SECRET = "mock-oauth2-e2e-only-secret";
    private static final String FRONTEND_REDIRECT = "http://localhost:5173/login/callback";
    private static final String SUBJECT = "tenant-a-user";
    private static final int TEST_REDIS_DATABASE = 15;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void completesLoginRefreshAndLogoutWithoutLeakingProviderCredentials() throws Exception {
        int redisPort = LocalRedisTestServer.port();
        int mockPort = availablePort();
        int serverPort = availablePort();
        String h2Database = "oauth2_e2e_" + UUID.randomUUID().toString().replace("-", "");
        String jdbcUrl = h2JdbcUrl(h2Database);
        LettuceConnectionFactory redisFactory = null;
        StringRedisTemplate redis = null;

        try {
            var redisConfiguration = LocalRedisTestServer.configuration(TEST_REDIS_DATABASE);
            redisFactory = new LettuceConnectionFactory(redisConfiguration);
            redisFactory.afterPropertiesSet();
            redis = new StringRedisTemplate(redisFactory);
            redis.afterPropertiesSet();
            flushTestDatabase(redis);

            String callbackUri = "http://127.0.0.1:" + serverPort + "/api/auth/oauth2/callback";
            String mockBaseUri = "http://127.0.0.1:" + mockPort;
            try (ConfigurableApplicationContext mockApplication = startMockApplication(mockPort, callbackUri);
                 ConfigurableApplicationContext serverApplication = startServerApplication(
                         serverPort, mockBaseUri, callbackUri, redisPort, h2Database)) {
                assertR2dbcSharesFlywayDatabase(serverApplication);
                WebClient http = WebClient.builder().build();
                String serverBaseUri = "http://127.0.0.1:" + port(serverApplication);
                mockBaseUri = "http://127.0.0.1:" + port(mockApplication);

                HttpResult authorize = get(http, serverBaseUri + "/api/auth/oauth2/authorize");
                assertThat(authorize.status()).isEqualTo(302);
                String authorizeLocation = location(authorize);
                URI providerAuthorizeUri = URI.create(authorizeLocation);
                String state = query(providerAuthorizeUri, "state");
                String stateCookie = cookiePair(authorize.headers().getFirst(HttpHeaders.SET_COOKIE));
                String codeVerifier = redis.opsForValue().get("oauth2state:" + state);
                assertTrue(state != null && !state.isBlank(), "authorize response omitted OAuth2 state");
                assertTrue(codeVerifier != null && !codeVerifier.isBlank(), "state did not bind a PKCE verifier");

                HttpResult chooser = get(http, authorizeLocation);
                assertThat(chooser.status()).isEqualTo(200);
                assertThat(chooser.body()).contains(SUBJECT, "Tenant A User");

                LinkedMultiValueMap<String, String> choice = new LinkedMultiValueMap<>();
                choice.add("response_type", query(providerAuthorizeUri, "response_type"));
                choice.add("client_id", query(providerAuthorizeUri, "client_id"));
                choice.add("redirect_uri", query(providerAuthorizeUri, "redirect_uri"));
                choice.add("state", query(providerAuthorizeUri, "state"));
                choice.add("code_challenge", query(providerAuthorizeUri, "code_challenge"));
                choice.add("code_challenge_method", query(providerAuthorizeUri, "code_challenge_method"));
                choice.add("subject", SUBJECT);
                HttpResult providerAuthorize = postForm(http, mockBaseUri + "/oauth2/authorize", choice, null);
                assertThat(providerAuthorize.status()).isEqualTo(302);
                URI providerCallbackUri = URI.create(location(providerAuthorize));
                String code = query(providerCallbackUri, "code");
                assertTrue(code != null && !code.isBlank(), "mock provider omitted authorization code");

                String mismatchCallback = callbackUri(providerCallbackUri, code, state,
                        "https://attacker.example/steal", "https://attacker.example/next");
                HttpResult mismatchedState = getWithCookie(http, mismatchCallback,
                        "oauth2_state_binding=not-the-issued-state");
                assertThat(mismatchedState.status()).isEqualTo(302);
                assertThat(location(mismatchedState)).isEqualTo(FRONTEND_REDIRECT + "#error=auth_failed");
                assertTrue(redis.keys("authsession:*").isEmpty(),
                        "mismatched state binding created an application session");
                assertTrue(codeVerifier.equals(redis.opsForValue().get("oauth2state:" + state)),
                        "mismatched state binding consumed the PKCE verifier");

                String validCallback = callbackUri(providerCallbackUri, code, state,
                        "https://attacker.example/steal", "https://attacker.example/next");
                HttpResult callback = getWithCookie(http, validCallback, stateCookie);
                assertThat(callback.status()).isEqualTo(302);
                String callbackLocation = location(callback);
                assertTrue(callbackLocation.startsWith(FRONTEND_REDIRECT + "#token="),
                        "callback did not use the configured frontend redirect");
                URI frontendLocation = URI.create(callbackLocation);
                String fragment = frontendLocation.getRawFragment();
                assertTrue(fragment != null && fragment.startsWith("token="),
                        "frontend redirect omitted the application token");
                String appToken = fragment.substring("token=".length());

                String storedSession = redis.opsForValue().get("authsession:" + appToken);
                assertTrue(storedSession != null, "OAuth2 callback did not create an application session");
                JsonNode session = json.readTree(storedSession);
                String providerAccessToken = session.path("accessToken").asText();
                String providerRefreshToken = session.path("refreshToken").asText();
                assertTrue(!providerAccessToken.isBlank(), "session omitted provider access token");
                assertTrue(!providerRefreshToken.isBlank(), "session omitted provider refresh token");

                HttpResult firstMe = getWithBearer(http, serverBaseUri + "/api/auth/me", appToken);
                assertThat(firstMe.status()).isEqualTo(200);
                assertSeedIdentity(json.readTree(firstMe.body()));

                poisonCachedIdentity(redis, appToken, session);
                HttpResult refreshedMe = getWithBearer(http, serverBaseUri + "/api/auth/me", appToken);
                assertThat(refreshedMe.status()).isEqualTo(200);
                JsonNode refreshedIdentity = json.readTree(refreshedMe.body());
                assertSeedIdentity(refreshedIdentity);
                assertFalse(refreshedIdentity.path("platformAdmin").asBoolean(),
                        "identity refresh retained forged platform privilege");
                assertFalse(refreshedIdentity.path("tenantAdmin").asBoolean(),
                        "identity refresh retained forged tenant privilege");

                String refreshedSession = redis.opsForValue().get("authsession:" + appToken);
                assertTrue(refreshedSession != null, "OAuth2 /me refresh removed the application session");
                JsonNode rotatedSession = json.readTree(refreshedSession);
                String currentAccessToken = rotatedSession.path("accessToken").asText();
                String currentRefreshToken = rotatedSession.path("refreshToken").asText();
                assertFalse(providerRefreshToken.equals(currentRefreshToken),
                        "provider refresh token did not rotate during /me refresh");
                assertFalse(providerAccessToken.equals(currentAccessToken),
                        "provider access token did not rotate during /me refresh");

                List<String> publicArtifacts = List.of(authorizeLocation, chooser.body(),
                        location(providerAuthorize), location(mismatchedState), callbackLocation,
                        firstMe.body(), refreshedMe.body());
                String clientSecret = serverApplication.getEnvironment().getProperty("oauth2.client-secret", "");
                for (String artifact : publicArtifacts) {
                    assertNoProviderCredentials(artifact, List.of(providerAccessToken, currentAccessToken),
                            List.of(providerRefreshToken, currentRefreshToken),
                            clientSecret, codeVerifier);
                }

                HttpResult logout = postWithBearer(http, serverBaseUri + "/api/auth/logout", appToken);
                assertThat(logout.status()).isEqualTo(204);
                HttpResult afterLogout = getWithBearer(http, serverBaseUri + "/api/auth/me", appToken);
                assertThat(afterLogout.status()).isEqualTo(401);

                LinkedMultiValueMap<String, String> revokedRefreshAttempt = new LinkedMultiValueMap<>();
                revokedRefreshAttempt.add("grant_type", "refresh_token");
                revokedRefreshAttempt.add("refresh_token", currentRefreshToken);
                revokedRefreshAttempt.add("client_id", CLIENT_ID);
                revokedRefreshAttempt.add("client_secret", CLIENT_SECRET);
                HttpResult providerRefresh = postForm(http, mockBaseUri + "/oauth2/token",
                        revokedRefreshAttempt, null);
                assertThat(providerRefresh.status()).isEqualTo(400);
                assertThat(json.readTree(providerRefresh.body()).path("error").asText()).isEqualTo("invalid_grant");
            }
        } finally {
            try {
                shutdownH2Database(jdbcUrl);
            } finally {
                try {
                    if (redis != null) {
                        flushTestDatabase(redis);
                    }
                } finally {
                    if (redisFactory != null) redisFactory.destroy();
                }
            }
        }
    }

    private ConfigurableApplicationContext startMockApplication(int mockPort, String callbackUri) {
        Map<String, Object> properties = Map.of(
                "mock.oauth2.enabled", "true",
                "server.address", "127.0.0.1",
                "server.port", mockPort,
                "mock.oauth2.client-id", CLIENT_ID,
                "mock.oauth2.client-secret", CLIENT_SECRET,
                "mock.oauth2.redirect-uris", callbackUri);
        return new SpringApplicationBuilder(MockOAuth2Application.class)
                .initializers(context -> context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("oauth2-e2e-mock", properties)))
                .run();
    }

    private ConfigurableApplicationContext startServerApplication(int serverPort, String mockBaseUri,
                                                                   String callbackUri, int redisPort,
                                                                   String h2Database) {
        String jdbcUrl = h2JdbcUrl(h2Database);
        Map<String, Object> properties = Map.ofEntries(
                Map.entry("server.address", "127.0.0.1"),
                Map.entry("server.port", serverPort),
                Map.entry("spring.data.redis.host", "127.0.0.1"),
                Map.entry("spring.data.redis.port", redisPort),
                Map.entry("spring.data.redis.database", TEST_REDIS_DATABASE),
                Map.entry("spring.datasource.url", jdbcUrl),
                Map.entry("spring.datasource.username", "sa"),
                Map.entry("spring.datasource.password", ""),
                Map.entry("spring.flyway.url", jdbcUrl),
                Map.entry("spring.flyway.user", "sa"),
                Map.entry("spring.flyway.password", ""),
                Map.entry("task6.e2e.h2.database", h2Database),
                Map.entry("oauth2.authorization-uri", mockBaseUri + "/oauth2/authorize"),
                Map.entry("oauth2.token-uri", mockBaseUri + "/oauth2/token"),
                Map.entry("oauth2.user-info-uri", mockBaseUri + "/oauth2/userinfo"),
                Map.entry("oauth2.revocation-uri", mockBaseUri + "/oauth2/revoke"),
                Map.entry("oauth2.redirect-uri", callbackUri),
                Map.entry("oauth2.frontend-redirect-uri", FRONTEND_REDIRECT),
                Map.entry("oauth2.client-secret", CLIENT_SECRET));
        return new SpringApplicationBuilder(ServerTestApplication.class, TestDatabaseConfiguration.class)
                .profiles("local-mock")
                .initializers(context -> context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("oauth2-e2e-server", properties)))
                .run();
    }

    private void assertSeedIdentity(JsonNode identity) {
        assertThat(identity.path("subject").asText()).isEqualTo(SUBJECT);
        assertThat(identity.path("displayName").asText()).isEqualTo("Tenant A User");
        assertThat(identity.path("tenantId").asText()).isEqualTo("tenant-a");
        assertThat(identity.path("tenantName").asText()).isEqualTo("Tenant A");
        assertThat(identity.path("primaryOrgId").asText()).isEqualTo("tenant-a-team-1");
        assertThat(identity.path("primaryOrgName").asText()).isEqualTo("Tenant A Team 1");
        assertThat(textValues(identity.path("roles"))).containsExactly("user");
        assertThat(textValues(identity.path("manageableOrgIds"))).containsExactly("tenant-a-team-1");
        assertThat(textValues(identity.path("readableOrgIds")))
                .containsExactlyInAnyOrder("tenant-a-team-1", "tenant-a-team-2");
    }

    private static void assertR2dbcSharesFlywayDatabase(ConfigurableApplicationContext serverApplication) {
        String historyTable = "l7" + "_skill_flyway_schema_history";
        Long migratedVersionCount = DatabaseClient.create(serverApplication.getBean(ConnectionFactory.class))
                .sql("SELECT COUNT(*) FROM \"" + historyTable + "\"")
                .map((row, metadata) -> row.get(0, Long.class))
                .one()
                .block(Duration.ofSeconds(10));
        assertThat(migratedVersionCount).isEqualTo(14L);
    }

    private static String h2JdbcUrl(String database) {
        return "jdbc:h2:mem:" + database + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    }

    private static void shutdownH2Database(String jdbcUrl) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }

    private void poisonCachedIdentity(StringRedisTemplate redis, String appToken, JsonNode originalSession)
            throws IOException {
        ObjectNode cached = (ObjectNode) originalSession.deepCopy();
        cached.put("displayName", "Forged Cached Name");
        cached.put("expiresAt", Instant.now().minusSeconds(1).toString());
        cached.put("identityFetchedAt", Instant.now().minus(Duration.ofMinutes(6)).toString());
        ObjectNode identity = (ObjectNode) cached.path("identity");
        identity.put("displayName", "Forged Cached Name");
        identity.put("tenantId", "forged-tenant");
        identity.put("tenantName", "Forged Tenant");
        identity.put("primaryOrgId", "forged-org");
        identity.put("primaryOrgName", "Forged Org");
        identity.set("roles", strings("platform_admin", "tenant_admin"));
        identity.set("manageableOrgIds", strings("platform-root", "tenant-a-root", "tenant-a-team-1"));
        identity.set("readableOrgIds", strings("platform-root", "tenant-a-root", "tenant-a-team-1"));
        identity.put("platformAdmin", true);
        identity.put("tenantAdmin", true);

        Long remainingTtl = redis.getExpire("authsession:" + appToken, TimeUnit.SECONDS);
        assertTrue(remainingTtl != null && remainingTtl > 0, "application session lost its Redis expiry");
        redis.opsForValue().set("authsession:" + appToken, json.writeValueAsString(cached),
                Duration.ofSeconds(remainingTtl));
    }

    private static ArrayNode strings(String... values) {
        ArrayNode array = new ObjectMapper().createArrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static List<String> textValues(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static void assertNoProviderCredentials(String artifact, List<String> accessTokens,
                                                     List<String> refreshTokens, String clientSecret,
                                                     String codeVerifier) {
        for (String accessToken : accessTokens) {
            assertFalse(artifact.contains(accessToken), "public artifact exposed provider access token");
        }
        for (String refreshToken : refreshTokens) {
            assertFalse(artifact.contains(refreshToken), "public artifact exposed provider refresh token");
        }
        assertFalse(artifact.contains("client_secret"), "public artifact exposed client secret field");
        assertFalse(artifact.contains("code_verifier"), "public artifact exposed PKCE verifier field");
        if (clientSecret != null && !clientSecret.isBlank()) {
            assertFalse(artifact.contains(clientSecret), "public artifact exposed client secret");
        }
        assertFalse(artifact.contains(codeVerifier), "public artifact exposed PKCE verifier");
    }

    private static String callbackUri(URI providerCallback, String code, String state,
                                      String redirectUri, String next) {
        return UriComponentsBuilder.fromUriString(providerCallback.getScheme() + "://"
                        + providerCallback.getRawAuthority() + providerCallback.getRawPath())
                .queryParam("code", code)
                .queryParam("state", state)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("next", next)
                .build().encode().toUriString();
    }

    private static String cookiePair(String setCookie) {
        assertNotNull(setCookie, "authorize response omitted the state-binding cookie");
        String cookie = setCookie.split(";", 2)[0];
        assertTrue(cookie.startsWith("oauth2_state_binding="),
                "authorize response returned an unexpected state-binding cookie");
        return cookie;
    }

    private static String location(HttpResult response) {
        String location = response.headers().getFirst(HttpHeaders.LOCATION);
        assertNotNull(location, "HTTP redirect omitted Location");
        return location;
    }

    private static String query(URI uri, String name) {
        return UriComponentsBuilder.fromUri(uri).build(true).getQueryParams().getFirst(name);
    }

    private static HttpResult get(WebClient http, String uri) {
        return http.get().uri(uri).exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("")
                        .map(body -> new HttpResult(response.statusCode().value(), response.headers().asHttpHeaders(), body)))
                .block(Duration.ofSeconds(10));
    }

    private static HttpResult getWithCookie(WebClient http, String uri, String cookie) {
        return http.get().uri(uri).header(HttpHeaders.COOKIE, cookie)
                .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("")
                        .map(body -> new HttpResult(response.statusCode().value(), response.headers().asHttpHeaders(), body)))
                .block(Duration.ofSeconds(10));
    }

    private static HttpResult getWithBearer(WebClient http, String uri, String token) {
        return http.get().uri(uri).headers(headers -> headers.setBearerAuth(token))
                .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("")
                        .map(body -> new HttpResult(response.statusCode().value(), response.headers().asHttpHeaders(), body)))
                .block(Duration.ofSeconds(10));
    }

    private static HttpResult postWithBearer(WebClient http, String uri, String token) {
        return http.post().uri(uri).headers(headers -> headers.setBearerAuth(token))
                .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("")
                        .map(body -> new HttpResult(response.statusCode().value(), response.headers().asHttpHeaders(), body)))
                .block(Duration.ofSeconds(10));
    }

    private static HttpResult postForm(WebClient http, String uri,
                                       LinkedMultiValueMap<String, String> form, String cookie) {
        var request = http.post().uri(uri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form));
        if (cookie != null) {
            request = request.header(HttpHeaders.COOKIE, cookie);
        }
        return request.exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("")
                        .map(body -> new HttpResult(response.statusCode().value(), response.headers().asHttpHeaders(), body)))
                .block(Duration.ofSeconds(10));
    }

    private static int port(ConfigurableApplicationContext context) {
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private static void flushTestDatabase(StringRedisTemplate redis) {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private record HttpResult(int status, HttpHeaders headers, String body) {
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableScheduling
    @ConfigurationPropertiesScan(basePackages = "io.github.sukimiya.agileagent")
    @ComponentScan(basePackages = "io.github.sukimiya.agileagent", excludeFilters = {
            @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = "io\\.github\\.sukimiya\\.agileagent\\.mockoauth\\..*"),
            @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = "io\\.github\\.sukimiya\\.agileagent\\..*Test\\$.*"),
            @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = io.github.sukimiya.agileagent.AgentApplication.class)
    })
    static class ServerTestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestDatabaseConfiguration {
        @Bean
        @Primary
        ConnectionFactory testConnectionFactory(@Value("${task6.e2e.h2.database}") String database) {
            return ConnectionFactories.get(ConnectionFactoryOptions.builder()
                    .option(ConnectionFactoryOptions.DRIVER, "h2")
                    .option(ConnectionFactoryOptions.PROTOCOL, "mem")
                    .option(ConnectionFactoryOptions.DATABASE, database)
                    .option(ConnectionFactoryOptions.USER, "sa")
                    .option(ConnectionFactoryOptions.PASSWORD, "")
                    .option(Option.valueOf("options"), "MODE=MySQL;DB_CLOSE_DELAY=-1")
                    .build());
        }
    }
}
