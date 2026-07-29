# SmartLink 登录 + 用户会话隔离 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add SmartLink-backed login to the chat app, so each user only sees their own historical
conversations, with a left sidebar listing them and no per-session message cap.

**Architecture:** Backend (`mcp-tool`) proxies SmartLink login and issues its own opaque Redis-backed
token; a new MySQL `sessions` table (Flyway-managed) tracks session ownership/metadata; a Spring MVC
`HandlerMethodArgumentResolver` injects the current user into any controller method that declares a
`@CurrentUser` parameter, with everything else remaining public. Frontend (`l7-skill-frontend`) gets a
login screen, a left sidebar backed by the new `/api/conversations` endpoints, and switches from
localStorage-cached history to fetching messages from the backend.

**Tech Stack:** Backend: Java 21, Spring Boot 3.5.0, Spring MVC, Spring Data Redis, Spring JDBC,
Flyway, MySQL. Frontend: React 19, TypeScript, Vite, Tailwind v4, Zustand, Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-07-08-smartlink-login-sessions-design.md`

## Global Constraints

- Backend controllers stay package-private classes (matches existing `ChatController`/`UploadController`).
- No javadoc / comment blocks unless documenting a non-obvious constraint (matches existing style).
- `local` profile now requires a **reachable local MySQL** in addition to the already-required shared
  Redis (`192.168.3.232:6379`). Start it once before running any backend task in this plan:
  `brew services start mysql@8.0`, then create the schema used by `application-local.yml`'s defaults
  (`db_jowo_im`, user `root`, password `123456`, host `127.0.0.1:3306`):
  ```bash
  mysql -h 127.0.0.1 -u root -e "CREATE DATABASE IF NOT EXISTS db_jowo_im"
  ```
  If `root` has no password locally, set one to match the default (`ALTER USER 'root'@'localhost'
  IDENTIFIED BY '123456';`) or export `JDBC_USER`/`JDBC_PASS` to match your local root credentials
  before running `mvn test` / `mvn spring-boot:run`.
- Backend tests: `SessionRepositoryTest` runs Flyway against an isolated in-memory H2 database
  (`MODE=MySQL`) — it never touches the local/shared MySQL. Every other new backend test follows
  whichever existing convention its neighbor already uses (`MockRestServiceServer` for HTTP clients,
  `@WebMvcTest` + `@MockBean` for sliced controller tests, `@SpringBootTest` for full-context tests,
  manually-constructed `LettuceConnectionFactory` against the shared Redis db `1` for Redis-backed
  classes, matching `ChatMemoryTest`).
- Frontend tests: Vitest + Testing Library, `vi.stubGlobal('fetch', ...)` for network mocking (matches
  `upload.test.ts`).
- Backend tasks (1–11) must all land before frontend tasks (12–18) start, since the frontend calls the
  new endpoints directly.

---

## Task 1: Flyway-managed `sessions` table + `SessionRepository`

**Repo:** `mcp-tool`

**Files:**
- Modify: `pom.xml`
- Create: `src/main/resources/db/migration/V1__create_sessions_table.sql`
- Create: `src/main/java/com/jowo/agent/repository/SessionRepository.java`
- Test: `src/test/java/com/jowo/agent/repository/SessionRepositoryTest.java`

**Interfaces:**
- Produces: `SessionRepository` with `Optional<SessionRow> findById(String id)`,
  `void insert(String id, String userId, String title)`, `void touch(String id)`,
  `List<SessionRow> findByUserId(String userId)`; and
  `record SessionRow(String id, String userId, String title, LocalDateTime createdAt, LocalDateTime updatedAt)`.
  Later tasks (7, 9) depend on these exact names/types.

- [ ] **Step 1: Add Flyway to `pom.xml`**

Add these two dependencies right after the existing `mysql-connector-j` dependency (no `<version>`
needed — the Spring Boot 3.5.0 parent BOM manages it, matching every other dependency in this file):

```xml
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-core</artifactId>
		</dependency>
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-mysql</artifactId>
		</dependency>
```

- [ ] **Step 2: Write the migration**

Create `src/main/resources/db/migration/V1__create_sessions_table.sql`:

```sql
CREATE TABLE sessions (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE INDEX idx_sessions_user_updated ON sessions (user_id, updated_at);
```

- [ ] **Step 3: Write the failing test**

Create `src/test/java/com/jowo/agent/repository/SessionRepositoryTest.java`:

```java
package com.jowo.agent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SessionRepositoryTest {

    private SessionRepository repository;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:session-repo-test-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        Flyway.configure().dataSource(dataSource).load().migrate();
        repository = new SessionRepository(new JdbcTemplate(dataSource));
    }

    @Test
    void findByIdOnUnknownIdReturnsEmpty() {
        assertThat(repository.findById("no-such-id")).isEmpty();
    }

    @Test
    void insertThenFindByIdReturnsRow() {
        repository.insert("s1", "user-1", "第一次对话");

        var row = repository.findById("s1");

        assertThat(row).isPresent();
        assertThat(row.get().id()).isEqualTo("s1");
        assertThat(row.get().userId()).isEqualTo("user-1");
        assertThat(row.get().title()).isEqualTo("第一次对话");
    }

    @Test
    void touchUpdatesUpdatedAt() throws InterruptedException {
        repository.insert("s1", "user-1", "标题");
        var before = repository.findById("s1").get().updatedAt();
        Thread.sleep(10);

        repository.touch("s1");

        var after = repository.findById("s1").get().updatedAt();
        assertThat(after).isAfter(before);
    }

    @Test
    void findByUserIdReturnsOnlyThatUsersSessionsSortedByUpdatedAtDesc() throws InterruptedException {
        repository.insert("s1", "user-1", "会话一");
        Thread.sleep(10);
        repository.insert("s2", "user-1", "会话二");
        repository.insert("s3", "user-2", "别人的会话");

        var rows = repository.findByUserId("user-1");

        assertThat(rows).extracting(SessionRepository.SessionRow::id).containsExactly("s2", "s1");
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd /Users/breannalinlin/Documents/code/jowo/mcp-tool && mvn test -Dtest=SessionRepositoryTest`
Expected: compilation failure — `SessionRepository` doesn't exist yet.

- [ ] **Step 5: Write the implementation**

Create `src/main/java/com/jowo/agent/repository/SessionRepository.java`:

```java
package com.jowo.agent.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SessionRepository {

    private final JdbcTemplate jdbc;

    public SessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SessionRow> findById(String id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT id, user_id, title, created_at, updated_at FROM sessions WHERE id = ?",
                    this::mapRow, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void insert(String id, String userId, String title) {
        var now = LocalDateTime.now();
        jdbc.update(
                "INSERT INTO sessions (id, user_id, title, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                id, userId, title, now, now);
    }

    public void touch(String id) {
        jdbc.update("UPDATE sessions SET updated_at = ? WHERE id = ?", LocalDateTime.now(), id);
    }

    public List<SessionRow> findByUserId(String userId) {
        return jdbc.query(
                "SELECT id, user_id, title, created_at, updated_at FROM sessions WHERE user_id = ? ORDER BY updated_at DESC",
                this::mapRow, userId);
    }

    private SessionRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SessionRow(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("title"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("updated_at").toLocalDateTime());
    }

    public record SessionRow(String id, String userId, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn test -Dtest=SessionRepositoryTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add pom.xml src/main/resources/db/migration/V1__create_sessions_table.sql \
    src/main/java/com/jowo/agent/repository/SessionRepository.java \
    src/test/java/com/jowo/agent/repository/SessionRepositoryTest.java
git commit -m "feat: add sessions table via Flyway + SessionRepository"
```

---

## Task 2: `SmartLinkProperties` config

**Repo:** `mcp-tool`

**Files:**
- Create: `src/main/java/com/jowo/agent/config/SmartLinkProperties.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-local.yml`
- Test: `src/test/java/com/jowo/agent/config/SmartLinkPropertiesTest.java`

**Interfaces:**
- Produces: `SmartLinkProperties` record with `String baseUrl()`. Task 3 consumes this.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/jowo/agent/config/SmartLinkPropertiesTest.java`:

```java
package com.jowo.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "smartlink.base-url=http://smartlink-test:9000"
})
class SmartLinkPropertiesTest {
    @Autowired SmartLinkProperties props;

    @Test
    void bindsBaseUrl() {
        assertThat(props.baseUrl()).isEqualTo("http://smartlink-test:9000");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SmartLinkPropertiesTest`
Expected: compilation failure — `SmartLinkProperties` doesn't exist yet.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/jowo/agent/config/SmartLinkProperties.java`:

```java
package com.jowo.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("smartlink")
public record SmartLinkProperties(String baseUrl) {}
```

(`AgentApplication` already has `@ConfigurationPropertiesScan`, so no extra registration is needed —
matches how `EnterpriseApiProperties` is picked up today.)

In `src/main/resources/application.yml`, add this block right after the existing `enterprise-api:`
block and before the `app:` block:

```yaml
smartlink:
  base-url: ${SMARTLINK_BASE_URL:http://CHANGE_ME}
```

In `src/main/resources/application-local.yml`, add at the top level (alongside the existing
`enterprise-api:` block):

```yaml
smartlink:
  base-url: http://192.168.3.234:9000
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SmartLinkPropertiesTest`
Expected: `Tests run: 1, Failures: 0, Errors: 0`

(This is a `@SpringBootTest`, so it needs the local MySQL from Global Constraints running — Flyway
migrates on context startup.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/config/SmartLinkProperties.java \
    src/main/resources/application.yml src/main/resources/application-local.yml \
    src/test/java/com/jowo/agent/config/SmartLinkPropertiesTest.java
git commit -m "feat: add SmartLinkProperties config"
```

---

## Task 3: `SmartLinkClient`

**Repo:** `mcp-tool`

**Files:**
- Create: `src/main/java/com/jowo/agent/service/SmartLinkClient.java`
- Test: `src/test/java/com/jowo/agent/service/SmartLinkClientTest.java`

**Interfaces:**
- Consumes: `SmartLinkProperties.baseUrl()` (Task 2).
- Produces: `SmartLinkClient` with `LoginResult login(String username, String password)` and
  `String fetchDisplayName(String smartLinkToken)`; `record LoginResult(String userId, String smartLinkToken)`;
  `SmartLinkAuthException` (bad credentials) and `SmartLinkUnavailableException` (network/5xx) as
  unchecked exceptions. Task 6 (`AuthController`) consumes all of these.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/jowo/agent/service/SmartLinkClientTest.java`:

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SmartLinkClientTest {

    @Test
    void loginReturnsUserIdAndTokenOnSuccess() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://smartlink:9000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://smartlink:9000/api/authorize/login"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(content().json("{\"username\":\"sukimiya\",\"password\":\"pw\",\"expires\":-1,\"remember\":true}"))
              .andRespond(withSuccess(
                  "{\"message\":\"success\",\"result\":{\"userId\":\"u1\",\"token\":\"tok-sl\"},\"status\":200}",
                  MediaType.APPLICATION_JSON));
        var client = new SmartLinkClient(builder);

        var result = client.login("sukimiya", "pw");

        assertThat(result.userId()).isEqualTo("u1");
        assertThat(result.smartLinkToken()).isEqualTo("tok-sl");
        server.verify();
    }

    @Test
    void loginThrowsAuthExceptionWhenBodyStatusIsNot200() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://smartlink:9000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://smartlink:9000/api/authorize/login"))
              .andRespond(withSuccess("{\"message\":\"用户名或密码错误\",\"status\":401}", MediaType.APPLICATION_JSON));
        var client = new SmartLinkClient(builder);

        assertThatThrownBy(() -> client.login("sukimiya", "wrong"))
                .isInstanceOf(SmartLinkClient.SmartLinkAuthException.class)
                .hasMessage("用户名或密码错误");
        server.verify();
    }

    @Test
    void loginThrowsAuthExceptionOn401HttpStatus() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://smartlink:9000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://smartlink:9000/api/authorize/login"))
              .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        var client = new SmartLinkClient(builder);

        assertThatThrownBy(() -> client.login("sukimiya", "wrong"))
                .isInstanceOf(SmartLinkClient.SmartLinkAuthException.class);
        server.verify();
    }

    @Test
    void loginThrowsUnavailableExceptionOn5xx() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://smartlink:9000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://smartlink:9000/api/authorize/login"))
              .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        var client = new SmartLinkClient(builder);

        assertThatThrownBy(() -> client.login("sukimiya", "pw"))
                .isInstanceOf(SmartLinkClient.SmartLinkUnavailableException.class);
        server.verify();
    }

    @Test
    void fetchDisplayNameReturnsNameFromUserDetail() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://smartlink:9000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://smartlink:9000/api/user/detail"))
              .andExpect(method(HttpMethod.GET))
              .andExpect(header("X-Access-Token", "tok-sl"))
              .andRespond(withSuccess("{\"result\":{\"name\":\"周琳琳\"},\"status\":200}", MediaType.APPLICATION_JSON));
        var client = new SmartLinkClient(builder);

        assertThat(client.fetchDisplayName("tok-sl")).isEqualTo("周琳琳");
        server.verify();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=SmartLinkClientTest`
Expected: compilation failure — `SmartLinkClient` doesn't exist yet.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/jowo/agent/service/SmartLinkClient.java`:

```java
package com.jowo.agent.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.jowo.agent.config.SmartLinkProperties;

@Component
public class SmartLinkClient {

    private final RestClient http;

    @Autowired
    public SmartLinkClient(SmartLinkProperties props, RestClient.Builder builder) {
        this(builder.clone().baseUrl(props.baseUrl()));
    }

    SmartLinkClient(RestClient.Builder builder) {
        this.http = builder.build();
    }

    public LoginResult login(String username, String password) {
        JsonNode body;
        try {
            body = http.post().uri("/api/authorize/login")
                    .body(Map.of("username", username, "password", password, "expires", -1, "remember", true))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) throw new SmartLinkAuthException("用户名或密码错误");
            throw new SmartLinkUnavailableException(e.getMessage());
        } catch (ResourceAccessException e) {
            throw new SmartLinkUnavailableException(e.getMessage());
        }
        if (body.path("status").asInt(200) != 200) {
            throw new SmartLinkAuthException(body.path("message").asText("用户名或密码错误"));
        }
        var result = body.path("result");
        return new LoginResult(result.path("userId").asText(), result.path("token").asText());
    }

    public String fetchDisplayName(String smartLinkToken) {
        JsonNode body;
        try {
            body = http.get().uri("/api/user/detail")
                    .header("X-Access-Token", smartLinkToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new SmartLinkUnavailableException(e.getMessage());
        }
        return body.path("result").path("name").asText();
    }

    public record LoginResult(String userId, String smartLinkToken) {}

    public static class SmartLinkAuthException extends RuntimeException {
        public SmartLinkAuthException(String message) { super(message); }
    }

    public static class SmartLinkUnavailableException extends RuntimeException {
        public SmartLinkUnavailableException(String message) { super(message); }
    }
}
```

(The `body.path("status").asInt(200) != 200` check matters: the real SmartLink API — see
`doc/api-doc/smartlink.md` in the `l7-skill-frontend` repo — nests a `status` field inside a `200 OK`
HTTP response body, so a logical failure may not surface as a non-2xx HTTP status at all.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=SmartLinkClientTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/service/SmartLinkClient.java \
    src/test/java/com/jowo/agent/service/SmartLinkClientTest.java
git commit -m "feat: add SmartLinkClient for login + user detail"
```

---

## Task 4: `AuthSessionStore` (Redis-backed opaque token store)

**Repo:** `mcp-tool`

**Files:**
- Create: `src/main/java/com/jowo/agent/service/AuthSessionStore.java`
- Test: `src/test/java/com/jowo/agent/service/AuthSessionStoreTest.java`

**Interfaces:**
- Produces: `AuthSessionStore` with `String create(String userId, String name)`,
  `Optional<AuthUser> resolve(String token)`, `void delete(String token)`; and
  `record AuthUser(String userId, String name)`. Tasks 5, 6, 9, 10 consume this.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/jowo/agent/service/AuthSessionStoreTest.java`:

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class AuthSessionStoreTest {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private AuthSessionStore store;
    private String issuedToken;

    @BeforeEach
    void setUp() {
        var config = new RedisStandaloneConfiguration("192.168.3.232", 6379);
        config.setPassword("FeetxXuFkZIaM2Pq");
        config.setDatabase(1);
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        store = new AuthSessionStore(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        if (issuedToken != null) redisTemplate.delete("authsession:" + issuedToken);
        connectionFactory.destroy();
    }

    @Test
    void createThenResolveReturnsSameUser() {
        issuedToken = store.create("user-1", "周琳琳");

        var resolved = store.resolve(issuedToken);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().userId()).isEqualTo("user-1");
        assertThat(resolved.get().name()).isEqualTo("周琳琳");
    }

    @Test
    void resolveOnUnknownTokenReturnsEmpty() {
        assertThat(store.resolve("no-such-token")).isEmpty();
    }

    @Test
    void deleteInvalidatesToken() {
        issuedToken = store.create("user-1", "周琳琳");

        store.delete(issuedToken);

        assertThat(store.resolve(issuedToken)).isEmpty();
    }

    @Test
    void tokensAreUniquePerCall() {
        var t1 = store.create("user-1", "A");
        var t2 = store.create("user-1", "A");
        issuedToken = t1;
        redisTemplate.delete("authsession:" + t2);

        assertThat(t1).isNotEqualTo(t2);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=AuthSessionStoreTest`
Expected: compilation failure — `AuthSessionStore` doesn't exist yet.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/jowo/agent/service/AuthSessionStore.java`:

```java
package com.jowo.agent.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AuthSessionStore {

    private static final String KEY_PREFIX = "authsession:";
    private static final Duration TTL = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();

    public AuthSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String create(String userId, String name) {
        String token = randomToken();
        try {
            redis.opsForValue().set(KEY_PREFIX + token, json.writeValueAsString(new AuthUser(userId, name)), TTL);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize auth session for user " + userId, e);
        }
        return token;
    }

    public Optional<AuthUser> resolve(String token) {
        String raw = redis.opsForValue().get(KEY_PREFIX + token);
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(json.readValue(raw, AuthUser.class));
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt auth session JSON for token " + token, e);
        }
    }

    public void delete(String token) {
        redis.delete(KEY_PREFIX + token);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record AuthUser(String userId, String name) {}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=AuthSessionStoreTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/service/AuthSessionStore.java \
    src/test/java/com/jowo/agent/service/AuthSessionStoreTest.java
git commit -m "feat: add Redis-backed AuthSessionStore"
```

---

## Task 5: Current-user argument resolver infra

**Repo:** `mcp-tool`

**Files:**
- Create: `src/main/java/com/jowo/agent/web/CurrentUser.java`
- Create: `src/main/java/com/jowo/agent/web/UnauthorizedException.java`
- Create: `src/main/java/com/jowo/agent/web/CurrentUserArgumentResolver.java`
- Create: `src/main/java/com/jowo/agent/web/AuthExceptionHandler.java`
- Create: `src/main/java/com/jowo/agent/web/WebConfig.java`
- Test: `src/test/java/com/jowo/agent/web/CurrentUserArgumentResolverTest.java`

**Interfaces:**
- Consumes: `AuthSessionStore.resolve(String)` (Task 4).
- Produces: `@CurrentUser` parameter annotation — any controller method can add a parameter
  `@CurrentUser AuthSessionStore.AuthUser user` to require a valid `Authorization: Bearer <token>`
  header; missing/invalid token → `401` via `UnauthorizedException` + `AuthExceptionHandler`. A method
  with no `@CurrentUser` parameter stays public. Tasks 6, 9, 10 consume this pattern.

Why an argument resolver instead of a `HandlerInterceptor`: `HandlerMethodArgumentResolver` and
`WebMvcConfigurer` beans are both in Spring Boot's `@WebMvcTest` default include-filter, so
`@WebMvcTest(SomeController.class)` slices pick this up automatically (with `AuthSessionStore`
`@MockBean`'d) without extra wiring — a plain `HandlerInterceptor` component isn't guaranteed to be
included the same way. This also makes "except `/api/auth/login`" implicit: that one handler method
simply never declares a `@CurrentUser` parameter, no path-pattern allowlist required.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/jowo/agent/web/CurrentUserArgumentResolverTest.java`:

```java
package com.jowo.agent.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

import com.jowo.agent.service.AuthSessionStore;

class CurrentUserArgumentResolverTest {

    @Test
    void supportsParameterOnlyWhenAnnotationPresent() throws NoSuchMethodException {
        var sessions = mock(AuthSessionStore.class);
        var resolver = new CurrentUserArgumentResolver(sessions);
        var annotatedParam = new MethodParameter(
                Probe.class.getDeclaredMethod("annotated", AuthSessionStore.AuthUser.class), 0);
        var plainParam = new MethodParameter(
                Probe.class.getDeclaredMethod("plain", String.class), 0);

        assertThat(resolver.supportsParameter(annotatedParam)).isTrue();
        assertThat(resolver.supportsParameter(plainParam)).isFalse();
    }

    @Test
    void resolveArgumentReturnsUserForValidBearerToken() {
        var sessions = mock(AuthSessionStore.class);
        var user = new AuthSessionStore.AuthUser("user-1", "周琳琳");
        when(sessions.resolve("tok-1")).thenReturn(Optional.of(user));
        var resolver = new CurrentUserArgumentResolver(sessions);
        var request = mock(NativeWebRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer tok-1");

        var resolved = resolver.resolveArgument(null, null, request, null);

        assertThat(resolved).isEqualTo(user);
    }

    @Test
    void resolveArgumentThrowsWhenHeaderMissing() {
        var sessions = mock(AuthSessionStore.class);
        var resolver = new CurrentUserArgumentResolver(sessions);
        var request = mock(NativeWebRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, request, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void resolveArgumentThrowsWhenTokenUnknown() {
        var sessions = mock(AuthSessionStore.class);
        when(sessions.resolve("bad-token")).thenReturn(Optional.empty());
        var resolver = new CurrentUserArgumentResolver(sessions);
        var request = mock(NativeWebRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, request, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    private static class Probe {
        void annotated(@CurrentUser AuthSessionStore.AuthUser user) {}
        void plain(String s) {}
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=CurrentUserArgumentResolverTest`
Expected: compilation failure — none of the `web` package classes exist yet.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/jowo/agent/web/CurrentUser.java`:

```java
package com.jowo.agent.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {}
```

Create `src/main/java/com/jowo/agent/web/UnauthorizedException.java`:

```java
package com.jowo.agent.web;

public class UnauthorizedException extends RuntimeException {}
```

Create `src/main/java/com/jowo/agent/web/CurrentUserArgumentResolver.java`:

```java
package com.jowo.agent.web;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.jowo.agent.service.AuthSessionStore;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final AuthSessionStore sessions;

    public CurrentUserArgumentResolver(AuthSessionStore sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String header = webRequest.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        if (token == null) throw new UnauthorizedException();
        return sessions.resolve(token).orElseThrow(UnauthorizedException::new);
    }
}
```

Create `src/main/java/com/jowo/agent/web/AuthExceptionHandler.java`:

```java
package com.jowo.agent.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<Void> handleUnauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
```

Create `src/main/java/com/jowo/agent/web/WebConfig.java`:

```java
package com.jowo.agent.web;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebConfig(CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=CurrentUserArgumentResolverTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/web/ src/test/java/com/jowo/agent/web/
git commit -m "feat: add @CurrentUser argument resolver for auth"
```

---

## Task 6: `AuthController`

**Repo:** `mcp-tool`

**Files:**
- Create: `src/main/java/com/jowo/agent/controller/AuthController.java`
- Test: `src/test/java/com/jowo/agent/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `SmartLinkClient` (Task 3), `AuthSessionStore` (Task 4), `@CurrentUser` (Task 5).
- Produces: `POST /api/auth/login`, `GET /api/auth/me`, `POST /api/auth/logout`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/jowo/agent/controller/AuthControllerTest.java`:

```java
package com.jowo.agent.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.SmartLinkClient;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @MockBean SmartLinkClient smartLink;
    @MockBean AuthSessionStore sessions;

    @Test
    void loginReturnsTokenOnSuccess() throws Exception {
        when(smartLink.login("sukimiya", "pw")).thenReturn(new SmartLinkClient.LoginResult("u1", "sl-tok"));
        when(smartLink.fetchDisplayName("sl-tok")).thenReturn("周琳琳");
        when(sessions.create("u1", "周琳琳")).thenReturn("app-tok");

        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"sukimiya\",\"password\":\"pw\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.token").value("app-tok"))
           .andExpect(jsonPath("$.userId").value("u1"))
           .andExpect(jsonPath("$.name").value("周琳琳"));
    }

    @Test
    void loginReturns401OnBadCredentials() throws Exception {
        when(smartLink.login("sukimiya", "wrong"))
                .thenThrow(new SmartLinkClient.SmartLinkAuthException("用户名或密码错误"));

        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"sukimiya\",\"password\":\"wrong\"}"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void loginReturns502WhenSmartLinkUnavailable() throws Exception {
        when(smartLink.login(anyString(), anyString()))
                .thenThrow(new SmartLinkClient.SmartLinkUnavailableException("timeout"));

        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"sukimiya\",\"password\":\"pw\"}"))
           .andExpect(status().isBadGateway());
    }

    @Test
    void meReturnsCurrentUserForValidToken() throws Exception {
        when(sessions.resolve("app-tok")).thenReturn(Optional.of(new AuthSessionStore.AuthUser("u1", "周琳琳")));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer app-tok"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.userId").value("u1"))
           .andExpect(jsonPath("$.name").value("周琳琳"));
    }

    @Test
    void meReturns401WithoutToken() throws Exception {
        mvc.perform(get("/api/auth/me"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutDeletesSessionAndReturns204() throws Exception {
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer app-tok"))
           .andExpect(status().isNoContent());

        verify(sessions).delete("app-tok");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=AuthControllerTest`
Expected: compilation failure — `AuthController` doesn't exist yet.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/jowo/agent/controller/AuthController.java`:

```java
package com.jowo.agent.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.SmartLinkClient;
import com.jowo.agent.web.CurrentUser;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
class AuthController {

    private final SmartLinkClient smartLink;
    private final AuthSessionStore sessions;

    AuthController(SmartLinkClient smartLink, AuthSessionStore sessions) {
        this.smartLink = smartLink;
        this.sessions = sessions;
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            var result = smartLink.login(request.username(), request.password());
            var name = smartLink.fetchDisplayName(result.smartLinkToken());
            var token = sessions.create(result.userId(), name);
            return ResponseEntity.ok(Map.of("token", token, "userId", result.userId(), "name", name));
        } catch (SmartLinkClient.SmartLinkAuthException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        } catch (SmartLinkClient.SmartLinkUnavailableException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", "登录服务暂时不可用"));
        }
    }

    @GetMapping("/me")
    Map<String, String> me(@CurrentUser AuthSessionStore.AuthUser user) {
        return Map.of("userId", user.userId(), "name", user.name());
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        sessions.delete(token);
        return ResponseEntity.noContent().build();
    }

    record LoginRequest(String username, String password) {}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=AuthControllerTest`
Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/controller/AuthController.java \
    src/test/java/com/jowo/agent/controller/AuthControllerTest.java
git commit -m "feat: add AuthController (login/me/logout)"
```

---

## Task 7: `SessionService`

**Repo:** `mcp-tool`

**Files:**
- Create: `src/main/java/com/jowo/agent/service/SessionService.java`
- Test: `src/test/java/com/jowo/agent/service/SessionServiceTest.java`

**Interfaces:**
- Consumes: `SessionRepository` (Task 1).
- Produces: `SessionService` with `enum AccessResult { OWNED, FORBIDDEN }`,
  `AccessResult touchOrCreate(String sessionId, String userId, String firstMessage)`,
  `Optional<SessionRow> findOwned(String sessionId, String userId)`,
  `List<SessionRow> listForUser(String userId)`. Task 9 (`ChatController`) consumes all of these.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/jowo/agent/service/SessionServiceTest.java`:

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.jowo.agent.repository.SessionRepository;
import com.jowo.agent.repository.SessionRepository.SessionRow;

class SessionServiceTest {

    @Test
    void touchOrCreateInsertsNewSessionWithTruncatedTitle() {
        var repository = mock(SessionRepository.class);
        when(repository.findById("s1")).thenReturn(Optional.empty());
        var service = new SessionService(repository);

        var result = service.touchOrCreate("s1", "user-1",
                "这是一条非常长的用户消息用来测试标题截断逻辑是否按预期工作而不是原样保留全部文字");

        assertThat(result).isEqualTo(SessionService.AccessResult.OWNED);
        verify(repository).insert(eq("s1"), eq("user-1"), argThat(title -> title.endsWith("…") && title.length() == 25));
    }

    @Test
    void touchOrCreateTouchesExistingOwnedSession() {
        var repository = mock(SessionRepository.class);
        when(repository.findById("s1")).thenReturn(Optional.of(
                new SessionRow("s1", "user-1", "标题", LocalDateTime.now(), LocalDateTime.now())));
        var service = new SessionService(repository);

        var result = service.touchOrCreate("s1", "user-1", "second message");

        assertThat(result).isEqualTo(SessionService.AccessResult.OWNED);
        verify(repository).touch("s1");
        verify(repository, never()).insert(any(), any(), any());
    }

    @Test
    void touchOrCreateForbidsAccessToAnotherUsersSession() {
        var repository = mock(SessionRepository.class);
        when(repository.findById("s1")).thenReturn(Optional.of(
                new SessionRow("s1", "user-2", "标题", LocalDateTime.now(), LocalDateTime.now())));
        var service = new SessionService(repository);

        var result = service.touchOrCreate("s1", "user-1", "message");

        assertThat(result).isEqualTo(SessionService.AccessResult.FORBIDDEN);
        verify(repository, never()).touch(any());
        verify(repository, never()).insert(any(), any(), any());
    }

    @Test
    void findOwnedReturnsEmptyWhenSessionBelongsToAnotherUser() {
        var repository = mock(SessionRepository.class);
        when(repository.findById("s1")).thenReturn(Optional.of(
                new SessionRow("s1", "user-2", "标题", LocalDateTime.now(), LocalDateTime.now())));
        var service = new SessionService(repository);

        assertThat(service.findOwned("s1", "user-1")).isEmpty();
    }

    @Test
    void listForUserDelegatesToRepository() {
        var repository = mock(SessionRepository.class);
        var rows = List.of(new SessionRow("s1", "user-1", "标题", LocalDateTime.now(), LocalDateTime.now()));
        when(repository.findByUserId("user-1")).thenReturn(rows);
        var service = new SessionService(repository);

        assertThat(service.listForUser("user-1")).isEqualTo(rows);
    }

    @Test
    void emptyMessageProducesDefaultTitle() {
        var repository = mock(SessionRepository.class);
        when(repository.findById("s1")).thenReturn(Optional.empty());
        var service = new SessionService(repository);

        service.touchOrCreate("s1", "user-1", "   ");

        verify(repository).insert("s1", "user-1", "新对话");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=SessionServiceTest`
Expected: compilation failure — `SessionService` doesn't exist yet.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/jowo/agent/service/SessionService.java`:

```java
package com.jowo.agent.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.jowo.agent.repository.SessionRepository;
import com.jowo.agent.repository.SessionRepository.SessionRow;

@Service
public class SessionService {

    private static final int TITLE_MAX_LENGTH = 24;

    private final SessionRepository repository;

    public SessionService(SessionRepository repository) {
        this.repository = repository;
    }

    public enum AccessResult { OWNED, FORBIDDEN }

    public AccessResult touchOrCreate(String sessionId, String userId, String firstMessage) {
        var existing = repository.findById(sessionId);
        if (existing.isPresent()) {
            if (!existing.get().userId().equals(userId)) return AccessResult.FORBIDDEN;
            repository.touch(sessionId);
            return AccessResult.OWNED;
        }
        repository.insert(sessionId, userId, title(firstMessage));
        return AccessResult.OWNED;
    }

    public Optional<SessionRow> findOwned(String sessionId, String userId) {
        return repository.findById(sessionId).filter(s -> s.userId().equals(userId));
    }

    public List<SessionRow> listForUser(String userId) {
        return repository.findByUserId(userId);
    }

    static String title(String message) {
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.isEmpty()) return "新对话";
        return trimmed.length() <= TITLE_MAX_LENGTH ? trimmed : trimmed.substring(0, TITLE_MAX_LENGTH) + "…";
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=SessionServiceTest`
Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/service/SessionService.java \
    src/test/java/com/jowo/agent/service/SessionServiceTest.java
git commit -m "feat: add SessionService (ownership + touch-or-create)"
```

---

## Task 8: Remove the 20-message cap from `ChatMemory`

**Repo:** `mcp-tool`

**Files:**
- Modify: `src/main/java/com/jowo/agent/service/ChatMemory.java`
- Modify: `src/test/java/com/jowo/agent/service/ChatMemoryTest.java`

**Interfaces:**
- Unchanged: `ChatMemory.get(String)`/`append(String, List<Message>)` signatures. Only internal
  truncation behavior changes. Task 9 depends on `get()` still returning full, unfiltered history.

- [ ] **Step 1: Update the test first**

In `src/test/java/com/jowo/agent/service/ChatMemoryTest.java`, remove these two tests entirely:
`appendTrimsToLast20Messages` and `appendDropsOrphanedLeadingToolMessageAfterTrim`. Replace them with:

```java
    @Test
    void appendDoesNotCapMessageCount() {
        for (int i = 0; i < 25; i++) {
            memory.append("conv-1", List.of(LlmClient.Message.user("msg-" + i)));
        }

        var history = memory.get("conv-1");

        assertThat(history).hasSize(25);
        assertThat(history.get(0).content()).isEqualTo("msg-0");
        assertThat(history.get(24).content()).isEqualTo("msg-24");
    }
```

(Leave `getOnUnknownOwnerReturnsEmptyList`, `appendThenGetReturnsAppendedMessagesInOrder`, and
`ownersAreIsolatedFromEachOther` untouched.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=ChatMemoryTest#appendDoesNotCapMessageCount`
Expected: FAIL — `history` has size 20, not 25 (current cap still in place).

- [ ] **Step 3: Remove the cap from the implementation**

In `src/main/java/com/jowo/agent/service/ChatMemory.java`, delete the `MAX_MESSAGES` constant and
replace the body of `append`:

```java
    public void append(String ownerId, List<LlmClient.Message> newMessages) {
        var merged = new ArrayList<>(readList(ownerId));
        merged.addAll(newMessages);
        writeList(ownerId, List.copyOf(merged));
    }
```

(Delete the old `int from = ...` truncation block and the "skip orphaned tool message" `while` loop
that followed it — both existed only to implement the cap.)

- [ ] **Step 4: Run all `ChatMemoryTest` tests to verify they pass**

Run: `mvn test -Dtest=ChatMemoryTest`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/service/ChatMemory.java \
    src/test/java/com/jowo/agent/service/ChatMemoryTest.java
git commit -m "feat: remove 20-message cap from ChatMemory"
```

---

## Task 9: `ChatController` — auth, ownership, real `/conversations` endpoints

**Repo:** `mcp-tool`

**Files:**
- Modify: `src/main/java/com/jowo/agent/controller/ChatController.java`
- Modify: `src/test/java/com/jowo/agent/controller/ChatControllerTest.java`

**Interfaces:**
- Consumes: `SessionService` (Task 7), `ChatMemory` (Task 8), `@CurrentUser` (Task 5).
- Produces: `GET /api/conversations` (now real), `GET /api/conversations/{id}/messages` (new);
  `POST /api/chat/completions` and `POST /api/chat` now require `@CurrentUser` and return `404` when
  the `conversationId` belongs to another user.

- [ ] **Step 1: Replace `ChatController.java` entirely**

```java
package com.jowo.agent.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jowo.agent.service.AgentService;
import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.ChatMemory;
import com.jowo.agent.service.SessionService;
import com.jowo.agent.web.CurrentUser;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
class ChatController {

    private final AgentService agentService;
    private final SessionService sessionService;
    private final ChatMemory memory;
    private static final ObjectMapper json = new ObjectMapper();

    ChatController(AgentService agentService, SessionService sessionService, ChatMemory memory) {
        this.agentService = agentService;
        this.sessionService = sessionService;
        this.memory = memory;
    }

    // OpenAI-compatible chat completions endpoint
    @PostMapping(value = "/chat/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<Flux<String>> chatCompletions(@RequestBody Map<String, Object> body,
                                                  @CurrentUser AuthSessionStore.AuthUser user) {
        var messages = getMessages(body);
        var userMsg = messages.isEmpty() ? "" :
                messages.get(messages.size() - 1).getOrDefault("content", "");

        // Use conversationId from frontend as the owner key for session continuity across requests
        var ownerId = body.getOrDefault("conversationId", UUID.randomUUID().toString()).toString();
        if (sessionService.touchOrCreate(ownerId, user.userId(), userMsg) == SessionService.AccessResult.FORBIDDEN) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        var chunkId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);
        Flux<String> stream = agentService.chat(ownerId, userMsg)
                .flatMap(line -> toContentChunk(line).map(Flux::just).orElseGet(Flux::empty))
                .map(line -> {
                    var chunk = json.createObjectNode();
                    chunk.put("id", chunkId);
                    chunk.put("object", "chat.completion.chunk");
                    var choice = chunk.putArray("choices").addObject();
                    choice.put("index", 0);
                    choice.putObject("delta").put("content", line);
                    try {
                        return json.writeValueAsString(chunk);
                    } catch (Exception e) {
                        return "";
                    }
                })
                .concatWithValues("[DONE]");
        return ResponseEntity.ok().body(stream);
    }

    static Optional<String> toContentChunk(String line) {
        if (!line.startsWith("{\"type\":")) return Optional.of(line);
        try {
            JsonNode n = json.readTree(line);
            if ("error".equals(n.path("type").asText())) {
                return Optional.of(n.path("content").asText(""));
            }
        } catch (Exception ignored) {
            // not a control line we understand — drop it, same as before
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> getMessages(Map<String, Object> body) {
        return (List<Map<String, String>>) body.getOrDefault("messages", List.of());
    }

    // GET /api/models for chatbot-ui compatibility
    @GetMapping("/models")
    Map<String, Object> models() {
        return Map.of("object", "list",
                "data", List.of(Map.of("id", "jowo-agent", "object", "model")));
    }

    // Legacy endpoint for custom frontend
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    ResponseEntity<Flux<String>> chat(@RequestBody ChatRequest request,
                                       @CurrentUser AuthSessionStore.AuthUser user) {
        var ownerId = request.conversationId() != null
                ? request.conversationId()
                : UUID.randomUUID().toString();
        if (sessionService.touchOrCreate(ownerId, user.userId(), request.message()) == SessionService.AccessResult.FORBIDDEN) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok().body(agentService.chat(ownerId, request.message()));
    }

    @GetMapping("/conversations")
    List<ConversationSummary> listConversations(@CurrentUser AuthSessionStore.AuthUser user) {
        return sessionService.listForUser(user.userId()).stream()
                .map(s -> new ConversationSummary(s.id(), s.title(), s.updatedAt()))
                .toList();
    }

    @GetMapping("/conversations/{id}")
    Object getConversation(@PathVariable String id) {
        return null;
    }

    @GetMapping("/conversations/{id}/messages")
    ResponseEntity<List<Map<String, String>>> getConversationMessages(@PathVariable String id,
                                                                        @CurrentUser AuthSessionStore.AuthUser user) {
        if (sessionService.findOwned(id, user.userId()).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var displayMessages = new ArrayList<Map<String, String>>();
        int i = 0;
        for (var m : memory.get(id)) {
            boolean displayable = ("user".equals(m.role()) || "assistant".equals(m.role())) && m.content() != null;
            if (!displayable) continue;
            displayMessages.add(Map.of("id", "msg-" + (i++), "role", m.role(), "content", m.content()));
        }
        return ResponseEntity.ok(displayMessages);
    }

    @DeleteMapping("/conversations/{id}")
    void deleteConversation(@PathVariable String id) {
    }

    @GetMapping("/services")
    List<ServiceInfo> listServices() {
        return List.of(
                new ServiceInfo("MES", "制造执行系统", "/api/mes"),
                new ServiceInfo("CRM", "客户关系管理系统", "/api/crm"),
                new ServiceInfo("WMS", "仓储管理系统", "/api/wms"),
                new ServiceInfo("QMS", "质量管理系统", "/api/qms"),
                new ServiceInfo("SRM", "供应商关系管理", "/api/srm")
        );
    }

    record ChatRequest(String conversationId, String message) {}

    record ConversationSummary(String id, String title, LocalDateTime updatedAt) {}

    record ServiceInfo(String code, String name, String basePath) {}
}
```

- [ ] **Step 2: Replace `ChatControllerTest.java` entirely**

```java
package com.jowo.agent.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jowo.agent.repository.SessionRepository.SessionRow;
import com.jowo.agent.service.AgentService;
import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.ChatMemory;
import com.jowo.agent.service.LlmClient;
import com.jowo.agent.service.SessionService;

import reactor.core.publisher.Flux;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AgentService agentService;
    @MockBean SessionService sessionService;
    @MockBean ChatMemory chatMemory;
    @MockBean AuthSessionStore sessions;

    private void asUser1() {
        when(sessions.resolve("test-token")).thenReturn(
                Optional.of(new AuthSessionStore.AuthUser("user-1", "Test User")));
    }

    @Test
    void chatCompletionsStreamsWithoutRequiringAnyBinding() throws Exception {
        asUser1();
        when(sessionService.touchOrCreate("conv-1", "user-1", "hi")).thenReturn(SessionService.AccessResult.OWNED);
        when(agentService.chat(eq("conv-1"), eq("hi"))).thenReturn(Flux.just("hello back"));

        mvc.perform(post("/api/chat/completions").contentType("application/json")
                .header("Authorization", "Bearer test-token")
                .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"conversationId\":\"conv-1\"}"))
           .andExpect(status().isOk());

        verify(agentService).chat("conv-1", "hi");
    }

    @Test
    void chatCompletionsReturns404WhenSessionOwnedByAnotherUser() throws Exception {
        asUser1();
        when(sessionService.touchOrCreate("conv-1", "user-1", "hi")).thenReturn(SessionService.AccessResult.FORBIDDEN);

        mvc.perform(post("/api/chat/completions").contentType("application/json")
                .header("Authorization", "Bearer test-token")
                .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"conversationId\":\"conv-1\"}"))
           .andExpect(status().isNotFound());

        verifyNoInteractions(agentService);
    }

    @Test
    void chatCompletionsRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/chat/completions").contentType("application/json")
                .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"conversationId\":\"conv-1\"}"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void legacyChatStreamsWithoutRequiringAnyBinding() throws Exception {
        asUser1();
        when(sessionService.touchOrCreate("conv-1", "user-1", "hi")).thenReturn(SessionService.AccessResult.OWNED);
        when(agentService.chat(eq("conv-1"), eq("hi"))).thenReturn(Flux.just("hello back"));

        mvc.perform(post("/api/chat").contentType("application/json")
                .header("Authorization", "Bearer test-token")
                .content("{\"conversationId\":\"conv-1\",\"message\":\"hi\"}"))
           .andExpect(status().isOk());

        verify(agentService).chat("conv-1", "hi");
    }

    @Test
    void toContentChunkDropsControlLinesExceptError() {
        assertThat(ChatController.toContentChunk("plain text")).contains("plain text");
        assertThat(ChatController.toContentChunk("{\"type\":\"thinking\",\"content\":\"x\"}")).isEmpty();
        assertThat(ChatController.toContentChunk("{\"type\":\"done\"}")).isEmpty();
        assertThat(ChatController.toContentChunk("{\"type\":\"error\",\"content\":\"boom\"}")).contains("boom");
    }

    @Test
    void servicesEndpointListsAllFiveServices() throws Exception {
        asUser1();
        mvc.perform(get("/api/services").header("Authorization", "Bearer test-token"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void listConversationsReturnsOnlyCurrentUsersSessions() throws Exception {
        asUser1();
        var now = LocalDateTime.now();
        when(sessionService.listForUser("user-1")).thenReturn(
                List.of(new SessionRow("s1", "user-1", "标题一", now, now)));

        mvc.perform(get("/api/conversations").header("Authorization", "Bearer test-token"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(1))
           .andExpect(jsonPath("$[0].id").value("s1"))
           .andExpect(jsonPath("$[0].title").value("标题一"));
    }

    @Test
    void getConversationMessagesReturns404WhenNotOwned() throws Exception {
        asUser1();
        when(sessionService.findOwned("s1", "user-1")).thenReturn(Optional.empty());

        mvc.perform(get("/api/conversations/s1/messages").header("Authorization", "Bearer test-token"))
           .andExpect(status().isNotFound());
    }

    @Test
    void getConversationMessagesFiltersToUserAndAssistantRoles() throws Exception {
        asUser1();
        var now = LocalDateTime.now();
        when(sessionService.findOwned("s1", "user-1")).thenReturn(
                Optional.of(new SessionRow("s1", "user-1", "标题", now, now)));
        when(chatMemory.get("s1")).thenReturn(List.of(
                LlmClient.Message.system("system prompt"),
                LlmClient.Message.user("hi"),
                LlmClient.Message.assistantText("hello")));

        mvc.perform(get("/api/conversations/s1/messages").header("Authorization", "Bearer test-token"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(2))
           .andExpect(jsonPath("$[0].role").value("user"))
           .andExpect(jsonPath("$[0].content").value("hi"))
           .andExpect(jsonPath("$[1].role").value("assistant"))
           .andExpect(jsonPath("$[1].content").value("hello"));
    }
}
```

- [ ] **Step 3: Run tests to verify they pass**

Run: `mvn test -Dtest=ChatControllerTest`
Expected: `Tests run: 10, Failures: 0, Errors: 0`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/jowo/agent/controller/ChatController.java \
    src/test/java/com/jowo/agent/controller/ChatControllerTest.java
git commit -m "feat: require auth + enforce session ownership in ChatController"
```

---

## Task 10: `UploadController` — require auth

**Repo:** `mcp-tool`

**Files:**
- Modify: `src/main/java/com/jowo/agent/controller/UploadController.java`
- Modify: `src/test/java/com/jowo/agent/controller/UploadControllerTest.java`

**Interfaces:**
- Consumes: `@CurrentUser` (Task 5), `AuthSessionStore` (Task 4).

- [ ] **Step 1: Add `@CurrentUser` to both endpoints**

In `src/main/java/com/jowo/agent/controller/UploadController.java`, add the import
`import com.jowo.agent.service.AuthSessionStore;` and `import com.jowo.agent.web.CurrentUser;`, then
add a trailing parameter to both handler methods:

```java
    @GetMapping("/download")
    ResponseEntity<?> download(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("name") String name,
            @CurrentUser AuthSessionStore.AuthUser user) {
```

```java
    @PostMapping("/upload")
    ResponseEntity<?> upload(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("files") List<MultipartFile> files,
            @CurrentUser AuthSessionStore.AuthUser user) {
```

(No other change to the method bodies — `user` is unused inside, its only job is forcing
authentication. There's no per-file ownership check, matching the spec's non-goals.)

- [ ] **Step 2: Update the test to authenticate every request**

Replace `src/test/java/com/jowo/agent/controller/UploadControllerTest.java` entirely:

```java
package com.jowo.agent.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import com.jowo.agent.config.UploadProperties;
import com.jowo.agent.service.AuthSessionStore;

@SpringBootTest
@TestPropertySource(properties = {
    "app.upload.dir=${java.io.tmpdir}/upload-test",
    "app.upload.allowed-content-types=image/png,application/pdf"
})
class UploadControllerTest {

    @Autowired WebApplicationContext ctx;
    @Autowired UploadProperties props;
    @Autowired AuthSessionStore sessions;
    MockMvc mvc;
    String token;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
        token = sessions.create("upload-test-user", "Upload Test");
        var dir = Path.of(props.getDir());
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        }
    }

    @AfterEach
    void tearDown() {
        sessions.delete(token);
    }

    @Test
    void uploadsMultipleFiles() throws Exception {
        var f1 = new MockMultipartFile("files", "a.png", "image/png", FileStorageServiceTestData.PNG);
        var f2 = new MockMultipartFile("files", "b.png", "image/png", FileStorageServiceTestData.PNG);
        mvc.perform(multipart("/api/files/upload")
                .file(f1).file(f2)
                .param("sessionId", "conv-it")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("a.png"))
            .andExpect(jsonPath("$[0].path").exists());
    }

    @Test
    void rejectsMissingSessionId() throws Exception {
        var f = new MockMultipartFile("files", "a.png", "image/png", FileStorageServiceTestData.PNG);
        mvc.perform(multipart("/api/files/upload").file(f)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTooManyFiles() throws Exception {
        var req = multipart("/api/files/upload");
        for (int i = 0; i < 11; i++) {
            req = req.file(new MockMultipartFile("files", "a" + i + ".png", "image/png", FileStorageServiceTestData.PNG));
        }
        mvc.perform(req.param("sessionId", "conv-many")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("FILE_REJECTED"));
    }

    @Test
    void downloadsExistingFile() throws Exception {
        var dir = Path.of(props.getDir(), "conv-dl");
        Files.createDirectories(dir);
        var name = "国台办.md";
        var body = "# 标题\n\n正文。\n";
        Files.writeString(dir.resolve(name), body);
        mvc.perform(get("/api/files/download")
                .param("sessionId", "conv-dl")
                .param("name", name)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
            .andExpect(content().bytes(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsDownloadPathTraversal() throws Exception {
        mvc.perform(get("/api/files/download")
                .param("sessionId", "conv-dl")
                .param("name", "../../etc/passwd")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
    }

    @Test
    void downloadMissingFileReturns404() throws Exception {
        mvc.perform(get("/api/files/download")
                .param("sessionId", "conv-dl")
                .param("name", "ghost.md")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test
    void uploadRequiresAuthentication() throws Exception {
        var f = new MockMultipartFile("files", "a.png", "image/png", FileStorageServiceTestData.PNG);
        mvc.perform(multipart("/api/files/upload").file(f).param("sessionId", "conv-it"))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 3: Run tests to verify they pass**

Run: `mvn test -Dtest=UploadControllerTest`
Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/jowo/agent/controller/UploadController.java \
    src/test/java/com/jowo/agent/controller/UploadControllerTest.java
git commit -m "feat: require auth on UploadController endpoints"
```

---

## Task 11: Backend manual smoke test

**Repo:** `mcp-tool`

No new files — this is a manual verification checklist to run before moving to the frontend.

- [ ] **Step 1: Run the full backend test suite**

```bash
cd /Users/breannalinlin/Documents/code/jowo/mcp-tool && mvn test
```

Expected: `BUILD SUCCESS`, no failures.

- [ ] **Step 2: Start the app on the `local` profile**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Expected: starts cleanly on port `8088` (Flyway migration runs against local MySQL, no errors).

- [ ] **Step 3: Log in with the real SmartLink test account**

In a second terminal (credentials from `doc/api-doc/smartlink.md` in the `l7-skill-frontend` repo,
already committed there):

```bash
curl -s -X POST http://localhost:8088/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"sukimiya","password":"Jowoiot@123"}'
```

Expected: `200` with a JSON body containing `token`, `userId`, `name`. Save the token as `$TOKEN`.

- [ ] **Step 4: Confirm unauthenticated access is rejected**

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8088/api/conversations
```

Expected: `401`.

- [ ] **Step 5: Confirm a chat message creates a session owned by this user**

```bash
TOKEN=<paste from step 3>
curl -s -N -X POST http://localhost:8088/api/chat \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"conversationId":"smoke-test-1","message":"你好"}' | head -5

curl -s http://localhost:8088/api/conversations -H "Authorization: Bearer $TOKEN"
```

Expected: the second call returns a JSON array containing `{"id":"smoke-test-1", ...}`.

- [ ] **Step 6: Confirm cross-user access is blocked**

```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  http://localhost:8088/api/conversations/smoke-test-1/messages \
  -H "Authorization: Bearer not-a-real-token"
```

Expected: `401` (invalid token) — then repeat with a second SmartLink account's token (if available)
against `smoke-test-1` and confirm `404` (valid token, wrong owner).

- [ ] **Step 7: Stop the app**

Ctrl-C the `mvn spring-boot:run` process.

---

## Task 12: `api/client.ts` + `api/auth.ts`

**Repo:** `l7-skill-frontend`

**Files:**
- Create: `src/api/client.ts`
- Create: `src/api/auth.ts`
- Test: `src/__tests__/api/client.test.ts`
- Test: `src/__tests__/api/auth.test.ts`

**Interfaces:**
- Produces: `getToken()`, `setToken(token)`, `clearToken()`, `apiFetch(input, init?)`,
  `UnauthorizedError` from `client.ts`; `login(username, password)`, `fetchCurrentUser()`, `logout()`,
  `LoginError`, `interface AuthUser { token, userId, name }` from `auth.ts`. Tasks 13, 15, 17 consume
  these.

- [ ] **Step 1: Write the failing tests**

Create `src/__tests__/api/client.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiFetch, getToken, setToken, clearToken, UnauthorizedError } from '../../api/client';

describe('client', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('setToken/getToken/clearToken round-trip through localStorage', () => {
    expect(getToken()).toBeNull();
    setToken('tok-1');
    expect(getToken()).toBe('tok-1');
    clearToken();
    expect(getToken()).toBeNull();
  });

  it('apiFetch attaches Authorization header when a token is stored', async () => {
    setToken('tok-1');
    vi.stubGlobal('fetch', vi.fn(async () => ({ status: 200, ok: true })));

    await apiFetch('/api/conversations');

    const call = (globalThis.fetch as any).mock.calls[0];
    expect(call[1].headers.get('Authorization')).toBe('Bearer tok-1');
  });

  it('apiFetch omits Authorization header when no token is stored', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ status: 200, ok: true })));

    await apiFetch('/api/conversations');

    const call = (globalThis.fetch as any).mock.calls[0];
    expect(call[1].headers.get('Authorization')).toBeNull();
  });

  it('apiFetch clears the token and throws UnauthorizedError on 401', async () => {
    setToken('tok-1');
    vi.stubGlobal('fetch', vi.fn(async () => ({ status: 401, ok: false })));

    await expect(apiFetch('/api/conversations')).rejects.toBeInstanceOf(UnauthorizedError);
    expect(getToken()).toBeNull();
  });
});
```

Create `src/__tests__/api/auth.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { login, fetchCurrentUser, logout, LoginError } from '../../api/auth';
import { getToken, setToken } from '../../api/client';

describe('auth', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('login stores the returned token and resolves with the user', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ token: 'app-tok', userId: 'u1', name: '周琳琳' }),
    })));

    const user = await login('sukimiya', 'pw');

    expect(user.name).toBe('周琳琳');
    expect(getToken()).toBe('app-tok');
  });

  it('login throws LoginError on 401', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 401 })));

    await expect(login('sukimiya', 'wrong')).rejects.toThrow(LoginError);
  });

  it('login throws LoginError with a different message on 502', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 502 })));

    await expect(login('sukimiya', 'pw')).rejects.toMatchObject({ message: '登录服务暂时不可用' });
  });

  it('fetchCurrentUser calls /api/auth/me with the stored token', async () => {
    setToken('app-tok');
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ userId: 'u1', name: '周琳琳' }),
    })));

    const user = await fetchCurrentUser();

    expect(user.name).toBe('周琳琳');
    const call = (globalThis.fetch as any).mock.calls[0];
    expect(call[0]).toBe('/api/auth/me');
  });

  it('logout calls the backend then clears the local token', async () => {
    setToken('app-tok');
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 204 })));

    await logout();

    expect(getToken()).toBeNull();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/breannalinlin/Documents/code/jowo/l7-skill-frontend && npx vitest run src/__tests__/api/client.test.ts src/__tests__/api/auth.test.ts`
Expected: fails — modules don't exist yet.

- [ ] **Step 3: Write the implementation**

Create `src/api/client.ts`:

```ts
const TOKEN_KEY = 'auth-token';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

export class UnauthorizedError extends Error {
  constructor() {
    super('unauthorized');
  }
}

export async function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const token = getToken();
  const headers = new Headers(init.headers);
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const res = await fetch(input, { ...init, headers });
  if (res.status === 401) {
    clearToken();
    throw new UnauthorizedError();
  }
  return res;
}
```

Create `src/api/auth.ts`:

```ts
import { apiFetch, setToken, clearToken } from './client';

export interface AuthUser {
  token: string;
  userId: string;
  name: string;
}

export class LoginError extends Error {}

export async function login(username: string, password: string): Promise<AuthUser> {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) {
    const message = res.status === 401 ? '用户名或密码错误' : '登录服务暂时不可用';
    throw new LoginError(message);
  }
  const data = await res.json();
  setToken(data.token);
  return data;
}

export async function fetchCurrentUser(): Promise<{ userId: string; name: string }> {
  const res = await apiFetch('/api/auth/me');
  if (!res.ok) throw new Error('failed to fetch current user');
  return res.json();
}

export async function logout(): Promise<void> {
  await apiFetch('/api/auth/logout', { method: 'POST' });
  clearToken();
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run src/__tests__/api/client.test.ts src/__tests__/api/auth.test.ts`
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/api/client.ts src/api/auth.ts src/__tests__/api/client.test.ts src/__tests__/api/auth.test.ts
git commit -m "feat: add auth API client (login/me/logout, token-aware fetch)"
```

---

## Task 13: `store.ts` auth slice

**Repo:** `l7-skill-frontend`

**Files:**
- Modify: `src/store.ts`
- Modify: `src/__tests__/store.test.ts`

**Interfaces:**
- Consumes: `getToken`, `setToken`, `clearToken` (Task 12).
- Produces: on `useStore`: `token: string | null`, `userId: string | null`, `userName: string | null`,
  `setAuth({ token, userId, name })`, `clearAuth()`. Tasks 14, 17 consume these.

- [ ] **Step 1: Write the failing tests**

Append to `src/__tests__/store.test.ts` (after the existing `describe('store.pendingFiles', ...)`
block):

```ts
describe('store.auth', () => {
  beforeEach(() => {
    localStorage.clear();
    useStore.setState({ token: null, userId: null, userName: null });
  });

  it('starts with no auth when localStorage is empty', () => {
    expect(useStore.getState().token).toBeNull();
    expect(useStore.getState().userId).toBeNull();
  });

  it('setAuth stores the token/user in state and localStorage', () => {
    useStore.getState().setAuth({ token: 'app-tok', userId: 'u1', name: '周琳琳' });

    expect(useStore.getState().token).toBe('app-tok');
    expect(useStore.getState().userId).toBe('u1');
    expect(useStore.getState().userName).toBe('周琳琳');
    expect(localStorage.getItem('auth-token')).toBe('app-tok');
  });

  it('clearAuth removes the token/user from state and localStorage', () => {
    useStore.getState().setAuth({ token: 'app-tok', userId: 'u1', name: '周琳琳' });

    useStore.getState().clearAuth();

    expect(useStore.getState().token).toBeNull();
    expect(localStorage.getItem('auth-token')).toBeNull();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/__tests__/store.test.ts`
Expected: fails — `setAuth`/`clearAuth`/`token`/`userId`/`userName` don't exist on the store yet.

- [ ] **Step 3: Write the implementation**

Replace `src/store.ts` entirely:

```ts
import { create } from "zustand";
import { getToken, setToken, clearToken } from "./api/client";

export interface Conversation {
  id: string;
  title: string;
  createdAt: Date;
}

interface ChatState {
  token: string | null;
  userId: string | null;
  userName: string | null;
  setAuth: (auth: { token: string; userId: string; name: string }) => void;
  clearAuth: () => void;

  conversations: Conversation[];
  activeId: string | null;
  setActiveId: (id: string) => void;
  addConversation: (conv: Conversation) => void;

  pendingFiles: File[];
  addFiles: (files: File[]) => void;
  removeFile: (index: number) => void;
  clearFiles: () => void;
}

export const useStore = create<ChatState>((set) => ({
  token: getToken(),
  userId: localStorage.getItem("auth-user-id"),
  userName: localStorage.getItem("auth-user-name"),
  setAuth: ({ token, userId, name }) => {
    setToken(token);
    localStorage.setItem("auth-user-id", userId);
    localStorage.setItem("auth-user-name", name);
    set({ token, userId, userName: name });
  },
  clearAuth: () => {
    clearToken();
    localStorage.removeItem("auth-user-id");
    localStorage.removeItem("auth-user-name");
    set({ token: null, userId: null, userName: null });
  },

  conversations: [],
  activeId: null,
  setActiveId: (id) => set({ activeId: id }),
  addConversation: (conv) =>
    set((s) => ({ conversations: [conv, ...s.conversations] })),

  pendingFiles: [],
  addFiles: (files) => set((s) => ({ pendingFiles: [...s.pendingFiles, ...files] })),
  removeFile: (index) =>
    set((s) => ({ pendingFiles: s.pendingFiles.filter((_, i) => i !== index) })),
  clearFiles: () => set({ pendingFiles: [] }),
}));
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run src/__tests__/store.test.ts`
Expected: all tests pass (existing `store.pendingFiles` tests plus the new `store.auth` tests).

- [ ] **Step 5: Commit**

```bash
git add src/store.ts src/__tests__/store.test.ts
git commit -m "feat: add auth slice to store (token/userId/userName)"
```

---

## Task 14: `Login.tsx`

**Repo:** `l7-skill-frontend`

**Files:**
- Create: `src/Login.tsx`
- Test: `src/__tests__/Login.test.tsx`

**Interfaces:**
- Consumes: `login`, `LoginError` (Task 12), `useStore().setAuth` (Task 13).
- Produces: `export default function Login()`. Task 17 renders this when `token` is null.

- [ ] **Step 1: Write the failing tests**

Create `src/__tests__/Login.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import Login from '../Login';
import { useStore } from '../store';

describe('Login', () => {
  beforeEach(() => {
    localStorage.clear();
    useStore.setState({ token: null, userId: null, userName: null });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('logs in and updates the store on valid credentials', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ token: 'app-tok', userId: 'u1', name: '周琳琳' }),
    })));

    render(<Login />);
    fireEvent.change(screen.getByLabelText('用户名'), { target: { value: 'sukimiya' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'pw' } });
    fireEvent.click(screen.getByText('登录'));

    await waitFor(() => expect(useStore.getState().token).toBe('app-tok'));
    expect(useStore.getState().userName).toBe('周琳琳');
  });

  it('shows an error message on bad credentials', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 401 })));

    render(<Login />);
    fireEvent.change(screen.getByLabelText('用户名'), { target: { value: 'sukimiya' } });
    fireEvent.change(screen.getByLabelText('密码'), { target: { value: 'wrong' } });
    fireEvent.click(screen.getByText('登录'));

    await waitFor(() => expect(screen.getByText('用户名或密码错误')).toBeInTheDocument());
    expect(useStore.getState().token).toBeNull();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/__tests__/Login.test.tsx`
Expected: fails — `src/Login.tsx` doesn't exist yet.

- [ ] **Step 3: Write the implementation**

Create `src/Login.tsx`:

```tsx
import { useState } from "react";
import { login, LoginError } from "./api/auth";
import { useStore } from "./store";

export default function Login() {
  const setAuth = useStore((s) => s.setAuth);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password) return;
    setIsLoading(true);
    setError("");
    try {
      const user = await login(username.trim(), password);
      setAuth(user);
    } catch (err) {
      setError(err instanceof LoginError ? err.message : "登录失败，请重试");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex items-center justify-center h-screen bg-slate-50">
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-sm bg-white p-8 rounded-xl shadow-sm border border-slate-100"
      >
        <h1 className="text-lg font-semibold mb-1">Jowo Agent</h1>
        <p className="text-sm text-slate-400 mb-6">使用 SmartLink 账号登录</p>

        <label htmlFor="login-username" className="block text-sm text-slate-600 mb-1">用户名</label>
        <input
          id="login-username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          className="w-full px-3 py-2 mb-4 border border-slate-200 rounded-lg text-sm
            focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary/20"
          disabled={isLoading}
          autoFocus
        />

        <label htmlFor="login-password" className="block text-sm text-slate-600 mb-1">密码</label>
        <input
          id="login-password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="w-full px-3 py-2 mb-4 border border-slate-200 rounded-lg text-sm
            focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary/20"
          disabled={isLoading}
        />

        {error && <p className="text-xs text-red-500 mb-4">{error}</p>}

        <button
          type="submit"
          disabled={isLoading || !username.trim() || !password}
          className="w-full px-4 py-2.5 bg-primary text-white text-sm font-medium rounded-lg
            hover:bg-primary-light disabled:opacity-40 disabled:cursor-not-allowed
            transition-colors cursor-pointer"
        >
          {isLoading ? "登录中..." : "登录"}
        </button>
      </form>
    </div>
  );
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run src/__tests__/Login.test.tsx`
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/Login.tsx src/__tests__/Login.test.tsx
git commit -m "feat: add Login screen"
```

---

## Task 15: `api/conversations.ts`

**Repo:** `l7-skill-frontend`

**Files:**
- Create: `src/api/conversations.ts`
- Test: `src/__tests__/api/conversations.test.ts`

**Interfaces:**
- Consumes: `apiFetch` (Task 12).
- Produces: `interface ConversationSummary { id, title, updatedAt }`,
  `interface ConversationMessage { id, role, content }`,
  `listConversations(): Promise<ConversationSummary[]>`,
  `getConversationMessages(id): Promise<ConversationMessage[] | null>` (null on 404).
  Tasks 16, 17 consume these.

- [ ] **Step 1: Write the failing tests**

Create `src/__tests__/api/conversations.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { listConversations, getConversationMessages } from '../../api/conversations';

describe('conversations api', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('listConversations returns the parsed list', async () => {
    const fake = [{ id: 's1', title: '标题', updatedAt: '2026-07-08T00:00:00' }];
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => fake })));

    const result = await listConversations();

    expect(result).toEqual(fake);
  });

  it('getConversationMessages returns null on 404', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 404 })));

    const result = await getConversationMessages('missing');

    expect(result).toBeNull();
  });

  it('getConversationMessages returns the parsed messages on success', async () => {
    const fake = [{ id: 'msg-0', role: 'user', content: 'hi' }];
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => fake })));

    const result = await getConversationMessages('s1');

    expect(result).toEqual(fake);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/__tests__/api/conversations.test.ts`
Expected: fails — `src/api/conversations.ts` doesn't exist yet.

- [ ] **Step 3: Write the implementation**

Create `src/api/conversations.ts`:

```ts
import { apiFetch } from './client';

export interface ConversationSummary {
  id: string;
  title: string;
  updatedAt: string;
}

export interface ConversationMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
}

export async function listConversations(): Promise<ConversationSummary[]> {
  const res = await apiFetch('/api/conversations');
  if (!res.ok) throw new Error('failed to list conversations');
  return res.json();
}

export async function getConversationMessages(id: string): Promise<ConversationMessage[] | null> {
  const res = await apiFetch(`/api/conversations/${id}/messages`);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error('failed to fetch conversation messages');
  return res.json();
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run src/__tests__/api/conversations.test.ts`
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/api/conversations.ts src/__tests__/api/conversations.test.ts
git commit -m "feat: add conversations API client"
```

---

## Task 16: `Sidebar.tsx`

**Repo:** `l7-skill-frontend`

**Files:**
- Create: `src/Sidebar.tsx`
- Test: `src/__tests__/Sidebar.test.tsx`

**Interfaces:**
- Consumes: `listConversations` (Task 15).
- Produces: `export default function Sidebar({ activeId, onSelect, onNewChat, refreshKey })`. Task 17
  renders this.

- [ ] **Step 1: Write the failing tests**

Create `src/__tests__/Sidebar.test.tsx`:

```tsx
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import Sidebar from '../Sidebar';

describe('Sidebar', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('lists conversations fetched from the backend', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => [{ id: 's1', title: '第一次对话', updatedAt: new Date().toISOString() }],
    })));

    render(<Sidebar activeId="s1" onSelect={() => {}} onNewChat={() => {}} refreshKey={0} />);

    await waitFor(() => expect(screen.getByText('第一次对话')).toBeInTheDocument());
  });

  it('calls onSelect when a conversation is clicked', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({
      ok: true,
      status: 200,
      json: async () => [{ id: 's1', title: '第一次对话', updatedAt: new Date().toISOString() }],
    })));
    const onSelect = vi.fn();

    render(<Sidebar activeId="" onSelect={onSelect} onNewChat={() => {}} refreshKey={0} />);
    await waitFor(() => screen.getByText('第一次对话'));
    fireEvent.click(screen.getByText('第一次对话'));

    expect(onSelect).toHaveBeenCalledWith('s1');
  });

  it('calls onNewChat when the new-chat button is clicked', () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => [] })));
    const onNewChat = vi.fn();

    render(<Sidebar activeId="" onSelect={() => {}} onNewChat={onNewChat} refreshKey={0} />);
    fireEvent.click(screen.getByText('+ 新对话'));

    expect(onNewChat).toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/__tests__/Sidebar.test.tsx`
Expected: fails — `src/Sidebar.tsx` doesn't exist yet.

- [ ] **Step 3: Write the implementation**

Create `src/Sidebar.tsx`:

```tsx
import { useEffect, useState } from "react";
import { listConversations, type ConversationSummary } from "./api/conversations";

interface Props {
  activeId: string;
  onSelect: (id: string) => void;
  onNewChat: () => void;
  refreshKey: number;
}

function formatTime(iso: string): string {
  const d = new Date(iso);
  const now = new Date();
  const sameDay = d.toDateString() === now.toDateString();
  return sameDay
    ? d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    : d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' });
}

export default function Sidebar({ activeId, onSelect, onNewChat, refreshKey }: Props) {
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);

  useEffect(() => {
    listConversations().then(setConversations).catch(() => setConversations([]));
  }, [refreshKey]);

  return (
    <div className="w-64 shrink-0 border-r border-slate-200 bg-white flex flex-col h-full">
      <div className="p-3">
        <button
          onClick={onNewChat}
          className="w-full px-3 py-2 text-sm rounded-lg border border-slate-200
            hover:border-primary hover:text-primary transition-colors cursor-pointer"
        >
          + 新对话
        </button>
      </div>
      <div className="flex-1 overflow-y-auto px-2 space-y-1">
        {conversations.map((c) => (
          <button
            key={c.id}
            onClick={() => onSelect(c.id)}
            className={`w-full text-left px-3 py-2 rounded-lg text-sm truncate transition-colors cursor-pointer
              ${c.id === activeId ? "bg-indigo-50 text-primary" : "hover:bg-slate-50 text-slate-600"}`}
          >
            <div className="truncate">{c.title}</div>
            <div className="text-xs text-slate-400">{formatTime(c.updatedAt)}</div>
          </button>
        ))}
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run src/__tests__/Sidebar.test.tsx`
Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/Sidebar.tsx src/__tests__/Sidebar.test.tsx
git commit -m "feat: add Sidebar with conversation history list"
```

---

## Task 17: `App.tsx` integration

**Repo:** `l7-skill-frontend`

**Files:**
- Modify: `src/App.tsx`
- Modify: `src/api/upload.ts`
- Modify: `src/__tests__/App.test.tsx`

**Interfaces:**
- Consumes: `Login` (14), `Sidebar` (16), `api/auth.ts` (12), `api/conversations.ts` (15),
  `apiFetch`/`UnauthorizedError` (12), `useStore` auth slice (13).

- [ ] **Step 1: Update `api/upload.ts` to use the token-aware fetch**

Replace `src/api/upload.ts` entirely:

```ts
import { apiFetch } from './client';

export interface UploadedRef {
  name: string;
  path: string;
  size: number;
}

export class UploadError extends Error {
  code: string;
  reason: string;
  constructor(code: string, reason: string) {
    super(`${code}: ${reason}`);
    this.code = code;
    this.reason = reason;
  }
}

export async function uploadFiles(
  sessionId: string,
  files: File[],
): Promise<UploadedRef[]> {
  const form = new FormData();
  form.set('sessionId', sessionId);
  for (const f of files) form.append('files', f);

  const res = await apiFetch('/api/files/upload', {
    method: 'POST',
    body: form,
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ code: 'UNKNOWN', reason: res.statusText }));
    throw new UploadError(err.code ?? 'UNKNOWN', err.reason ?? '');
  }
  return res.json();
}
```

(`src/__tests__/api/upload.test.ts` needs no changes — it doesn't assert on request headers, and
`apiFetch` forwards method/body through unchanged.)

- [ ] **Step 2: Rewrite `App.test.tsx` for the new auth-gated behavior**

Replace `src/__tests__/App.test.tsx` entirely:

```tsx
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import App from '../App';
import { useStore } from '../store';

function mockFetchByUrl(handlers: Record<string, () => any>) {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    for (const [prefix, handler] of Object.entries(handlers)) {
      if (url.startsWith(prefix)) return handler();
    }
    return { ok: true, status: 200, json: async () => [] };
  }));
}

describe('App', () => {
  beforeEach(() => {
    localStorage.clear();
    window.history.replaceState(null, '', '/');
    useStore.setState({ token: null, userId: null, userName: null });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows the login screen when there is no stored auth token', () => {
    render(<App />);

    expect(screen.getByLabelText('用户名')).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('输入您的问题...')).not.toBeInTheDocument();
  });

  it('shows the chat UI when a valid token is present', async () => {
    useStore.getState().setAuth({ token: 'app-tok', userId: 'u1', name: '周琳琳' });
    mockFetchByUrl({
      '/api/auth/me': () => ({ ok: true, status: 200, json: async () => ({ userId: 'u1', name: '周琳琳' }) }),
      '/api/conversations/': () => ({ ok: false, status: 404 }),
      '/api/conversations': () => ({ ok: true, status: 200, json: async () => [] }),
    });

    render(<App />);

    await waitFor(() => expect(screen.getByText('Jowo Agent')).toBeInTheDocument());
    expect(screen.getByPlaceholderText('输入您的问题...')).toBeInTheDocument();
    expect(screen.getByText('周琳琳')).toBeInTheDocument();
  });

  it('clears auth and shows login when the stored token is invalid', async () => {
    useStore.getState().setAuth({ token: 'stale-tok', userId: 'u1', name: '周琳琳' });
    mockFetchByUrl({
      '/api/auth/me': () => ({ ok: false, status: 401 }),
    });

    render(<App />);

    await waitFor(() => expect(screen.getByLabelText('用户名')).toBeInTheDocument());
  });
});
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `npx vitest run src/__tests__/App.test.tsx`
Expected: fails — `App.tsx` doesn't yet gate on auth or use the new APIs.

- [ ] **Step 4: Rewrite `App.tsx`**

Replace `src/App.tsx` entirely:

```tsx
import { useState, useCallback, useEffect, useRef } from "react";
import Chat from "./Chat";
import Login from "./Login";
import Sidebar from "./Sidebar";
import { uploadFiles, UploadError } from "./api/upload";
import { apiFetch, UnauthorizedError } from "./api/client";
import { fetchCurrentUser, logout as apiLogout } from "./api/auth";
import { getConversationMessages } from "./api/conversations";
import { useStore } from "./store";
import { genId } from "./lib/id";

interface UIMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  attachments?: { name: string; path: string }[];
}

function getInitialConvId(): string {
  const params = new URLSearchParams(window.location.search);
  return params.get("id") ?? genId();
}

export default function App() {
  const token = useStore((s) => s.token);
  const userName = useStore((s) => s.userName);
  const clearAuth = useStore((s) => s.clearAuth);
  const pendingFiles = useStore((s) => s.pendingFiles);
  const clearFiles = useStore((s) => s.clearFiles);

  const [convId, setConvId] = useState<string>(getInitialConvId);
  const [messages, setMessages] = useState<UIMessage[]>([]);
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [sidebarRefreshKey, setSidebarRefreshKey] = useState(0);
  const messagesRef = useRef(messages);

  useEffect(() => { messagesRef.current = messages; }, [messages]);

  // Validate the stored token on mount; drop it if it's stale.
  useEffect(() => {
    if (!token) return;
    fetchCurrentUser().catch(() => clearAuth());
  }, [token]);

  // Update URL without reload
  useEffect(() => {
    const url = new URL(window.location.href);
    url.searchParams.set("id", convId);
    window.history.replaceState(null, "", url.toString());
  }, [convId]);

  // Load this conversation's history from the backend whenever we switch to it.
  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    getConversationMessages(convId)
      .then((msgs) => {
        if (cancelled) return;
        setMessages((msgs ?? []).map((m) => ({ id: m.id, role: m.role, content: m.content })));
      })
      .catch(() => { if (!cancelled) setMessages([]); });
    return () => { cancelled = true; };
  }, [convId, token]);

  const send = useCallback(async (text?: string) => {
    const msg = (text ?? input).trim();
    if ((!msg && pendingFiles.length === 0) || isLoading) return;

    setInput("");
    setIsLoading(true);

    let uploadedRefs: { name: string; path: string }[] = [];
    if (pendingFiles.length > 0) {
      try {
        uploadedRefs = await uploadFiles(convId, pendingFiles);
      } catch (e) {
        const reason = e instanceof UploadError ? e.message : "上传失败";
        setMessages((prev) => [...prev, {
          id: genId(),
          role: "assistant",
          content: `文件上传失败：${reason}`,
        }]);
        setIsLoading(false);
        return;
      }
      clearFiles();
    }

    const displayContent = msg || "（已上传文件）";
    const apiContent = uploadedRefs.length > 0
      ? `[已上传文件]\n${uploadedRefs.map(r => `- ${r.path}`).join("\n")}\n\n${msg}`
      : msg;

    const uid = genId();
    const aid = genId();
    const newMsgs: UIMessage[] = [
      ...messagesRef.current,
      { id: uid, role: "user", content: displayContent, attachments: uploadedRefs },
      { id: aid, role: "assistant", content: "" },
    ];
    setMessages(newMsgs);

    try {
      const res = await apiFetch("/api/chat/completions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          model: "jowo-agent",
          messages: [{ role: "user", content: apiContent }],
          conversationId: convId,
          stream: true,
        }),
      });

      if (!res.body) throw new Error("No stream body");
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() || "";

        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed.startsWith("data:")) continue;
          const jsonStr = trimmed.slice(5).trim();
          if (!jsonStr || jsonStr === "[DONE]") continue;
          try {
            const c = JSON.parse(jsonStr);
            const content = c?.choices?.[0]?.delta?.content || "";
            if (content) {
              setMessages((prev) => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                updated[updated.length - 1] = { ...last, content: last.content + content };
                return updated;
              });
            }
          } catch { /* skip */ }
        }
      }
      setSidebarRefreshKey((k) => k + 1);
    } catch (err) {
      const message = err instanceof UnauthorizedError
        ? "登录已过期，请重新登录"
        : "请求失败: " + (err instanceof Error ? err.message : "未知错误");
      setMessages((prev) => {
        const updated = [...prev];
        updated[updated.length - 1] = { ...updated[updated.length - 1], content: message };
        return updated;
      });
    } finally {
      setIsLoading(false);
    }
  }, [input, isLoading, pendingFiles, clearFiles, convId]);

  const newChat = () => {
    setConvId(genId());
    setMessages([]);
  };

  const handleLogout = async () => {
    try { await apiLogout(); } catch { /* best-effort */ }
    clearAuth();
  };

  if (!token) {
    return <Login />;
  }

  return (
    <div className="flex h-screen bg-slate-50">
      <Sidebar
        activeId={convId}
        onSelect={(id) => setConvId(id)}
        onNewChat={newChat}
        refreshKey={sidebarRefreshKey}
      />
      <div className="flex flex-col flex-1">
        <header className="flex items-center gap-3 px-5 py-3 bg-slate-900 text-white shrink-0">
          <span className="text-lg">🔧</span>
          <h1 className="text-base font-semibold">Jowo Agent</h1>
          <span className="text-xs text-slate-400 ml-2">企业 API 查询助手</span>
          <div className="ml-auto flex items-center gap-3">
            <span className="text-xs text-slate-400">{userName}</span>
            <button
              onClick={handleLogout}
              className="text-xs px-2 py-1 rounded bg-slate-700 hover:bg-slate-600 transition-colors cursor-pointer"
            >
              退出登录
            </button>
          </div>
        </header>
        <div className="flex-1 overflow-hidden">
          <Chat
            messages={messages}
            isLoading={isLoading}
            input={input}
            onInputChange={setInput}
            onSubmit={send}
          />
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `npx vitest run src/__tests__/App.test.tsx src/__tests__/api/upload.test.ts`
Expected: all tests pass.

- [ ] **Step 6: Run the full frontend test suite**

Run: `npm test`
Expected: all tests pass (existing `store.test.ts`, `Attachment.test.tsx`, `DownloadButton.test.tsx`,
`upload.test.ts` plus everything added in Tasks 12–17).

- [ ] **Step 7: Commit**

```bash
git add src/App.tsx src/api/upload.ts src/__tests__/App.test.tsx
git commit -m "feat: gate App on auth, wire Sidebar + backend-sourced message history"
```

---

## Task 18: Frontend manual verification

**Repo:** `l7-skill-frontend`

No new files — manual browser verification against the real backend from Task 11.

- [ ] **Step 1: Start the backend**

```bash
cd /Users/breannalinlin/Documents/code/jowo/mcp-tool && mvn spring-boot:run -Dspring-boot.run.profiles=local
```

- [ ] **Step 2: Start the frontend dev server**

```bash
cd /Users/breannalinlin/Documents/code/jowo/l7-skill-frontend && npm run dev
```

- [ ] **Step 3: Open `http://localhost:5173` in a browser**

Expected: login screen appears (not the chat UI).

- [ ] **Step 4: Log in with `sukimiya` / `Jowoiot@123`**

Expected: redirected into the chat UI, username shown top-right, empty sidebar (no conversations yet).

- [ ] **Step 5: Send a message, then reload the page**

Expected: the message and its reply persist across reload (loaded from the backend, not
localStorage); the conversation now appears in the sidebar with a title derived from the first
message.

- [ ] **Step 6: Click "+ 新对话", send a message, then switch back to the first conversation via the sidebar**

Expected: both conversations are listed; switching correctly swaps the visible message history for
each; the URL `?id=` updates to match the selected conversation.

- [ ] **Step 7: Manually edit the URL to a conversation id that doesn't exist (e.g. `?id=does-not-exist`)**

Expected: chat area shows no messages (not an error), and sending a message in it creates a brand new
session under that id, owned by the current user.

- [ ] **Step 8: Send 25+ messages in one conversation**

Expected: no truncation — all messages remain visible after reload (verifies the removed 20-message
cap).

- [ ] **Step 9: Click "退出登录"**

Expected: returns to the login screen; reloading the page does not silently log back in.

- [ ] **Step 10: Stop both dev servers**

Ctrl-C both `npm run dev` and `mvn spring-boot:run`.
