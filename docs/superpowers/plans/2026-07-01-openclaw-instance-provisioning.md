# OpenClaw Instance Provisioning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `POST /api/instances` to `mcp-tool` that deploys an isolated OpenClaw container on the Portainer host and returns its access URL.

**Architecture:** A new secrets-free `openclaw-base` image is built once (via a `--strip-secrets` mode added to `~/openclaw-pkg/build-image.sh`) and pushed to Harbor. `mcp-tool` gains a `PortainerClient` (Spring `RestClient`) that drives Portainer's Docker API proxy to pull the image and create/start/inspect a uniquely-named container, injecting the DeepSeek key and a generated gateway token as env vars.

**Tech Stack:** Java 21, Spring Boot 3.5.0 (spring-boot-starter-web, `RestClient`), JUnit 5 + Mockito + `MockRestServiceServer`, Bash + Docker for the base image.

## Global Constraints

- Java 21, Spring Boot 3.5.0; **no new Maven dependencies** — use `RestClient` (bundled) and `spring-boot-starter-test` (bundled).
- DTOs are Java `record` types (match `ChatController`).
- All secrets live in `application-local.yml`, never in the committed `application.yml`.
- **Never log** `deepseekApiKey` or gateway tokens; mask in any error message.
- Container gateway port is `18789/tcp`. Base image ref: `192.168.3.119/jowosmart/openclaw-base:<tag>`.
- Portainer base URL `http://redacted-host.invalid:19002`, API key auth via `X-API-Key` header, `endpoint-id` default `1`.
- The runtime base image has `node` but **not** `python3` — any in-container JSON edit uses `node`.
- Work on a feature branch (repo default branch is `main`); commit per task.

---

## Task 1: `--strip-secrets` base image mode in `~/openclaw-pkg/build-image.sh`

Produces the secrets-free image every instance is created from. (`~/openclaw-pkg` is not a git repo — no commit step; verification-driven instead.)

**Files:**
- Modify: `~/openclaw-pkg/build-image.sh`

**Interfaces:**
- Produces: a Docker image `192.168.3.119/jowosmart/openclaw-base:<tag>` whose entrypoint, on a fresh volume, patches the DeepSeek key from `DEEPSEEK_API_KEY` into `catalog.json` (via node), then runs `openclaw gateway run --bind lan --port 18789 --token "$OPENCLAW_GATEWAY_TOKEN" --force`.

- [ ] **Step 1: Add the `--strip-secrets` flag**

In the flag-parsing `while` loop add:

```bash
    --strip-secrets)    STRIP_SECRETS=1; shift ;;
```

and initialize `STRIP_SECRETS=0` with the other defaults.

- [ ] **Step 2: Extend staging to drop identity/devices/service-env and scrub secrets**

Immediately after the payload is staged/extracted into `$PAYLOAD` (before the path-rewrite block), add:

```bash
if [ "$STRIP_SECRETS" -eq 1 ]; then
  echo "==> Stripping secrets (identity, devices, service-env, gateway token, deepseek key)"
  rm -rf "$PAYLOAD/identity" "$PAYLOAD/devices" "$PAYLOAD/service-env"
  # scrub gateway token
  node -e '
    const fs=require("fs"); const p=process.argv[1];
    const d=JSON.parse(fs.readFileSync(p));
    if (d.gateway && d.gateway.auth) delete d.gateway.auth.token;
    fs.writeFileSync(p, JSON.stringify(d,null,2));
  ' "$PAYLOAD/openclaw.json"
  # scrub deepseek key
  CAT="$PAYLOAD/agents/main/agent/plugins/deepseek/catalog.json"
  [ -f "$CAT" ] && node -e '
    const fs=require("fs"); const p=process.argv[1];
    const d=JSON.parse(fs.readFileSync(p));
    if (d.providers && d.providers.deepseek) d.providers.deepseek.apiKey="";
    fs.writeFileSync(p, JSON.stringify(d,null,2));
  ' "$CAT"
fi
```

> Note: `node` is available on the host if OpenClaw is installed; if building on a host without node, substitute `python3` (host has it). Keep the node form since Task-1 verification runs on this Mac which has node.

- [ ] **Step 3: Replace the entrypoint with a bootstrap-aware version**

Change the generated `entrypoint.sh` heredoc to:

```sh
#!/bin/sh
set -e
PORT="${OPENCLAW_GATEWAY_PORT:-18789}"
BIND="${OPENCLAW_BIND:-lan}"
CAT="$HOME/.openclaw/agents/main/agent/plugins/deepseek/catalog.json"

# Inject the DeepSeek key once, if provided and not already set.
if [ -n "$DEEPSEEK_API_KEY" ] && [ -f "$CAT" ]; then
  node -e '
    const fs=require("fs"); const p=process.argv[1]; const k=process.argv[2];
    const d=JSON.parse(fs.readFileSync(p));
    d.providers=d.providers||{}; d.providers.deepseek=d.providers.deepseek||{};
    if (!d.providers.deepseek.apiKey) d.providers.deepseek.apiKey=k;
    fs.writeFileSync(p, JSON.stringify(d,null,2));
  ' "$CAT" "$DEEPSEEK_API_KEY"
fi

echo "Starting OpenClaw Gateway on 0.0.0.0:$PORT (bind=$BIND)"
exec openclaw gateway run --bind "$BIND" --port "$PORT" --token "$OPENCLAW_GATEWAY_TOKEN" --force
```

- [ ] **Step 4: Verify syntax**

Run: `bash -n ~/openclaw-pkg/build-image.sh`
Expected: no output, exit 0.

- [ ] **Step 5: Verify stripping works (staging dry-check, no Docker build)**

Run:
```bash
cd /tmp && rm -rf oc-strip && mkdir oc-strip
tar xzf ~/openclaw-migration-20260626.tar.gz -C oc-strip --strip-components=1
P=oc-strip/dotopenclaw
rm -rf "$P/identity" "$P/devices" "$P/service-env"
node -e 'const fs=require("fs");const d=JSON.parse(fs.readFileSync("'"$P"'/openclaw.json"));console.log("token:",d.gateway?.auth?.token ?? "(none)")'
ls "$P" | grep -E "identity|devices|service-env" || echo "secrets dirs removed OK"
```
Expected: prints `token: (none)` after a scrub run and `secrets dirs removed OK`.

- [ ] **Step 6: Confirm identity auto-generation (open item #1 from spec)**

Run a throwaway container from a strip-built image with no identity and confirm the gateway generates one:
```bash
~/openclaw-pkg/build-image.sh --from ~/openclaw-migration-20260626.tar.gz --strip-secrets --tag openclaw-base:test
docker run -d --name oc-idtest -e OPENCLAW_GATEWAY_TOKEN=testtok -p 0:18789 openclaw-base:test
sleep 8 && docker exec oc-idtest sh -c 'ls /root/.openclaw/identity 2>/dev/null && echo IDENTITY_OK || echo NO_IDENTITY'
docker rm -f oc-idtest
```
Expected: `IDENTITY_OK`. **If `NO_IDENTITY`**, add before the `exec` line in the entrypoint: `openclaw doctor --yes >/dev/null 2>&1 || true` (or the headless configure command found via `openclaw configure --help`) and re-verify.

---

## Task 2: Portainer configuration properties

**Files:**
- Create: `src/main/java/com/jowo/agent/config/PortainerProperties.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-local.yml`
- Modify: `src/main/java/com/jowo/agent/AgentApplication.java` (add `@ConfigurationPropertiesScan` if not present) — verify exact class name first with `ls src/main/java/com/jowo/agent/*Application.java`.
- Test: `src/test/java/com/jowo/agent/config/PortainerPropertiesTest.java`

**Interfaces:**
- Produces: `PortainerProperties` with getters `baseUrl()`, `apiKey()`, `endpointId()`, `image()`.

- [ ] **Step 1: Write the failing test**

```java
package com.jowo.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "portainer.base-url=http://p:19002",
    "portainer.api-key=ptr_abc",
    "portainer.endpoint-id=1",
    "portainer.image=192.168.3.119/jowosmart/openclaw-base:test"
})
class PortainerPropertiesTest {
    @Autowired PortainerProperties props;

    @Test
    void bindsAllFields() {
        assertThat(props.baseUrl()).isEqualTo("http://p:19002");
        assertThat(props.apiKey()).isEqualTo("ptr_abc");
        assertThat(props.endpointId()).isEqualTo(1);
        assertThat(props.image()).isEqualTo("192.168.3.119/jowosmart/openclaw-base:test");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=PortainerPropertiesTest`
Expected: FAIL — `PortainerProperties` does not exist / no bean.

- [ ] **Step 3: Implement the properties record**

```java
package com.jowo.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("portainer")
public record PortainerProperties(String baseUrl, String apiKey, int endpointId, String image) {}
```

Ensure the main application class carries `@ConfigurationPropertiesScan` (add the annotation and its import if absent). Add non-secret config to `application.yml`:

```yaml
portainer:
  base-url: http://redacted-host.invalid:19002
  endpoint-id: 1
  image: 192.168.3.119/jowosmart/openclaw-base:20260701
```

and the secret to `application-local.yml`:

```yaml
portainer:
  api-key: ptr_… # redacted — real value in application-local.yml, never commit
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=PortainerPropertiesTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/config/PortainerProperties.java \
        src/main/java/com/jowo/agent/AgentApplication.java \
        src/main/resources/application.yml src/main/resources/application-local.yml \
        src/test/java/com/jowo/agent/config/PortainerPropertiesTest.java
git commit -m "feat: add Portainer configuration properties"
```

---

## Task 3: PortainerClient (Docker API proxy wrapper)

**Files:**
- Create: `src/main/java/com/jowo/agent/service/PortainerClient.java`
- Test: `src/test/java/com/jowo/agent/service/PortainerClientTest.java`

**Interfaces:**
- Consumes: `PortainerProperties` (Task 2).
- Produces:
  - `void pullImage(String image)` — image like `repo/name:tag`.
  - `String createContainer(String name, Map<String,Object> createBody)` → returns container id.
  - `void startContainer(String id)`.
  - `String inspectHostPort(String id)` → the host port bound to `18789/tcp`, or `null` if none.
  - Throws `PortainerException(String message, int status)` (nested static class) on non-2xx.

- [ ] **Step 1: Write the failing test**

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.jowo.agent.config.PortainerProperties;

class PortainerClientTest {

    private PortainerClient newClient(java.util.function.Consumer<MockRestServiceServer> setup) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        setup.accept(server);
        var props = new PortainerProperties("http://p:19002", "ptr_abc", 1,
                "192.168.3.119/jowosmart/openclaw-base:test");
        return new PortainerClient(props, builder);
    }

    @Test
    void createContainerReturnsId() {
        var client = newClient(server -> server.expect(
                requestTo("http://p:19002/api/endpoints/1/docker/containers/create?name=openclaw-demo"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header("X-API-Key", "ptr_abc"))
            .andRespond(withSuccess("{\"Id\":\"abc123\"}", MediaType.APPLICATION_JSON)));
        String id = client.createContainer("openclaw-demo", Map.of("Image", "x"));
        assertThat(id).isEqualTo("abc123");
    }

    @Test
    void inspectReadsHostPort() {
        var client = newClient(server -> server.expect(
                requestTo("http://p:19002/api/endpoints/1/docker/containers/abc123/json"))
            .andRespond(withSuccess(
                "{\"NetworkSettings\":{\"Ports\":{\"18789/tcp\":[{\"HostPort\":\"49155\"}]}}}",
                MediaType.APPLICATION_JSON)));
        assertThat(client.inspectHostPort("abc123")).isEqualTo("49155");
    }

    @Test
    void non2xxThrowsPortainerException() {
        var client = newClient(server -> server.expect(
                requestTo("http://p:19002/api/endpoints/1/docker/containers/create?name=dup"))
            .andRespond(withStatus(org.springframework.http.HttpStatus.CONFLICT)
                .body("{\"message\":\"name in use\"}")));
        assertThatThrownBy(() -> client.createContainer("dup", Map.of()))
            .isInstanceOf(PortainerClient.PortainerException.class)
            .hasMessageContaining("name in use");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=PortainerClientTest`
Expected: FAIL — `PortainerClient` does not exist.

- [ ] **Step 3: Implement PortainerClient**

```java
package com.jowo.agent.service;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.jowo.agent.config.PortainerProperties;

@Component
public class PortainerClient {

    private final RestClient http;
    private final PortainerProperties props;

    public PortainerClient(PortainerProperties props, RestClient.Builder builder) {
        this.props = props;
        this.http = builder
                .baseUrl(props.baseUrl())
                .defaultHeader("X-API-Key", props.apiKey())
                .build();
    }

    private String docker(String path) {
        return "/api/endpoints/" + props.endpointId() + "/docker" + path;
    }

    public void pullImage(String image) {
        String repo = image.contains(":") ? image.substring(0, image.lastIndexOf(':')) : image;
        String tag  = image.contains(":") ? image.substring(image.lastIndexOf(':') + 1) : "latest";
        // images/create streams newline-delimited JSON — consume without parsing.
        try {
            http.post().uri(docker("/images/create?fromImage=" + repo + "&tag=" + tag))
                .retrieve().toBodilessEntity();
        } catch (org.springframework.web.client.RestClientResponseException e) {
            throw new PortainerException(e.getResponseBodyAsString(), e.getStatusCode().value());
        } catch (Exception e) {
            throw new PortainerException("Portainer unreachable: " + e.getMessage(), 502);
        }
    }

    public String createContainer(String name, Map<String, Object> body) {
        JsonNode n = exchange("POST", docker("/containers/create?name=" + name), body);
        return n.path("Id").asText();
    }

    public void startContainer(String id) {
        exchange("POST", docker("/containers/" + id + "/start"), null);
    }

    public String inspectHostPort(String id) {
        JsonNode n = exchange("GET", docker("/containers/" + id + "/json"), null);
        JsonNode arr = n.path("NetworkSettings").path("Ports").path("18789/tcp");
        return arr.isArray() && arr.size() > 0 ? arr.get(0).path("HostPort").asText() : null;
    }

    private JsonNode exchange(String method, String uri, Object body) {
        try {
            var spec = http.method(org.springframework.http.HttpMethod.valueOf(method)).uri(uri);
            if (body != null) spec = spec.body(body);
            return spec.retrieve().body(JsonNode.class);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            throw new PortainerException(e.getResponseBodyAsString(), e.getStatusCode().value());
        } catch (Exception e) {
            throw new PortainerException("Portainer unreachable: " + e.getMessage(), 502);
        }
    }

    public static class PortainerException extends RuntimeException {
        public final int status;
        public PortainerException(String message, int status) { super(message); this.status = status; }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=PortainerClientTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/service/PortainerClient.java \
        src/test/java/com/jowo/agent/service/PortainerClientTest.java
git commit -m "feat: add PortainerClient Docker API wrapper"
```

---

## Task 4: InstanceService (orchestration)

**Files:**
- Create: `src/main/java/com/jowo/agent/service/InstanceService.java`
- Test: `src/test/java/com/jowo/agent/service/InstanceServiceTest.java`

**Interfaces:**
- Consumes: `PortainerClient` (Task 3), `PortainerProperties` (Task 2).
- Produces:
  - `InstanceResult deploy(String instanceName, String deepseekApiKey)` where
    `record InstanceResult(String instanceName, String containerId, String url, String gatewayToken, String status)`.
  - `static String sanitizeName(String raw)` → lowercased, `[^a-z0-9-]`→`-`, trimmed of leading/trailing `-`.

- [ ] **Step 1: Write the failing test**

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import com.jowo.agent.config.PortainerProperties;

class InstanceServiceTest {

    private final PortainerProperties props = new PortainerProperties(
            "http://redacted-host.invalid:19002", "ptr_abc", 1,
            "192.168.3.119/jowosmart/openclaw-base:test");

    @Test
    void deployCreatesStartsAndReturnsUrl() {
        PortainerClient client = mock(PortainerClient.class);
        when(client.createContainer(eq("openclaw-demo"), any())).thenReturn("cid1");
        when(client.inspectHostPort("cid1")).thenReturn("49155");
        var service = new InstanceService(client, props);

        var r = service.deploy("Demo", "sk-secret");

        assertThat(r.url()).isEqualTo("http://redacted-host.invalid:49155");
        assertThat(r.containerId()).isEqualTo("cid1");
        assertThat(r.gatewayToken()).isNotBlank();
        verify(client).pullImage("192.168.3.119/jowosmart/openclaw-base:test");
        verify(client).startContainer("cid1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void createBodyCarriesEnvVolumeAndPublishAll() {
        PortainerClient client = mock(PortainerClient.class);
        when(client.createContainer(any(), any())).thenReturn("cid1");
        when(client.inspectHostPort(any())).thenReturn("40000");
        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        var service = new InstanceService(client, props);

        service.deploy("demo", "sk-secret");

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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=InstanceServiceTest`
Expected: FAIL — `InstanceService` does not exist.

- [ ] **Step 3: Implement InstanceService**

```java
package com.jowo.agent.service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.jowo.agent.config.PortainerProperties;

@Service
public class InstanceService {

    private final PortainerClient portainer;
    private final PortainerProperties props;
    private static final SecureRandom RNG = new SecureRandom();

    public InstanceService(PortainerClient portainer, PortainerProperties props) {
        this.portainer = portainer;
        this.props = props;
    }

    public record InstanceResult(String instanceName, String containerId, String url,
                                 String gatewayToken, String status) {}

    public InstanceResult deploy(String instanceName, String deepseekApiKey) {
        String name = sanitizeName(instanceName);
        if (name.isEmpty()) throw new IllegalArgumentException("instanceName is empty after sanitizing");
        if (deepseekApiKey == null || deepseekApiKey.isBlank())
            throw new IllegalArgumentException("deepseekApiKey is required");

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

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=InstanceServiceTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/service/InstanceService.java \
        src/test/java/com/jowo/agent/service/InstanceServiceTest.java
git commit -m "feat: add InstanceService orchestration"
```

---

## Task 5: InstanceController (`POST /api/instances`)

**Files:**
- Create: `src/main/java/com/jowo/agent/controller/InstanceController.java`
- Test: `src/test/java/com/jowo/agent/controller/InstanceControllerTest.java`

**Interfaces:**
- Consumes: `InstanceService` (Task 4), its `InstanceResult`, and `PortainerClient.PortainerException` (Task 3).
- Produces: HTTP endpoint `POST /api/instances` accepting `{instanceName, deepseekApiKey}`, returning `InstanceResult` JSON; error mappings below.

- [ ] **Step 1: Write the failing test**

```java
package com.jowo.agent.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import com.jowo.agent.service.InstanceService;
import com.jowo.agent.service.PortainerClient;

@WebMvcTest(InstanceController.class)
class InstanceControllerTest {

    @Autowired MockMvc mvc;
    @MockBean InstanceService service;

    @Test
    void deployReturns200WithUrl() throws Exception {
        when(service.deploy("demo", "sk-x")).thenReturn(
            new InstanceService.InstanceResult("demo", "cid1",
                "http://redacted-host.invalid:49155", "tok", "running"));
        mvc.perform(post("/api/instances").contentType("application/json")
                .content("{\"instanceName\":\"demo\",\"deepseekApiKey\":\"sk-x\"}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.url").value("http://redacted-host.invalid:49155"))
           .andExpect(jsonPath("$.containerId").value("cid1"));
    }

    @Test
    void badRequestOnInvalidArgs() throws Exception {
        when(service.deploy(any(), any())).thenThrow(new IllegalArgumentException("bad"));
        mvc.perform(post("/api/instances").contentType("application/json")
                .content("{\"instanceName\":\"\",\"deepseekApiKey\":\"\"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void conflictMappedFromPortainer409() throws Exception {
        when(service.deploy(any(), any()))
            .thenThrow(new PortainerClient.PortainerException("name in use", 409));
        mvc.perform(post("/api/instances").contentType("application/json")
                .content("{\"instanceName\":\"demo\",\"deepseekApiKey\":\"sk-x\"}"))
           .andExpect(status().isConflict());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=InstanceControllerTest`
Expected: FAIL — `InstanceController` does not exist.

- [ ] **Step 3: Implement InstanceController**

```java
package com.jowo.agent.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.jowo.agent.service.InstanceService;
import com.jowo.agent.service.PortainerClient;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
class InstanceController {

    private final InstanceService instances;

    InstanceController(InstanceService instances) {
        this.instances = instances;
    }

    record CreateInstanceRequest(String instanceName, String deepseekApiKey) {}

    @PostMapping("/instances")
    InstanceService.InstanceResult create(@RequestBody CreateInstanceRequest req) {
        try {
            return instances.deploy(req.instanceName(), req.deepseekApiKey());
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
}
```

> Note: the error handler must **not** echo secrets — it passes only sanitized messages (`"Portainer error"`, or the validation message which never contains the key).

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=InstanceControllerTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Run the full suite**

Run: `./mvnw test`
Expected: all tests PASS (existing + new).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/jowo/agent/controller/InstanceController.java \
        src/test/java/com/jowo/agent/controller/InstanceControllerTest.java
git commit -m "feat: add POST /api/instances endpoint"
```

---

## Task 6: End-to-end verification (manual)

**Files:** none (verification only).

**Prerequisite:** register `192.168.3.119` as a Registry in Portainer (Registries → Add registry → Custom, URL `192.168.3.119`, user `sukimiya`) so the private pull succeeds.

- [ ] **Step 1: Build & push the base image**

```bash
~/openclaw-pkg/build-image.sh --from ~/openclaw-migration-20260626.tar.gz \
  --strip-secrets --tag 192.168.3.119/jowosmart/openclaw-base:20260701
# then, in the user's own terminal (registry push is user-run):
#   docker push 192.168.3.119/jowosmart/openclaw-base:20260701
```

- [ ] **Step 2: Start mcp-tool and call the endpoint**

```bash
cd /Users/breannalinlin/Documents/code/jowo/mcp-tool && ./mvnw spring-boot:run &
curl -s -X POST http://localhost:8088/api/instances \
  -H 'Content-Type: application/json' \
  -d '{"instanceName":"demo","deepseekApiKey":"sk-REPLACE"}'
```
Expected: JSON with `url: http://redacted-host.invalid:<port>`, a `containerId`, `status: running`.

- [ ] **Step 3: Verify the instance is live**

```bash
# container visible in Portainer UI, and the gateway answers:
curl -s http://redacted-host.invalid:<port>/  # expect a WebSocket/HTTP response from the gateway
```
Expected: the deployed OpenClaw gateway responds; a quick agent turn using the injected key succeeds.

---

## Notes for the executor

- Work on a feature branch: `git checkout -b feat/openclaw-instance-provisioning` before Task 2.
- `./mvnw` is the Maven wrapper; if absent, use `mvn`.
- Registry **push** and Portainer registry setup are user-run (the harness blocks pushing secret-bearing images); pause and hand those steps to the user.
