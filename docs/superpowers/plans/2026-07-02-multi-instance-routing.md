> **ARCHIVED (2026-07-06):** This plan's approach (OpenClaw per-container via Portainer) was rejected on security grounds
> (arbitrary code execution, unvetted third-party runtime, container supply chain/isolation). The implementation it
> describes has been removed from `main` and preserved on branch `archive/openclaw-multi-instance`. See
> `docs/superpowers/specs/2026-07-06-in-backend-agent-design.md` for the replacement design.

---

# Multi-Instance OpenClaw Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let `mcp-tool` remember every Portainer-provisioned OpenClaw instance and route each `conversationId` to the right one, replacing the single shared local embedded agent.

**Architecture:** A new file-backed `InstanceRegistry` persists `{instanceName -> {url, gatewayToken, ...}}` and `{conversationId -> instanceName}`. `InstanceService.deploy` writes into it after a successful Portainer provision. `ChatController` resolves the binding before opening the SSE stream (404 if unbound) and hands the resolved record to a rewritten `AgentService` that shells out to `openclaw gateway call agent --url ws://... --token ... --expect-final --json` instead of the old `openclaw agent --local` path. The frontend gains an instance picker shown before a new conversation starts.

**Tech Stack:** Java 21, Spring Boot 3.5.0 (Spring MVC, WebMvcTest, JUnit 5, Mockito, AssertJ), Jackson (already on the classpath via `spring-boot-starter-web`), React 19 + Vite + TypeScript + vitest + @testing-library/react (all already in `web/package.json`).

## Global Constraints

- Java 21 / Spring Boot 3.5.0 — do not bump versions.
- No new Maven or npm dependencies — Jackson's `ObjectMapper`/`JsonNode` are already transitively available (used today in `ChatController`/`PortainerClient`); vitest + testing-library are already in `web/package.json`.
- Never log `gatewayToken` or `deepseekApiKey`, matching the existing rule in `InstanceService`/`PortainerClient`.
- `instances.json` registry file defaults to `${user.home}/.jowo-agent/instances.json` — outside the repo, so no `.gitignore` entry is needed.
- The local embedded `openclaw agent --local` path is fully removed — every chat call requires a resolved instance binding, no fallback.
- No auto-retry and no auto-instance-switch on remote-call failure — surface the error once.
- `openclaw gateway call agent ... --timeout 120000` — keep this equal to the existing local-path timeout (`process.waitFor(120, TimeUnit.SECONDS)`).
- Follow existing code style exactly: constructor injection, package-private Spring components where the existing sibling files use package-private (e.g. `ChatController`, `InstanceController`), records for DTOs.

---

## File Map

| File | Change |
|---|---|
| `src/main/resources/application.yml` | add `app.instance-registry.path` |
| `src/main/java/com/jowo/agent/service/InstanceRegistry.java` | **new** — file-backed registry |
| `src/test/java/com/jowo/agent/service/InstanceRegistryTest.java` | **new** |
| `src/main/java/com/jowo/agent/service/InstanceService.java` | `deploy(...)` gains `conversationId`, writes to registry |
| `src/test/java/com/jowo/agent/service/InstanceServiceTest.java` | update for new signature + registry assertions |
| `src/main/java/com/jowo/agent/controller/InstanceController.java` | `conversationId` field, new `GET /api/instances` |
| `src/test/java/com/jowo/agent/controller/InstanceControllerTest.java` | update + new test |
| `src/main/java/com/jowo/agent/service/AgentService.java` | rewrite: remote `gateway call agent` instead of local embedded |
| `src/test/java/com/jowo/agent/service/AgentServiceTest.java` | **new** — unit tests for the pure helper methods |
| `src/main/java/com/jowo/agent/controller/ChatController.java` | resolve binding, 404 if unbound, new bind endpoint |
| `src/test/java/com/jowo/agent/controller/ChatControllerTest.java` | **new** |
| `web/src/lib/id.ts` | **new** — `genId()` extracted from `App.tsx` |
| `web/src/api/instances.ts` | **new** — typed API client |
| `web/src/__tests__/api-instances.test.ts` | **new** |
| `web/src/InstancePicker.tsx` | **new** — new-chat instance picker modal |
| `web/src/__tests__/InstancePicker.test.tsx` | **new** |
| `web/src/App.tsx` | wire picker into new-chat flow, show bound instance, handle 404 |
| `web/src/__tests__/App.test.tsx` | **new** |

---

### Task 1: `InstanceRegistry` — file-backed instance + binding store

**Files:**
- Modify: `src/main/resources/application.yml`
- Create: `src/main/java/com/jowo/agent/service/InstanceRegistry.java`
- Test: `src/test/java/com/jowo/agent/service/InstanceRegistryTest.java`

**Interfaces:**
- Produces: `InstanceRegistry` (`@Service`) with:
  - `record InstanceRecord(String instanceName, String containerId, String url, String gatewayToken, String status, String createdAt)`
  - `record InstanceSummary(String instanceName, String status, String createdAt)`
  - `class UnknownInstanceException extends RuntimeException`
  - `void save(InstanceRecord record)`
  - `void bind(String conversationId, String instanceName)` — throws `UnknownInstanceException` if `instanceName` isn't registered
  - `Optional<InstanceRecord> resolve(String conversationId)`
  - `List<InstanceSummary> list()`

- [ ] **Step 1: Add the config property**

Edit `src/main/resources/application.yml`, add a new top-level `app.instance-registry` block (alongside the existing `app.upload` block):

```yaml
app:
  instance-registry:
    path: ${user.home}/.jowo-agent/instances.json
  upload:
    dir: ${user.home}/.openclaw/workspace/uploads
```

- [ ] **Step 2: Write the failing test**

Create `src/test/java/com/jowo/agent/service/InstanceRegistryTest.java`:

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstanceRegistryTest {

    @TempDir
    Path tmp;

    private InstanceRegistry newRegistry() {
        return new InstanceRegistry(tmp.resolve("sub/instances.json").toString());
    }

    @Test
    void saveThenResolveAfterBind() {
        var registry = newRegistry();
        var record = new InstanceRegistry.InstanceRecord(
                "demo", "cid1", "http://host:1234", "tok", "running", "2026-07-02T10:00:00Z");

        registry.save(record);
        registry.bind("conv-1", "demo");

        assertThat(registry.resolve("conv-1")).contains(record);
        assertThat(registry.resolve("conv-unknown")).isEmpty();
    }

    @Test
    void listReturnsSummariesWithoutToken() {
        var registry = newRegistry();
        registry.save(new InstanceRegistry.InstanceRecord(
                "demo", "cid1", "http://host:1234", "secret-tok", "running", "2026-07-02T10:00:00Z"));

        var summaries = registry.list();

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).instanceName()).isEqualTo("demo");
        assertThat(summaries.get(0).status()).isEqualTo("running");
    }

    @Test
    void bindUnknownInstanceThrows() {
        var registry = newRegistry();
        assertThatThrownBy(() -> registry.bind("conv-1", "nope"))
                .isInstanceOf(InstanceRegistry.UnknownInstanceException.class);
    }

    @Test
    void survivesReloadFromDisk() {
        var path = tmp.resolve("sub/instances.json").toString();
        var first = new InstanceRegistry(path);
        first.save(new InstanceRegistry.InstanceRecord(
                "demo", "cid1", "http://host:1234", "tok", "running", "2026-07-02T10:00:00Z"));
        first.bind("conv-1", "demo");

        var reloaded = new InstanceRegistry(path);

        assertThat(reloaded.resolve("conv-1").map(InstanceRegistry.InstanceRecord::instanceName))
                .contains("demo");
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `mvn -q -Dtest=InstanceRegistryTest test`
Expected: compilation failure — `InstanceRegistry` does not exist yet.

- [ ] **Step 4: Implement `InstanceRegistry`**

Create `src/main/java/com/jowo/agent/service/InstanceRegistry.java`:

```java
package com.jowo.agent.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class InstanceRegistry {

    private final Path file;
    private final ObjectMapper json = new ObjectMapper();
    private final ReentrantLock lock = new ReentrantLock();
    private Map<String, InstanceRecord> instances;
    private Map<String, String> bindings;

    public record InstanceRecord(String instanceName, String containerId, String url,
                                  String gatewayToken, String status, String createdAt) {}

    public record InstanceSummary(String instanceName, String status, String createdAt) {}

    private record FileContents(Map<String, InstanceRecord> instances, Map<String, String> bindings) {}

    public static class UnknownInstanceException extends RuntimeException {
        public UnknownInstanceException(String instanceName) {
            super("unknown instance: " + instanceName);
        }
    }

    public InstanceRegistry(@Value("${app.instance-registry.path}") String path) {
        this.file = Path.of(path);
        load();
    }

    private void load() {
        if (Files.exists(file)) {
            try {
                var contents = json.readValue(file.toFile(), FileContents.class);
                instances = new LinkedHashMap<>(contents.instances());
                bindings = new LinkedHashMap<>(contents.bindings());
                return;
            } catch (IOException e) {
                throw new IllegalStateException("failed to read instance registry at " + file, e);
            }
        }
        instances = new LinkedHashMap<>();
        bindings = new LinkedHashMap<>();
    }

    private void persist() {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            var tmp = file.resolveSibling(file.getFileName() + ".tmp");
            json.writerWithDefaultPrettyPrinter()
                    .writeValue(tmp.toFile(), new FileContents(instances, bindings));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write instance registry at " + file, e);
        }
    }

    public void save(InstanceRecord record) {
        lock.lock();
        try {
            instances.put(record.instanceName(), record);
            persist();
        } finally {
            lock.unlock();
        }
    }

    public void bind(String conversationId, String instanceName) {
        lock.lock();
        try {
            if (!instances.containsKey(instanceName)) throw new UnknownInstanceException(instanceName);
            bindings.put(conversationId, instanceName);
            persist();
        } finally {
            lock.unlock();
        }
    }

    public Optional<InstanceRecord> resolve(String conversationId) {
        lock.lock();
        try {
            var name = bindings.get(conversationId);
            if (name == null) return Optional.empty();
            return Optional.ofNullable(instances.get(name));
        } finally {
            lock.unlock();
        }
    }

    public List<InstanceSummary> list() {
        lock.lock();
        try {
            return instances.values().stream()
                    .map(r -> new InstanceSummary(r.instanceName(), r.status(), r.createdAt()))
                    .toList();
        } finally {
            lock.unlock();
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q -Dtest=InstanceRegistryTest test`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/application.yml src/main/java/com/jowo/agent/service/InstanceRegistry.java src/test/java/com/jowo/agent/service/InstanceRegistryTest.java
git commit -m "feat: add file-backed InstanceRegistry for instance/conversation bindings"
```

---

### Task 2: `InstanceService.deploy` persists to the registry

**Files:**
- Modify: `src/main/java/com/jowo/agent/service/InstanceService.java`
- Test: `src/test/java/com/jowo/agent/service/InstanceServiceTest.java`

**Interfaces:**
- Consumes: `InstanceRegistry` (Task 1) — `save(InstanceRecord)`, `bind(String, String)`.
- Produces: `InstanceService.deploy(String instanceName, String deepseekApiKey, String conversationId)` — same as before plus the new required `conversationId` param; throws `IllegalArgumentException` if `conversationId` is null/blank (same pattern as the existing `instanceName`/`deepseekApiKey` checks). `InstanceResult` shape unchanged.

- [ ] **Step 1: Update the test to the new signature and add registry assertions**

Replace `src/test/java/com/jowo/agent/service/InstanceServiceTest.java` in full:

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import com.jowo.agent.config.PortainerProperties;

class InstanceServiceTest {

    private final PortainerProperties props = new PortainerProperties(
            "http://192.168.3.232:19002", "ptr_abc", 1,
            "192.168.3.119/jowosmart/openclaw-base:test");

    @Test
    void deployCreatesStartsAndReturnsUrl() {
        PortainerClient client = mock(PortainerClient.class);
        InstanceRegistry registry = mock(InstanceRegistry.class);
        when(client.createContainer(eq("openclaw-demo"), any())).thenReturn("cid1");
        when(client.inspectHostPort("cid1")).thenReturn("49155");
        var service = new InstanceService(client, props, registry);

        var r = service.deploy("Demo", "sk-secret", "conv-1");

        assertThat(r.url()).isEqualTo("http://192.168.3.232:49155");
        assertThat(r.containerId()).isEqualTo("cid1");
        assertThat(r.gatewayToken()).isNotBlank();
        verify(client).pullImage("192.168.3.119/jowosmart/openclaw-base:test");
        verify(client).startContainer("cid1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createBodyCarriesEnvVolumeAndPublishAll() {
        PortainerClient client = mock(PortainerClient.class);
        InstanceRegistry registry = mock(InstanceRegistry.class);
        when(client.createContainer(any(), any())).thenReturn("cid1");
        when(client.inspectHostPort(any())).thenReturn("40000");
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        var service = new InstanceService(client, props, registry);

        service.deploy("demo", "sk-secret", "conv-1");

        verify(client).createContainer(eq("openclaw-demo"), captor.capture());
        Map<String,Object> body = captor.getValue();
        assertThat((java.util.List<String>) body.get("Env"))
            .anyMatch(e -> e.equals("DEEPSEEK_API_KEY=sk-secret"))
            .anyMatch(e -> e.startsWith("OPENCLAW_GATEWAY_TOKEN="));
        Map<String,Object> host = (Map<String,Object>) body.get("HostConfig");
        assertThat(host.get("PublishAllPorts")).isEqualTo(true);
        assertThat((java.util.List<String>) host.get("Binds"))
            .contains("openclaw-demo:/root/.openclaw");
    }

    @Test
    void sanitizeNameLowercasesAndStrips() {
        assertThat(InstanceService.sanitizeName("My Demo #1")).isEqualTo("my-demo-1");
    }

    @Test
    void deploySavesAndBindsInRegistry() {
        PortainerClient client = mock(PortainerClient.class);
        InstanceRegistry registry = mock(InstanceRegistry.class);
        when(client.createContainer(any(), any())).thenReturn("cid1");
        when(client.inspectHostPort(any())).thenReturn("49155");
        var service = new InstanceService(client, props, registry);

        service.deploy("demo", "sk-secret", "conv-1");

        var captor = org.mockito.ArgumentCaptor.forClass(InstanceRegistry.InstanceRecord.class);
        verify(registry).save(captor.capture());
        assertThat(captor.getValue().instanceName()).isEqualTo("demo");
        assertThat(captor.getValue().url()).isEqualTo("http://192.168.3.232:49155");
        verify(registry).bind("conv-1", "demo");
    }

    @Test
    void deployRejectsBlankConversationId() {
        PortainerClient client = mock(PortainerClient.class);
        InstanceRegistry registry = mock(InstanceRegistry.class);
        var service = new InstanceService(client, props, registry);

        assertThatThrownBy(() -> service.deploy("demo", "sk-secret", " "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=InstanceServiceTest test`
Expected: compilation failure — `InstanceService` constructor and `deploy` don't match the 3-arg registry / `conversationId` usage yet.

- [ ] **Step 3: Update `InstanceService`**

Replace `src/main/java/com/jowo/agent/service/InstanceService.java` in full:

```java
package com.jowo.agent.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.jowo.agent.config.PortainerProperties;

@Service
public class InstanceService {

    private final PortainerClient portainer;
    private final PortainerProperties props;
    private final InstanceRegistry registry;
    private static final SecureRandom RNG = new SecureRandom();

    public InstanceService(PortainerClient portainer, PortainerProperties props, InstanceRegistry registry) {
        this.portainer = portainer;
        this.props = props;
        this.registry = registry;
    }

    public record InstanceResult(String instanceName, String containerId, String url,
                                 String gatewayToken, String status) {}

    public InstanceResult deploy(String instanceName, String deepseekApiKey, String conversationId) {
        String name = sanitizeName(instanceName);
        if (name.isEmpty()) throw new IllegalArgumentException("instanceName is empty after sanitizing");
        if (deepseekApiKey == null || deepseekApiKey.isBlank())
            throw new IllegalArgumentException("deepseekApiKey is required");
        if (conversationId == null || conversationId.isBlank())
            throw new IllegalArgumentException("conversationId is required");

        String container = "openclaw-" + name;
        String token = newToken();

        portainer.pullImage(props.image());

        Map<String, Object> body = Map.of(
            "Image", props.image(),
            "Env", List.of(
                "DEEPSEEK_API_KEY=" + deepseekApiKey,
                "OPENCLAW_GATEWAY_TOKEN=" + token,
                "OPENCLAW_BIND=lan"),
            "ExposedPorts", Map.of("18789/tcp", Map.of()),
            "HostConfig", Map.of(
                "PublishAllPorts", true,
                "RestartPolicy", Map.of("Name", "unless-stopped"),
                "Binds", List.of(container + ":/root/.openclaw"))
        );

        String id = portainer.createContainer(container, body);
        portainer.startContainer(id);

        String port = portainer.inspectHostPort(id);
        for (int i = 0; port == null && i < 5; i++) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            port = portainer.inspectHostPort(id);
        }
        if (port == null) throw new IllegalStateException("container started but no host port was bound");

        String host = props.baseUrl().replaceAll("^https?://", "").replaceAll(":\\d+$", "");
        String url = "http://" + host + ":" + port;

        registry.save(new InstanceRegistry.InstanceRecord(
                name, id, url, token, "running", Instant.now().toString()));
        registry.bind(conversationId, name);

        return new InstanceResult(name, id, url, token, "running");
    }

    public static String sanitizeName(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase()
                  .replaceAll("[^a-z0-9-]", "-")
                  .replaceAll("-+", "-")
                  .replaceAll("^-|-$", "");
    }

    private static String newToken() {
        byte[] b = new byte[24];
        RNG.nextBytes(b);
        return java.util.HexFormat.of().formatHex(b);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=InstanceServiceTest test`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/service/InstanceService.java src/test/java/com/jowo/agent/service/InstanceServiceTest.java
git commit -m "feat: InstanceService.deploy persists and binds via InstanceRegistry"
```

---

### Task 3: `InstanceController` — `conversationId` field + `GET /api/instances`

**Files:**
- Modify: `src/main/java/com/jowo/agent/controller/InstanceController.java`
- Test: `src/test/java/com/jowo/agent/controller/InstanceControllerTest.java`

**Interfaces:**
- Consumes: `InstanceService.deploy(String, String, String)` (Task 2); `InstanceRegistry.list()` (Task 1).
- Produces: `POST /api/instances` request body now requires `conversationId`; new `GET /api/instances` → `List<InstanceRegistry.InstanceSummary>`.

- [ ] **Step 1: Update the test**

Replace `src/test/java/com/jowo/agent/controller/InstanceControllerTest.java` in full:

```java
package com.jowo.agent.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import com.jowo.agent.service.InstanceRegistry;
import com.jowo.agent.service.InstanceService;
import com.jowo.agent.service.PortainerClient;

@WebMvcTest(InstanceController.class)
class InstanceControllerTest {

    @Autowired MockMvc mvc;
    @MockBean InstanceService service;
    @MockBean InstanceRegistry registry;

    @Test
    void deployReturns200WithUrl() throws Exception {
        when(service.deploy("demo", "sk-x", "conv-1")).thenReturn(
            new InstanceService.InstanceResult("demo", "cid1",
                "http://192.168.3.232:49155", "tok", "running"));
        mvc.perform(post("/api/instances").contentType("application/json")
                .content("{\"instanceName\":\"demo\",\"deepseekApiKey\":\"sk-x\",\"conversationId\":\"conv-1\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.url").value("http://192.168.3.232:49155"))
           .andExpect(jsonPath("$.containerId").value("cid1"));
    }

    @Test
    void badRequestOnInvalidArgs() throws Exception {
        when(service.deploy(any(), any(), any())).thenThrow(new IllegalArgumentException("bad"));
        mvc.perform(post("/api/instances").contentType("application/json")
                .content("{\"instanceName\":\"\",\"deepseekApiKey\":\"\",\"conversationId\":\"\"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void conflictMappedFromPortainer409() throws Exception {
        when(service.deploy(any(), any(), any()))
            .thenThrow(new PortainerClient.PortainerException("name in use", 409));
        mvc.perform(post("/api/instances").contentType("application/json")
                .content("{\"instanceName\":\"demo\",\"deepseekApiKey\":\"sk-x\",\"conversationId\":\"conv-1\"}"))
           .andExpect(status().isConflict());
    }

    @Test
    void listReturnsRegistrySummaries() throws Exception {
        when(registry.list()).thenReturn(List.of(
            new InstanceRegistry.InstanceSummary("demo", "running", "2026-07-02T10:00:00Z")));

        mvc.perform(get("/api/instances"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].instanceName").value("demo"))
           .andExpect(jsonPath("$[0].status").value("running"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=InstanceControllerTest test`
Expected: compilation failure — `deploy(String,String,String)` and `GET /api/instances` don't exist yet.

- [ ] **Step 3: Update `InstanceController`**

Replace `src/main/java/com/jowo/agent/controller/InstanceController.java` in full:

```java
package com.jowo.agent.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.jowo.agent.service.InstanceRegistry;
import com.jowo.agent.service.InstanceService;
import com.jowo.agent.service.PortainerClient;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
class InstanceController {

    private final InstanceService instances;
    private final InstanceRegistry registry;

    InstanceController(InstanceService instances, InstanceRegistry registry) {
        this.instances = instances;
        this.registry = registry;
    }

    record CreateInstanceRequest(String instanceName, String deepseekApiKey, String conversationId) {}

    @PostMapping("/instances")
    InstanceService.InstanceResult create(@RequestBody CreateInstanceRequest req) {
        try {
            return instances.deploy(req.instanceName(), req.deepseekApiKey(), req.conversationId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (PortainerClient.PortainerException e) {
            HttpStatus s = switch (e.status) {
                case 409 -> HttpStatus.CONFLICT;
                case 401, 403 -> HttpStatus.UNAUTHORIZED;
                default -> HttpStatus.BAD_GATEWAY;
            };
            throw new ResponseStatusException(s, "Portainer error");
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/instances")
    List<InstanceRegistry.InstanceSummary> list() {
        return registry.list();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=InstanceControllerTest test`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/controller/InstanceController.java src/test/java/com/jowo/agent/controller/InstanceControllerTest.java
git commit -m "feat: require conversationId on POST /api/instances, add GET /api/instances"
```

---

### Task 4: `AgentService` — route to the remote Gateway instead of the local embedded agent

**Files:**
- Modify: `src/main/java/com/jowo/agent/service/AgentService.java`
- Test: `src/test/java/com/jowo/agent/service/AgentServiceTest.java`

**Interfaces:**
- Consumes: `InstanceRegistry.InstanceRecord` (Task 1) — `url()`, `gatewayToken()`.
- Produces: `AgentService.chat(InstanceRegistry.InstanceRecord instance, String conversationId, String userMessage) -> Flux<String>` (signature changed — was `chat(String conversationId, String userMessage)`). Also exposes two package-private static helpers used by later tasks' understanding of behavior (not consumed elsewhere, but documented for the test): `buildCommand(...)` and `extractAnswer(...)`.

This task shells process execution out to `openclaw gateway call agent`. I verified this manually against a live local gateway before writing this plan:
- Success: exit code `0`, stdout is a single JSON document whose assistant text lives at `result.meta.finalAssistantVisibleText`, and `status` is `"ok"`.
- Failure (bad token, unreachable, etc.): exit code `1`, empty stdout, error text on stderr.
- Without `--expect-final` the call returns immediately with `{"status":"accepted"}` and no token-level streaming — hence this design accepts "thinking → one full answer" (see the design doc).

`Process`/`ProcessBuilder` are not mockable (final classes, no seams), and the pre-existing `AgentService` had zero unit tests for the same reason (relies on manual E2E — see both design docs' "Manual E2E" sections). This task keeps that precedent: pure logic (command construction, response parsing) is extracted into static package-private methods and unit tested directly; the `chat(...)` method's actual process spawning is exercised via manual E2E, not a unit test.

- [ ] **Step 1: Write the failing test for the pure helpers**

Create `src/test/java/com/jowo/agent/service/AgentServiceTest.java`:

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

class AgentServiceTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void buildCommandTargetsWsUrlAndCarriesParams() throws Exception {
        var instance = new InstanceRegistry.InstanceRecord(
                "demo", "cid1", "http://192.168.3.232:49155", "tok-secret", "running", "2026-07-02T10:00:00Z");

        var cmd = AgentService.buildCommand(instance, "conv-1", "hello there");

        assertThat(cmd).containsSubsequence("--url", "ws://192.168.3.232:49155");
        assertThat(cmd).containsSubsequence("--token", "tok-secret");
        assertThat(cmd).contains("--expect-final", "--json");
        assertThat(cmd).containsSubsequence("--timeout", "120000");

        int paramsIdx = cmd.indexOf("--params") + 1;
        var params = json.readTree(cmd.get(paramsIdx));
        assertThat(params.path("message").asText()).isEqualTo("hello there");
        assertThat(params.path("sessionKey").asText()).isEqualTo("agent:main:conv-1");
        assertThat(params.path("idempotencyKey").asText()).isNotBlank();
    }

    @Test
    void extractAnswerReadsFinalAssistantVisibleText() {
        String stdout = """
            {"runId":"r1","status":"ok","summary":"completed",
             "result":{"meta":{"finalAssistantVisibleText":"pong"}}}
            """;

        assertThat(AgentService.extractAnswer(stdout, 0)).isEqualTo("pong");
    }

    @Test
    void extractAnswerReturnsNullOnNonZeroExit() {
        assertThat(AgentService.extractAnswer("{\"status\":\"ok\"}", 1)).isNull();
    }

    @Test
    void extractAnswerReturnsNullOnMalformedJson() {
        assertThat(AgentService.extractAnswer("not json", 0)).isNull();
    }

    @Test
    void extractAnswerReturnsNullWhenStatusNotOk() {
        String stdout = "{\"status\":\"error\",\"result\":{\"meta\":{\"finalAssistantVisibleText\":\"x\"}}}";
        assertThat(AgentService.extractAnswer(stdout, 0)).isNull();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=AgentServiceTest test`
Expected: compilation failure — `AgentService.buildCommand`/`extractAnswer` don't exist yet.

- [ ] **Step 3: Rewrite `AgentService`**

Replace `src/main/java/com/jowo/agent/service/AgentService.java` in full:

```java
package com.jowo.agent.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    public Flux<String> chat(InstanceRegistry.InstanceRecord instance, String conversationId, String userMessage) {
        return Flux.create(sink -> {
            Process process = null;
            try {
                sink.next("{\"type\":\"thinking\",\"content\":\"正在处理...\"}\n");
                sink.next("{\"type\":\"answer_start\"}\n");

                List<String> cmd = buildCommand(instance, conversationId, userMessage);
                process = new ProcessBuilder(cmd).start();

                var stdout = new StringBuilder();
                var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                var thread = new Thread(() -> {
                    try {
                        int c;
                        while ((c = reader.read()) != -1) stdout.append((char) c);
                    } catch (Exception e) {
                        log.debug("Read interrupted: {}", e.getMessage());
                    }
                });
                thread.start();

                var exited = process.waitFor(125, TimeUnit.SECONDS);
                thread.join(5000);
                reader.close();

                if (!exited) {
                    process.destroyForcibly();
                    sink.next("{\"type\":\"error\",\"content\":\"实例无法访问，请重试或换实例\"}\n");
                    sink.next("{\"type\":\"done\"}\n");
                    sink.complete();
                    return;
                }

                String answer = extractAnswer(stdout.toString(), process.exitValue());
                if (answer == null) {
                    sink.next("{\"type\":\"error\",\"content\":\"实例无法访问，请重试或换实例\"}\n");
                } else {
                    sink.next(answer + "\n");
                }
                sink.next("{\"type\":\"done\"}\n");
                sink.complete();

            } catch (Exception e) {
                log.error("Agent error", e);
                if (process != null) process.destroyForcibly();
                sink.next("{\"type\":\"error\",\"content\":\"处理出错: " + e.getMessage() + "\"}\n");
                sink.complete();
            }
        });
    }

    static List<String> buildCommand(InstanceRegistry.InstanceRecord instance, String conversationId, String userMessage) {
        try {
            String wsUrl = instance.url().replaceFirst("^http", "ws");
            var params = JSON.createObjectNode();
            params.put("message", userMessage);
            params.put("sessionKey", "agent:main:" + conversationId);
            params.put("idempotencyKey", UUID.randomUUID().toString());
            return List.of("openclaw", "gateway", "call", "agent",
                    "--url", wsUrl,
                    "--token", instance.gatewayToken(),
                    "--params", JSON.writeValueAsString(params),
                    "--expect-final",
                    "--json",
                    "--timeout", "120000");
        } catch (Exception e) {
            throw new IllegalStateException("failed to build gateway call command", e);
        }
    }

    static String extractAnswer(String stdout, int exitCode) {
        if (exitCode != 0) return null;
        try {
            JsonNode root = JSON.readTree(stdout);
            if (!"ok".equals(root.path("status").asText())) return null;
            String text = root.path("result").path("meta").path("finalAssistantVisibleText").asText(null);
            return (text == null || text.isBlank()) ? null : text;
        } catch (Exception e) {
            return null;
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=AgentServiceTest test`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/service/AgentService.java src/test/java/com/jowo/agent/service/AgentServiceTest.java
git commit -m "feat: AgentService routes chat turns through the remote instance's Gateway"
```

---

### Task 5: `ChatController` — resolve binding, 404 if unbound, new bind endpoint

**Files:**
- Modify: `src/main/java/com/jowo/agent/controller/ChatController.java`
- Test: `src/test/java/com/jowo/agent/controller/ChatControllerTest.java`

**Interfaces:**
- Consumes: `InstanceRegistry.resolve(String) -> Optional<InstanceRecord>` and `bind(String, String)` (Task 1); `AgentService.chat(InstanceRecord, String, String) -> Flux<String>` (Task 4).
- Produces: `POST /api/conversations/{conversationId}/instance` (new) — body `{instanceName}`, 200 on success, 404 if `instanceName` unknown. `POST /api/chat/completions` and `POST /api/chat` now return 404 (via `ResponseStatusException`, thrown before the `Flux` is returned) when `conversationId` has no binding. Also introduces a testable static helper `toContentChunk(String line) -> Optional<String>` that turns `AgentService`'s internal `{"type":...}` control lines into either a visible-content string (for `error`) or nothing (for `thinking`/`answer_start`/`done`), fixing the pre-existing gap where `error` lines were silently dropped by the old unconditional filter.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/jowo/agent/controller/ChatControllerTest.java`:

```java
package com.jowo.agent.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import com.jowo.agent.service.AgentService;
import com.jowo.agent.service.InstanceRegistry;
import reactor.core.publisher.Flux;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AgentService agentService;
    @MockBean InstanceRegistry registry;

    @Test
    void chatCompletionsReturns404WhenUnbound() throws Exception {
        when(registry.resolve("conv-unbound")).thenReturn(Optional.empty());

        mvc.perform(post("/api/chat/completions").contentType("application/json")
                .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"conversationId\":\"conv-unbound\"}"))
           .andExpect(status().isNotFound());
    }

    @Test
    void legacyChatReturns404WhenUnbound() throws Exception {
        when(registry.resolve("conv-unbound")).thenReturn(Optional.empty());

        mvc.perform(post("/api/chat").contentType("application/json")
                .content("{\"conversationId\":\"conv-unbound\",\"message\":\"hi\"}"))
           .andExpect(status().isNotFound());
    }

    @Test
    void bindInstanceReturns404OnUnknownInstance() throws Exception {
        doThrow(new InstanceRegistry.UnknownInstanceException("nope"))
                .when(registry).bind("conv-1", "nope");

        mvc.perform(post("/api/conversations/conv-1/instance").contentType("application/json")
                .content("{\"instanceName\":\"nope\"}"))
           .andExpect(status().isNotFound());
    }

    @Test
    void bindInstanceReturns200OnKnownInstance() throws Exception {
        mvc.perform(post("/api/conversations/conv-1/instance").contentType("application/json")
                .content("{\"instanceName\":\"demo\"}"))
           .andExpect(status().isOk());

        verify(registry).bind("conv-1", "demo");
    }

    @Test
    void chatCompletionsStreamsWhenBound() throws Exception {
        var instance = new InstanceRegistry.InstanceRecord(
                "demo", "cid1", "http://host:1234", "tok", "running", "2026-07-02T10:00:00Z");
        when(registry.resolve("conv-1")).thenReturn(Optional.of(instance));
        when(agentService.chat(eq(instance), eq("conv-1"), eq("hi")))
                .thenReturn(Flux.just("hello back"));

        mvc.perform(post("/api/chat/completions").contentType("application/json")
                .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"conversationId\":\"conv-1\"}"))
           .andExpect(status().isOk());
    }

    @Test
    void toContentChunkDropsControlLinesExceptError() {
        assertThat(ChatController.toContentChunk("plain text")).contains("plain text");
        assertThat(ChatController.toContentChunk("{\"type\":\"thinking\",\"content\":\"x\"}")).isEmpty();
        assertThat(ChatController.toContentChunk("{\"type\":\"done\"}")).isEmpty();
        assertThat(ChatController.toContentChunk("{\"type\":\"error\",\"content\":\"boom\"}")).contains("boom");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=ChatControllerTest test`
Expected: compilation failure — `ChatController` doesn't take `InstanceRegistry` yet, no bind endpoint, no `toContentChunk`.

- [ ] **Step 3: Rewrite `ChatController`**

Replace `src/main/java/com/jowo/agent/controller/ChatController.java` in full:

```java
package com.jowo.agent.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jowo.agent.service.AgentService;
import com.jowo.agent.service.InstanceRegistry;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
class ChatController {

    private final AgentService agentService;
    private final InstanceRegistry registry;
    private static final ObjectMapper json = new ObjectMapper();

    ChatController(AgentService agentService, InstanceRegistry registry) {
        this.agentService = agentService;
        this.registry = registry;
    }

    private InstanceRegistry.InstanceRecord resolveOrThrow(String conversationId) {
        return registry.resolve(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "conversation not bound to an instance: " + conversationId));
    }

    // OpenAI-compatible chat completions endpoint
    @PostMapping(value = "/chat/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<String> chatCompletions(@RequestBody Map<String, Object> body) {
        var messages = getMessages(body);
        var userMsg = messages.isEmpty() ? "" :
                messages.get(messages.size() - 1).getOrDefault("content", "");

        // Use conversationId from frontend for session continuity across requests
        var convId = body.getOrDefault("conversationId", UUID.randomUUID().toString()).toString();
        var instance = resolveOrThrow(convId);
        var chunkId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);

        return agentService.chat(instance, convId, userMsg)
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
    Flux<String> chat(@RequestBody ChatRequest request) {
        var conversationId = request.conversationId() != null
                ? request.conversationId()
                : UUID.randomUUID().toString();
        var instance = resolveOrThrow(conversationId);
        return agentService.chat(instance, conversationId, request.message());
    }

    @PostMapping("/conversations/{conversationId}/instance")
    void bindInstance(@PathVariable String conversationId, @RequestBody BindInstanceRequest req) {
        try {
            registry.bind(conversationId, req.instanceName());
        } catch (InstanceRegistry.UnknownInstanceException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/conversations")
    List<ConversationSummary> listConversations() {
        return List.of();
    }

    @GetMapping("/conversations/{id}")
    Object getConversation(@PathVariable String id) {
        return null;
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

    record BindInstanceRequest(String instanceName) {}

    record ConversationSummary(String id, String title, LocalDateTime updatedAt) {}

    record ServiceInfo(String code, String name, String basePath) {}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=ChatControllerTest test`
Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 5: Run the full backend test suite**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`, all tests (including Tasks 1-5's plus pre-existing `PortainerClientTest`, `PortainerPropertiesTest`, `UploadControllerTest`, `FileStorageServiceTest`, `UploadCleanupTaskTest`) pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/jowo/agent/controller/ChatController.java src/test/java/com/jowo/agent/controller/ChatControllerTest.java
git commit -m "feat: ChatController requires a resolved instance binding before chatting"
```

---

### Task 6: `web/src/lib/id.ts` — shared id generator

**Files:**
- Create: `web/src/lib/id.ts`
- Modify: `web/src/App.tsx:1-20` (remove the local `genId`, import the shared one)

**Interfaces:**
- Produces: `genId(): string` — identical behavior to the function currently inlined in `App.tsx` (`Date.now().toString(36) + Math.random().toString(36).slice(2, 8)`).

- [ ] **Step 1: Create the shared module**

Create `web/src/lib/id.ts`:

```typescript
export function genId(): string {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}
```

- [ ] **Step 2: Point `App.tsx` at it**

In `web/src/App.tsx`, remove the local `genId` definition (lines 13-15):

```typescript
function genId() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
}
```

and add an import at the top of the file instead:

```typescript
import { genId } from "./lib/id";
```

(This step alone is a pure refactor — `App.tsx`'s further restructuring happens in Task 9. Verify the app still builds after this step.)

- [ ] **Step 3: Verify the build still passes**

Run: `cd web && npx tsc -b --noEmit`
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
cd web
git add src/lib/id.ts src/App.tsx
git commit -m "refactor: extract genId into web/src/lib/id.ts"
```

---

### Task 7: `web/src/api/instances.ts` — typed API client

**Files:**
- Create: `web/src/api/instances.ts`
- Test: `web/src/__tests__/api-instances.test.ts`

**Interfaces:**
- Produces:
  - `interface InstanceSummary { instanceName: string; status: string; createdAt: string }`
  - `listInstances(): Promise<InstanceSummary[]>` — `GET /api/instances`
  - `createInstance(params: { instanceName: string; deepseekApiKey: string; conversationId: string }): Promise<{ instanceName: string; url: string; status: string }>` — `POST /api/instances`
  - `bindInstance(conversationId: string, instanceName: string): Promise<void>` — `POST /api/conversations/{conversationId}/instance`
  - `class InstanceApiError extends Error { status: number }` — thrown on any non-OK response

- [ ] **Step 1: Write the failing test**

Create `web/src/__tests__/api-instances.test.ts`:

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { listInstances, createInstance, bindInstance, InstanceApiError } from '../api/instances';

describe('api/instances', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  it('listInstances GETs /api/instances', async () => {
    (fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => [{ instanceName: 'demo', status: 'running', createdAt: '2026-07-02T10:00:00Z' }],
    });

    const result = await listInstances();

    expect(fetch).toHaveBeenCalledWith('/api/instances');
    expect(result).toEqual([{ instanceName: 'demo', status: 'running', createdAt: '2026-07-02T10:00:00Z' }]);
  });

  it('createInstance POSTs the right body', async () => {
    (fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({ instanceName: 'demo', url: 'http://host:1234', status: 'running' }),
    });

    await createInstance({ instanceName: 'demo', deepseekApiKey: 'sk-x', conversationId: 'conv-1' });

    expect(fetch).toHaveBeenCalledWith('/api/instances', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ instanceName: 'demo', deepseekApiKey: 'sk-x', conversationId: 'conv-1' }),
    });
  });

  it('bindInstance POSTs to /api/conversations/{id}/instance', async () => {
    (fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({ ok: true, json: async () => ({}) });

    await bindInstance('conv-1', 'demo');

    expect(fetch).toHaveBeenCalledWith('/api/conversations/conv-1/instance', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ instanceName: 'demo' }),
    });
  });

  it('throws InstanceApiError with status on non-ok response', async () => {
    (fetch as unknown as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: false,
      status: 404,
      json: async () => ({ message: 'unknown instance' }),
    });

    await expect(bindInstance('conv-1', 'nope')).rejects.toBeInstanceOf(InstanceApiError);
    await expect(bindInstance('conv-1', 'nope')).rejects.toMatchObject({ status: 404 });
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd web && npx vitest run src/__tests__/api-instances.test.ts`
Expected: FAIL — `../api/instances` module not found.

- [ ] **Step 3: Implement the API client**

Create `web/src/api/instances.ts`:

```typescript
export interface InstanceSummary {
  instanceName: string;
  status: string;
  createdAt: string;
}

export interface CreatedInstance {
  instanceName: string;
  containerId: string;
  url: string;
  gatewayToken: string;
  status: string;
}

export class InstanceApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function unwrap<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new InstanceApiError(res.status, body.message ?? res.statusText);
  }
  return res.json();
}

export async function listInstances(): Promise<InstanceSummary[]> {
  const res = await fetch('/api/instances');
  return unwrap<InstanceSummary[]>(res);
}

export async function createInstance(params: {
  instanceName: string;
  deepseekApiKey: string;
  conversationId: string;
}): Promise<CreatedInstance> {
  const res = await fetch('/api/instances', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  });
  return unwrap<CreatedInstance>(res);
}

export async function bindInstance(conversationId: string, instanceName: string): Promise<void> {
  const res = await fetch(`/api/conversations/${conversationId}/instance`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ instanceName }),
  });
  await unwrap<unknown>(res);
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd web && npx vitest run src/__tests__/api-instances.test.ts`
Expected: `4 passed`

- [ ] **Step 5: Commit**

```bash
cd web
git add src/api/instances.ts src/__tests__/api-instances.test.ts
git commit -m "feat: add typed API client for instance listing/creation/binding"
```

---

### Task 8: `web/src/InstancePicker.tsx` — new-chat instance picker

**Files:**
- Create: `web/src/InstancePicker.tsx`
- Test: `web/src/__tests__/InstancePicker.test.tsx`

**Interfaces:**
- Consumes: `listInstances`, `createInstance`, `bindInstance`, `InstanceApiError` from `../api/instances` (Task 7); `genId` from `../lib/id` (Task 6).
- Produces: `export default function InstancePicker(props: { onBound: (conversationId: string, instanceName: string) => void }): JSX.Element`. Calling `onBound` is the only way this component hands control back — it does not manage `conversationId` state itself beyond generating one to pass up.

- [ ] **Step 1: Write the failing test**

Create `web/src/__tests__/InstancePicker.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import InstancePicker from '../InstancePicker';
import * as api from '../api/instances';

describe('InstancePicker', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('lists existing instances and binds on selection', async () => {
    vi.spyOn(api, 'listInstances').mockResolvedValue([
      { instanceName: 'demo', status: 'running', createdAt: '2026-07-02T10:00:00Z' },
    ]);
    const bindSpy = vi.spyOn(api, 'bindInstance').mockResolvedValue(undefined);
    const onBound = vi.fn();

    render(<InstancePicker onBound={onBound} />);

    const option = await screen.findByText('demo');
    await userEvent.click(option);
    const confirm = screen.getByRole('button', { name: '进入对话' });
    await userEvent.click(confirm);

    await waitFor(() => expect(bindSpy).toHaveBeenCalledWith(expect.any(String), 'demo'));
    expect(onBound).toHaveBeenCalledWith(expect.any(String), 'demo');
  });

  it('creates a new instance and binds it', async () => {
    vi.spyOn(api, 'listInstances').mockResolvedValue([]);
    const createSpy = vi.spyOn(api, 'createInstance').mockResolvedValue({
      instanceName: 'fresh', containerId: 'c1', url: 'http://h:1', gatewayToken: 't', status: 'running',
    });
    const onBound = vi.fn();

    render(<InstancePicker onBound={onBound} />);

    await userEvent.click(await screen.findByRole('tab', { name: '新建实例' }));
    await userEvent.type(screen.getByLabelText('实例名称'), 'Fresh');
    await userEvent.type(screen.getByLabelText('DeepSeek API Key'), 'sk-test');
    await userEvent.click(screen.getByRole('button', { name: '创建并进入对话' }));

    await waitFor(() => expect(createSpy).toHaveBeenCalledWith(
      expect.objectContaining({ instanceName: 'Fresh', deepseekApiKey: 'sk-test' })
    ));
    expect(onBound).toHaveBeenCalledWith(expect.any(String), 'fresh');
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd web && npx vitest run src/__tests__/InstancePicker.test.tsx`
Expected: FAIL — `../InstancePicker` module not found.

- [ ] **Step 3: Implement `InstancePicker`**

Create `web/src/InstancePicker.tsx`:

```tsx
import { useEffect, useState } from "react";
import { listInstances, createInstance, bindInstance, type InstanceSummary } from "./api/instances";
import { genId } from "./lib/id";

interface InstancePickerProps {
  onBound: (conversationId: string, instanceName: string) => void;
}

export default function InstancePicker({ onBound }: InstancePickerProps) {
  const [tab, setTab] = useState<"existing" | "new">("existing");
  const [instances, setInstances] = useState<InstanceSummary[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [newName, setNewName] = useState("");
  const [newKey, setNewKey] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listInstances().then(setInstances).catch(() => setInstances([]));
  }, []);

  const confirmExisting = async () => {
    if (!selected) return;
    setBusy(true);
    setError(null);
    try {
      const convId = genId();
      await bindInstance(convId, selected);
      onBound(convId, selected);
    } catch {
      setError("绑定失败，请重试");
    } finally {
      setBusy(false);
    }
  };

  const confirmNew = async () => {
    if (!newName.trim() || !newKey.trim()) return;
    setBusy(true);
    setError(null);
    try {
      const convId = genId();
      const created = await createInstance({
        instanceName: newName.trim(),
        deepseekApiKey: newKey.trim(),
        conversationId: convId,
      });
      onBound(convId, created.instanceName);
    } catch {
      setError("创建失败，请重试");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex flex-col items-center justify-center h-screen bg-slate-50 px-4">
      <div className="w-full max-w-md bg-white rounded-lg shadow p-6">
        <h2 className="text-lg font-semibold mb-4">选择 OpenClaw 实例</h2>
        <div className="flex gap-2 mb-4">
          <button
            role="tab"
            aria-selected={tab === "existing"}
            className={`px-3 py-1 rounded text-sm ${tab === "existing" ? "bg-slate-900 text-white" : "bg-slate-100"}`}
            onClick={() => setTab("existing")}
          >
            选现有实例
          </button>
          <button
            role="tab"
            aria-selected={tab === "new"}
            className={`px-3 py-1 rounded text-sm ${tab === "new" ? "bg-slate-900 text-white" : "bg-slate-100"}`}
            onClick={() => setTab("new")}
          >
            新建实例
          </button>
        </div>

        {tab === "existing" ? (
          <div>
            <ul className="mb-4 max-h-48 overflow-auto">
              {instances.map((i) => (
                <li key={i.instanceName}>
                  <button
                    onClick={() => setSelected(i.instanceName)}
                    className={`w-full text-left px-3 py-2 rounded mb-1 text-sm ${
                      selected === i.instanceName ? "bg-slate-200" : "hover:bg-slate-100"
                    }`}
                  >
                    {i.instanceName} <span className="text-xs text-slate-500">({i.status})</span>
                  </button>
                </li>
              ))}
              {instances.length === 0 && (
                <li className="text-sm text-slate-500">暂无已注册实例</li>
              )}
            </ul>
            <button
              disabled={!selected || busy}
              onClick={confirmExisting}
              className="w-full px-3 py-2 rounded bg-slate-900 text-white text-sm disabled:opacity-50"
            >
              进入对话
            </button>
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            <label className="text-sm">
              实例名称
              <input
                aria-label="实例名称"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                className="mt-1 w-full border rounded px-2 py-1"
              />
            </label>
            <label className="text-sm">
              DeepSeek API Key
              <input
                aria-label="DeepSeek API Key"
                type="password"
                value={newKey}
                onChange={(e) => setNewKey(e.target.value)}
                className="mt-1 w-full border rounded px-2 py-1"
              />
            </label>
            <button
              disabled={busy}
              onClick={confirmNew}
              className="w-full px-3 py-2 rounded bg-slate-900 text-white text-sm disabled:opacity-50"
            >
              创建并进入对话
            </button>
          </div>
        )}

        {error && <p className="text-xs text-red-600 mt-3">{error}</p>}
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd web && npx vitest run src/__tests__/InstancePicker.test.tsx`
Expected: `2 passed`

- [ ] **Step 5: Commit**

```bash
cd web
git add src/InstancePicker.tsx src/__tests__/InstancePicker.test.tsx
git commit -m "feat: add InstancePicker for choosing/creating an OpenClaw instance"
```

---

### Task 9: `App.tsx` — wire the picker into the new-chat flow

**Files:**
- Modify: `web/src/App.tsx` (full rewrite)
- Test: `web/src/__tests__/App.test.tsx`

**Interfaces:**
- Consumes: `InstancePicker` (Task 8, `onBound` prop); `genId` from `./lib/id` (Task 6).
- Produces: `App.tsx` now renders `<InstancePicker onBound={...} />` when there is no `?id=` in the URL and no conversation has been bound yet this session; otherwise renders the existing chat UI. The chat header shows the bound instance name (persisted to `localStorage` under `chat-instance-<convId>` when a binding is first made, best-effort display only — not re-fetched from the server). Sending a message that gets a `404` shows an inline error and offers a way back to the picker.

- [ ] **Step 1: Write the failing test**

Create `web/src/__tests__/App.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from '../App';
import * as api from '../api/instances';

describe('App', () => {
  beforeEach(() => {
    localStorage.clear();
    window.history.replaceState(null, '', '/');
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows the instance picker when there is no ?id= in the URL', async () => {
    vi.spyOn(api, 'listInstances').mockResolvedValue([
      { instanceName: 'demo', status: 'running', createdAt: '2026-07-02T10:00:00Z' },
    ]);

    render(<App />);

    expect(await screen.findByText('选择 OpenClaw 实例')).toBeInTheDocument();
  });

  it('goes straight to chat when ?id= is already present', async () => {
    window.history.replaceState(null, '', '/?id=existing-conv');

    render(<App />);

    expect(screen.queryByText('选择 OpenClaw 实例')).not.toBeInTheDocument();
    expect(await screen.findByText(/existin/)).toBeInTheDocument();
  });

  it('enters chat after binding via the picker', async () => {
    vi.spyOn(api, 'listInstances').mockResolvedValue([
      { instanceName: 'demo', status: 'running', createdAt: '2026-07-02T10:00:00Z' },
    ]);
    vi.spyOn(api, 'bindInstance').mockResolvedValue(undefined);

    render(<App />);

    await userEvent.click(await screen.findByText('demo'));
    await userEvent.click(screen.getByRole('button', { name: '进入对话' }));

    await waitFor(() => expect(screen.queryByText('选择 OpenClaw 实例')).not.toBeInTheDocument());
    expect(screen.getByText('demo', { exact: false })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd web && npx vitest run src/__tests__/App.test.tsx`
Expected: FAIL — current `App.tsx` always shows chat immediately (no picker), so `选择 OpenClaw 实例` is never found.

- [ ] **Step 3: Rewrite `App.tsx`**

Replace `web/src/App.tsx` in full:

```tsx
import { useState, useCallback, useEffect, useRef } from "react";
import Chat from "./Chat";
import InstancePicker from "./InstancePicker";
import { uploadFiles, UploadError } from "./api/upload";
import { useStore } from "./store";
import { genId } from "./lib/id";

interface UIMessage {
  id: string;
  role: "user" | "assistant";
  content: string;
  attachments?: { name: string; path: string }[];
}

function getInitialConvId(): string | null {
  const params = new URLSearchParams(window.location.search);
  return params.get("id");
}

function loadMessages(convId: string): UIMessage[] {
  try {
    const stored = localStorage.getItem("chat-" + convId);
    return stored ? JSON.parse(stored) : [];
  } catch {
    return [];
  }
}

function saveMessages(convId: string, msgs: UIMessage[]) {
  try {
    // Only persist last 50 messages to avoid localStorage limits
    localStorage.setItem("chat-" + convId, JSON.stringify(msgs.slice(-50)));
  } catch { /* quota exceeded */ }
}

function loadInstanceName(convId: string): string | null {
  return localStorage.getItem("chat-instance-" + convId);
}

function saveInstanceName(convId: string, instanceName: string) {
  try {
    localStorage.setItem("chat-instance-" + convId, instanceName);
  } catch { /* quota exceeded */ }
}

export default function App() {
  const [convId, setConvId] = useState<string | null>(getInitialConvId);
  const [instanceName, setInstanceName] = useState<string | null>(
    () => (convId ? loadInstanceName(convId) : null),
  );
  const pendingFiles = useStore((s) => s.pendingFiles);
  const clearFiles = useStore((s) => s.clearFiles);
  const [messages, setMessages] = useState<UIMessage[]>(() => (convId ? loadMessages(convId) : []));
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  const messagesRef = useRef(messages);

  // Keep ref in sync
  useEffect(() => { messagesRef.current = messages; }, [messages]);

  // Update URL without reload
  useEffect(() => {
    if (!convId) return;
    const url = new URL(window.location.href);
    url.searchParams.set("id", convId);
    window.history.replaceState(null, "", url.toString());
  }, [convId]);

  // Persist messages
  useEffect(() => {
    if (convId && messages.length > 0) saveMessages(convId, messages);
  }, [messages, convId]);

  const handleBound = useCallback((newConvId: string, boundInstanceName: string) => {
    saveInstanceName(newConvId, boundInstanceName);
    setInstanceName(boundInstanceName);
    setMessages([]);
    setConvId(newConvId);
  }, []);

  const send = useCallback(async (text?: string) => {
    if (!convId) return;
    const msg = (text ?? input).trim();
    if ((!msg && pendingFiles.length === 0) || isLoading) return;

    setInput("");
    setIsLoading(true);
    setSendError(null);

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
      const res = await fetch("/api/chat/completions", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          model: "jowo-agent",
          messages: [{ role: "user", content: apiContent }],
          conversationId: convId,
          stream: true,
        }),
      });

      if (res.status === 404) {
        setSendError("对话未绑定实例，请重新选择或新建实例");
        setIsLoading(false);
        return;
      }

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
    } catch (err) {
      setMessages((prev) => {
        const updated = [...prev];
        updated[updated.length - 1] = {
          ...updated[updated.length - 1],
          content: "请求失败: " + (err instanceof Error ? err.message : "未知错误"),
        };
        return updated;
      });
    } finally {
      setIsLoading(false);
    }
  }, [input, isLoading, pendingFiles, clearFiles, convId]);

  const newChat = () => {
    const url = new URL(window.location.href);
    url.searchParams.delete("id");
    window.history.pushState(null, "", url.toString());
    window.location.reload();
  };

  const backToPicker = () => {
    setSendError(null);
    newChat();
  };

  if (!convId) {
    return <InstancePicker onBound={handleBound} />;
  }

  return (
    <div className="flex flex-col h-screen bg-slate-50">
      <header className="flex items-center gap-3 px-5 py-3 bg-slate-900 text-white shrink-0">
        <span className="text-lg">🔧</span>
        <h1 className="text-base font-semibold">Jowo Agent</h1>
        <span className="text-xs text-slate-400 ml-2">企业 API 查询助手</span>
        <div className="ml-auto flex items-center gap-2">
          {instanceName && (
            <span className="text-xs text-slate-400 mr-2">实例: {instanceName}</span>
          )}
          <span className="text-xs text-slate-500 mr-2">#{convId.slice(0, 8)}</span>
          <button
            onClick={newChat}
            className="text-xs px-2 py-1 rounded bg-slate-700 hover:bg-slate-600 transition-colors cursor-pointer"
          >
            新对话
          </button>
        </div>
      </header>
      {sendError && (
        <div className="px-5 py-2 bg-red-50 text-red-700 text-sm flex items-center justify-between shrink-0">
          <span>{sendError}</span>
          <button onClick={backToPicker} className="underline">重新选择实例</button>
        </div>
      )}
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
  );
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd web && npx vitest run src/__tests__/App.test.tsx`
Expected: `3 passed`

- [ ] **Step 5: Run the full frontend test suite**

Run: `cd web && npx vitest run`
Expected: all test files (including the pre-existing `store.test.ts` and Tasks 7-9's new files) pass.

- [ ] **Step 6: Run the frontend build**

Run: `cd web && npx tsc -b --noEmit && npx vite build`
Expected: no type errors, build succeeds.

- [ ] **Step 7: Commit**

```bash
cd web
git add src/App.tsx src/__tests__/App.test.tsx
git commit -m "feat: require picking/creating an OpenClaw instance before a new chat starts"
```

---

### Task 10: Manual end-to-end verification

**Files:** none (verification only).

- [ ] **Step 1: Build the backend**

Run: `mvn -q clean package -DskipTests=false`
Expected: `BUILD SUCCESS`, all backend tests from Tasks 1-5 pass as part of the build.

- [ ] **Step 2: Start the app locally**

Run (from repo root): `java -jar target/agent-*.jar --spring.config.additional-location=file:src/main/resources/application-local.yml`
Expected: starts on port 8088 without errors; `~/.jowo-agent/instances.json` does not exist yet (created lazily on first `save`/`bind`).

- [ ] **Step 3: Provision a real instance through the API and confirm persistence**

Run:
```bash
curl -s -X POST http://localhost:8088/api/instances \
  -H 'Content-Type: application/json' \
  -d '{"instanceName":"e2e-demo","deepseekApiKey":"<real-deepseek-key>","conversationId":"e2e-conv-1"}'
```
Expected: JSON response with `status: "running"` and a `url` like `http://192.168.3.232:<port>`. Then run:
```bash
cat ~/.jowo-agent/instances.json
```
Expected: contains an `instances.e2e-demo` entry and a `bindings.e2e-conv-1: "e2e-demo"` entry.

- [ ] **Step 4: Chat with the provisioned instance and confirm isolation**

Run:
```bash
curl -s -N -X POST http://localhost:8088/api/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"reply with exactly one word: pong"}],"conversationId":"e2e-conv-1"}'
```
Expected: SSE stream containing a `delta.content` chunk with `pong`, followed by `[DONE]`.

- [ ] **Step 5: Confirm an unbound conversation is rejected**

Run:
```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8088/api/chat/completions \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"hi"}],"conversationId":"never-bound"}'
```
Expected: `404`

- [ ] **Step 6: Confirm the registry survives a restart**

Stop the app (Ctrl-C), restart it (Step 2's command again), then repeat Step 4's curl.
Expected: same successful `pong` response — the binding made in Step 3 was not lost.

- [ ] **Step 7: Frontend smoke test in the browser**

Run: `cd web && npm run dev`, open the printed local URL with no `?id=` param.
Expected: the instance picker appears (not the chat UI); selecting the `e2e-demo` instance from the "选现有实例" tab and clicking "进入对话" opens the chat view, shows "实例: e2e-demo" in the header, and sending "reply with exactly one word: pong" gets `pong` back with no console errors.

- [ ] **Step 8: No commit for this task** — it's verification only. If any step fails, go back to the relevant task, fix, and re-run this task's steps from the top.
