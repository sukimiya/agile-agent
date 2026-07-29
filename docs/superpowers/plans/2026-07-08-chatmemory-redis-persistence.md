# ChatMemory Redis Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `ChatMemory` (per-conversation LLM chat history) persist to Redis instead of an in-process `ConcurrentHashMap`, so history survives app restarts, while keeping its exact current trimming behavior.

**Architecture:** Add `spring-boot-starter-data-redis` and wire real Redis connection info for `local` (matching what's already in the Nacos-managed `dev` config from the prior Nacos-integration work). Rewrite `ChatMemory` to keep its exact public API (`get`/`append`) but back it with a single `StringRedisTemplate` String key per owner, holding the whole trimmed history JSON-serialized — a near 1:1 port of the existing in-memory merge+trim logic, not a redesign.

**Tech Stack:** Spring Boot 3.5.0, Spring Data Redis (Lettuce client), Jackson (already on the classpath via `spring-boot-starter-web`), JUnit 5, AssertJ, Mockito.

## Global Constraints

- No dual in-memory/Redis implementation — `ChatMemory` has exactly one backing implementation (Redis). `local` profile also requires a real reachable Redis; there is no fallback branch.
- No Redis-native List storage (`RPUSH`/`LTRIM`) — one `String` key per owner holding the whole history as a JSON array.
- No `WATCH`/`MULTI`/Lua atomicity — plain `GET` then `SET`, matching the existing in-memory version's actual concurrency guarantees (none beyond what `ConcurrentHashMap.compute()` gave, which is now a two-round-trip operation instead of in-process).
- Key format: `chatmemory:{ownerId}`. No `EXPIRE`/TTL is ever set.
- `local` and `dev` share Redis database index `0`. Tests use database index `1` for isolation. Redis connection: host `192.168.3.232`, port `6379`, password `FeetxXuFkZIaM2Pq`.
- `spring-boot-starter-data-redis` + `commons-pool2` are the only new dependencies (matches `crm-service`'s `pom.xml` pattern for Lettuce pooling).
- `ChatMemoryTest` must NOT use `@SpringBootTest` or a `src/test/resources/application.yml` — that shadowing footgun was already hit and reverted during the Nacos-integration work (a test-scoped `application.yml` fully replaces, not merges with, `src/main/resources/application.yml` on the test classpath). Construct the `StringRedisTemplate` directly in the test instead.

---

## File Structure

- **Modify `pom.xml`** — add `spring-boot-starter-data-redis` + `commons-pool2`.
- **Modify `src/main/resources/application-local.yml`** — add a `spring.data.redis.*` block pointing at the shared Redis, database `0`.
- **Modify `src/main/java/com/jowo/agent/service/ChatMemory.java`** — swap `ConcurrentHashMap` internals for `StringRedisTemplate` + Jackson, same public API.
- **Modify `src/test/java/com/jowo/agent/service/ChatMemoryTest.java`** — construct a real `StringRedisTemplate` (database `1`) directly, same 5 test methods, same assertions.
- **Modify `src/test/java/com/jowo/agent/service/AgentServiceTest.java`** — replace all 6 `new ChatMemory()` call sites with `mock(ChatMemory.class)`. Required in the same commit as the `ChatMemory` constructor change: `AgentServiceTest` currently calls the no-arg constructor, which will no longer exist, so leaving this for a later task would leave `mvn test` unable to even compile.

---

### Task 1: Add Redis dependency and local connection config

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application-local.yml`

**Interfaces:**
- Produces: `spring-boot-starter-data-redis` + `commons-pool2` on the classpath, and a working `spring.data.redis.*` property set for the `local` profile — both consumed by Task 2's `ChatMemory` rewrite and its test.

- [ ] **Step 1: Confirm current test baseline passes**

Run: `mvn -q test`
Expected: `BUILD SUCCESS` (49 tests, all green — this is the state left after the Nacos-integration work).

- [ ] **Step 2: Add the two dependencies**

In `pom.xml`, inside the existing `<dependencies>` block, add (after the existing `mysql-connector-j`/`h2` entries added by the prior Nacos work, before `</dependencies>`):

```xml
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-redis</artifactId>
		</dependency>
		<dependency>
			<groupId>org.apache.commons</groupId>
			<artifactId>commons-pool2</artifactId>
		</dependency>
```

- [ ] **Step 3: Verify dependency resolution**

Run: `mvn -q dependency:tree -Dincludes=org.springframework.boot:spring-boot-starter-data-redis,org.apache.commons:commons-pool2,io.lettuce:lettuce-core`
Expected: prints a tree containing `spring-boot-starter-data-redis`, `commons-pool2`, and `lettuce-core` (Lettuce is pulled in transitively by the starter) — no `BUILD FAILURE`.

- [ ] **Step 4: Verify existing tests still pass with the dependency present**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`, same 49 tests. Nothing references Redis yet, and unlike the earlier JDBC/Nacos additions, Spring Data Redis's `RedisAutoConfiguration` does not require any property to be set — it defaults to `localhost:6379` and Lettuce connects lazily (on first actual command, not at startup), so simply adding the dependency cannot break context loading.

- [ ] **Step 5: Add local Redis connection config**

At the end of `src/main/resources/application-local.yml`, merge a new `data.redis` block into the existing top-level `spring:` key (do not create a second top-level `spring:` key — add these lines as siblings of the existing `spring.datasource` block):

```yaml
  data:
    redis:
      host: 192.168.3.232
      port: 6379
      password: FeetxXuFkZIaM2Pq
      database: 0
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: -1ms
```

- [ ] **Step 6: Verify local profile still starts and now reaches the real Redis**

Run: `mvn spring-boot:run -Dspring-boot.run.profiles=local`
Expected: reaches `Started AgentApplication` (same as before — no code uses Redis yet, so this just proves the YAML is valid and nothing eagerly fails). Stop with Ctrl-C once confirmed.

- [ ] **Step 7: Commit**

```bash
git add pom.xml
git commit -m "$(cat <<'EOF'
build: add Redis dependency and local connection config

Prep for ChatMemory's Redis-backed persistence. Nothing reads this
yet — RedisAutoConfiguration connects lazily, so this alone doesn't
change runtime behavior.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Rewrite ChatMemory to persist to Redis

**Files:**
- Modify: `src/main/java/com/jowo/agent/service/ChatMemory.java`
- Modify: `src/test/java/com/jowo/agent/service/ChatMemoryTest.java`
- Modify: `src/test/java/com/jowo/agent/service/AgentServiceTest.java`

**Interfaces:**
- Consumes: `spring.data.redis.*` config from Task 1 (for `ChatMemoryTest`'s own manually-constructed `StringRedisTemplate`, pointed at database `1` instead of the app's database `0`).
- Produces: `ChatMemory(StringRedisTemplate redis)` — a new required constructor argument. `get(String ownerId): List<LlmClient.Message>` and `append(String ownerId, List<LlmClient.Message> newMessages): void` keep their exact existing signatures and behavior — this is what `AgentService` (unchanged) continues to call.

This task changes the `ChatMemory` constructor, so anything that calls `new ChatMemory()` breaks. That's `ChatMemoryTest` (needs real Redis — that's the whole point of this task) and, separately, `AgentServiceTest` (uses `ChatMemory` only as an incidental collaborator for testing `AgentService`'s tool-use loop — mocking it there is more correct than spinning up Redis for 6 unrelated tests, and matches how `LlmClient`/`EnterpriseApiClient`/`ApiCatalog` are already mocked in that same file). Both must be fixed in this task, or `mvn test` won't even compile.

- [ ] **Step 1: Confirm this environment can reach the shared test Redis**

Run: `nc -z -w3 192.168.3.232 6379 && echo REACHABLE || echo UNREACHABLE`
Expected: `REACHABLE`. If not reachable from wherever this task runs, stop and report BLOCKED — the rest of this task requires a real Redis connection, per the design's explicit choice not to mock Redis in `ChatMemoryTest`.

- [ ] **Step 2: Rewrite `ChatMemoryTest.java` to use a real Redis connection (this will fail to compile until Step 4)**

Replace the entire contents of `src/test/java/com/jowo/agent/service/ChatMemoryTest.java` with:

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class ChatMemoryTest {

    private static final Set<String> OWNER_IDS_USED = Set.of("nobody", "conv-1", "conv-2");

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private ChatMemory memory;

    @BeforeEach
    void setUp() {
        var config = new RedisStandaloneConfiguration("192.168.3.232", 6379);
        config.setPassword("FeetxXuFkZIaM2Pq");
        config.setDatabase(1);
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        memory = new ChatMemory(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        for (String ownerId : OWNER_IDS_USED) {
            redisTemplate.delete("chatmemory:" + ownerId);
        }
        connectionFactory.destroy();
    }

    @Test
    void getOnUnknownOwnerReturnsEmptyList() {
        assertThat(memory.get("nobody")).isEmpty();
    }

    @Test
    void appendThenGetReturnsAppendedMessagesInOrder() {
        memory.append("conv-1", List.of(LlmClient.Message.user("hi")));
        memory.append("conv-1", List.of(LlmClient.Message.assistantText("hello")));

        assertThat(memory.get("conv-1")).extracting(LlmClient.Message::content)
                .containsExactly("hi", "hello");
    }

    @Test
    void appendTrimsToLast20Messages() {
        for (int i = 0; i < 25; i++) {
            memory.append("conv-1", List.of(LlmClient.Message.user("msg-" + i)));
        }

        var history = memory.get("conv-1");

        assertThat(history).hasSize(20);
        assertThat(history.get(0).content()).isEqualTo("msg-5");
        assertThat(history.get(19).content()).isEqualTo("msg-24");
    }

    @Test
    void appendDropsOrphanedLeadingToolMessageAfterTrim() {
        var toolCall = new LlmClient.ToolCall("call-1", "getApiDetail", "{}");
        memory.append("conv-1", List.of(
                LlmClient.Message.user("first turn"),
                LlmClient.Message.assistantToolCalls(List.of(toolCall)),
                LlmClient.Message.toolResult("call-1", "detail text")));
        for (int i = 0; i < 19; i++) {
            memory.append("conv-1", List.of(LlmClient.Message.user("msg-" + i)));
        }

        var history = memory.get("conv-1");

        assertThat(history).noneMatch(m -> "tool".equals(m.role()));
        assertThat(history.get(0).content()).isEqualTo("msg-0");
        assertThat(history).hasSize(19);
    }

    @Test
    void ownersAreIsolatedFromEachOther() {
        memory.append("conv-1", List.of(LlmClient.Message.user("from conv-1")));
        memory.append("conv-2", List.of(LlmClient.Message.user("from conv-2")));

        assertThat(memory.get("conv-1")).extracting(LlmClient.Message::content).containsExactly("from conv-1");
        assertThat(memory.get("conv-2")).extracting(LlmClient.Message::content).containsExactly("from conv-2");
    }
}
```

Note: `new StringRedisTemplate(connectionFactory)` already calls `setConnectionFactory` and `afterPropertiesSet()` internally (it's the convenience constructor Spring Data Redis provides for exactly this kind of manual, non-Spring-managed construction) — do not call `redisTemplate.afterPropertiesSet()` again separately.

- [ ] **Step 3: Run to confirm it fails to compile (RED)**

Run: `mvn -q test-compile`
Expected: `BUILD FAILURE` — compile error on `ChatMemory.java`'s test callers, e.g. `constructor ChatMemory in class ChatMemory cannot be applied to given types` for `new ChatMemory(redisTemplate)`, since the production class still has a no-arg constructor. This confirms the test is exercising the new constructor shape before it exists.

- [ ] **Step 4: Rewrite `ChatMemory.java`**

Replace the entire contents of `src/main/java/com/jowo/agent/service/ChatMemory.java` with:

```java
package com.jowo.agent.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ChatMemory {

    private static final int MAX_MESSAGES = 20;
    private static final String KEY_PREFIX = "chatmemory:";
    private static final TypeReference<List<LlmClient.Message>> MESSAGE_LIST_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();

    public ChatMemory(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public List<LlmClient.Message> get(String ownerId) {
        return readList(ownerId);
    }

    public void append(String ownerId, List<LlmClient.Message> newMessages) {
        var merged = new ArrayList<>(readList(ownerId));
        merged.addAll(newMessages);
        int from = Math.max(0, merged.size() - MAX_MESSAGES);
        // A "tool" message must immediately follow the assistant message that issued its
        // tool_calls; if the size-based cut lands between that pair, skip forward past any
        // leading orphaned "tool" messages so the retained history is never invalid.
        while (from < merged.size() && "tool".equals(merged.get(from).role())) {
            from++;
        }
        writeList(ownerId, List.copyOf(merged.subList(from, merged.size())));
    }

    private List<LlmClient.Message> readList(String ownerId) {
        String raw = redis.opsForValue().get(KEY_PREFIX + ownerId);
        if (raw == null) {
            return List.of();
        }
        try {
            return json.readValue(raw, MESSAGE_LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt chat memory JSON for owner " + ownerId, e);
        }
    }

    private void writeList(String ownerId, List<LlmClient.Message> messages) {
        try {
            redis.opsForValue().set(KEY_PREFIX + ownerId, json.writeValueAsString(messages));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize chat memory for owner " + ownerId, e);
        }
    }
}
```

This is a direct port of the previous `ConcurrentHashMap`-based merge+trim logic (same trim algorithm, same comment explaining the orphaned-tool-message skip) — only the storage backend changed.

- [ ] **Step 5: Run `ChatMemoryTest` to confirm it passes (GREEN)**

Run: `mvn -q test -Dtest=ChatMemoryTest`
Expected: `BUILD FAILURE` still — at this point `AgentServiceTest.java` (unchanged so far) still calls the old `new ChatMemory()` no-arg constructor, so the module won't compile yet. This is expected; proceed to Step 6 before re-running.

- [ ] **Step 6: Fix `AgentServiceTest.java` to mock `ChatMemory` instead of constructing a real one**

In `src/test/java/com/jowo/agent/service/AgentServiceTest.java`, replace all 6 occurrences of:

```java
        ChatMemory memory = new ChatMemory();
```

with:

```java
        ChatMemory memory = mock(ChatMemory.class);
```

(`mock` is already statically imported via `import static org.mockito.Mockito.*;` at the top of this file — no new import needed.) Every other line in this file stays exactly as-is.

- [ ] **Step 7: Run the full suite to confirm everything compiles and passes (GREEN)**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`, 49 tests total (same count as before — no tests added or removed, `ChatMemory`'s 5 tests and `AgentService`'s 6 tests all still present). Confirm `ChatMemoryTest`'s 5 tests specifically passed against the real Redis (database `1`) — re-run `mvn -q test -Dtest=ChatMemoryTest -DfailIfNoTests=true` and check for `Tests run: 5, Failures: 0, Errors: 0`.

- [ ] **Step 8: Manually verify no leftover keys after a full test run**

Run: `redis-cli -h 192.168.3.232 -p 6379 -a 'FeetxXuFkZIaM2Pq' -n 1 KEYS 'chatmemory:*'` (the `-a` password flag will print a harmless "Warning: Using a password with '-a'..." line to stderr — ignore it)
Expected: empty output — confirms `ChatMemoryTest`'s `@AfterEach` cleanup actually deletes every key it wrote, leaving database `1` clean for the next run (whether by you or a colleague).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/jowo/agent/service/ChatMemory.java src/test/java/com/jowo/agent/service/ChatMemoryTest.java src/test/java/com/jowo/agent/service/AgentServiceTest.java
git commit -m "$(cat <<'EOF'
feat: persist ChatMemory to Redis instead of in-process memory

Same public API and trim behavior (20-message cap, skip leading
orphaned tool messages, per-owner isolation) — only the backing store
changed, from ConcurrentHashMap to a single JSON-blob String key per
owner in Redis (chatmemory:{ownerId}). No TTL: history is kept until
the 20-message cap prunes it, matching current behavior.

ChatMemoryTest now runs against the real shared Redis (database 1,
isolated from the app's own database 0) instead of a bare in-memory
instance — no @SpringBootTest, no test-scoped application.yml (that
shadows rather than merges with the real one on the test classpath).

AgentServiceTest switches from a real ChatMemory instance to
mock(ChatMemory.class), since those tests exercise AgentService's
tool-use loop, not ChatMemory itself — consistent with how
LlmClient/EnterpriseApiClient/ApiCatalog are already mocked in that
file, and removes an unrelated Redis dependency from 6 tests that
don't need one.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```
