# In-Backend LLM Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the OpenClaw-per-container runtime with a bounded tool-use agent loop running inside the existing Spring Boot backend, so the enterprise API assistant keeps OpenClaw's effect (LLM reads a compact API index, picks the right interface, self-corrects on failure) without any code-execution/third-party-runtime/container-provisioning attack surface.

**Architecture:** `AgentService` runs a loop (≤8 rounds, ≤180s total) that calls `LlmClient` (DeepSeek, OpenAI-compatible function-calling) with two whitelisted tools — `getApiDetail` (read-only doc lookup) and `callEnterpriseApi` (whitelist-checked HTTP call via `EnterpriseApiClient`, with mechanical retry on transient failures). `ApiCatalog` parses the existing `src/main/resources/api-docs/**/*.md` files into a compact index (for the prompt) and a whitelist (for call validation). `ChatMemory` stores conversation history per `ownerId` (today `ownerId = conversationId`; the naming is the seam for a future login system). `ChatController` drops instance binding entirely. The OpenClaw multi-instance code (`InstanceService`, `InstanceRegistry`, `PortainerClient`, `InstanceController`, `InstancePicker`) is archived to a branch and removed from this branch.

**Tech Stack:** Java 21, Spring Boot 3.5.0 (Spring MVC, `RestClient`, `MockRestServiceServer`, JUnit 5, Mockito, AssertJ), Jackson (already on the classpath via `spring-boot-starter-web`), reactor-core (already present), React 19 + Vite + TypeScript + vitest + @testing-library/react (all already in `web/package.json`).

## Global Constraints

- Java 21 / Spring Boot 3.5.0 — do not bump versions.
- No new Maven or npm dependencies — Jackson's `ObjectMapper`/`JsonNode`, `RestClient`, `MockRestServiceServer`, reactor-core are already transitively available; vitest + testing-library are already in `web/package.json`.
- Never log the DeepSeek API key or any `access-token` — matches the existing rule against logging `gatewayToken`/`deepseekApiKey`.
- `callEnterpriseApi` MUST be rejected by `AgentService` before any HTTP call is made unless `(service, method, path)` is present in `ApiCatalog`'s whitelist — this is the security boundary the whole design rests on.
- No shell execution, no file-system access beyond the bundled classpath `api-docs/` resources, no dynamic container provisioning anywhere in the new code.
- Bounded loop: max **8** tool-call rounds AND max **180 000 ms** total per chat request — whichever limit is hit first stops the loop with a friendly give-up message, no exceptions.
- Mechanical retry (transient enterprise-API failures only: 5xx / network) with a **fixed 3-attempt** backoff of **500 / 1000 / 2000 ms**; single enterprise-API call timeout **15 000 ms**. 4xx responses are never retried.
- Follow existing code style exactly: constructor injection, package-private Spring components where sibling files use package-private (`ChatController`), records for DTOs, nested records/exceptions inside the owning class (see `InstanceRegistry`, `PortainerClient` for precedent).
- Retired OpenClaw-instance code is **archived, not deleted from history**: create branch `archive/openclaw-multi-instance` pointing at the current commit before removing any of those files from this feature branch.

---

## File Map

| File | Change |
|---|---|
| `docs/superpowers/plans/2026-07-02-multi-instance-routing.md` | prepend archival note |
| `src/main/java/com/jowo/agent/config/DeepSeekProperties.java` | **new** |
| `src/test/java/com/jowo/agent/config/DeepSeekPropertiesTest.java` | **new** |
| `src/main/java/com/jowo/agent/config/EnterpriseApiProperties.java` | **new** |
| `src/test/java/com/jowo/agent/config/EnterpriseApiPropertiesTest.java` | **new** |
| `src/main/resources/application.yml` | add `deepseek` + `enterprise-api` blocks; later remove `portainer` + `app.instance-registry` |
| `src/main/java/com/jowo/agent/service/ApiCatalog.java` | **new** |
| `src/test/java/com/jowo/agent/service/ApiCatalogTest.java` | **new** |
| `src/main/java/com/jowo/agent/service/LlmClient.java` | **new** — interface + nested DTOs |
| `src/main/java/com/jowo/agent/service/DeepSeekLlmClient.java` | **new** |
| `src/test/java/com/jowo/agent/service/DeepSeekLlmClientTest.java` | **new** |
| `src/main/java/com/jowo/agent/service/EnterpriseApiClient.java` | **new** — interface + exception |
| `src/main/java/com/jowo/agent/service/HttpEnterpriseApiClient.java` | **new** |
| `src/test/java/com/jowo/agent/service/HttpEnterpriseApiClientTest.java` | **new** |
| `src/main/java/com/jowo/agent/service/ChatMemory.java` | **new** |
| `src/test/java/com/jowo/agent/service/ChatMemoryTest.java` | **new** |
| `src/main/java/com/jowo/agent/service/AgentService.java` | rewrite — bounded tool-use loop |
| `src/test/java/com/jowo/agent/service/AgentServiceTest.java` | rewrite |
| `src/main/java/com/jowo/agent/controller/ChatController.java` | rewrite — drop instance binding |
| `src/test/java/com/jowo/agent/controller/ChatControllerTest.java` | rewrite |
| `src/main/java/com/jowo/agent/service/InstanceService.java`, `InstanceRegistry.java`, `PortainerClient.java` | **removed** |
| `src/main/java/com/jowo/agent/controller/InstanceController.java` | **removed** |
| `src/main/java/com/jowo/agent/config/PortainerProperties.java` | **removed** |
| `src/test/java/com/jowo/agent/service/InstanceServiceTest.java`, `InstanceRegistryTest.java`, `PortainerClientTest.java` | **removed** |
| `src/test/java/com/jowo/agent/controller/InstanceControllerTest.java` | **removed** |
| `src/test/java/com/jowo/agent/config/PortainerPropertiesTest.java` | **removed** |
| `web/src/InstancePicker.tsx`, `web/src/api/instances.ts` | **removed** |
| `web/src/__tests__/InstancePicker.test.tsx`, `web/src/__tests__/api-instances.test.ts` | **removed** |
| `web/src/App.tsx` | modify — remove picker gating, go straight to chat |
| `web/src/__tests__/App.test.tsx` | rewrite |

---

### Task 1: Archive the OpenClaw multi-instance code, mark its plan as archived

**Files:**
- Modify: `docs/superpowers/plans/2026-07-02-multi-instance-routing.md`

**Interfaces:** none — this task touches no code.

- [ ] **Step 1: Create the archive branch**

Run:
```bash
git branch archive/openclaw-multi-instance
```
Expected: a new branch is created pointing at the current commit (`HEAD`), which still contains the full OpenClaw multi-instance implementation. Nothing on the current branch changes. Verify with:
```bash
git log -1 --oneline archive/openclaw-multi-instance
git log -1 --oneline HEAD
```
Expected: both print the same commit hash.

- [ ] **Step 2: Prepend an archival note to the old plan doc**

Edit `docs/superpowers/plans/2026-07-02-multi-instance-routing.md`, inserting this at the very top of the file (line 1, before the existing `# Multi-Instance OpenClaw Routing Implementation Plan` heading):

```markdown
> **ARCHIVED (2026-07-06):** This plan's approach (OpenClaw per-container via Portainer) was rejected on security grounds
> (arbitrary code execution, unvetted third-party runtime, container supply chain/isolation). The implementation it
> describes has been removed from `main` and preserved on branch `archive/openclaw-multi-instance`. See
> `docs/superpowers/specs/2026-07-06-in-backend-agent-design.md` for the replacement design.

---

```

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/plans/2026-07-02-multi-instance-routing.md
git commit -m "docs: archive multi-instance-routing plan, preserve code on archive/openclaw-multi-instance"
```

---

### Task 2: Configuration — `DeepSeekProperties` and `EnterpriseApiProperties`

**Files:**
- Create: `src/main/java/com/jowo/agent/config/DeepSeekProperties.java`
- Test: `src/test/java/com/jowo/agent/config/DeepSeekPropertiesTest.java`
- Create: `src/main/java/com/jowo/agent/config/EnterpriseApiProperties.java`
- Test: `src/test/java/com/jowo/agent/config/EnterpriseApiPropertiesTest.java`
- Modify: `src/main/resources/application.yml`

**Interfaces:**
- Produces: `DeepSeekProperties(String baseUrl, String apiKey, String model)` — `@ConfigurationProperties("deepseek")`.
- Produces: `EnterpriseApiProperties(Map<String, EnterpriseApiProperties.ServiceConfig> services)` with nested `record ServiceConfig(String baseUrl, String accessToken)` — `@ConfigurationProperties("enterprise-api")`.
- Both are auto-registered via the existing `@ConfigurationPropertiesScan` on `AgentApplication` — no extra wiring needed.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/jowo/agent/config/DeepSeekPropertiesTest.java`:

```java
package com.jowo.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "deepseek.base-url=https://api.deepseek.com",
    "deepseek.api-key=sk-test",
    "deepseek.model=deepseek-chat"
})
class DeepSeekPropertiesTest {
    @Autowired DeepSeekProperties props;

    @Test
    void bindsAllFields() {
        assertThat(props.baseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(props.apiKey()).isEqualTo("sk-test");
        assertThat(props.model()).isEqualTo("deepseek-chat");
    }
}
```

Create `src/test/java/com/jowo/agent/config/EnterpriseApiPropertiesTest.java`:

```java
package com.jowo.agent.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "enterprise-api.services.MES.base-url=http://mes:8888",
    "enterprise-api.services.MES.access-token=tok-mes",
    "enterprise-api.services.CRM.base-url=http://crm:8080",
    "enterprise-api.services.CRM.access-token=tok-crm"
})
class EnterpriseApiPropertiesTest {
    @Autowired EnterpriseApiProperties props;

    @Test
    void bindsPerServiceConfig() {
        assertThat(props.services()).containsOnlyKeys("MES", "CRM");
        assertThat(props.services().get("MES").baseUrl()).isEqualTo("http://mes:8888");
        assertThat(props.services().get("MES").accessToken()).isEqualTo("tok-mes");
        assertThat(props.services().get("CRM").baseUrl()).isEqualTo("http://crm:8080");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -Dtest=DeepSeekPropertiesTest,EnterpriseApiPropertiesTest test`
Expected: compilation failure — `DeepSeekProperties`/`EnterpriseApiProperties` don't exist yet.

- [ ] **Step 3: Implement the properties records**

Create `src/main/java/com/jowo/agent/config/DeepSeekProperties.java`:

```java
package com.jowo.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("deepseek")
public record DeepSeekProperties(String baseUrl, String apiKey, String model) {}
```

Create `src/main/java/com/jowo/agent/config/EnterpriseApiProperties.java`:

```java
package com.jowo.agent.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("enterprise-api")
public record EnterpriseApiProperties(Map<String, ServiceConfig> services) {

    public record ServiceConfig(String baseUrl, String accessToken) {}
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -Dtest=DeepSeekPropertiesTest,EnterpriseApiPropertiesTest test`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Add real config blocks to `application.yml`**

Edit `src/main/resources/application.yml`, adding these two new top-level blocks (leave `portainer` and `app.instance-registry` untouched for now — they're removed in Task 8 alongside the Java classes that use them):

```yaml
deepseek:
  base-url: https://api.deepseek.com
  api-key: ${DEEPSEEK_API_KEY:}
  model: deepseek-chat

enterprise-api:
  services:
    MES:
      base-url: ${MES_BASE_URL:http://CHANGE_ME}
      access-token: ${MES_ACCESS_TOKEN:}
    CRM:
      base-url: ${CRM_BASE_URL:http://CHANGE_ME}
      access-token: ${CRM_ACCESS_TOKEN:}
    WMS:
      base-url: ${WMS_BASE_URL:http://CHANGE_ME}
      access-token: ${WMS_ACCESS_TOKEN:}
    QMS:
      base-url: ${QMS_BASE_URL:http://CHANGE_ME}
      access-token: ${QMS_ACCESS_TOKEN:}
    SRM:
      base-url: ${SRM_BASE_URL:http://CHANGE_ME}
      access-token: ${SRM_ACCESS_TOKEN:}
```

Note for whoever deploys this: `http://CHANGE_ME` and the `*_BASE_URL`/`*_ACCESS_TOKEN` env vars must be set to the real MES/CRM/WMS/QMS/SRM hosts and tokens before this works against real services — there is no discoverable "real" value for these in the repo (the original RAG-era `ApiCallingTool` that held them was never committed here).

- [ ] **Step 6: Run the full test suite to confirm nothing else broke**

Run: `mvn -q test`
Expected: `BUILD SUCCESS` — the new blocks don't affect any existing bean.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/jowo/agent/config/DeepSeekProperties.java src/test/java/com/jowo/agent/config/DeepSeekPropertiesTest.java src/main/java/com/jowo/agent/config/EnterpriseApiProperties.java src/test/java/com/jowo/agent/config/EnterpriseApiPropertiesTest.java src/main/resources/application.yml
git commit -m "feat: add DeepSeek and per-service enterprise API configuration"
```

---

### Task 3: `ApiCatalog` — parse `api-docs/` into a compact index + whitelist

**Files:**
- Create: `src/main/java/com/jowo/agent/service/ApiCatalog.java`
- Test: `src/test/java/com/jowo/agent/service/ApiCatalogTest.java`

**Interfaces:**
- Produces: `ApiCatalog` (`@Component`, public no-arg constructor) with:
  - `record ApiEntry(String service, String name, String method, String path, String summary, String detail)`
  - `static List<ApiEntry> parse(String serviceCode, String markdown)` — pure, package-private-testable
  - `String compactIndex()` — one line per entry: `"SERVICE | name | METHOD path | summary"`
  - `boolean isWhitelisted(String service, String method, String path)`
  - `Optional<String> detail(String service, String path)` — returns the raw markdown block for that interface
  - `int size()`

This task only reads the five files already bundled at `src/main/resources/api-docs/{crm,mes,qms,srm,wms}/*.md`. Their format (confirmed by inspection) is consistently: a `## 接口：<name>` heading per interface, followed within the block by a `- **路径：** <METHOD> <path>` line and a `**功能说明：**` line followed by one or more non-blank lines of description text, ending at a blank line.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/jowo/agent/service/ApiCatalogTest.java`:

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ApiCatalogTest {

    private static final String SAMPLE = """
            # 服务：CRM Service API

            ## 接口：创建分享二维码

            - **路径：** POST /api/qrcode/create
            - **所属模块：** 分享二维码管理
            - **HTTP 方法：** POST

            **功能说明：**
            用于向客户生成专属分享二维码，客户扫码后可进入客户H5页面。

            **请求参数：**
            - customerId: 客户ID（类型: integer，必填）

            **返回字段：**
            - code: （类型: integer）

            ## 接口：删除反馈单

            - **路径：** DELETE /api/feedback/delete/{id}
            - **所属模块：** 反馈单管理
            - **HTTP 方法：** DELETE

            **功能说明：**
            按ID删除一条反馈单记录。

            **请求参数：**
            - id （路径参数）: （类型: string，必填）
            """;

    @Test
    void parseExtractsEachInterfaceBlock() {
        var entries = ApiCatalog.parse("CRM", SAMPLE);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).service()).isEqualTo("CRM");
        assertThat(entries.get(0).name()).isEqualTo("创建分享二维码");
        assertThat(entries.get(0).method()).isEqualTo("POST");
        assertThat(entries.get(0).path()).isEqualTo("/api/qrcode/create");
        assertThat(entries.get(0).summary()).isEqualTo("用于向客户生成专属分享二维码，客户扫码后可进入客户H5页面。");
        assertThat(entries.get(0).detail()).contains("**请求参数：**", "customerId");
    }

    @Test
    void parseHandlesPathParamsAndSecondEntry() {
        var entries = ApiCatalog.parse("CRM", SAMPLE);

        assertThat(entries.get(1).method()).isEqualTo("DELETE");
        assertThat(entries.get(1).path()).isEqualTo("/api/feedback/delete/{id}");
        assertThat(entries.get(1).summary()).isEqualTo("按ID删除一条反馈单记录。");
    }

    @Test
    void realBundledDocsParseWithExpectedCounts() {
        var catalog = new ApiCatalog();

        assertThat(catalog.size()).isEqualTo(81 + 289 + 121 + 116 + 108);
        assertThat(catalog.isWhitelisted("MES", "POST", "/api/mes/five-s-records/create")).isTrue();
        assertThat(catalog.isWhitelisted("MES", "POST", "/etc/passwd")).isFalse();
        assertThat(catalog.isWhitelisted("CRM", "GET", "/api/mes/five-s-records/create")).isFalse();
    }

    @Test
    void compactIndexContainsOneLinePerEntry() {
        var catalog = new ApiCatalog();
        var index = catalog.compactIndex();

        assertThat(index.lines().count()).isEqualTo(catalog.size());
        assertThat(index).contains("MES | ", "POST /api/mes/five-s-records/create");
    }

    @Test
    void detailReturnsRawBlockForKnownPathAndEmptyForUnknown() {
        var catalog = new ApiCatalog();

        assertThat(catalog.detail("MES", "/api/mes/five-s-records/create")).isPresent();
        assertThat(catalog.detail("MES", "/no/such/path")).isEmpty();
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -Dtest=ApiCatalogTest test`
Expected: compilation failure — `ApiCatalog` does not exist yet.

- [ ] **Step 3: Implement `ApiCatalog`**

Create `src/main/java/com/jowo/agent/service/ApiCatalog.java`:

```java
package com.jowo.agent.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
public class ApiCatalog {

    private static final List<String> SERVICE_CODES = List.of("MES", "CRM", "WMS", "QMS", "SRM");
    private static final Pattern INTERFACE_HEADING = Pattern.compile("^## 接口：(.+)$", Pattern.MULTILINE);
    private static final Pattern PATH_LINE = Pattern.compile("\\*\\*路径：\\*\\*\\s*(\\S+)\\s+(\\S+)");

    public record ApiEntry(String service, String name, String method, String path, String summary, String detail) {}

    private final List<ApiEntry> entries;

    public ApiCatalog() {
        this.entries = loadAll();
    }

    private static List<ApiEntry> loadAll() {
        var resolver = new PathMatchingResourcePatternResolver();
        var all = new ArrayList<ApiEntry>();
        for (String code : SERVICE_CODES) {
            try {
                var resources = resolver.getResources("classpath:api-docs/" + code.toLowerCase() + "/*.md");
                for (var resource : resources) {
                    String text = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
                    all.addAll(parse(code, text));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("failed to load api-docs for " + code, e);
            }
        }
        return all;
    }

    static List<ApiEntry> parse(String serviceCode, String markdown) {
        var headingMatcher = INTERFACE_HEADING.matcher(markdown);
        var starts = new ArrayList<Integer>();
        var names = new ArrayList<String>();
        while (headingMatcher.find()) {
            starts.add(headingMatcher.start());
            names.add(headingMatcher.group(1).trim());
        }

        var result = new ArrayList<ApiEntry>();
        for (int i = 0; i < starts.size(); i++) {
            int begin = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : markdown.length();
            String block = markdown.substring(begin, end).trim();

            var pathMatcher = PATH_LINE.matcher(block);
            if (!pathMatcher.find()) continue;
            String method = pathMatcher.group(1);
            String path = pathMatcher.group(2);
            String summary = extractSummary(block);

            result.add(new ApiEntry(serviceCode, names.get(i), method, path, summary, block));
        }
        return result;
    }

    private static String extractSummary(String block) {
        var lines = block.split("\n");
        var summary = new StringBuilder();
        boolean collecting = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("**功能说明：**")) {
                collecting = true;
                continue;
            }
            if (collecting) {
                if (trimmed.isEmpty()) break;
                summary.append(trimmed);
            }
        }
        return summary.toString();
    }

    public String compactIndex() {
        var sb = new StringBuilder();
        for (var e : entries) {
            sb.append(e.service()).append(" | ").append(e.name()).append(" | ")
              .append(e.method()).append(' ').append(e.path()).append(" | ")
              .append(e.summary()).append('\n');
        }
        return sb.toString();
    }

    public boolean isWhitelisted(String service, String method, String path) {
        return entries.stream().anyMatch(e ->
                e.service().equalsIgnoreCase(service) &&
                e.method().equalsIgnoreCase(method) &&
                e.path().equals(path));
    }

    public Optional<String> detail(String service, String path) {
        return entries.stream()
                .filter(e -> e.service().equalsIgnoreCase(service) && e.path().equals(path))
                .map(ApiEntry::detail)
                .findFirst();
    }

    public int size() {
        return entries.size();
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -Dtest=ApiCatalogTest test`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

If `realBundledDocsParseWithExpectedCounts` fails on the exact count, run `grep -c '^## 接口：' src/main/resources/api-docs/*/*.md` to re-confirm current per-file counts and adjust the expected sum in the test — the counts used here (81/289/121/116/108) were measured directly against the files in this repo at plan-writing time.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/service/ApiCatalog.java src/test/java/com/jowo/agent/service/ApiCatalogTest.java
git commit -m "feat: add ApiCatalog to parse api-docs into a compact index + call whitelist"
```

---

### Task 4: `LlmClient` interface + `DeepSeekLlmClient`

**Files:**
- Create: `src/main/java/com/jowo/agent/service/LlmClient.java`
- Create: `src/main/java/com/jowo/agent/service/DeepSeekLlmClient.java`
- Test: `src/test/java/com/jowo/agent/service/DeepSeekLlmClientTest.java`

**Interfaces:**
- Consumes: `DeepSeekProperties` (Task 2).
- Produces: `LlmClient` interface with nested types (used by `AgentService` in Task 8):
  - `record ToolDefinition(String name, String description, Map<String, Object> parametersSchema)`
  - `record ToolCall(String id, String name, String argumentsJson)`
  - `record Message(String role, String content, String toolCallId, List<ToolCall> toolCalls)` with static factories `system`, `user`, `assistantText`, `assistantToolCalls`, `toolResult`
  - `sealed interface Response permits Response.FinalAnswer, Response.ToolCalls` with `record FinalAnswer(String text)` and `record ToolCalls(List<ToolCall> calls)`
  - `class LlmException extends RuntimeException { public final int status; }`
  - `Response chat(List<Message> messages, List<ToolDefinition> tools)`
- `DeepSeekLlmClient implements LlmClient` (`@Component`), with package-private static `buildRequestBody(String model, List<Message> messages, List<ToolDefinition> tools)` and `parseResponse(JsonNode root)` for pure-logic testing (same pattern as the old `AgentService.buildCommand`/`extractAnswer`).

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/jowo/agent/service/DeepSeekLlmClientTest.java`:

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jowo.agent.config.DeepSeekProperties;

class DeepSeekLlmClientTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void buildRequestBodyCarriesModelMessagesAndTools() throws Exception {
        var messages = List.of(
                LlmClient.Message.system("sys"),
                LlmClient.Message.user("hello"),
                LlmClient.Message.assistantToolCalls(List.of(new LlmClient.ToolCall("c1", "getApiDetail", "{\"a\":1}"))),
                LlmClient.Message.toolResult("c1", "detail text"));
        var tools = List.of(new LlmClient.ToolDefinition("getApiDetail", "desc",
                Map.of("type", "object", "properties", Map.of())));

        var body = DeepSeekLlmClient.buildRequestBody("deepseek-chat", messages, tools);
        String asJson = json.writeValueAsString(body);
        JsonNode n = json.readTree(asJson);

        assertThat(n.path("model").asText()).isEqualTo("deepseek-chat");
        assertThat(n.path("messages")).hasSize(4);
        assertThat(n.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(n.path("messages").get(2).path("tool_calls").get(0).path("function").path("name").asText())
                .isEqualTo("getApiDetail");
        assertThat(n.path("messages").get(3).path("tool_call_id").asText()).isEqualTo("c1");
        assertThat(n.path("tools").get(0).path("function").path("name").asText()).isEqualTo("getApiDetail");
        assertThat(n.path("tool_choice").asText()).isEqualTo("auto");
    }

    @Test
    void parseResponseReadsFinalAnswer() throws Exception {
        JsonNode root = json.readTree("""
                {"choices":[{"message":{"role":"assistant","content":"你好"}}]}
                """);

        var response = DeepSeekLlmClient.parseResponse(root);

        assertThat(response).isInstanceOf(LlmClient.Response.FinalAnswer.class);
        assertThat(((LlmClient.Response.FinalAnswer) response).text()).isEqualTo("你好");
    }

    @Test
    void parseResponseReadsToolCalls() throws Exception {
        JsonNode root = json.readTree("""
                {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
                    {"id":"call_1","type":"function","function":{"name":"callEnterpriseApi","arguments":"{\\"service\\":\\"MES\\"}"}}
                ]}}]}
                """);

        var response = DeepSeekLlmClient.parseResponse(root);

        assertThat(response).isInstanceOf(LlmClient.Response.ToolCalls.class);
        var calls = ((LlmClient.Response.ToolCalls) response).calls();
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).id()).isEqualTo("call_1");
        assertThat(calls.get(0).name()).isEqualTo("callEnterpriseApi");
        assertThat(calls.get(0).argumentsJson()).isEqualTo("{\"service\":\"MES\"}");
    }

    @Test
    void chatPostsToCompletionsEndpointWithAuthHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
              .andExpect(header("Authorization", "Bearer sk-test"))
              .andRespond(withSuccess("""
                      {"choices":[{"message":{"role":"assistant","content":"pong"}}]}
                      """, MediaType.APPLICATION_JSON));
        var props = new DeepSeekProperties("https://api.deepseek.com", "sk-test", "deepseek-chat");
        var client = new DeepSeekLlmClient(props, builder);

        var response = client.chat(List.of(LlmClient.Message.user("ping")), List.of());

        assertThat(response).isInstanceOf(LlmClient.Response.FinalAnswer.class);
        assertThat(((LlmClient.Response.FinalAnswer) response).text()).isEqualTo("pong");
        server.verify();
    }

    @Test
    void chatThrowsLlmExceptionOnErrorStatus() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
              .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED).body("{\"error\":\"bad key\"}"));
        var props = new DeepSeekProperties("https://api.deepseek.com", "sk-bad", "deepseek-chat");
        var client = new DeepSeekLlmClient(props, builder);

        assertThatThrownBy(() -> client.chat(List.of(LlmClient.Message.user("ping")), List.of()))
                .isInstanceOf(LlmClient.LlmException.class)
                .extracting("status").isEqualTo(401);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -Dtest=DeepSeekLlmClientTest test`
Expected: compilation failure — `LlmClient`/`DeepSeekLlmClient` don't exist yet.

- [ ] **Step 3: Implement `LlmClient`**

Create `src/main/java/com/jowo/agent/service/LlmClient.java`:

```java
package com.jowo.agent.service;

import java.util.List;
import java.util.Map;

public interface LlmClient {

    record ToolDefinition(String name, String description, Map<String, Object> parametersSchema) {}

    record ToolCall(String id, String name, String argumentsJson) {}

    record Message(String role, String content, String toolCallId, List<ToolCall> toolCalls) {

        public static Message system(String content) {
            return new Message("system", content, null, null);
        }

        public static Message user(String content) {
            return new Message("user", content, null, null);
        }

        public static Message assistantText(String content) {
            return new Message("assistant", content, null, null);
        }

        public static Message assistantToolCalls(List<ToolCall> calls) {
            return new Message("assistant", null, null, calls);
        }

        public static Message toolResult(String toolCallId, String content) {
            return new Message("tool", content, toolCallId, null);
        }
    }

    sealed interface Response permits Response.FinalAnswer, Response.ToolCalls {
        record FinalAnswer(String text) implements Response {}
        record ToolCalls(List<ToolCall> calls) implements Response {}
    }

    class LlmException extends RuntimeException {
        public final int status;
        public LlmException(String message, int status) {
            super(message);
            this.status = status;
        }
    }

    Response chat(List<Message> messages, List<ToolDefinition> tools);
}
```

- [ ] **Step 4: Implement `DeepSeekLlmClient`**

Create `src/main/java/com/jowo/agent/service/DeepSeekLlmClient.java`:

```java
package com.jowo.agent.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.jowo.agent.config.DeepSeekProperties;

@Component
public class DeepSeekLlmClient implements LlmClient {

    private final RestClient http;
    private final DeepSeekProperties props;

    public DeepSeekLlmClient(DeepSeekProperties props, RestClient.Builder builder) {
        this.props = props;
        this.http = builder
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .build();
    }

    @Override
    public Response chat(List<Message> messages, List<ToolDefinition> tools) {
        var body = buildRequestBody(props.model(), messages, tools);
        JsonNode root;
        try {
            root = http.post().uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new LlmException("DeepSeek error: " + e.getResponseBodyAsString(), e.getStatusCode().value());
        } catch (Exception e) {
            throw new LlmException("DeepSeek unreachable: " + e.getMessage(), 502);
        }
        return parseResponse(root);
    }

    static Map<String, Object> buildRequestBody(String model, List<Message> messages, List<ToolDefinition> tools) {
        var body = new LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("messages", messages.stream().map(DeepSeekLlmClient::toWireMessage).toList());
        body.put("tools", tools.stream().map(DeepSeekLlmClient::toWireTool).toList());
        body.put("tool_choice", "auto");
        return body;
    }

    private static Map<String, Object> toWireMessage(Message m) {
        var wire = new LinkedHashMap<String, Object>();
        wire.put("role", m.role());
        if (m.content() != null) wire.put("content", m.content());
        if (m.toolCallId() != null) wire.put("tool_call_id", m.toolCallId());
        if (m.toolCalls() != null) {
            wire.put("tool_calls", m.toolCalls().stream().map(tc -> Map.of(
                    "id", tc.id(),
                    "type", "function",
                    "function", Map.of("name", tc.name(), "arguments", tc.argumentsJson())
            )).toList());
        }
        return wire;
    }

    private static Map<String, Object> toWireTool(ToolDefinition t) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", t.name(),
                        "description", t.description(),
                        "parameters", t.parametersSchema()
                )
        );
    }

    static Response parseResponse(JsonNode root) {
        JsonNode message = root.path("choices").path(0).path("message");
        JsonNode toolCallsNode = message.path("tool_calls");
        if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
            var calls = new ArrayList<ToolCall>();
            for (JsonNode tc : toolCallsNode) {
                calls.add(new ToolCall(
                        tc.path("id").asText(),
                        tc.path("function").path("name").asText(),
                        tc.path("function").path("arguments").asText()));
            }
            return new Response.ToolCalls(calls);
        }
        return new Response.FinalAnswer(message.path("content").asText(""));
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q -Dtest=DeepSeekLlmClientTest test`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/jowo/agent/service/LlmClient.java src/main/java/com/jowo/agent/service/DeepSeekLlmClient.java src/test/java/com/jowo/agent/service/DeepSeekLlmClientTest.java
git commit -m "feat: add LlmClient abstraction and DeepSeek function-calling implementation"
```

---

### Task 5: `EnterpriseApiClient` interface + `HttpEnterpriseApiClient` with retry/backoff

**Files:**
- Create: `src/main/java/com/jowo/agent/service/EnterpriseApiClient.java`
- Create: `src/main/java/com/jowo/agent/service/HttpEnterpriseApiClient.java`
- Test: `src/test/java/com/jowo/agent/service/HttpEnterpriseApiClientTest.java`

**Interfaces:**
- Consumes: `EnterpriseApiProperties` (Task 2).
- Produces: `EnterpriseApiClient` interface:
  - `JsonNode call(String service, String method, String path, Map<String, Object> params)`
  - `class EnterpriseApiException extends RuntimeException { public EnterpriseApiException(String message) { super(message); } }`
- `HttpEnterpriseApiClient implements EnterpriseApiClient` (`@Component`), public constructor `(EnterpriseApiProperties, RestClient.Builder)` and package-private constructor `(EnterpriseApiProperties, RestClient.Builder, LongConsumer sleeper)` for fast tests. Retries exactly 3 attempts with a fixed 500/1000/2000 ms backoff on 5xx or network failure; never retries 4xx; single-call timeout 15s; substitutes `{param}` path placeholders from `params` before building the query string (GET/DELETE) or JSON body (POST/PUT).

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/jowo/agent/service/HttpEnterpriseApiClientTest.java`:

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import com.jowo.agent.config.EnterpriseApiProperties;

class HttpEnterpriseApiClientTest {

    private EnterpriseApiProperties props(String baseUrl, String token) {
        return new EnterpriseApiProperties(Map.of("MES", new EnterpriseApiProperties.ServiceConfig(baseUrl, token)));
    }

    @Test
    void getRequestSendsRemainingParamsAsQueryString() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://mes:8888/api/mes/work-orders/page?status=%E6%9C%AA%E5%AE%8C%E6%88%90"))
              .andExpect(method(org.springframework.http.HttpMethod.GET))
              .andExpect(header("x-access-token", "tok-mes"))
              .andRespond(withSuccess("{\"code\":0,\"data\":[]}", MediaType.APPLICATION_JSON));
        var client = new HttpEnterpriseApiClient(props("http://mes:8888", "tok-mes"), builder, ms -> {});

        var result = client.call("MES", "GET", "/api/mes/work-orders/page", Map.of("status", "未完成"));

        assertThat(result.path("code").asInt()).isEqualTo(0);
        server.verify();
    }

    @Test
    void postRequestSendsParamsAsJsonBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://mes:8888/api/mes/five-s-records/create"))
              .andExpect(method(org.springframework.http.HttpMethod.POST))
              .andExpect(content().json("{\"area\":\"A1\"}"))
              .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));
        var client = new HttpEnterpriseApiClient(props("http://mes:8888", "tok-mes"), builder, ms -> {});

        client.call("MES", "POST", "/api/mes/five-s-records/create", Map.of("area", "A1"));

        server.verify();
    }

    @Test
    void pathParamsAreSubstitutedAndRemovedFromRemainingParams() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://mes:8888/api/feedback/delete/123"))
              .andExpect(method(org.springframework.http.HttpMethod.DELETE))
              .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));
        var client = new HttpEnterpriseApiClient(props("http://mes:8888", "tok-mes"), builder, ms -> {});

        client.call("MES", "DELETE", "/api/feedback/delete/{id}", Map.of("id", "123"));

        server.verify();
    }

    @Test
    void retriesTransientFailuresThenSucceeds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://mes:8888/api/x"))
              .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo("http://mes:8888/api/x"))
              .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo("http://mes:8888/api/x"))
              .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));
        var client = new HttpEnterpriseApiClient(props("http://mes:8888", "tok-mes"), builder, ms -> {});

        var result = client.call("MES", "GET", "/api/x", Map.of());

        assertThat(result.path("code").asInt()).isEqualTo(0);
        server.verify();
    }

    @Test
    void doesNotRetryOn4xx() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://mes:8888/api/x"))
              .andRespond(withStatus(HttpStatus.NOT_FOUND).body("not found"));
        var client = new HttpEnterpriseApiClient(props("http://mes:8888", "tok-mes"), builder, ms -> {});

        assertThatThrownBy(() -> client.call("MES", "GET", "/api/x", Map.of()))
                .isInstanceOf(EnterpriseApiClient.EnterpriseApiException.class);
        server.verify();
    }

    @Test
    void throwsAfterExhaustingAllRetries() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://mes:8888/api/x")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo("http://mes:8888/api/x")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo("http://mes:8888/api/x")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        var recordedDelays = new java.util.ArrayList<Long>();
        var client = new HttpEnterpriseApiClient(props("http://mes:8888", "tok-mes"), builder, recordedDelays::add);

        assertThatThrownBy(() -> client.call("MES", "GET", "/api/x", Map.of()))
                .isInstanceOf(EnterpriseApiClient.EnterpriseApiException.class);
        assertThat(recordedDelays).containsExactly(500L, 1000L);
        server.verify();
    }

    @Test
    void unknownServiceThrowsWithoutAnyHttpCall() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build();
        var client = new HttpEnterpriseApiClient(props("http://mes:8888", "tok-mes"), builder, ms -> {});

        assertThatThrownBy(() -> client.call("CRM", "GET", "/api/x", Map.of()))
                .isInstanceOf(EnterpriseApiClient.EnterpriseApiException.class)
                .hasMessageContaining("CRM");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -Dtest=HttpEnterpriseApiClientTest test`
Expected: compilation failure — `EnterpriseApiClient`/`HttpEnterpriseApiClient` don't exist yet.

- [ ] **Step 3: Implement `EnterpriseApiClient`**

Create `src/main/java/com/jowo/agent/service/EnterpriseApiClient.java`:

```java
package com.jowo.agent.service;

import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

public interface EnterpriseApiClient {

    JsonNode call(String service, String method, String path, Map<String, Object> params);

    class EnterpriseApiException extends RuntimeException {
        public EnterpriseApiException(String message) {
            super(message);
        }
    }
}
```

- [ ] **Step 4: Implement `HttpEnterpriseApiClient`**

Create `src/main/java/com/jowo/agent/service/HttpEnterpriseApiClient.java`:

```java
package com.jowo.agent.service;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.jowo.agent.config.EnterpriseApiProperties;

@Component
public class HttpEnterpriseApiClient implements EnterpriseApiClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] BACKOFF_MS = {500, 1000, 2000};
    private static final Pattern PATH_PARAM = Pattern.compile("\\{(\\w+)}");

    private final Map<String, RestClient> clients;
    private final LongConsumer sleeper;

    // Explicit @Autowired is required here: with 3 declared constructors (this one plus the
    // two test-facing overloads below) and no no-arg constructor, Spring cannot infer which
    // one to use for dependency injection and fails with "No default constructor found"
    // unless exactly one constructor is marked. This is the only class in the codebase with
    // more than one constructor, so it's the only one that needs the annotation.
    @Autowired
    public HttpEnterpriseApiClient(EnterpriseApiProperties props, RestClient.Builder builder) {
        this(props, builder, HttpEnterpriseApiClient::defaultSleep, timeoutRequestFactory());
    }

    HttpEnterpriseApiClient(EnterpriseApiProperties props, RestClient.Builder builder, LongConsumer sleeper) {
        this(props, builder, sleeper, null);
    }

    // requestFactoryOverride is only non-null from the public (production) constructor.
    // The test-facing 3-arg constructor above passes null so it never clobbers a request
    // factory the caller (e.g. MockRestServiceServer.bindTo(builder)) already configured —
    // builder.clone() would otherwise carry that factory forward untouched, but an
    // unconditional .requestFactory(...) call here would overwrite it.
    private HttpEnterpriseApiClient(EnterpriseApiProperties props, RestClient.Builder builder,
                                     LongConsumer sleeper, ClientHttpRequestFactory requestFactoryOverride) {
        this.sleeper = sleeper;
        this.clients = new HashMap<>();
        props.services().forEach((name, cfg) -> {
            var b = builder.clone()
                    .baseUrl(cfg.baseUrl())
                    .defaultHeader("x-access-token", cfg.accessToken());
            if (requestFactoryOverride != null) b = b.requestFactory(requestFactoryOverride);
            clients.put(name, b.build());
        });
    }

    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(15_000);
        return factory;
    }

    private static void defaultSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public JsonNode call(String service, String method, String path, Map<String, Object> params) {
        var http = clients.get(service);
        if (http == null) throw new EnterpriseApiException("unknown service: " + service);

        var remaining = new LinkedHashMap<String, Object>(params == null ? Map.of() : params);
        String resolvedPath = resolvePathParams(path, remaining);

        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return exchange(http, method, resolvedPath, remaining);
            } catch (TransientFailure e) {
                lastError = e;
                if (attempt < MAX_ATTEMPTS) sleeper.accept(BACKOFF_MS[attempt - 1]);
            }
        }
        throw new EnterpriseApiException("企业接口多次重试后仍不可用: " + lastError.getMessage());
    }

    private static String resolvePathParams(String path, Map<String, Object> params) {
        var matcher = PATH_PARAM.matcher(path);
        var sb = new StringBuilder();
        while (matcher.find()) {
            Object value = params.remove(matcher.group(1));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private JsonNode exchange(RestClient http, String method, String path, Map<String, Object> params) {
        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
        try {
            if (httpMethod == HttpMethod.GET || httpMethod == HttpMethod.DELETE) {
                URI uri = buildUri(path, params);
                return http.method(httpMethod).uri(uri).retrieve().body(JsonNode.class);
            }
            return http.method(httpMethod).uri(path).body(params).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is5xxServerError()) throw new TransientFailure(e.getMessage());
            throw new EnterpriseApiException(
                    "企业接口返回错误 " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            throw new TransientFailure(e.getMessage());
        }
    }

    // Builds the URI via UriComponentsBuilder (which performs exactly one percent-encoding
    // pass over the raw values) and hands RestClient a finished java.net.URI, rather than a
    // pre-encoded String passed to .uri(String) — that overload re-parses its argument as a
    // URI template and would percent-encode the already-encoded "%" characters a second time.
    private static URI buildUri(String path, Map<String, Object> params) {
        var uriBuilder = UriComponentsBuilder.fromPath(path);
        params.forEach((k, v) -> uriBuilder.queryParam(k, String.valueOf(v)));
        return uriBuilder.encode().build().toUri();
    }

    private static final class TransientFailure extends RuntimeException {
        TransientFailure(String message) {
            super(message);
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q -Dtest=HttpEnterpriseApiClientTest test`
Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/jowo/agent/service/EnterpriseApiClient.java src/main/java/com/jowo/agent/service/HttpEnterpriseApiClient.java src/test/java/com/jowo/agent/service/HttpEnterpriseApiClientTest.java
git commit -m "feat: add whitelisted EnterpriseApiClient with retry/backoff and path-param substitution"
```

---

### Task 6: `ChatMemory` — per-owner conversation history

**Files:**
- Create: `src/main/java/com/jowo/agent/service/ChatMemory.java`
- Test: `src/test/java/com/jowo/agent/service/ChatMemoryTest.java`

**Interfaces:**
- Consumes: `LlmClient.Message` (Task 4).
- Produces: `ChatMemory` (`@Component`) with:
  - `List<LlmClient.Message> get(String ownerId)` — returns an immutable snapshot, empty list if unknown
  - `void append(String ownerId, List<LlmClient.Message> newMessages)` — appends and trims to the last 20 messages

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/jowo/agent/service/ChatMemoryTest.java`:

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChatMemoryTest {

    @Test
    void getOnUnknownOwnerReturnsEmptyList() {
        var memory = new ChatMemory();
        assertThat(memory.get("nobody")).isEmpty();
    }

    @Test
    void appendThenGetReturnsAppendedMessagesInOrder() {
        var memory = new ChatMemory();

        memory.append("conv-1", List.of(LlmClient.Message.user("hi")));
        memory.append("conv-1", List.of(LlmClient.Message.assistantText("hello")));

        assertThat(memory.get("conv-1")).extracting(LlmClient.Message::content)
                .containsExactly("hi", "hello");
    }

    @Test
    void appendTrimsToLast20Messages() {
        var memory = new ChatMemory();
        for (int i = 0; i < 25; i++) {
            memory.append("conv-1", List.of(LlmClient.Message.user("msg-" + i)));
        }

        var history = memory.get("conv-1");

        assertThat(history).hasSize(20);
        assertThat(history.get(0).content()).isEqualTo("msg-5");
        assertThat(history.get(19).content()).isEqualTo("msg-24");
    }

    @Test
    void ownersAreIsolatedFromEachOther() {
        var memory = new ChatMemory();

        memory.append("conv-1", List.of(LlmClient.Message.user("from conv-1")));
        memory.append("conv-2", List.of(LlmClient.Message.user("from conv-2")));

        assertThat(memory.get("conv-1")).extracting(LlmClient.Message::content).containsExactly("from conv-1");
        assertThat(memory.get("conv-2")).extracting(LlmClient.Message::content).containsExactly("from conv-2");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=ChatMemoryTest test`
Expected: compilation failure — `ChatMemory` does not exist yet.

- [ ] **Step 3: Implement `ChatMemory`**

Create `src/main/java/com/jowo/agent/service/ChatMemory.java`:

```java
package com.jowo.agent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class ChatMemory {

    private static final int MAX_MESSAGES = 20;

    private final Map<String, List<LlmClient.Message>> byOwner = new ConcurrentHashMap<>();

    public List<LlmClient.Message> get(String ownerId) {
        return List.copyOf(byOwner.getOrDefault(ownerId, List.of()));
    }

    public void append(String ownerId, List<LlmClient.Message> newMessages) {
        byOwner.compute(ownerId, (id, existing) -> {
            var merged = new ArrayList<>(existing == null ? List.<LlmClient.Message>of() : existing);
            merged.addAll(newMessages);
            int from = Math.max(0, merged.size() - MAX_MESSAGES);
            return List.copyOf(merged.subList(from, merged.size()));
        });
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=ChatMemoryTest test`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/service/ChatMemory.java src/test/java/com/jowo/agent/service/ChatMemoryTest.java
git commit -m "feat: add ChatMemory for per-owner conversation history"
```

---

### Task 7: Rewrite `AgentService` — bounded tool-use loop

**Files:**
- Modify: `src/main/java/com/jowo/agent/service/AgentService.java`
- Test: `src/test/java/com/jowo/agent/service/AgentServiceTest.java`

**Interfaces:**
- Consumes: `LlmClient` (Task 4), `EnterpriseApiClient` (Task 5), `ApiCatalog` (Task 3), `ChatMemory` (Task 6).
- Produces: `AgentService.chat(String ownerId, String userMessage) -> Flux<String>` (signature changed — was `chat(InstanceRegistry.InstanceRecord, String, String)`). Emits the same SSE control-line contract as before: `{"type":"thinking",...}`, `{"type":"tool_call",...}`, a final plain-text line (no `{"type":...}` prefix) or `{"type":"error",...}`, then `{"type":"done"}` — this is what `ChatController.toContentChunk` (Task 8) parses. Package-private static `AgentService.systemPrompt(String apiIndex)` and constants `MAX_ROUNDS = 8`, `MAX_TOTAL_MILLIS = 180_000` for tests/reference. Package-private constructor overload `(LlmClient, EnterpriseApiClient, ApiCatalog, ChatMemory, LongSupplier clock)` for deterministic time-budget tests.

This task deletes the old `AgentService.java` content entirely (the `openclaw gateway call agent` shell-out is gone) and replaces it with the tool-use loop. `EnterpriseApiClient`/`LlmClient` are interfaces so `AgentServiceTest` can fully mock them — no manual E2E needed for this class (unlike the old process-based version).

- [ ] **Step 1: Write the failing tests**

Replace `src/test/java/com/jowo/agent/service/AgentServiceTest.java` in full:

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

class AgentServiceTest {

    private final ObjectMapper json = new ObjectMapper();

    private String argsJson(Map<String, Object> args) {
        try {
            return json.writeValueAsString(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void finalAnswerOnFirstRoundStreamsAnswerAndDone() {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        ChatMemory memory = new ChatMemory();
        when(catalog.compactIndex()).thenReturn("MES | x | GET /api/x | desc\n");
        when(llm.chat(any(), any())).thenReturn(new LlmClient.Response.FinalAnswer("你好，答案是 5 个"));
        var service = new AgentService(llm, enterpriseApi, catalog, memory);

        List<String> lines = service.chat("conv-1", "有多少个客户").collectList().block();

        assertThat(lines).anySatisfy(l -> assertThat(l).contains("你好，答案是 5 个"));
        assertThat(lines.get(lines.size() - 1)).contains("\"type\":\"done\"");
        verifyNoInteractions(enterpriseApi);
    }

    @Test
    void whitelistedToolCallIsExecutedThenFinalAnswerReturned() throws Exception {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        ChatMemory memory = new ChatMemory();
        when(catalog.compactIndex()).thenReturn("MES | x | GET /api/mes/customers/count | desc\n");
        when(catalog.isWhitelisted("MES", "GET", "/api/mes/customers/count")).thenReturn(true);
        JsonNode fakeResult = json.readTree("{\"count\":48}");
        when(enterpriseApi.call(eq("MES"), eq("GET"), eq("/api/mes/customers/count"), anyMap()))
                .thenReturn(fakeResult);
        var toolCall = new LlmClient.ToolCall("call_1", "callEnterpriseApi",
                argsJson(Map.of("service", "MES", "method", "GET", "path", "/api/mes/customers/count", "params", Map.of())));
        when(llm.chat(any(), any()))
                .thenReturn(new LlmClient.Response.ToolCalls(List.of(toolCall)))
                .thenReturn(new LlmClient.Response.FinalAnswer("共有 48 个客户"));
        var service = new AgentService(llm, enterpriseApi, catalog, memory);

        List<String> lines = service.chat("conv-1", "有多少个客户").collectList().block();

        assertThat(lines).anySatisfy(l -> assertThat(l).contains("共有 48 个客户"));
        verify(enterpriseApi).call("MES", "GET", "/api/mes/customers/count", Map.of());
        verify(llm, times(2)).chat(any(), any());
    }

    @Test
    void nonWhitelistedCallIsRejectedWithoutHittingEnterpriseApi() {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        ChatMemory memory = new ChatMemory();
        when(catalog.compactIndex()).thenReturn("MES | x | GET /api/mes/customers/count | desc\n");
        when(catalog.isWhitelisted("MES", "POST", "/etc/passwd")).thenReturn(false);
        var badCall = new LlmClient.ToolCall("call_1", "callEnterpriseApi",
                argsJson(Map.of("service", "MES", "method", "POST", "path", "/etc/passwd", "params", Map.of())));
        when(llm.chat(any(), any()))
                .thenReturn(new LlmClient.Response.ToolCalls(List.of(badCall)))
                .thenReturn(new LlmClient.Response.FinalAnswer("抱歉，我找不到相关接口"));
        var service = new AgentService(llm, enterpriseApi, catalog, memory);

        List<String> lines = service.chat("conv-1", "帮我读一下密码文件").collectList().block();

        assertThat(lines).anySatisfy(l -> assertThat(l).contains("抱歉，我找不到相关接口"));
        verifyNoInteractions(enterpriseApi);
    }

    @Test
    void enterpriseApiFailureIsFedBackAsToolResultNotACrash() {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        ChatMemory memory = new ChatMemory();
        when(catalog.compactIndex()).thenReturn("MES | x | GET /api/mes/customers/count | desc\n");
        when(catalog.isWhitelisted("MES", "GET", "/api/mes/customers/count")).thenReturn(true);
        when(enterpriseApi.call(eq("MES"), eq("GET"), eq("/api/mes/customers/count"), anyMap()))
                .thenThrow(new EnterpriseApiClient.EnterpriseApiException("企业接口多次重试后仍不可用: timeout"));
        var toolCall = new LlmClient.ToolCall("call_1", "callEnterpriseApi",
                argsJson(Map.of("service", "MES", "method", "GET", "path", "/api/mes/customers/count", "params", Map.of())));
        when(llm.chat(any(), any()))
                .thenReturn(new LlmClient.Response.ToolCalls(List.of(toolCall)))
                .thenReturn(new LlmClient.Response.FinalAnswer("系统暂时无法访问，请稍后重试"));
        var service = new AgentService(llm, enterpriseApi, catalog, memory);

        List<String> lines = service.chat("conv-1", "有多少个客户").collectList().block();

        assertThat(lines).anySatisfy(l -> assertThat(l).contains("系统暂时无法访问，请稍后重试"));
    }

    @Test
    void stopsAfterMaxRoundsWithoutHanging() throws Exception {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        ChatMemory memory = new ChatMemory();
        when(catalog.compactIndex()).thenReturn("MES | x | GET /api/x | desc\n");
        when(catalog.isWhitelisted(any(), any(), any())).thenReturn(true);
        when(enterpriseApi.call(any(), any(), any(), anyMap())).thenReturn(json.readTree("{}"));
        var toolCall = new LlmClient.ToolCall("call_1", "callEnterpriseApi",
                argsJson(Map.of("service", "MES", "method", "GET", "path", "/api/x", "params", Map.of())));
        when(llm.chat(any(), any())).thenReturn(new LlmClient.Response.ToolCalls(List.of(toolCall)));
        var service = new AgentService(llm, enterpriseApi, catalog, memory);

        List<String> lines = service.chat("conv-1", "反复失败的问题").collectList().block();

        verify(llm, times(AgentService.MAX_ROUNDS)).chat(any(), any());
        assertThat(lines.get(lines.size() - 1)).contains("\"type\":\"done\"");
    }

    @Test
    void stopsWhenTimeBudgetExceeded() throws Exception {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        ChatMemory memory = new ChatMemory();
        when(catalog.compactIndex()).thenReturn("MES | x | GET /api/x | desc\n");
        when(catalog.isWhitelisted(any(), any(), any())).thenReturn(true);
        when(enterpriseApi.call(any(), any(), any(), anyMap())).thenReturn(json.readTree("{}"));
        var toolCall = new LlmClient.ToolCall("call_1", "callEnterpriseApi",
                argsJson(Map.of("service", "MES", "method", "GET", "path", "/api/x", "params", Map.of())));
        when(llm.chat(any(), any())).thenReturn(new LlmClient.Response.ToolCalls(List.of(toolCall)));
        java.util.function.LongSupplier clock = mock(java.util.function.LongSupplier.class);
        when(clock.getAsLong()).thenReturn(0L, 0L, 200_000L);
        var service = new AgentService(llm, enterpriseApi, catalog, memory, clock);

        List<String> lines = service.chat("conv-1", "反复失败的问题").collectList().block();

        verify(llm, times(1)).chat(any(), any());
        assertThat(lines.get(lines.size() - 1)).contains("\"type\":\"done\"");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -Dtest=AgentServiceTest test`
Expected: compilation failure — `AgentService` doesn't have this constructor/`chat` signature yet.

- [ ] **Step 3: Rewrite `AgentService`**

Replace `src/main/java/com/jowo/agent/service/AgentService.java` in full:

```java
package com.jowo.agent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    static final int MAX_ROUNDS = 8;
    static final long MAX_TOTAL_MILLIS = 180_000;

    private static final LlmClient.ToolDefinition GET_API_DETAIL_TOOL = new LlmClient.ToolDefinition(
            "getApiDetail",
            "查看某个企业服务接口的完整文档（参数、返回字段），在调用 callEnterpriseApi 前用于确认参数格式",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "service", Map.of("type", "string", "description", "服务代码，如 MES/CRM/WMS/QMS/SRM"),
                            "path", Map.of("type", "string", "description", "接口路径，如 /api/mes/five-s-records/create")
                    ),
                    "required", List.of("service", "path")));

    private static final LlmClient.ToolDefinition CALL_ENTERPRISE_API_TOOL = new LlmClient.ToolDefinition(
            "callEnterpriseApi",
            "调用企业服务的一个已注册接口。service/method/path 必须与索引或 getApiDetail 返回的完全一致",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "service", Map.of("type", "string", "description", "服务代码，如 MES/CRM/WMS/QMS/SRM"),
                            "method", Map.of("type", "string", "description", "HTTP 方法，如 GET/POST/PUT/DELETE"),
                            "path", Map.of("type", "string", "description", "接口路径"),
                            "params", Map.of("type", "object", "description", "请求参数（路径参数、查询参数或请求体字段）")
                    ),
                    "required", List.of("service", "method", "path")));

    private final LlmClient llm;
    private final EnterpriseApiClient enterpriseApi;
    private final ApiCatalog catalog;
    private final ChatMemory memory;
    private final LongSupplier clock;

    // Explicit @Autowired is required: with two declared constructors and no no-arg
    // constructor, Spring cannot infer which to use for dependency injection (same
    // issue as HttpEnterpriseApiClient in Task 5).
    @Autowired
    public AgentService(LlmClient llm, EnterpriseApiClient enterpriseApi, ApiCatalog catalog, ChatMemory memory) {
        this(llm, enterpriseApi, catalog, memory, System::currentTimeMillis);
    }

    AgentService(LlmClient llm, EnterpriseApiClient enterpriseApi, ApiCatalog catalog, ChatMemory memory, LongSupplier clock) {
        this.llm = llm;
        this.enterpriseApi = enterpriseApi;
        this.catalog = catalog;
        this.memory = memory;
        this.clock = clock;
    }

    public Flux<String> chat(String ownerId, String userMessage) {
        return Flux.create(sink -> {
            try {
                sink.next(controlLine("thinking", "正在处理..."));
                String answer = runLoop(ownerId, userMessage, sink);
                if (answer != null) sink.next(answer + "\n");
                else sink.next(controlLine("error", "已尽力但未能得到结果，请换个问法或重试"));
                sink.next(controlLine("done", null));
                sink.complete();
            } catch (Exception e) {
                log.error("Agent error", e);
                sink.next(controlLine("error", "处理出错: " + e.getMessage()));
                sink.next(controlLine("done", null));
                sink.complete();
            }
        });
    }

    private String runLoop(String ownerId, String userMessage, reactor.core.publisher.FluxSink<String> sink) {
        var messages = new ArrayList<LlmClient.Message>();
        messages.add(LlmClient.Message.system(systemPrompt(catalog.compactIndex())));
        messages.addAll(memory.get(ownerId));
        messages.add(LlmClient.Message.user(userMessage));

        var tools = List.of(GET_API_DETAIL_TOOL, CALL_ENTERPRISE_API_TOOL);
        var turnMessages = new ArrayList<LlmClient.Message>();
        turnMessages.add(LlmClient.Message.user(userMessage));

        long start = clock.getAsLong();
        for (int round = 0; round < MAX_ROUNDS; round++) {
            if (clock.getAsLong() - start > MAX_TOTAL_MILLIS) break;

            LlmClient.Response response = llm.chat(messages, tools);

            if (response instanceof LlmClient.Response.FinalAnswer fa) {
                var assistantMsg = LlmClient.Message.assistantText(fa.text());
                turnMessages.add(assistantMsg);
                memory.append(ownerId, turnMessages);
                return fa.text();
            }

            var toolCalls = ((LlmClient.Response.ToolCalls) response).calls();
            var assistantToolCallMsg = LlmClient.Message.assistantToolCalls(toolCalls);
            messages.add(assistantToolCallMsg);
            turnMessages.add(assistantToolCallMsg);
            for (var call : toolCalls) {
                sink.next(controlLine("tool_call", call.name()));
                String result = executeTool(call);
                var toolResultMsg = LlmClient.Message.toolResult(call.id(), result);
                messages.add(toolResultMsg);
                turnMessages.add(toolResultMsg);
            }
        }
        memory.append(ownerId, turnMessages);
        return null;
    }

    private String executeTool(LlmClient.ToolCall call) {
        try {
            JsonNode args = JSON.readTree(call.argumentsJson());
            return switch (call.name()) {
                case "getApiDetail" -> getApiDetail(args);
                case "callEnterpriseApi" -> callEnterpriseApi(args);
                default -> "unknown tool: " + call.name();
            };
        } catch (Exception e) {
            return "tool execution error: " + e.getMessage();
        }
    }

    private String getApiDetail(JsonNode args) {
        String service = args.path("service").asText();
        String path = args.path("path").asText();
        return catalog.detail(service, path).orElse("接口不存在: " + service + " " + path);
    }

    private String callEnterpriseApi(JsonNode args) {
        String service = args.path("service").asText();
        String method = args.path("method").asText();
        String path = args.path("path").asText();
        Map<String, Object> params = args.has("params")
                ? JSON.convertValue(args.get("params"), new TypeReference<Map<String, Object>>() {})
                : Map.of();

        if (!catalog.isWhitelisted(service, method, path)) {
            return "调用被拒绝：不在已注册接口白名单中 (" + service + " " + method + " " + path + ")";
        }
        try {
            return enterpriseApi.call(service, method, path, params).toString();
        } catch (EnterpriseApiClient.EnterpriseApiException e) {
            return "接口调用失败：" + e.getMessage();
        }
    }

    static String systemPrompt(String apiIndex) {
        return """
                你是企业内部 API 智能助手，帮助用户通过自然语言查询 MES/CRM/WMS/QMS/SRM 等系统的数据。

                可用接口索引（格式：服务 | 接口名 | 方法 路径 | 功能说明）：
                %s

                规则：
                1. 先从索引中找到最匹配用户问题的接口；如需确认参数或返回字段，调用 getApiDetail 查看完整文档。
                2. 调用 callEnterpriseApi 时，service/method/path 必须与索引或 getApiDetail 返回的完全一致，不要臆造路径。
                3. 如果调用失败或返回为空，根据错误信息判断是否选错了接口或参数，可以换接口或修正参数后重试。
                4. 拿到数据后，用自然语言总结归纳，可用表格/分组呈现，不要原样输出 JSON。
                5. 如果多次尝试仍无法获取需要的数据，如实告知用户，不要编造结果。
                """.formatted(apiIndex);
    }

    private static String controlLine(String type, String content) {
        var node = JSON.createObjectNode();
        node.put("type", type);
        if (content != null) node.put("content", content);
        try {
            return JSON.writeValueAsString(node) + "\n";
        } catch (Exception e) {
            return "";
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -Dtest=AgentServiceTest test`
Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jowo/agent/service/AgentService.java src/test/java/com/jowo/agent/service/AgentServiceTest.java
git commit -m "feat: rewrite AgentService as a bounded tool-use loop over LlmClient/EnterpriseApiClient"
```

---

### Task 8: Rewrite `ChatController`; remove retired OpenClaw-instance code

**Files:**
- Modify: `src/main/java/com/jowo/agent/controller/ChatController.java`
- Test: `src/test/java/com/jowo/agent/controller/ChatControllerTest.java`
- Remove: `src/main/java/com/jowo/agent/service/InstanceService.java`, `InstanceRegistry.java`, `PortainerClient.java`
- Remove: `src/main/java/com/jowo/agent/controller/InstanceController.java`
- Remove: `src/main/java/com/jowo/agent/config/PortainerProperties.java`
- Remove: `src/test/java/com/jowo/agent/service/InstanceServiceTest.java`, `InstanceRegistryTest.java`, `PortainerClientTest.java`
- Remove: `src/test/java/com/jowo/agent/controller/InstanceControllerTest.java`
- Remove: `src/test/java/com/jowo/agent/config/PortainerPropertiesTest.java`
- Modify: `src/main/resources/application.yml` (remove `portainer` and `app.instance-registry` blocks)

**Interfaces:**
- Consumes: `AgentService.chat(String ownerId, String userMessage) -> Flux<String>` (Task 7).
- Produces: `ChatController` with only `AgentService` as a dependency (no more `InstanceRegistry`); `POST /api/conversations/{conversationId}/instance` and `GET /api/instances` are gone; `POST /api/chat/completions` and `POST /api/chat` no longer 404 — every `conversationId` is usable immediately as an `ownerId`.

This task must land as one commit: `ChatController` currently depends on `InstanceRegistry`, and the old `AgentService` (already rewritten in Task 7) no longer accepts an `InstanceRegistry.InstanceRecord` — so `InstanceRegistry` and friends can only be deleted once `ChatController` stops referencing them, which happens in this same task.

- [ ] **Step 1: Write the failing test**

Replace `src/test/java/com/jowo/agent/controller/ChatControllerTest.java` in full:

```java
package com.jowo.agent.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import com.jowo.agent.service.AgentService;
import reactor.core.publisher.Flux;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AgentService agentService;

    @Test
    void chatCompletionsStreamsWithoutRequiringAnyBinding() throws Exception {
        when(agentService.chat(eq("conv-1"), eq("hi"))).thenReturn(Flux.just("hello back"));

        mvc.perform(post("/api/chat/completions").contentType("application/json")
                .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"conversationId\":\"conv-1\"}"))
           .andExpect(status().isOk());

        verify(agentService).chat("conv-1", "hi");
    }

    @Test
    void legacyChatStreamsWithoutRequiringAnyBinding() throws Exception {
        when(agentService.chat(eq("conv-1"), eq("hi"))).thenReturn(Flux.just("hello back"));

        mvc.perform(post("/api/chat").contentType("application/json")
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
        mvc.perform(get("/api/services"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(5));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=ChatControllerTest test`
Expected: compilation failure — `ChatController(AgentService)` single-arg constructor doesn't exist yet (current one takes `InstanceRegistry` too), and `agentService.chat(String, String)` doesn't match the current 3-arg signature reference inside `ChatController.java`.

- [ ] **Step 3: Rewrite `ChatController`**

Replace `src/main/java/com/jowo/agent/controller/ChatController.java` in full:

```java
package com.jowo.agent.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.MediaType;
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

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
class ChatController {

    private final AgentService agentService;
    private static final ObjectMapper json = new ObjectMapper();

    ChatController(AgentService agentService) {
        this.agentService = agentService;
    }

    // OpenAI-compatible chat completions endpoint
    @PostMapping(value = "/chat/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<String> chatCompletions(@RequestBody Map<String, Object> body) {
        var messages = getMessages(body);
        var userMsg = messages.isEmpty() ? "" :
                messages.get(messages.size() - 1).getOrDefault("content", "");

        // Use conversationId from frontend as the owner key for session continuity across requests
        var ownerId = body.getOrDefault("conversationId", UUID.randomUUID().toString()).toString();
        var chunkId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);

        return agentService.chat(ownerId, userMsg)
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
        var ownerId = request.conversationId() != null
                ? request.conversationId()
                : UUID.randomUUID().toString();
        return agentService.chat(ownerId, request.message());
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

    record ConversationSummary(String id, String title, LocalDateTime updatedAt) {}

    record ServiceInfo(String code, String name, String basePath) {}
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=ChatControllerTest test`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Remove the retired OpenClaw-instance files**

```bash
git rm src/main/java/com/jowo/agent/service/InstanceService.java
git rm src/main/java/com/jowo/agent/service/InstanceRegistry.java
git rm src/main/java/com/jowo/agent/service/PortainerClient.java
git rm src/main/java/com/jowo/agent/controller/InstanceController.java
git rm src/main/java/com/jowo/agent/config/PortainerProperties.java
git rm src/test/java/com/jowo/agent/service/InstanceServiceTest.java
git rm src/test/java/com/jowo/agent/service/InstanceRegistryTest.java
git rm src/test/java/com/jowo/agent/service/PortainerClientTest.java
git rm src/test/java/com/jowo/agent/controller/InstanceControllerTest.java
git rm src/test/java/com/jowo/agent/config/PortainerPropertiesTest.java
```

- [ ] **Step 6: Remove the now-unused config blocks from `application.yml`**

Edit `src/main/resources/application.yml`, deleting the entire `portainer:` block and the `instance-registry:` sub-block under `app:` (leave `app.upload` untouched). The file should read:

```yaml
spring:
  application:
    name: agent
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 110MB

server:
  port: 8088

app:
  upload:
    dir: ${user.home}/.openclaw/workspace/uploads
    ttl-hours: 24
    max-size-mb: 10
    max-files-per-request: 10
    allowed-content-types:
      - image/jpeg
      - image/png
      - image/gif
      - image/webp
      - application/pdf

deepseek:
  base-url: https://api.deepseek.com
  api-key: ${DEEPSEEK_API_KEY:}
  model: deepseek-chat

enterprise-api:
  services:
    MES:
      base-url: ${MES_BASE_URL:http://CHANGE_ME}
      access-token: ${MES_ACCESS_TOKEN:}
    CRM:
      base-url: ${CRM_BASE_URL:http://CHANGE_ME}
      access-token: ${CRM_ACCESS_TOKEN:}
    WMS:
      base-url: ${WMS_BASE_URL:http://CHANGE_ME}
      access-token: ${WMS_ACCESS_TOKEN:}
    QMS:
      base-url: ${QMS_BASE_URL:http://CHANGE_ME}
      access-token: ${QMS_ACCESS_TOKEN:}
    SRM:
      base-url: ${SRM_BASE_URL:http://CHANGE_ME}
      access-token: ${SRM_ACCESS_TOKEN:}
```

- [ ] **Step 7: Run the full backend test suite**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`, all remaining tests pass (Tasks 2–8's tests plus pre-existing `UploadControllerTest`, `FileStorageServiceTest`, `UploadCleanupTaskTest`).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/jowo/agent/controller/ChatController.java src/test/java/com/jowo/agent/controller/ChatControllerTest.java src/main/resources/application.yml
git add -u src/main/java/com/jowo/agent/service src/main/java/com/jowo/agent/controller src/main/java/com/jowo/agent/config src/test/java/com/jowo/agent/service src/test/java/com/jowo/agent/controller src/test/java/com/jowo/agent/config
git commit -m "feat: drop instance binding from ChatController; remove retired OpenClaw-instance code"
```

---

### Task 9: Frontend — remove the instance-picker gate, go straight to chat

**Files:**
- Remove: `web/src/InstancePicker.tsx`
- Remove: `web/src/api/instances.ts`
- Remove: `web/src/__tests__/InstancePicker.test.tsx`
- Remove: `web/src/__tests__/api-instances.test.ts`
- Modify: `web/src/App.tsx`
- Test: `web/src/__tests__/App.test.tsx`

**Interfaces:**
- Produces: `App` no longer imports from `./InstancePicker` or `./api/instances`. A new chat starts immediately with a freshly generated `conversationId` (via the existing `genId()` from `web/src/lib/id.ts`) — no picker screen, no `instanceName` display, no "对话未绑定实例" 404 branch in `send()`.

- [ ] **Step 1: Remove the retired frontend files**

```bash
git rm web/src/InstancePicker.tsx
git rm web/src/api/instances.ts
git rm web/src/__tests__/InstancePicker.test.tsx
git rm web/src/__tests__/api-instances.test.ts
```

- [ ] **Step 2: Write the updated failing test**

Replace `web/src/__tests__/App.test.tsx` in full:

```tsx
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import App from '../App';

describe('App', () => {
  beforeEach(() => {
    localStorage.clear();
    window.history.replaceState(null, '', '/');
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('starts a new chat immediately when there is no ?id= in the URL', () => {
    render(<App />);

    expect(screen.queryByText('选择 OpenClaw 实例')).not.toBeInTheDocument();
    expect(screen.getByText('Jowo Agent')).toBeInTheDocument();
  });

  it('goes straight to chat when ?id= is already present', () => {
    window.history.replaceState(null, '', '/?id=existing-conv');

    render(<App />);

    expect(screen.getByText(/existin/)).toBeInTheDocument();
  });
});
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd web && npx vitest run src/__tests__/App.test.tsx`
Expected: FAIL — `App` still imports `InstancePicker`, which no longer exists, and/or still shows the picker with no `?id=`.

- [ ] **Step 4: Rewrite `App.tsx`**

Replace `web/src/App.tsx` in full:

```tsx
import { useState, useCallback, useEffect, useRef } from "react";
import Chat from "./Chat";
import { uploadFiles, UploadError } from "./api/upload";
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

export default function App() {
  const [convId, setConvId] = useState<string>(getInitialConvId);
  const pendingFiles = useStore((s) => s.pendingFiles);
  const clearFiles = useStore((s) => s.clearFiles);
  const [messages, setMessages] = useState<UIMessage[]>(() => loadMessages(convId));
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const messagesRef = useRef(messages);

  // Keep ref in sync
  useEffect(() => { messagesRef.current = messages; }, [messages]);

  // Update URL without reload
  useEffect(() => {
    const url = new URL(window.location.href);
    url.searchParams.set("id", convId);
    window.history.replaceState(null, "", url.toString());
  }, [convId]);

  // Persist messages
  useEffect(() => {
    if (messages.length > 0) saveMessages(convId, messages);
  }, [messages, convId]);

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
    setConvId(genId());
    setMessages([]);
  };

  return (
    <div className="flex flex-col h-screen bg-slate-50">
      <header className="flex items-center gap-3 px-5 py-3 bg-slate-900 text-white shrink-0">
        <span className="text-lg">🔧</span>
        <h1 className="text-base font-semibold">Jowo Agent</h1>
        <span className="text-xs text-slate-400 ml-2">企业 API 查询助手</span>
        <div className="ml-auto flex items-center gap-2">
          <span className="text-xs text-slate-500 mr-2">#{convId.slice(0, 8)}</span>
          <button
            onClick={newChat}
            className="text-xs px-2 py-1 rounded bg-slate-700 hover:bg-slate-600 transition-colors cursor-pointer"
          >
            新对话
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
  );
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd web && npx vitest run src/__tests__/App.test.tsx`
Expected: `2 passed`

- [ ] **Step 6: Run the full frontend test suite and typecheck**

Run: `cd web && npx vitest run && npx tsc -b --noEmit`
Expected: all tests pass, no type errors. (`InstancePicker`/`api/instances` are gone, so no leftover imports should remain — if `tsc` reports a missing-module error anywhere, grep for `InstancePicker` or `api/instances` across `web/src` and remove the stale import.)

- [ ] **Step 7: Commit**

```bash
cd web
git add -A src/App.tsx src/__tests__/App.test.tsx src/InstancePicker.tsx src/api/instances.ts src/__tests__/InstancePicker.test.tsx src/__tests__/api-instances.test.ts
git commit -m "feat: remove instance-picker gate, start chat immediately with a generated conversationId"
```

---

### Task 10: Full verification pass

**Files:** none (verification only).

**Interfaces:** none.

- [ ] **Step 1: Run the full backend suite**

Run: `mvn -q test`
Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run the full frontend suite and build**

Run: `cd web && npx vitest run && npm run build`
Expected: all tests pass; `tsc -b && vite build` completes without errors.

- [ ] **Step 3: Confirm the archive branch still has the old code**

Run: `git show archive/openclaw-multi-instance:src/main/java/com/jowo/agent/service/InstanceRegistry.java | head -5`
Expected: prints the top of the old file — confirms the retired implementation is recoverable.

- [ ] **Step 4: Manual smoke test against the two OpenClaw-era benchmark queries**

This requires a real `DEEPSEEK_API_KEY` and at least one real `*_BASE_URL`/`*_ACCESS_TOKEN` pointing at a reachable MES instance — set these as environment variables before starting the app (`export DEEPSEEK_API_KEY=... MES_BASE_URL=... MES_ACCESS_TOKEN=...`), since there is no way to fabricate working credentials for this plan.

Run: `mvn spring-boot:run` (backend on `:8088`) and, in a second terminal, `cd web && npm run dev` (frontend on `:5173`, proxying `/api` to `:8088` per `web/vite.config.ts`).

In the browser at `http://localhost:5173`, ask the same two questions the original design doc used to validate the OpenClaw approach (`docs/2026-06-12-enterprise-api-agent-design.md`, "实测结果" table):
- "帮我看看当前有多少个客户？"
- "有哪些未完成的工单？"

Expected: the assistant responds with a natural-language summary (not raw JSON) — this is the bar for "保证如 OpenClaw 那样的效果". If either query fails, use the `tool_call`/`error` SSE lines visible in the browser network tab to tell whether the failure is a whitelist/routing bug (fix in `AgentService`/`ApiCatalog`) or a credentials/reachability problem (fix the env vars, not the code).

- [ ] **Step 5: Report results to the user**

No commit for this task — report the manual smoke-test outcome back to the user before considering the branch ready to merge or open a PR.
