# OAuth2 Login Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current username/password login (proxied to a proprietary SmartLink `/api/authorize/login` endpoint) with a standard OAuth2 Authorization Code + PKCE flow, while keeping the existing Backend-for-Frontend (BFF) session pattern unchanged from the frontend's point of view.

**Architecture:** The backend becomes an OAuth2 *client* against any config-driven, standards-compliant OAuth2/OIDC authorization server (not a specific vendor). It performs the browser redirect, the authorization-code exchange, and token refresh itself; it never hands the raw OAuth2 access/refresh tokens to the frontend. The frontend still only ever sees the existing opaque, Redis-backed app session token (`Authorization: Bearer <app-token>`), so the only frontend-visible change is *how* login is triggered (redirect to `/api/auth/oauth2/authorize` instead of `POST /api/auth/login` with a password). Because the resulting OAuth2 access token is also the credential forwarded to every downstream enterprise API call (MES/CRM/WMS/QMS/SRM), it is renamed end-to-end from `smartLinkToken` to `accessToken` and sent as a standard `Authorization: Bearer` header instead of the proprietary `x-access-token` header.

**Tech Stack:** Spring Boot 3.5 WebFlux (reactive, no Spring MVC/Servlet), Spring Data Redis Reactive (`ReactiveStringRedisTemplate`), Jackson, JUnit 5 + Reactor `StepVerifier` + AssertJ + Mockito, OkHttp `MockWebServer` for HTTP-client tests. No Spring Security is introduced — the codebase already implements auth manually via `CurrentUserArgumentResolver` + `AuthSessionStore`, and this plan follows that existing pattern rather than bolting on `spring-boot-starter-oauth2-client`.

## Global Constraints

- Java 21, Spring Boot 3.5 (`pom.xml` — do not change dependency versions).
- Reactive end-to-end: every new method returns `Mono`/`Flux`, never blocks (no `.block()` outside tests).
- No Spring Security dependency is added. Auth continues to be hand-rolled (`CurrentUserArgumentResolver`, `@CurrentUser`, `AuthSessionStore`, `UnauthorizedException` → 401 via `AuthExceptionHandler`).
- All Redis-backed stores use `ReactiveStringRedisTemplate` and are tested against the real dev Redis instance already used by `AuthSessionStoreTest` (`${TEST_REDIS_HOST}:6379`, database `1`), matching the existing test convention — no embedded/fake Redis is introduced.
- WebClient-based HTTP clients are tested with OkHttp `MockWebServer`, matching `SmartLinkClientTest`'s existing pattern.
- Controller tests use `@WebFluxTest` + `@MockBean` + `@Import({WebConfig.class, AuthExceptionHandler.class, CurrentUserArgumentResolver.class})`, matching `AuthControllerTest`'s existing pattern.
- No frontend code exists in this repository (`start.sh` expects a sibling `web/` checkout that isn't part of this repo) — this plan covers backend only. The one frontend-visible contract change (login trigger becomes a redirect, not a password POST) is called out in Task 6 but is not a task here.
- This plan intentionally does **not** touch: `ChatMemory`, `SessionService`, `KnowledgeCatalog`, `ApiCatalog`, or the `enterprise-api.services` base-URL config — none of those depend on the auth mechanism.

---

## Assumptions (state these to whoever approves this plan)

1. **Grant type:** Authorization Code with PKCE (`S256`). This is the standard choice for a browser-driven login and works whether the OAuth2 client is technically "public" (SPA-style, no secret) or "confidential" (secret configured) — `client_secret` is optional/blank-able in config either way.
2. **Provider is config-driven, not vendor-locked:** any standards-compliant OAuth2/OIDC provider (Keycloak, Auth0, Azure AD, a generic authorization server) is supported by pointing config at its `authorization_endpoint` / `token_endpoint` / `userinfo_endpoint`. No provider SDK is used.
3. **`userType` claim:** the current proprietary "user type" concept (used by `AuthorizeController`) is assumed to be exposed as a claim in the userinfo response, under a configurable claim name (`oauth2.user-type-claim`). If the real provider doesn't expose this, that endpoint's behavior becomes "userType is null" until a provider-specific mapping is added later — flagged, not solved, by this plan.
4. **Token storage stays server-side:** the OAuth2 access/refresh tokens never leave the backend; the frontend keeps using the existing opaque app-session Bearer token. This is why the plan is larger than "swap one HTTP call" — the refresh lifecycle has to live somewhere, and the natural seam is `CurrentUserArgumentResolver`, where "resolve current user for this request" already happens.
5. **`SmartLinkClient` / `SmartLinkProperties` are deleted**, not kept alongside — after this migration nothing calls the proprietary SmartLink login/userinfo endpoints. If a later requirement needs to keep both auth mechanisms side by side (e.g. gradual rollout), that's a materially different plan (dual auth providers), not an extension of this one.

---

## File Structure

**New files:**
- `src/main/java/com/jowo/agent/config/OAuth2Properties.java` — config-driven provider endpoints/client id/secret/redirect/scope.
- `src/main/java/com/jowo/agent/service/Pkce.java` — PKCE code_verifier/code_challenge generation (pure, static, no Spring wiring).
- `src/main/java/com/jowo/agent/service/OAuth2StateStore.java` — Redis-backed `state → code_verifier` store for the redirect round trip (CSRF + PKCE binding).
- `src/main/java/com/jowo/agent/service/OAuth2Client.java` — WebClient service: builds the authorize URL, exchanges the code, refreshes tokens, fetches userinfo. Replaces `SmartLinkClient`.
- Tests mirroring each of the above under `src/test/java/...` in the same packages.

**Modified files:**
- `src/main/java/com/jowo/agent/service/AuthSessionStore.java` — `AuthUser` gains `accessToken`, `refreshToken`, `expiresAt`, `userType`; add `update(...)`.
- `src/main/java/com/jowo/agent/web/CurrentUserArgumentResolver.java` — refreshes the OAuth2 token pair when near expiry before returning the resolved user.
- `src/main/java/com/jowo/agent/controller/AuthController.java` — replaces password login with `/oauth2/authorize` + `/oauth2/callback`.
- `src/main/java/com/jowo/agent/controller/AuthorizeController.java` — reads `userType` from the session instead of round-tripping to SmartLink.
- `src/main/java/com/jowo/agent/agent/DomainTask.java`, `DomainAgent.java`, `Orchestrator.java`, `src/main/java/com/jowo/agent/controller/ChatController.java` — mechanical rename `smartLinkToken` → `accessToken`.
- `src/main/java/com/jowo/agent/service/HttpEnterpriseApiClient.java` — send `Authorization: Bearer` instead of `x-access-token`.
- `src/main/resources/application.yml` — new `oauth2:` block, remove `smartlink:` block.
- All existing tests referencing `smartLinkToken`/`SmartLinkClient` (`AuthControllerTest`, `AuthorizeControllerTest`, `AuthSessionStoreTest`, `CurrentUserArgumentResolverTest`, `HttpEnterpriseApiClientTest` if it asserts the header name).

**Deleted files:**
- `src/main/java/com/jowo/agent/config/SmartLinkProperties.java`
- `src/main/java/com/jowo/agent/service/SmartLinkClient.java`
- `src/test/java/com/jowo/agent/config/SmartLinkPropertiesTest.java`
- `src/test/java/com/jowo/agent/service/SmartLinkClientTest.java`

---

### Task 1: OAuth2 configuration properties

**Files:**
- Create: `src/main/java/com/jowo/agent/config/OAuth2Properties.java`
- Test: `src/test/java/com/jowo/agent/config/OAuth2PropertiesTest.java`
- Modify: `src/main/resources/application.yml`

**Interfaces:**
- Produces: `OAuth2Properties` record with accessors `authorizationUri()`, `tokenUri()`, `userInfoUri()`, `clientId()`, `clientSecret()`, `redirectUri()`, `scope()`, `userTypeClaim()`, `frontendRedirectUri()` — every later task that needs provider config depends on these exact names.

- [ ] **Step 1: Write the failing test**

```java
package com.jowo.agent.config;

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
        assertThat(props.clientId()).isEqualTo("agile-agent");
        assertThat(props.clientSecret()).isEqualTo("s3cr3t");
        assertThat(props.redirectUri()).isEqualTo("http://localhost:8088/api/auth/oauth2/callback");
        assertThat(props.scope()).isEqualTo("openid profile");
        assertThat(props.userTypeClaim()).isEqualTo("userType");
        assertThat(props.frontendRedirectUri()).isEqualTo("http://localhost:5173/login/callback");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=OAuth2PropertiesTest test`
Expected: FAIL — compilation error, `OAuth2Properties` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.jowo.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("oauth2")
public record OAuth2Properties(
        String authorizationUri,
        String tokenUri,
        String userInfoUri,
        String clientId,
        String clientSecret,
        String redirectUri,
        String scope,
        String userTypeClaim,
        String frontendRedirectUri) {}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=OAuth2PropertiesTest test`
Expected: PASS

- [ ] **Step 5: Update application.yml**

In `src/main/resources/application.yml`, replace the `smartlink:` block with:

```yaml
oauth2:
  authorization-uri: ${OAUTH2_AUTHORIZATION_URI:http://CHANGE_ME/oauth2/authorize}
  token-uri: ${OAUTH2_TOKEN_URI:http://CHANGE_ME/oauth2/token}
  user-info-uri: ${OAUTH2_USERINFO_URI:http://CHANGE_ME/oauth2/userinfo}
  client-id: ${OAUTH2_CLIENT_ID:}
  client-secret: ${OAUTH2_CLIENT_SECRET:}
  redirect-uri: ${OAUTH2_REDIRECT_URI:http://localhost:8088/api/auth/oauth2/callback}
  scope: ${OAUTH2_SCOPE:openid profile}
  user-type-claim: ${OAUTH2_USER_TYPE_CLAIM:}
  frontend-redirect-uri: ${OAUTH2_FRONTEND_REDIRECT_URI:http://localhost:5173/login/callback}
```

Leave the actual deletion of the `smartlink:` block to Task 9 (it's still read by `SmartLinkProperties` until then).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/jowo/agent/config/OAuth2Properties.java \
        src/test/java/com/jowo/agent/config/OAuth2PropertiesTest.java \
        src/main/resources/application.yml
git commit -m "feat: add OAuth2Properties config for OAuth2 login migration"
```

---

### Task 2: PKCE helper + OAuth2StateStore

**Files:**
- Create: `src/main/java/com/jowo/agent/service/Pkce.java`
- Create: `src/main/java/com/jowo/agent/service/OAuth2StateStore.java`
- Test: `src/test/java/com/jowo/agent/service/PkceTest.java`
- Test: `src/test/java/com/jowo/agent/service/OAuth2StateStoreTest.java`

**Interfaces:**
- Consumes: `ReactiveStringRedisTemplate` (Spring-managed bean, same as `AuthSessionStore`).
- Produces: `Pkce.newCodeVerifier(): String`, `Pkce.challenge(String codeVerifier): String` (S256, base64url, no padding). `OAuth2StateStore.save(String codeVerifier): Mono<String>` (returns the generated `state`), `OAuth2StateStore.consume(String state): Mono<String>` (returns the stored `codeVerifier`, empty if unknown/expired/already consumed) — `AuthController` (Task 6) calls both.

- [ ] **Step 1: Write the failing tests**

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.MessageDigest;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class PkceTest {

    @Test
    void codeVerifierIsUrlSafeAndLongEnough() {
        String verifier = Pkce.newCodeVerifier();
        assertThat(verifier).matches("[A-Za-z0-9\\-_]+");
        assertThat(verifier.length()).isGreaterThanOrEqualTo(43);
    }

    @Test
    void twoVerifiersAreDifferent() {
        assertThat(Pkce.newCodeVerifier()).isNotEqualTo(Pkce.newCodeVerifier());
    }

    @Test
    void challengeIsBase64UrlSha256OfVerifier() throws Exception {
        String verifier = "test-verifier-1234567890-abcdefghijklmno";
        String expected = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes("UTF-8")));

        assertThat(Pkce.challenge(verifier)).isEqualTo(expected);
    }
}
```

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

class OAuth2StateStoreTest {

    private LettuceConnectionFactory connectionFactory;
    private ReactiveStringRedisTemplate redisTemplate;
    private OAuth2StateStore store;
    private String issuedState;

    @BeforeEach
    void setUp() {
        var config = new RedisStandaloneConfiguration("${TEST_REDIS_HOST}", 6379);
        config.setPassword("${TEST_REDIS_PASSWORD}");
        config.setDatabase(1);
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new ReactiveStringRedisTemplate(connectionFactory);
        store = new OAuth2StateStore(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        if (issuedState != null) redisTemplate.delete("oauth2state:" + issuedState).block();
        connectionFactory.destroy();
    }

    @Test
    void saveThenConsumeReturnsCodeVerifierOnce() {
        issuedState = store.save("verifier-abc").block();

        assertThat(store.consume(issuedState).blockOptional()).contains("verifier-abc");
        assertThat(store.consume(issuedState).blockOptional()).isEmpty();
    }

    @Test
    void consumeOnUnknownStateReturnsEmpty() {
        assertThat(store.consume("no-such-state").blockOptional()).isEmpty();
    }

    @Test
    void statesAreUniquePerCall() {
        var s1 = store.save("verifier-1").block();
        var s2 = store.save("verifier-2").block();
        issuedState = s1;
        redisTemplate.delete("oauth2state:" + s2).block();

        assertThat(s1).isNotEqualTo(s2);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=PkceTest,OAuth2StateStoreTest test`
Expected: FAIL — `Pkce` and `OAuth2StateStore` don't exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.jowo.agent.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class Pkce {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Pkce() {}

    public static String newCodeVerifier() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String challenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

```java
package com.jowo.agent.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
public class OAuth2StateStore {

    private static final String KEY_PREFIX = "oauth2state:";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReactiveStringRedisTemplate redis;

    public OAuth2StateStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<String> save(String codeVerifier) {
        String state = randomState();
        return redis.opsForValue().set(KEY_PREFIX + state, codeVerifier, TTL).thenReturn(state);
    }

    public Mono<String> consume(String state) {
        String key = KEY_PREFIX + state;
        return redis.opsForValue().get(key)
                .flatMap(verifier -> redis.delete(key).thenReturn(verifier));
    }

    private static String randomState() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=PkceTest,OAuth2StateStoreTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/service/Pkce.java \
        src/main/java/com/jowo/agent/service/OAuth2StateStore.java \
        src/test/java/com/jowo/agent/service/PkceTest.java \
        src/test/java/com/jowo/agent/service/OAuth2StateStoreTest.java
git commit -m "feat: add PKCE helper and Redis-backed OAuth2 state store"
```

---

### Task 3: OAuth2Client

**Files:**
- Create: `src/main/java/com/jowo/agent/service/OAuth2Client.java`
- Test: `src/test/java/com/jowo/agent/service/OAuth2ClientTest.java`

**Interfaces:**
- Consumes: `OAuth2Properties` (Task 1), `WebClient.Builder` (Spring-managed).
- Produces: `OAuth2Client.buildAuthorizationUrl(String state, String codeChallenge): String`; `OAuth2Client.exchangeCode(String code, String codeVerifier): Mono<OAuth2Client.TokenResult>`; `OAuth2Client.refresh(String refreshToken): Mono<OAuth2Client.TokenResult>`; `OAuth2Client.fetchUserInfo(String accessToken): Mono<OAuth2Client.UserInfo>`; nested records `TokenResult(String accessToken, String refreshToken, int expiresInSeconds)` and `UserInfo(String subject, String name, String userType)`; exceptions `OAuth2Client.OAuth2AuthException` and `OAuth2Client.OAuth2UnavailableException` — `AuthController` (Task 6) and `CurrentUserArgumentResolver` (Task 5) depend on these exact names/shapes.

- [ ] **Step 1: Write the failing test**

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.jowo.agent.config.OAuth2Properties;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import reactor.test.StepVerifier;

class OAuth2ClientTest {

    private MockWebServer server;
    private OAuth2Client client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String base = server.url("/").toString();
        var props = new OAuth2Properties(
                base + "oauth2/authorize", base + "oauth2/token", base + "oauth2/userinfo",
                "agile-agent", "s3cr3t", "http://localhost:8088/api/auth/oauth2/callback",
                "openid profile", "userType", "http://localhost:5173/login/callback");
        client = new OAuth2Client(props, WebClient.builder().baseUrl(base));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void buildAuthorizationUrlIncludesPkceAndState() {
        String url = client.buildAuthorizationUrl("state-1", "challenge-1");

        assertThat(url).startsWith(server.url("/oauth2/authorize").toString());
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("client_id=agile-agent");
        assertThat(url).contains("state=state-1");
        assertThat(url).contains("code_challenge=challenge-1");
        assertThat(url).contains("code_challenge_method=S256");
    }

    @Test
    void exchangeCodePostsAuthorizationCodeGrantAndParsesTokens() throws InterruptedException {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"at-1\",\"refresh_token\":\"rt-1\",\"expires_in\":3600}"));

        StepVerifier.create(client.exchangeCode("code-1", "verifier-1"))
                .assertNext(r -> {
                    assertThat(r.accessToken()).isEqualTo("at-1");
                    assertThat(r.refreshToken()).isEqualTo("rt-1");
                    assertThat(r.expiresInSeconds()).isEqualTo(3600);
                })
                .verifyComplete();

        var req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/oauth2/token");
        String body = req.getBody().readUtf8();
        assertThat(body).contains("grant_type=authorization_code");
        assertThat(body).contains("code=code-1");
        assertThat(body).contains("code_verifier=verifier-1");
    }

    @Test
    void exchangeCodeThrowsAuthExceptionOn4xx() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("invalid_grant"));

        StepVerifier.create(client.exchangeCode("bad-code", "verifier-1"))
                .expectError(OAuth2Client.OAuth2AuthException.class)
                .verify();
    }

    @Test
    void refreshPostsRefreshTokenGrant() throws InterruptedException {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"at-2\",\"refresh_token\":\"rt-2\",\"expires_in\":1800}"));

        StepVerifier.create(client.refresh("rt-1"))
                .assertNext(r -> assertThat(r.accessToken()).isEqualTo("at-2"))
                .verifyComplete();

        var req = server.takeRequest();
        assertThat(req.getBody().readUtf8()).contains("grant_type=refresh_token").contains("refresh_token=rt-1");
    }

    @Test
    void refreshThrowsUnavailableExceptionOn5xx() {
        server.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(client.refresh("rt-1"))
                .expectError(OAuth2Client.OAuth2UnavailableException.class)
                .verify();
    }

    @Test
    void fetchUserInfoSendsBearerHeaderAndParsesConfiguredUserTypeClaim() throws InterruptedException {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"sub\":\"u-9\",\"name\":\"张三\",\"userType\":\"admin\"}"));

        StepVerifier.create(client.fetchUserInfo("at-1"))
                .assertNext(info -> {
                    assertThat(info.subject()).isEqualTo("u-9");
                    assertThat(info.name()).isEqualTo("张三");
                    assertThat(info.userType()).isEqualTo("admin");
                })
                .verifyComplete();

        var req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/oauth2/userinfo");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer at-1");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=OAuth2ClientTest test`
Expected: FAIL — `OAuth2Client` doesn't exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.jowo.agent.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.jowo.agent.config.OAuth2Properties;

import reactor.core.publisher.Mono;

@Component
public class OAuth2Client {

    private final OAuth2Properties props;
    private final WebClient http;

    @Autowired
    public OAuth2Client(OAuth2Properties props, WebClient.Builder builder) {
        this(props, builder.build());
    }

    OAuth2Client(OAuth2Properties props, WebClient http) {
        this.props = props;
        this.http = http;
    }

    public String buildAuthorizationUrl(String state, String codeChallenge) {
        return UriComponentsBuilder.fromUriString(props.authorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", props.clientId())
                .queryParam("redirect_uri", props.redirectUri())
                .queryParam("scope", props.scope())
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build().encode().toUriString();
    }

    public Mono<TokenResult> exchangeCode(String code, String codeVerifier) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", props.redirectUri());
        form.add("client_id", props.clientId());
        form.add("code_verifier", codeVerifier);
        addClientSecretIfPresent(form);
        return postForm(props.tokenUri(), form);
    }

    public Mono<TokenResult> refresh(String refreshToken) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", props.clientId());
        addClientSecretIfPresent(form);
        return postForm(props.tokenUri(), form);
    }

    public Mono<UserInfo> fetchUserInfo(String accessToken) {
        return http.get().uri(props.userInfoUri())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                        .map(OAuth2UnavailableException::new))
                .bodyToMono(JsonNode.class)
                .map(body -> new UserInfo(
                        body.path("sub").asText(),
                        body.path("name").asText(),
                        props.userTypeClaim() == null || props.userTypeClaim().isBlank()
                                ? null : body.path(props.userTypeClaim()).asText(null)));
    }

    private void addClientSecretIfPresent(LinkedMultiValueMap<String, String> form) {
        if (props.clientSecret() != null && !props.clientSecret().isBlank()) {
            form.add("client_secret", props.clientSecret());
        }
    }

    private Mono<TokenResult> postForm(String uri, LinkedMultiValueMap<String, String> form) {
        return http.post().uri(uri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                        .map(OAuth2UnavailableException::new))
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                        .map(OAuth2AuthException::new))
                .bodyToMono(JsonNode.class)
                .map(OAuth2Client::toTokenResult);
    }

    private static TokenResult toTokenResult(JsonNode body) {
        return new TokenResult(
                body.path("access_token").asText(),
                body.path("refresh_token").asText(null),
                body.path("expires_in").asInt(3600));
    }

    public record TokenResult(String accessToken, String refreshToken, int expiresInSeconds) {}

    public record UserInfo(String subject, String name, String userType) {}

    public static class OAuth2AuthException extends RuntimeException {
        public OAuth2AuthException(String message) { super(message); }
    }

    public static class OAuth2UnavailableException extends RuntimeException {
        public OAuth2UnavailableException(String message) { super(message); }
    }
}
```

Note: `WebClient`'s form encoding requires `LinkedMultiValueMap` to be sent with `MediaType.APPLICATION_FORM_URLENCODED` — this matches how Spring serializes `MultiValueMap<String,String>` bodies by default (no extra converter config needed since `spring-boot-starter-webflux` registers `FormHttpMessageWriter` out of the box).

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=OAuth2ClientTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/service/OAuth2Client.java \
        src/test/java/com/jowo/agent/service/OAuth2ClientTest.java
git commit -m "feat: add OAuth2Client for authorization-code exchange, refresh, and userinfo"
```

---

### Task 4: AuthSessionStore — store OAuth2 tokens instead of the SmartLink token

**Files:**
- Modify: `src/main/java/com/jowo/agent/service/AuthSessionStore.java`
- Modify: `src/test/java/com/jowo/agent/service/AuthSessionStoreTest.java`

**Interfaces:**
- Produces: `AuthSessionStore.AuthUser(String userId, String name, String accessToken, String refreshToken, java.time.Instant expiresAt, String userType)`; `AuthSessionStore.create(String userId, String name, String accessToken, String refreshToken, Instant expiresAt, String userType): Mono<String>`; new `AuthSessionStore.update(String token, AuthUser refreshed): Mono<Void>` (rewrites the same Redis key with a fresh TTL) — `AuthController` (Task 6) calls `create`; `CurrentUserArgumentResolver` (Task 5) calls `update` after a token refresh.

- [ ] **Step 1: Update the failing test first**

Replace `src/test/java/com/jowo/agent/service/AuthSessionStoreTest.java` with:

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

class AuthSessionStoreTest {

    private LettuceConnectionFactory connectionFactory;
    private ReactiveStringRedisTemplate redisTemplate;
    private AuthSessionStore store;
    private String issuedToken;
    private static final Instant EXPIRES = Instant.parse("2026-08-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        var config = new RedisStandaloneConfiguration("${TEST_REDIS_HOST}", 6379);
        config.setPassword("${TEST_REDIS_PASSWORD}");
        config.setDatabase(1);
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new ReactiveStringRedisTemplate(connectionFactory);
        store = new AuthSessionStore(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        if (issuedToken != null) redisTemplate.delete("authsession:" + issuedToken).block();
        connectionFactory.destroy();
    }

    @Test
    void createThenResolveReturnsSameUser() {
        issuedToken = store.create("user-1", "周琳琳", "at-1", "rt-1", EXPIRES, "admin").block();

        var resolved = store.resolve(issuedToken).blockOptional();

        assertThat(resolved).isPresent();
        assertThat(resolved.get().userId()).isEqualTo("user-1");
        assertThat(resolved.get().name()).isEqualTo("周琳琳");
        assertThat(resolved.get().accessToken()).isEqualTo("at-1");
        assertThat(resolved.get().refreshToken()).isEqualTo("rt-1");
        assertThat(resolved.get().expiresAt()).isEqualTo(EXPIRES);
        assertThat(resolved.get().userType()).isEqualTo("admin");
    }

    @Test
    void resolveOnUnknownTokenReturnsEmpty() {
        assertThat(store.resolve("no-such-token").blockOptional()).isEmpty();
    }

    @Test
    void deleteInvalidatesToken() {
        issuedToken = store.create("user-1", "周琳琳", "at-1", "rt-1", EXPIRES, "admin").block();

        store.delete(issuedToken).block();

        assertThat(store.resolve(issuedToken).blockOptional()).isEmpty();
    }

    @Test
    void tokensAreUniquePerCall() {
        var t1 = store.create("user-1", "A", "at-1", "rt-1", EXPIRES, "admin").block();
        var t2 = store.create("user-1", "A", "at-1", "rt-1", EXPIRES, "admin").block();
        issuedToken = t1;
        redisTemplate.delete("authsession:" + t2).block();

        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void updateOverwritesStoredUserUnderSameToken() {
        issuedToken = store.create("user-1", "周琳琳", "at-1", "rt-1", EXPIRES, "admin").block();
        var refreshed = new AuthSessionStore.AuthUser("user-1", "周琳琳", "at-2", "rt-2", EXPIRES.plusSeconds(3600), "admin");

        store.update(issuedToken, refreshed).block();

        assertThat(store.resolve(issuedToken).blockOptional()).contains(refreshed);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AuthSessionStoreTest test`
Expected: FAIL — `create` has the wrong arity, `AuthUser` has no `accessToken()`/`update` doesn't exist. Compilation error.

- [ ] **Step 3: Rewrite AuthSessionStore**

```java
package com.jowo.agent.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import reactor.core.publisher.Mono;

@Component
public class AuthSessionStore {

    private static final String KEY_PREFIX = "authsession:";
    private static final Duration TTL = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    public AuthSessionStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<String> create(String userId, String name, String accessToken, String refreshToken,
                                Instant expiresAt, String userType) {
        String token = randomToken();
        return write(token, new AuthUser(userId, name, accessToken, refreshToken, expiresAt, userType))
                .thenReturn(token);
    }

    public Mono<Void> update(String token, AuthUser refreshed) {
        return write(token, refreshed);
    }

    private Mono<Void> write(String token, AuthUser user) {
        String payload;
        try {
            payload = json.writeValueAsString(user);
        } catch (Exception e) {
            return Mono.error(new IllegalStateException("Failed to serialize auth session for user " + user.userId(), e));
        }
        return redis.opsForValue().set(KEY_PREFIX + token, payload, TTL).then();
    }

    public Mono<AuthUser> resolve(String token) {
        return redis.opsForValue().get(KEY_PREFIX + token).map(raw -> {
            try {
                return json.readValue(raw, AuthUser.class);
            } catch (Exception e) {
                throw new IllegalStateException("Corrupt auth session JSON for token " + token, e);
            }
        });
    }

    public Mono<Void> delete(String token) {
        return redis.delete(KEY_PREFIX + token).then();
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record AuthUser(String userId, String name, String accessToken, String refreshToken,
                            Instant expiresAt, String userType) {}
}
```

`jackson-datatype-jsr310` is already on the classpath transitively via `spring-boot-starter-webflux` (Spring Boot's Jackson auto-configuration registers it for the `ObjectMapper` bean, but `AuthSessionStore` builds its own private `ObjectMapper`, so the module must be registered explicitly here — otherwise `Instant` fails to serialize).

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=AuthSessionStoreTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/service/AuthSessionStore.java \
        src/test/java/com/jowo/agent/service/AuthSessionStoreTest.java
git commit -m "refactor: AuthSessionStore stores OAuth2 access/refresh tokens instead of SmartLink token"
```

---

### Task 5: CurrentUserArgumentResolver — silent refresh on expiry

**Files:**
- Modify: `src/main/java/com/jowo/agent/web/CurrentUserArgumentResolver.java`
- Modify: `src/test/java/com/jowo/agent/web/CurrentUserArgumentResolverTest.java`

**Interfaces:**
- Consumes: `AuthSessionStore.resolve/update` (Task 4), `OAuth2Client.refresh` (Task 3).
- Produces: unchanged public shape (`resolveArgument` still returns `Mono<Object>` wrapping an `AuthSessionStore.AuthUser`) — no other class needs to change because of this task.

- [ ] **Step 1: Update the failing test first**

Replace `src/test/java/com/jowo/agent/web/CurrentUserArgumentResolverTest.java` with:

```java
package com.jowo.agent.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.OAuth2Client;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CurrentUserArgumentResolverTest {

    @Test
    void supportsParameterOnlyWhenAnnotationPresent() throws NoSuchMethodException {
        var resolver = new CurrentUserArgumentResolver(mock(AuthSessionStore.class), mock(OAuth2Client.class));
        var annotatedParam = new MethodParameter(
                Probe.class.getDeclaredMethod("annotated", AuthSessionStore.AuthUser.class), 0);
        var plainParam = new MethodParameter(
                Probe.class.getDeclaredMethod("plain", String.class), 0);

        org.assertj.core.api.Assertions.assertThat(resolver.supportsParameter(annotatedParam)).isTrue();
        org.assertj.core.api.Assertions.assertThat(resolver.supportsParameter(plainParam)).isFalse();
    }

    @Test
    void resolvesUserFromBearerTokenWithoutRefreshWhenTokenStillFresh() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var user = new AuthSessionStore.AuthUser("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(3600), "admin");
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(user));
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/x").header("Authorization", "Bearer tok-1").build());

        StepVerifier.create(resolver.resolveArgument(null, null, exchange))
                .expectNext(user)
                .verifyComplete();
        verifyNoInteractions(oauth2);
    }

    @Test
    void refreshesAndPersistsWhenTokenNearExpiry() {
        var sessions = mock(AuthSessionStore.class);
        var oauth2 = mock(OAuth2Client.class);
        var expiring = new AuthSessionStore.AuthUser("user-1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(10), "admin");
        when(sessions.resolve("tok-1")).thenReturn(Mono.just(expiring));
        when(oauth2.refresh("rt-1")).thenReturn(Mono.just(new OAuth2Client.TokenResult("at-2", "rt-2", 3600)));
        when(sessions.update(any(), any())).thenReturn(Mono.empty());
        var resolver = new CurrentUserArgumentResolver(sessions, oauth2);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/x").header("Authorization", "Bearer tok-1").build());

        StepVerifier.create(resolver.resolveArgument(null, null, exchange))
                .assertNext(result -> {
                    var refreshed = (AuthSessionStore.AuthUser) result;
                    org.assertj.core.api.Assertions.assertThat(refreshed.accessToken()).isEqualTo("at-2");
                    org.assertj.core.api.Assertions.assertThat(refreshed.refreshToken()).isEqualTo("rt-2");
                })
                .verifyComplete();
        verify(sessions).update(org.mockito.ArgumentMatchers.eq("tok-1"), any());
    }

    @Test
    void missingHeaderErrorsUnauthorized() {
        var resolver = new CurrentUserArgumentResolver(mock(AuthSessionStore.class), mock(OAuth2Client.class));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x").build());

        StepVerifier.create(resolver.resolveArgument(null, null, exchange))
                .expectError(UnauthorizedException.class)
                .verify();
    }

    @Test
    void unknownTokenErrorsUnauthorized() {
        var sessions = mock(AuthSessionStore.class);
        when(sessions.resolve("bad-token")).thenReturn(Mono.empty());
        var resolver = new CurrentUserArgumentResolver(sessions, mock(OAuth2Client.class));
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/x").header("Authorization", "Bearer bad-token").build());

        StepVerifier.create(resolver.resolveArgument(null, null, exchange))
                .expectError(UnauthorizedException.class)
                .verify();
    }

    private static class Probe {
        void annotated(@CurrentUser AuthSessionStore.AuthUser user) {}
        void plain(String s) {}
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=CurrentUserArgumentResolverTest test`
Expected: FAIL — constructor arity mismatch (no `OAuth2Client` parameter yet).

- [ ] **Step 3: Rewrite CurrentUserArgumentResolver**

```java
package com.jowo.agent.web;

import java.time.Instant;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;

import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.OAuth2Client;

import reactor.core.publisher.Mono;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final java.time.Duration REFRESH_SKEW = java.time.Duration.ofSeconds(30);

    private final AuthSessionStore sessions;
    private final OAuth2Client oauth2;

    public CurrentUserArgumentResolver(AuthSessionStore sessions, OAuth2Client oauth2) {
        this.sessions = sessions;
        this.oauth2 = oauth2;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class);
    }

    @Override
    public Mono<Object> resolveArgument(MethodParameter parameter, BindingContext bindingContext,
                                         ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        if (token == null) return Mono.error(new UnauthorizedException());
        return sessions.resolve(token)
                .switchIfEmpty(Mono.error(new UnauthorizedException()))
                .flatMap(user -> refreshIfNearExpiry(token, user))
                .cast(Object.class);
    }

    private Mono<AuthSessionStore.AuthUser> refreshIfNearExpiry(String token, AuthSessionStore.AuthUser user) {
        if (Instant.now().isBefore(user.expiresAt().minus(REFRESH_SKEW))) {
            return Mono.just(user);
        }
        return oauth2.refresh(user.refreshToken())
                .map(tokens -> new AuthSessionStore.AuthUser(user.userId(), user.name(),
                        tokens.accessToken(), tokens.refreshToken(),
                        Instant.now().plusSeconds(tokens.expiresInSeconds()), user.userType()))
                .flatMap(refreshed -> sessions.update(token, refreshed).thenReturn(refreshed));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=CurrentUserArgumentResolverTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/web/CurrentUserArgumentResolver.java \
        src/test/java/com/jowo/agent/web/CurrentUserArgumentResolverTest.java
git commit -m "feat: refresh OAuth2 access token on resolve when near expiry"
```

---

### Task 6: AuthController — OAuth2 authorize/callback endpoints

**Files:**
- Modify: `src/main/java/com/jowo/agent/controller/AuthController.java`
- Modify: `src/test/java/com/jowo/agent/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `OAuth2Client` (Task 3), `OAuth2StateStore` (Task 2), `AuthSessionStore` (Task 4), `OAuth2Properties` (Task 1).
- Produces: `GET /api/auth/oauth2/authorize` → `302` redirect to the provider; `GET /api/auth/oauth2/callback?code=&state=` → `302` redirect to `oauth2.frontend-redirect-uri` with `#token=<app-token>` on success or `#error=<code>` on failure. `POST /api/auth/login` (password) is removed. `GET /api/auth/me` and `POST /api/auth/logout` are unchanged.

**Frontend note (not a task in this repo):** the login button must change from a form `POST /api/auth/login` to a plain navigation (`window.location.href = '/api/auth/oauth2/authorize'`), and the app must read `token` off the `#` fragment of the URL it lands back on after the provider redirects through `/api/auth/oauth2/callback`.

- [ ] **Step 1: Rewrite the failing test first**

Replace `src/test/java/com/jowo/agent/controller/AuthControllerTest.java` with:

```java
package com.jowo.agent.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.jowo.agent.config.OAuth2Properties;
import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.OAuth2Client;
import com.jowo.agent.service.OAuth2StateStore;
import com.jowo.agent.web.AuthExceptionHandler;
import com.jowo.agent.web.CurrentUserArgumentResolver;
import com.jowo.agent.web.WebConfig;

import reactor.core.publisher.Mono;

@WebFluxTest(AuthController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, CurrentUserArgumentResolver.class})
class AuthControllerTest {

    @Autowired WebTestClient client;
    @MockBean OAuth2Client oauth2;
    @MockBean OAuth2StateStore states;
    @MockBean AuthSessionStore sessions;
    @MockBean OAuth2Properties props;

    @Test
    void authorizeRedirectsToProviderAuthorizationUrl() {
        when(states.save(org.mockito.ArgumentMatchers.anyString())).thenReturn(Mono.just("state-1"));
        when(oauth2.buildAuthorizationUrl(org.mockito.ArgumentMatchers.eq("state-1"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("https://idp.example.com/oauth2/authorize?state=state-1");

        client.get().uri("/api/auth/oauth2/authorize")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "https://idp.example.com/oauth2/authorize?state=state-1");
    }

    @Test
    void callbackExchangesCodeAndRedirectsWithAppToken() {
        when(props.frontendRedirectUri()).thenReturn("http://localhost:5173/login/callback");
        when(states.consume("state-1")).thenReturn(Mono.just("verifier-1"));
        when(oauth2.exchangeCode("code-1", "verifier-1"))
                .thenReturn(Mono.just(new OAuth2Client.TokenResult("at-1", "rt-1", 3600)));
        when(oauth2.fetchUserInfo("at-1"))
                .thenReturn(Mono.just(new OAuth2Client.UserInfo("u1", "周琳琳", "admin")));
        when(sessions.create(org.mockito.ArgumentMatchers.eq("u1"), org.mockito.ArgumentMatchers.eq("周琳琳"),
                org.mockito.ArgumentMatchers.eq("at-1"), org.mockito.ArgumentMatchers.eq("rt-1"),
                org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.eq("admin")))
                .thenReturn(Mono.just("app-tok"));

        client.get().uri("/api/auth/oauth2/callback?code=code-1&state=state-1")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "http://localhost:5173/login/callback#token=app-tok");
    }

    @Test
    void callbackRedirectsWithErrorFragmentOnAuthFailure() {
        when(props.frontendRedirectUri()).thenReturn("http://localhost:5173/login/callback");
        when(states.consume("state-1")).thenReturn(Mono.just("verifier-1"));
        when(oauth2.exchangeCode("bad-code", "verifier-1"))
                .thenReturn(Mono.error(new OAuth2Client.OAuth2AuthException("invalid_grant")));

        client.get().uri("/api/auth/oauth2/callback?code=bad-code&state=state-1")
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "http://localhost:5173/login/callback#error=auth_failed");
    }

    @Test
    void meReturnsCurrentUserForValidToken() {
        when(sessions.resolve("app-tok")).thenReturn(Mono.just(
                new AuthSessionStore.AuthUser("u1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(3600), "admin")));

        client.get().uri("/api/auth/me").header("Authorization", "Bearer app-tok")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo("u1")
                .jsonPath("$.name").isEqualTo("周琳琳");
    }

    @Test
    void meReturns401WithoutToken() {
        client.get().uri("/api/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void logoutDeletesSessionAndReturns204() {
        when(sessions.delete("app-tok")).thenReturn(Mono.empty());

        client.post().uri("/api/auth/logout").header("Authorization", "Bearer app-tok")
                .exchange()
                .expectStatus().isNoContent();

        verify(sessions).delete("app-tok");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AuthControllerTest test`
Expected: FAIL — `AuthController` still only has the password-login constructor/endpoints.

- [ ] **Step 3: Rewrite AuthController**

```java
package com.jowo.agent.controller;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jowo.agent.config.OAuth2Properties;
import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.OAuth2Client;
import com.jowo.agent.service.OAuth2StateStore;
import com.jowo.agent.service.Pkce;
import com.jowo.agent.web.CurrentUser;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
class AuthController {

    private final OAuth2Client oauth2;
    private final OAuth2StateStore states;
    private final AuthSessionStore sessions;
    private final OAuth2Properties props;

    AuthController(OAuth2Client oauth2, OAuth2StateStore states, AuthSessionStore sessions, OAuth2Properties props) {
        this.oauth2 = oauth2;
        this.states = states;
        this.sessions = sessions;
        this.props = props;
    }

    @GetMapping("/oauth2/authorize")
    Mono<ResponseEntity<Void>> authorize() {
        String codeVerifier = Pkce.newCodeVerifier();
        String codeChallenge = Pkce.challenge(codeVerifier);
        return states.save(codeVerifier)
                .map(state -> ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(oauth2.buildAuthorizationUrl(state, codeChallenge)))
                        .build());
    }

    @GetMapping("/oauth2/callback")
    Mono<ResponseEntity<Void>> callback(@RequestParam("code") String code, @RequestParam("state") String state) {
        return states.consume(state)
                .switchIfEmpty(Mono.error(new OAuth2Client.OAuth2AuthException("unknown or expired state")))
                .flatMap(codeVerifier -> oauth2.exchangeCode(code, codeVerifier))
                .flatMap(tokens -> oauth2.fetchUserInfo(tokens.accessToken())
                        .flatMap(info -> sessions.create(info.subject(), info.name(),
                                tokens.accessToken(), tokens.refreshToken(),
                                Instant.now().plusSeconds(tokens.expiresInSeconds()), info.userType())))
                .map(this::redirectWithToken)
                .onErrorResume(OAuth2Client.OAuth2AuthException.class, e -> Mono.just(redirectWithError("auth_failed")))
                .onErrorResume(OAuth2Client.OAuth2UnavailableException.class, e -> Mono.just(redirectWithError("provider_unavailable")));
    }

    private ResponseEntity<Void> redirectWithToken(String appToken) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(props.frontendRedirectUri() + "#token=" + appToken))
                .build();
    }

    private ResponseEntity<Void> redirectWithError(String errorCode) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(props.frontendRedirectUri() + "#error=" + errorCode))
                .build();
    }

    @GetMapping("/me")
    Map<String, String> me(@CurrentUser AuthSessionStore.AuthUser user) {
        return Map.of("userId", user.userId(), "name", user.name());
    }

    @PostMapping("/logout")
    Mono<ResponseEntity<Void>> logout(@RequestHeader("Authorization") String authorization) {
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        return sessions.delete(token).thenReturn(ResponseEntity.noContent().build());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=AuthControllerTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/controller/AuthController.java \
        src/test/java/com/jowo/agent/controller/AuthControllerTest.java
git commit -m "feat: replace password login with OAuth2 authorization-code redirect flow"
```

---

### Task 7: AuthorizeController — read userType from the session

**Files:**
- Modify: `src/main/java/com/jowo/agent/controller/AuthorizeController.java`
- Modify: `src/test/java/com/jowo/agent/controller/AuthorizeControllerTest.java`

**Interfaces:**
- Consumes: `AuthSessionStore.AuthUser.userType()` (Task 4) via `@CurrentUser` — no service dependency at all after this change.
- Produces: `GET /api/authorize/me` → `200` with `{"result":{"user":{"userType": <value or null>}}}`, synchronously, no downstream call.

- [ ] **Step 1: Rewrite the failing test first**

```java
package com.jowo.agent.controller;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.web.AuthExceptionHandler;
import com.jowo.agent.web.CurrentUserArgumentResolver;
import com.jowo.agent.web.WebConfig;

import reactor.core.publisher.Mono;

import static org.mockito.Mockito.when;

@WebFluxTest(AuthorizeController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, CurrentUserArgumentResolver.class})
class AuthorizeControllerTest {

    @Autowired WebTestClient client;
    @MockBean AuthSessionStore sessions;
    @MockBean com.jowo.agent.service.OAuth2Client oauth2;

    @Test
    void meReturnsUserTypeFromSession() {
        when(sessions.resolve("app-tok")).thenReturn(Mono.just(
                new AuthSessionStore.AuthUser("u1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(3600), "admin")));

        client.get().uri("/api/authorize/me").header("Authorization", "Bearer app-tok")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.result.user.userType").isEqualTo("admin");
    }

    @Test
    void meReturns401WithoutToken() {
        client.get().uri("/api/authorize/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=AuthorizeControllerTest test`
Expected: FAIL — `AuthorizeController` still requires `SmartLinkClient` and calls `fetchUserType`.

- [ ] **Step 3: Rewrite AuthorizeController**

```java
package com.jowo.agent.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.web.CurrentUser;

@RestController
@RequestMapping("/api/authorize")
@CrossOrigin(origins = "*")
class AuthorizeController {

    @GetMapping("/me")
    Map<String, Object> me(@CurrentUser AuthSessionStore.AuthUser user) {
        return Map.of("result", Map.of("user", Map.of("userType",
                user.userType() == null ? "" : user.userType())));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=AuthorizeControllerTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/controller/AuthorizeController.java \
        src/test/java/com/jowo/agent/controller/AuthorizeControllerTest.java
git commit -m "refactor: AuthorizeController reads userType from session instead of proxying to SmartLink"
```

---

### Task 8: Rename smartLinkToken → accessToken through the call chain; switch to Bearer header

**Files:**
- Modify: `src/main/java/com/jowo/agent/agent/DomainTask.java`
- Modify: `src/main/java/com/jowo/agent/agent/DomainAgent.java`
- Modify: `src/main/java/com/jowo/agent/agent/Orchestrator.java`
- Modify: `src/main/java/com/jowo/agent/controller/ChatController.java`
- Modify: `src/main/java/com/jowo/agent/service/HttpEnterpriseApiClient.java`
- Modify: any existing test in `src/test/java/com/jowo/agent/service/HttpEnterpriseApiClientTest.java` that asserts on the `x-access-token` header name.

**Interfaces:**
- No change in shape, only in naming and one header. `EnterpriseApiClient.call(String service, String method, String path, Map<String,Object> params, String accessToken)` already uses the name `accessToken` in its signature (see `EnterpriseApiClient.java:9`) — this task makes every caller's local variable/field name match, and makes the wire format match what an OAuth2 resource server actually expects.

- [ ] **Step 1: Check current header assertion in HttpEnterpriseApiClientTest**

Run: `grep -n "x-access-token\|X-Access-Token" src/test/java/com/jowo/agent/service/HttpEnterpriseApiClientTest.java`

If it asserts the literal header name `x-access-token`, update that assertion to expect `Authorization: Bearer <token>` instead, e.g. change:
```java
assertThat(req.getHeader("x-access-token")).isEqualTo("tok-1");
```
to:
```java
assertThat(req.getHeader("Authorization")).isEqualTo("Bearer tok-1");
```
This is the "failing test first" step for this task — run the suite now and confirm this specific assertion fails against the current `x-access-token` implementation before touching production code.

Run: `mvn -q -Dtest=HttpEnterpriseApiClientTest test`
Expected: FAIL on the updated header assertion.

- [ ] **Step 2: Update HttpEnterpriseApiClient's header**

In `src/main/java/com/jowo/agent/service/HttpEnterpriseApiClient.java`, in `exchange(...)`, replace:

```java
        if (accessToken != null) spec = spec.header("x-access-token", accessToken);
```

with:

```java
        if (accessToken != null) spec = spec.headers(h -> h.setBearerAuth(accessToken));
```

- [ ] **Step 3: Run test to verify it passes**

Run: `mvn -q -Dtest=HttpEnterpriseApiClientTest test`
Expected: PASS

- [ ] **Step 4: Rename smartLinkToken → accessToken in DomainTask**

In `src/main/java/com/jowo/agent/agent/DomainTask.java`, replace:

```java
public record DomainTask(String taskId, String sessionId, String roleCode,
                          String userMessage, String smartLinkToken) {}
```

with:

```java
public record DomainTask(String taskId, String sessionId, String roleCode,
                          String userMessage, String accessToken) {}
```

- [ ] **Step 5: Rename usages in DomainAgent**

In `src/main/java/com/jowo/agent/agent/DomainAgent.java`:
- Line 78: `executeTool(call, task.smartLinkToken())` → `executeTool(call, task.accessToken())`
- Line 87: `private Mono<String> executeTool(LlmClient.ToolCall call, String smartLinkToken)` → `private Mono<String> executeTool(LlmClient.ToolCall call, String accessToken)`
- Line 96: `case "callEnterpriseApi" -> callEnterpriseApi(args, smartLinkToken);` → `case "callEnterpriseApi" -> callEnterpriseApi(args, accessToken);`
- Line 110: `private Mono<String> callEnterpriseApi(JsonNode args, String smartLinkToken)` → `private Mono<String> callEnterpriseApi(JsonNode args, String accessToken)`
- Line 122: `return enterpriseApi.call(service, method, path, params, smartLinkToken)` → `return enterpriseApi.call(service, method, path, params, accessToken)`

- [ ] **Step 6: Rename usages in Orchestrator**

In `src/main/java/com/jowo/agent/agent/Orchestrator.java`:
- Line 73: `public Flux<String> chat(String ownerId, String smartLinkToken, String userMessage)` → `public Flux<String> chat(String ownerId, String accessToken, String userMessage)`
- Line 78: `answerViaDomains(ownerId, history, userMessage, roleCodes, smartLinkToken)` → `answerViaDomains(ownerId, history, userMessage, roleCodes, accessToken)`
- Line 134-135: `private Flux<String> answerViaDomains(String ownerId, List<LlmClient.Message> history, String userMessage, List<String> roleCodes, String smartLinkToken)` → same with `String accessToken`
- Line 137: `new DomainTask(UUID.randomUUID().toString(), ownerId, code, userMessage, smartLinkToken)` → `new DomainTask(UUID.randomUUID().toString(), ownerId, code, userMessage, accessToken)`

- [ ] **Step 7: Rename usages in ChatController**

In `src/main/java/com/jowo/agent/controller/ChatController.java`:
- Line 67: `orchestrator.chat(ownerId, user.smartLinkToken(), userMsg)` → `orchestrator.chat(ownerId, user.accessToken(), userMsg)`
- Line 123: `orchestrator.chat(ownerId, user.smartLinkToken(), request.message())` → `orchestrator.chat(ownerId, user.accessToken(), request.message())`

- [ ] **Step 8: Compile and run the full test suite**

Run: `mvn -q test`
Expected: PASS — this is a pure rename plus one header-scheme change, so no other test should be affected; if `DomainAgentTest`, `OrchestratorTest`, or `ChatControllerTest` construct a `DomainTask`/mock `Orchestrator.chat(...)` positionally, they'll still compile (record field renamed, not reordered/retyped) — only tests referencing `.smartLinkToken()` by name would fail to compile, and none should remain after this task.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/jowo/agent/agent/DomainTask.java \
        src/main/java/com/jowo/agent/agent/DomainAgent.java \
        src/main/java/com/jowo/agent/agent/Orchestrator.java \
        src/main/java/com/jowo/agent/controller/ChatController.java \
        src/main/java/com/jowo/agent/service/HttpEnterpriseApiClient.java \
        src/test/java/com/jowo/agent/service/HttpEnterpriseApiClientTest.java
git commit -m "refactor: rename smartLinkToken to accessToken end-to-end and send it as Authorization: Bearer"
```

---

### Task 9: Delete SmartLinkClient/SmartLinkProperties and their tests; clean application.yml

**Files:**
- Delete: `src/main/java/com/jowo/agent/service/SmartLinkClient.java`
- Delete: `src/main/java/com/jowo/agent/config/SmartLinkProperties.java`
- Delete: `src/test/java/com/jowo/agent/service/SmartLinkClientTest.java`
- Delete: `src/test/java/com/jowo/agent/config/SmartLinkPropertiesTest.java`
- Modify: `src/main/resources/application.yml`

**Interfaces:** none — by this point in the plan nothing references `SmartLinkClient` or `SmartLinkProperties` (verified by Task 6/7's rewrites removing the only two call sites).

- [ ] **Step 1: Confirm nothing references SmartLinkClient/SmartLinkProperties any more**

Run: `grep -rln "SmartLinkClient\|SmartLinkProperties" src/main src/test`
Expected: only the four files listed above under "Files" — if anything else appears, stop and investigate before deleting (this task assumes Tasks 6–8 already removed every production usage).

- [ ] **Step 2: Delete the files**

```bash
git rm src/main/java/com/jowo/agent/service/SmartLinkClient.java \
       src/main/java/com/jowo/agent/config/SmartLinkProperties.java \
       src/test/java/com/jowo/agent/service/SmartLinkClientTest.java \
       src/test/java/com/jowo/agent/config/SmartLinkPropertiesTest.java
```

- [ ] **Step 3: Remove the smartlink: block from application.yml**

Delete the block added by nothing else — the pre-existing:
```yaml
smartlink:
  base-url: ${SMARTLINK_BASE_URL:http://CHANGE_ME}
```
from `src/main/resources/application.yml` (the `oauth2:` block from Task 1 stays).

- [ ] **Step 4: Run the full test suite**

Run: `mvn -q test`
Expected: PASS — no compilation errors, no missing beans (`SmartLinkClient`/`SmartLinkProperties` beans are gone, and nothing autowires them any more).

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "chore: remove SmartLinkClient/SmartLinkProperties, superseded by OAuth2Client/OAuth2Properties"
```

---

### Task 10: Manual smoke test against a real OAuth2 provider

**Files:** none — this is a verification task, not a code task. Automated coverage (Tasks 1–9) exercises every unit in isolation via `MockWebServer`/mocks; this task is the one place the plan validates the real, wired-together redirect round trip, which can't be captured by an in-process test because it requires an actual browser redirect to a real authorization server.

**Interfaces:** none.

- [ ] **Step 1: Configure real provider values**

Set environment variables against a real (or locally-run, e.g. Keycloak in Docker) OAuth2/OIDC provider:
```bash
export OAUTH2_AUTHORIZATION_URI=https://<provider>/oauth2/authorize
export OAUTH2_TOKEN_URI=https://<provider>/oauth2/token
export OAUTH2_USERINFO_URI=https://<provider>/oauth2/userinfo
export OAUTH2_CLIENT_ID=<real-client-id>
export OAUTH2_CLIENT_SECRET=<real-client-secret-or-blank-for-public-client>
export OAUTH2_REDIRECT_URI=http://localhost:8088/api/auth/oauth2/callback
export OAUTH2_FRONTEND_REDIRECT_URI=http://localhost:5173/login/callback
```

- [ ] **Step 2: Start the backend**

Run: `./start.sh` (or `mvn spring-boot:run`)

- [ ] **Step 3: Drive the flow in a browser**

Navigate to `http://localhost:8088/api/auth/oauth2/authorize`. Confirm:
- Browser is redirected to the real provider's login page.
- After logging in, the provider redirects to `http://localhost:8088/api/auth/oauth2/callback?code=...&state=...`.
- The backend redirects to `http://localhost:5173/login/callback#token=<opaque-token>`.

- [ ] **Step 4: Verify the session works**

```bash
curl -H "Authorization: Bearer <opaque-token-from-fragment>" http://localhost:8088/api/auth/me
curl -H "Authorization: Bearer <opaque-token-from-fragment>" http://localhost:8088/api/authorize/me
```
Expected: both return `200` with the expected user id/name/userType.

- [ ] **Step 5: Verify silent refresh**

If the provider supports a short-lived access token (e.g. configure it to expire in under 30 seconds for this test), wait past expiry, then re-issue the `/api/auth/me` call with the same opaque token. Expected: still `200` (the resolver transparently refreshed server-side), and `redis-cli -h ${TEST_REDIS_HOST} -n 1 GET authsession:<opaque-token>` shows an updated `accessToken`/`expiresAt`.

- [ ] **Step 6: Verify a downstream enterprise API call still works**

Trigger a chat request that exercises `callEnterpriseApi` (any question routed to MES/CRM/etc.) and confirm in the enterprise service's own access log that the inbound request now carries an `Authorization: Bearer` header instead of `x-access-token` — this requires the target enterprise service to actually be configured as an OAuth2 resource server; if it isn't yet, this is the point where a *separate* piece of work (resource-server-side validation) is needed before downstream calls succeed end-to-end. Note it as a follow-up rather than expanding this plan.

No commit for this task — it's verification, not code.

---

## Self-Review Notes

- **Spec coverage:** login (Task 6), token storage (Task 4), refresh (Task 5), PKCE/state (Task 2), provider config (Task 1), the downstream credential/header rename (Task 8), userType (Task 7), cleanup (Task 9), and end-to-end verification (Task 10) are all covered.
- **No placeholders:** every step has runnable code or an exact `grep`/`curl`/`git` command.
- **Type consistency checked:** `AuthSessionStore.AuthUser` fields (`accessToken`, `refreshToken`, `expiresAt`, `userType`) are used with the same names and types in `CurrentUserArgumentResolver` (Task 5), `AuthController` (Task 6), and `AuthorizeController` (Task 7). `OAuth2Client.TokenResult`/`UserInfo` field names match between Task 3's definition and Tasks 5/6's usage.
