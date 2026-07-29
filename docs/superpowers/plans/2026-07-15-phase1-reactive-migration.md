# 阶段一：全链路响应式化 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 mcp-tool 从 Spring MVC + 阻塞式客户端改造成 WebFlux + 全非阻塞链路，并实现 LLM token 级流式输出。

**Architecture:** 按"先内后外"顺序迁移：先让 LLM 客户端支持流式（Task 1-2，用户立刻获得逐字输出），再逐个把 HTTP/Redis 客户端换成非阻塞实现（Task 3-5），然后切换 Web 容器到 Netty（Task 6），最后 MySQL 换 R2DBC（Task 7）、BlockHound 守门（Task 8）。每个 Task 结束时全量测试通过、应用可运行。

**Tech Stack:** Spring Boot 3.5 / WebFlux (Reactor Netty) / WebClient / ReactiveStringRedisTemplate (Lettuce) / R2DBC (r2dbc-mysql) / Flyway (保留 JDBC 仅做启动迁移) / BlockHound / MockWebServer / StepVerifier

## Global Constraints

- JDK 21，Spring Boot 3.5.0，`spring-cloud-dependencies` 2025.0.0（不升级）。
- SSE 行协议保持兼容：控制行仍为 `{"type":...,"content":...}\n`；**新增** `{"type":"delta","content":"<token>"}` 增量事件；最终完整回答仍以纯文本行输出（legacy 前端依赖）。
- 设计文档：`docs/goal/2026-07-15-reactive-multi-agent-design.md`（阶段一章节）。
- 测试命令统一 `mvn -q test` / 单类 `mvn -q -Dtest=<ClassName> test`。
- Redis 集成测试沿用现有约定：直连 `192.168.3.232:6379`（密码 `FeetxXuFkZIaM2Pq`）database 1，`@AfterEach` 清理自己的 key。
- 每个 Task 一次 commit，消息格式 `feat|refactor: <描述>`，结尾加 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`。
- 不改动：`ApiCatalog`、`UploadCleanupTask`、Flyway 迁移脚本、Nacos 配置块、前端。

## 文件结构总览

| 文件 | 动作 | 职责变化 |
|---|---|---|
| `pom.xml` | Modify | webflux/r2dbc/blockhound/mockwebserver 依赖，最终移除 starter-web |
| `service/LlmClient.java` | Modify | 新增 `LlmEvent` + `chatStream()`；Task 2 删除阻塞 `chat()` |
| `service/DeepSeekLlmClient.java` | Modify | WebClient SSE 流式解析 + tool_call 增量拼装 |
| `service/AgentService.java` | Modify | while 循环 → 递归响应式链，透传 delta |
| `service/EnterpriseApiClient.java` / `HttpEnterpriseApiClient.java` | Modify | `Mono<JsonNode>` + retryWhen |
| `service/ChatMemory.java` / `AuthSessionStore.java` | Modify | ReactiveStringRedisTemplate |
| `service/SmartLinkClient.java` | Modify | WebClient + Mono |
| `service/FileStorageService.java` | Modify | 新增 `save(String, FilePart)` 响应式入口 |
| `service/SessionService.java` | Modify | Mono/Flux 签名 |
| `repository/SessionRepository.java` | Modify | JdbcTemplate → DatabaseClient (R2DBC) |
| `web/CurrentUserArgumentResolver.java` | Modify | MVC resolver → WebFlux resolver |
| `web/WebConfig.java` | Modify | WebMvcConfigurer → WebFluxConfigurer |
| `web/AuthExceptionHandler.java` | 不变 | @RestControllerAdvice 在 WebFlux 下同样生效 |
| `controller/ChatController.java` / `AuthController.java` / `UploadController.java` | Modify | 响应式签名 + FilePart |
| `src/main/resources/application.yml` | Modify | 删 servlet multipart 块，加 r2dbc |
| `src/main/resources/application-local.yml` | Modify | 加 spring.r2dbc.* |
| `src/test/.../ReactiveChainBlockHoundTest.java` | Create | 非阻塞守门 |
| 各现有测试类 | Modify | MockRestServiceServer→MockWebServer；MockMvc→WebTestClient |

---

### Task 1: 流式 LlmClient（LlmEvent 模型 + DeepSeek SSE 解析）

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/com/jowo/agent/service/LlmClient.java`
- Modify: `src/main/java/com/jowo/agent/service/DeepSeekLlmClient.java`
- Test: `src/test/java/com/jowo/agent/service/DeepSeekLlmClientStreamTest.java` (Create)

**Interfaces:**
- Consumes: 现有 `DeepSeekProperties`（baseUrl/apiKey/model）、现有 `buildRequestBody` 静态方法。
- Produces: `LlmClient.LlmEvent`（sealed：`ContentDelta(String text)` / `ToolCallsReady(List<ToolCall> calls)` / `Completed(String fullText)`）；`Flux<LlmEvent> chatStream(List<Message>, List<ToolDefinition>)`。Task 2 的 AgentService 只消费这三种事件。

- [ ] **Step 1: pom 加依赖**

`<dependencies>` 中追加（`reactor-core` 已有，可保留不动）：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<dependency>
    <groupId>io.projectreactor</groupId>
    <artifactId>reactor-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>mockwebserver</artifactId>
    <version>4.12.0</version>
    <scope>test</scope>
</dependency>
```

注意：`starter-web` 与 `starter-webflux` 并存时 Boot 自动配置仍选 MVC（Tomcat），行为不变。

Run: `mvn -q compile` Expected: BUILD SUCCESS

- [ ] **Step 2: LlmClient 接口加 LlmEvent 与 chatStream**

在 `LlmClient` 接口内（`Response` 定义之后）追加：

```java
sealed interface LlmEvent permits LlmEvent.ContentDelta, LlmEvent.ToolCallsReady, LlmEvent.Completed {
    record ContentDelta(String text) implements LlmEvent {}
    record ToolCallsReady(List<ToolCall> calls) implements LlmEvent {}
    record Completed(String fullText) implements LlmEvent {}
}

reactor.core.publisher.Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools);
```

（顶部 import `reactor.core.publisher.Flux` 亦可，二选一。）

- [ ] **Step 3: 写失败测试**

`DeepSeekLlmClientStreamTest.java` 完整内容：

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import com.jowo.agent.config.DeepSeekProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import reactor.test.StepVerifier;

class DeepSeekLlmClientStreamTest {

    private MockWebServer server;
    private DeepSeekLlmClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        var props = new DeepSeekProperties(server.url("/").toString(), "test-key", "deepseek-chat");
        client = new DeepSeekLlmClient(props, RestClient.builder(), WebClient.builder());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private void enqueueSse(String... dataLines) {
        var body = new StringBuilder();
        for (String line : dataLines) body.append("data: ").append(line).append("\n\n");
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body.toString()));
    }

    @Test
    void contentTokensAreEmittedAsDeltasThenCompleted() {
        enqueueSse(
            "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"共有\"}}]}",
            "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"48 个客户\"}}]}",
            "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}",
            "[DONE]");

        StepVerifier.create(client.chatStream(
                        java.util.List.of(LlmClient.Message.user("有多少客户")), java.util.List.of()))
                .expectNext(new LlmClient.LlmEvent.ContentDelta("共有"))
                .expectNext(new LlmClient.LlmEvent.ContentDelta("48 个客户"))
                .expectNext(new LlmClient.LlmEvent.Completed("共有48 个客户"))
                .verifyComplete();
    }

    @Test
    void toolCallArgumentFragmentsAreAssembledIntoSingleToolCall() {
        enqueueSse(
            "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"getApiDetail\",\"arguments\":\"{\\\"ser\"}}]}}]}",
            "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"vice\\\":\\\"MES\\\"}\"}}]}}]}",
            "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}]}",
            "[DONE]");

        StepVerifier.create(client.chatStream(
                        java.util.List.of(LlmClient.Message.user("q")), java.util.List.of()))
                .assertNext(e -> {
                    var calls = ((LlmClient.LlmEvent.ToolCallsReady) e).calls();
                    assertThat(calls).hasSize(1);
                    assertThat(calls.get(0).id()).isEqualTo("call_1");
                    assertThat(calls.get(0).name()).isEqualTo("getApiDetail");
                    assertThat(calls.get(0).argumentsJson()).isEqualTo("{\"service\":\"MES\"}");
                })
                .verifyComplete();
    }

    @Test
    void httpErrorSurfacesAsLlmExceptionWithStatus() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("rate limited"));

        StepVerifier.create(client.chatStream(
                        java.util.List.of(LlmClient.Message.user("q")), java.util.List.of()))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(LlmClient.LlmException.class);
                    assertThat(((LlmClient.LlmException) e).status).isEqualTo(429);
                })
                .verify();
    }
}
```

Run: `mvn -q -Dtest=DeepSeekLlmClientStreamTest test`
Expected: COMPILATION ERROR（`chatStream`、`LlmEvent`、三参构造器不存在）——接口在 Step 2 已加则为 DeepSeekLlmClient 未实现的编译错误。

- [ ] **Step 4: 实现 DeepSeekLlmClient.chatStream**

修改构造器并新增流式实现（保留现有 `chat()`/`buildRequestBody`/`parseResponse` 不动）：

```java
// 新增字段与构造器改造
private final org.springframework.web.reactive.function.client.WebClient web;

public DeepSeekLlmClient(DeepSeekProperties props, RestClient.Builder builder,
                          org.springframework.web.reactive.function.client.WebClient.Builder webBuilder) {
    this.props = props;
    this.http = builder.baseUrl(props.baseUrl())
            .defaultHeader("Authorization", "Bearer " + props.apiKey())
            .build();
    this.web = webBuilder.baseUrl(props.baseUrl())
            .defaultHeader("Authorization", "Bearer " + props.apiKey())
            .build();
}

@Override
public reactor.core.publisher.Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools) {
    var body = buildRequestBody(props.model(), messages, tools);
    body.put("stream", true);
    return reactor.core.publisher.Flux.defer(() -> {
        var aggregator = new StreamAggregator();
        return web.post().uri("/chat/completions")
                .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .onStatus(org.springframework.http.HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).defaultIfEmpty("")
                            .map(b -> new LlmException("DeepSeek error: " + b, resp.statusCode().value())))
                .bodyToFlux(String.class)
                .takeWhile(data -> !"[DONE]".equals(data.trim()))
                .concatMap(data -> reactor.core.publisher.Flux.fromIterable(aggregator.onChunk(parseChunk(data))))
                .onErrorMap(e -> !(e instanceof LlmException),
                        e -> new LlmException("DeepSeek unreachable: " + e.getMessage(), 502));
    });
}

private static JsonNode parseChunk(String data) {
    try {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(data);
    } catch (Exception e) {
        throw new LlmException("DeepSeek stream parse error: " + e.getMessage(), 502);
    }
}

static final class StreamAggregator {
    private final StringBuilder content = new StringBuilder();
    private final java.util.SortedMap<Integer, ToolCallBuilder> builders = new java.util.TreeMap<>();

    private static final class ToolCallBuilder {
        String id = "";
        String name = "";
        final StringBuilder args = new StringBuilder();
        ToolCall build() { return new ToolCall(id, name, args.toString()); }
    }

    List<LlmEvent> onChunk(JsonNode chunk) {
        var out = new java.util.ArrayList<LlmEvent>();
        JsonNode choice = chunk.path("choices").path(0);
        JsonNode delta = choice.path("delta");

        String piece = delta.path("content").asText(null);
        if (piece != null && !piece.isEmpty()) {
            content.append(piece);
            out.add(new LlmEvent.ContentDelta(piece));
        }
        for (JsonNode tc : delta.path("tool_calls")) {
            var b = builders.computeIfAbsent(tc.path("index").asInt(), i -> new ToolCallBuilder());
            if (tc.hasNonNull("id")) b.id = tc.path("id").asText();
            JsonNode fn = tc.path("function");
            if (fn.hasNonNull("name")) b.name = fn.path("name").asText();
            b.args.append(fn.path("arguments").asText(""));
        }
        String finish = choice.path("finish_reason").asText(null);
        if ("tool_calls".equals(finish)) {
            out.add(new LlmEvent.ToolCallsReady(builders.values().stream().map(ToolCallBuilder::build).toList()));
        } else if (finish != null && !finish.isEmpty()) {
            out.add(new LlmEvent.Completed(content.toString()));
        }
        return out;
    }
}
```

`buildRequestBody` 返回类型现为 `Map<String,Object>`（`LinkedHashMap`），`body.put("stream", true)` 可直接调用。

Run: `mvn -q -Dtest=DeepSeekLlmClientStreamTest test` Expected: PASS (3 tests)

- [ ] **Step 5: 全量回归 + 提交**

Run: `mvn -q test` Expected: 全部 PASS（现有 `DeepSeekLlmClientTest` 不受影响）

```bash
git add pom.xml src/main/java/com/jowo/agent/service/LlmClient.java src/main/java/com/jowo/agent/service/DeepSeekLlmClient.java src/test/java/com/jowo/agent/service/DeepSeekLlmClientStreamTest.java
git commit -m "feat: add streaming chatStream API to LlmClient with SSE tool-call assembly

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: AgentService 响应式化（delta 透传 + 删除阻塞 chat()）

**Files:**
- Modify: `src/main/java/com/jowo/agent/service/AgentService.java`
- Modify: `src/main/java/com/jowo/agent/service/LlmClient.java`（删除 `chat()` 与 `Response`）
- Modify: `src/main/java/com/jowo/agent/service/DeepSeekLlmClient.java`（删除 `chat()`/`parseResponse`/RestClient）
- Modify: `src/main/java/com/jowo/agent/controller/ChatController.java`（`toContentChunk` 适配 delta）
- Test: `src/test/java/com/jowo/agent/service/AgentServiceTest.java`（改写）
- Test: `src/test/java/com/jowo/agent/service/DeepSeekLlmClientTest.java`（删除针对阻塞 `chat()` 的用例，保留 `buildRequestBody` 静态测试）
- Test: `src/test/java/com/jowo/agent/controller/ChatControllerTest.java`（`toContentChunk` 断言更新）

**Interfaces:**
- Consumes: Task 1 的 `chatStream(List<Message>, List<ToolDefinition>) : Flux<LlmEvent>`。
- Produces: `AgentService.chat(String ownerId, String smartLinkToken, String userMessage) : Flux<String>` 签名不变；输出行序列变为 `thinking → (delta)* → (tool_call → (delta)*)* → <完整回答纯文本行> → done`。`ChatController.toContentChunk(String) : Optional<String>` 语义改为：只透传 `delta`/`error` 的 content，其余（含纯文本行）全部丢弃。

- [ ] **Step 1: 改写 AgentServiceTest（失败测试）**

用 `chatStream` mock 替换 `chat` mock。改写后的关键用例（整个文件按此模式重写，保留原有 5 个行为断言点：直接回答、白名单工具调用、非白名单拒绝、轮数上限、memory 追加）：

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;

class AgentServiceTest {

    private final ObjectMapper json = new ObjectMapper();

    private String argsJson(Map<String, Object> args) {
        try { return json.writeValueAsString(args); } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void finalAnswerStreamsDeltasThenPlainAnswerThenDone() {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        ChatMemory memory = mock(ChatMemory.class);
        when(catalog.compactIndex()).thenReturn("MES | x | GET /api/x | desc\n");
        when(memory.get("conv-1")).thenReturn(List.of());
        when(llm.chatStream(any(), any())).thenReturn(Flux.just(
                new LlmClient.LlmEvent.ContentDelta("你好，"),
                new LlmClient.LlmEvent.ContentDelta("答案是 5 个"),
                new LlmClient.LlmEvent.Completed("你好，答案是 5 个")));
        var service = new AgentService(llm, enterpriseApi, catalog, memory);

        List<String> lines = service.chat("conv-1", "sl-tok", "有多少个客户").collectList().block();

        assertThat(lines).anySatisfy(l -> assertThat(l).contains("\"type\":\"delta\"").contains("你好，"));
        assertThat(lines).anySatisfy(l -> assertThat(l).isEqualTo("你好，答案是 5 个\n"));
        assertThat(lines.get(lines.size() - 1)).contains("\"type\":\"done\"");
        verify(memory).append(eq("conv-1"), anyList());
        verifyNoInteractions(enterpriseApi);
    }

    @Test
    void toolCallRoundExecutesApiThenSecondRoundAnswers() throws Exception {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        ChatMemory memory = mock(ChatMemory.class);
        when(catalog.compactIndex()).thenReturn("MES | x | GET /api/mes/customers/count | desc\n");
        when(catalog.isWhitelisted("MES", "GET", "/api/mes/customers/count")).thenReturn(true);
        when(memory.get("conv-1")).thenReturn(List.of());
        JsonNode fakeResult = json.readTree("{\"count\":48}");
        when(enterpriseApi.call(eq("MES"), eq("GET"), eq("/api/mes/customers/count"), anyMap(), eq("sl-tok")))
                .thenReturn(fakeResult);
        var toolCall = new LlmClient.ToolCall("call_1", "callEnterpriseApi",
                argsJson(Map.of("service", "MES", "method", "GET", "path", "/api/mes/customers/count", "params", Map.of())));
        when(llm.chatStream(any(), any()))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(toolCall))))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("共有 48 个客户")));
        var service = new AgentService(llm, enterpriseApi, catalog, memory);

        List<String> lines = service.chat("conv-1", "sl-tok", "有多少个客户").collectList().block();

        assertThat(lines).anySatisfy(l -> assertThat(l).contains("\"type\":\"tool_call\""));
        assertThat(lines).anySatisfy(l -> assertThat(l).isEqualTo("共有 48 个客户\n"));
        verify(enterpriseApi).call("MES", "GET", "/api/mes/customers/count", Map.of(), "sl-tok");
        verify(llm, times(2)).chatStream(any(), any());
    }

    @Test
    void nonWhitelistedCallIsRejectedWithoutHittingEnterpriseApi() {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        ChatMemory memory = mock(ChatMemory.class);
        when(catalog.compactIndex()).thenReturn("MES | x | GET /a | d\n");
        when(catalog.isWhitelisted("MES", "POST", "/etc/passwd")).thenReturn(false);
        when(memory.get("conv-1")).thenReturn(List.of());
        var badCall = new LlmClient.ToolCall("call_1", "callEnterpriseApi",
                argsJson(Map.of("service", "MES", "method", "POST", "path", "/etc/passwd", "params", Map.of())));
        when(llm.chatStream(any(), any()))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(badCall))))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.Completed("抱歉，我找不到相关接口")));
        var service = new AgentService(llm, enterpriseApi, catalog, memory);

        service.chat("conv-1", "sl-tok", "q").collectList().block();

        verifyNoInteractions(enterpriseApi);
    }

    @Test
    void maxRoundsExhaustedEmitsErrorLine() {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        ChatMemory memory = mock(ChatMemory.class);
        when(catalog.compactIndex()).thenReturn("MES | x | GET /a | d\n");
        when(catalog.detail(any(), any())).thenReturn(java.util.Optional.of("doc"));
        when(memory.get("conv-1")).thenReturn(List.of());
        var call = new LlmClient.ToolCall("c", "getApiDetail", argsJson(Map.of("service", "MES", "path", "/a")));
        when(llm.chatStream(any(), any()))
                .thenReturn(Flux.just(new LlmClient.LlmEvent.ToolCallsReady(List.of(call))));
        var service = new AgentService(llm, enterpriseApi, catalog, memory);

        List<String> lines = service.chat("conv-1", "sl-tok", "q").collectList().block();

        assertThat(lines).anySatisfy(l -> assertThat(l).contains("\"type\":\"error\""));
        verify(llm, times(AgentService.MAX_ROUNDS)).chatStream(any(), any());
    }
}
```

（`Flux.just` 是冷流，Mockito `thenReturn` 的同一个 Flux 每轮被重新订阅时会重放 ToolCallsReady，从而驱动循环直到 MAX_ROUNDS。）

Run: `mvn -q -Dtest=AgentServiceTest test` Expected: COMPILATION ERROR / FAIL（AgentService 仍走 `llm.chat`）

- [ ] **Step 2: 重写 AgentService 主体**

保留：两个 `ToolDefinition` 常量、`systemPrompt`、`controlLine`、`getApiDetail`、`callEnterpriseApi`（内部逻辑不变）、`clock` 构造器。替换 `chat`/`runLoop`/`executeTool`：

```java
public Flux<String> chat(String ownerId, String smartLinkToken, String userMessage) {
    Flux<String> body = Mono.fromCallable(() -> memory.get(ownerId))
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
            .flatMapMany(history -> {
                var messages = new ArrayList<LlmClient.Message>();
                messages.add(LlmClient.Message.system(systemPrompt(catalog.compactIndex())));
                messages.addAll(history);
                messages.add(LlmClient.Message.user(userMessage));
                var turnMessages = new ArrayList<LlmClient.Message>();
                turnMessages.add(LlmClient.Message.user(userMessage));
                return round(messages, turnMessages, ownerId, smartLinkToken, 0, clock.getAsLong());
            });
    return Flux.concat(
                    Flux.just(controlLine("thinking", "正在处理...")),
                    body,
                    Flux.just(controlLine("done", null)))
            .onErrorResume(e -> {
                log.error("Agent error", e);
                return Flux.just(controlLine("error", "处理出错: " + e.getMessage()), controlLine("done", null));
            });
}

private Flux<String> round(List<LlmClient.Message> messages, List<LlmClient.Message> turnMessages,
                            String ownerId, String smartLinkToken, int roundIndex, long startMillis) {
    if (roundIndex >= MAX_ROUNDS || clock.getAsLong() - startMillis > MAX_TOTAL_MILLIS) {
        return saveTurn(ownerId, turnMessages)
                .thenMany(Flux.just(controlLine("error", "已尽力但未能得到结果，请换个问法或重试")));
    }
    var tools = List.of(GET_API_DETAIL_TOOL, CALL_ENTERPRISE_API_TOOL);
    return llm.chatStream(messages, tools).concatMap(event -> switch (event) {
        case LlmClient.LlmEvent.ContentDelta d -> Flux.just(controlLine("delta", d.text()));
        case LlmClient.LlmEvent.Completed c -> {
            turnMessages.add(LlmClient.Message.assistantText(c.fullText()));
            yield saveTurn(ownerId, turnMessages).thenMany(Flux.just(c.fullText() + "\n"));
        }
        case LlmClient.LlmEvent.ToolCallsReady tc -> {
            var assistantMsg = LlmClient.Message.assistantToolCalls(tc.calls());
            messages.add(assistantMsg);
            turnMessages.add(assistantMsg);
            yield Flux.fromIterable(tc.calls())
                    .concatMap(call -> Flux.concat(
                            Flux.just(controlLine("tool_call", call.name())),
                            executeTool(call, smartLinkToken).flatMapMany(result -> {
                                var toolResultMsg = LlmClient.Message.toolResult(call.id(), result);
                                messages.add(toolResultMsg);
                                turnMessages.add(toolResultMsg);
                                return Flux.<String>empty();
                            })))
                    .concatWith(Flux.defer(() ->
                            round(messages, turnMessages, ownerId, smartLinkToken, roundIndex + 1, startMillis)));
        }
    });
}

private Mono<Void> saveTurn(String ownerId, List<LlmClient.Message> turnMessages) {
    return Mono.fromRunnable(() -> memory.append(ownerId, List.copyOf(turnMessages)))
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
            .then();
}

private Mono<String> executeTool(LlmClient.ToolCall call, String smartLinkToken) {
    return Mono.fromCallable(() -> {
                JsonNode args = JSON.readTree(call.argumentsJson());
                return switch (call.name()) {
                    case "getApiDetail" -> getApiDetail(args);
                    case "callEnterpriseApi" -> callEnterpriseApi(args, smartLinkToken);
                    default -> "unknown tool: " + call.name();
                };
            })
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
            .onErrorResume(e -> Mono.just("tool execution error: " + e.getMessage()));
}
```

import 增加 `reactor.core.publisher.Mono`。原 `Flux.create`/`sink` 逻辑整体删除。

- [ ] **Step 3: 删除 LlmClient.chat() 及其实现**

- `LlmClient`：删除 `Response` sealed interface 与 `chat(...)` 声明。
- `DeepSeekLlmClient`：删除 `chat()`、`parseResponse`、`http` 字段与 RestClient import；构造器签名改为 `(DeepSeekProperties props, WebClient.Builder webBuilder)`。
- `DeepSeekLlmClientStreamTest`：构造调用同步改为两参。
- `DeepSeekLlmClientTest`：删除针对 `chat()`/`parseResponse` 的用例，保留 `buildRequestBody` 相关断言（如有）。若整个文件只剩空壳则删除该文件。

- [ ] **Step 4: ChatController.toContentChunk 适配**

```java
static Optional<String> toContentChunk(String line) {
    if (!line.startsWith("{\"type\":")) return Optional.empty();
    try {
        JsonNode n = json.readTree(line);
        String type = n.path("type").asText();
        if ("delta".equals(type) || "error".equals(type)) {
            return Optional.of(n.path("content").asText(""));
        }
    } catch (Exception ignored) {
    }
    return Optional.empty();
}
```

`ChatControllerTest` 中对 `toContentChunk` 的断言同步更新：纯文本行→empty；delta 行→content；error 行→content；thinking/tool_call/done→empty。mock `agentService.chat` 的用例把 `Flux.just("hello back")` 改为 `Flux.just("{\"type\":\"delta\",\"content\":\"hello back\"}\n")`。

- [ ] **Step 5: 回归 + 提交**

Run: `mvn -q test` Expected: 全部 PASS

```bash
git add -A src/main/java/com/jowo/agent src/test/java/com/jowo/agent
git commit -m "feat: reactive agent loop with token-level delta streaming

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: EnterpriseApiClient → WebClient（Mono + retryWhen）

**Files:**
- Modify: `src/main/java/com/jowo/agent/service/EnterpriseApiClient.java`
- Modify: `src/main/java/com/jowo/agent/service/HttpEnterpriseApiClient.java`
- Modify: `src/main/java/com/jowo/agent/service/AgentService.java`（callEnterpriseApi 改为返回 Mono）
- Test: `src/test/java/com/jowo/agent/service/HttpEnterpriseApiClientTest.java`（MockRestServiceServer → MockWebServer 重写）
- Test: `src/test/java/com/jowo/agent/service/AgentServiceTest.java`（mock 返回 `Mono.just(fakeResult)`）

**Interfaces:**
- Consumes: `EnterpriseApiProperties.services() : Map<String, ServiceCfg(baseUrl)>`（现有）。
- Produces: `Mono<JsonNode> call(String service, String method, String path, Map<String,Object> params, String accessToken)`；异常仍为 `EnterpriseApiClient.EnterpriseApiException`（通过 Mono error 信号传递）。重试语义保持：仅 5xx / 连接失败重试，共 3 次尝试，退避 500ms→1s（生产）；测试注入零延迟 Retry。

- [ ] **Step 1: 接口签名改 Mono（编译驱动，先改接口和实现）**

`EnterpriseApiClient.java`：

```java
package com.jowo.agent.service;

import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;

public interface EnterpriseApiClient {

    Mono<JsonNode> call(String service, String method, String path, Map<String, Object> params, String accessToken);

    class EnterpriseApiException extends RuntimeException {
        public EnterpriseApiException(String message) { super(message); }
    }
}
```

- [ ] **Step 2: 重写测试（MockWebServer）**

`HttpEnterpriseApiClientTest` 重写要点——保留原有行为断言集：GET 带查询参数、POST 带 body、x-access-token 头透传、路径参数替换、5xx 重试后成功、5xx 重试耗尽抛 EnterpriseApiException、4xx 不重试直接抛。代表性用例：

```java
package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import com.jowo.agent.config.EnterpriseApiProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

class HttpEnterpriseApiClientTest {

    private MockWebServer server;
    private HttpEnterpriseApiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        var props = new EnterpriseApiProperties(Map.of(
                "MES", new EnterpriseApiProperties.ServiceConfig(server.url("/mes-service").toString())));
        client = new HttpEnterpriseApiClient(props, WebClient.builder(), Retry.fixedDelay(2, java.time.Duration.ZERO));
    }

    @AfterEach
    void tearDown() throws IOException { server.shutdown(); }

    @Test
    void fiveXxIsRetriedThenSucceeds() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(502));
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"ok\":true}"));

        StepVerifier.create(client.call("MES", "GET", "/api/mes/x", Map.of("a", "1"), "tok"))
                .assertNext(node -> assertThat(node.path("ok").asBoolean()).isTrue())
                .verifyComplete();

        var req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/mes-service/api/mes/x?a=1");
        assertThat(req.getHeader("x-access-token")).isEqualTo("tok");
    }

    @Test
    void retriesExhaustedSurfaceEnterpriseApiException() {
        for (int i = 0; i < 3; i++) server.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(client.call("MES", "GET", "/api/mes/x", Map.of(), "tok"))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(EnterpriseApiClient.EnterpriseApiException.class)
                        .hasMessageContaining("多次重试"))
                .verify();
    }

    @Test
    void fourXxFailsFastWithoutRetry() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("bad param"));

        StepVerifier.create(client.call("MES", "POST", "/api/mes/x", Map.of("k", "v"), "tok"))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(EnterpriseApiClient.EnterpriseApiException.class)
                        .hasMessageContaining("400"))
                .verify();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }
}
```

原文件中其余行为断言（POST 带 body、路径参数 `{id}` 替换、未知 service 报错）按同一 StepVerifier 模式迁移，断言目标不变。

Run: `mvn -q -Dtest=HttpEnterpriseApiClientTest test` Expected: COMPILATION ERROR

- [ ] **Step 3: 重写 HttpEnterpriseApiClient**

```java
package com.jowo.agent.service;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.jowo.agent.config.EnterpriseApiProperties;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

@Component
public class HttpEnterpriseApiClient implements EnterpriseApiClient {

    private static final Pattern PATH_PARAM = Pattern.compile("\\{(\\w+)}");

    private final Map<String, WebClient> clients;
    private final Map<String, String> baseUrls;
    private final Retry retrySpec;

    @Autowired
    public HttpEnterpriseApiClient(EnterpriseApiProperties props, WebClient.Builder builder) {
        this(props, builder.clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(15))
                                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000))),
                Retry.backoff(2, Duration.ofMillis(500)));
    }

    HttpEnterpriseApiClient(EnterpriseApiProperties props, WebClient.Builder builder, Retry baseRetry) {
        this.clients = new HashMap<>();
        this.baseUrls = new HashMap<>();
        this.retrySpec = baseRetry
                .filter(TransientFailure.class::isInstance)
                .onRetryExhaustedThrow((spec, signal) -> new EnterpriseApiException(
                        "企业接口多次重试后仍不可用: " + signal.failure().getMessage()));
        props.services().forEach((name, cfg) -> {
            baseUrls.put(name, cfg.baseUrl());
            clients.put(name, builder.clone().baseUrl(cfg.baseUrl()).build());
        });
    }

    @Override
    public Mono<JsonNode> call(String service, String method, String path,
                                Map<String, Object> params, String accessToken) {
        var http = clients.get(service);
        if (http == null) return Mono.error(new EnterpriseApiException("unknown service: " + service));
        String baseUrl = baseUrls.get(service);

        var remaining = new LinkedHashMap<String, Object>(params == null ? Map.of() : params);
        String resolvedPath = resolvePathParams(path, remaining);

        return exchange(http, baseUrl, method, resolvedPath, remaining, accessToken)
                .retryWhen(retrySpec);
    }

    private Mono<JsonNode> exchange(WebClient http, String baseUrl, String method, String path,
                                     Map<String, Object> params, String accessToken) {
        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
        WebClient.RequestBodySpec spec;
        if (httpMethod == HttpMethod.GET || httpMethod == HttpMethod.DELETE) {
            spec = http.method(httpMethod).uri(buildAbsoluteUri(baseUrl, path, params));
        } else {
            spec = http.method(httpMethod).uri(path);
        }
        if (accessToken != null) spec = spec.header("x-access-token", accessToken);
        WebClient.RequestHeadersSpec<?> ready =
                (httpMethod == HttpMethod.GET || httpMethod == HttpMethod.DELETE) ? spec : spec.bodyValue(params);
        return ready.retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, resp ->
                        resp.bodyToMono(String.class).defaultIfEmpty("")
                            .map(b -> new TransientFailure("HTTP " + resp.statusCode().value() + " " + b)))
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).defaultIfEmpty("")
                            .map(b -> new EnterpriseApiException(
                                    "企业接口返回错误 " + resp.statusCode().value() + ": " + b)))
                .bodyToMono(JsonNode.class)
                .onErrorMap(WebClientRequestException.class, e -> new TransientFailure(e.getMessage()));
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

    // 保留原实现的绝对 URI 构造（含原注释说明的 RFC 3986 合并陷阱），逻辑不变
    private static URI buildAbsoluteUri(String baseUrl, String path, Map<String, Object> params) {
        var uriBuilder = UriComponentsBuilder.fromUriString(baseUrl + path);
        params.forEach((k, v) -> uriBuilder.queryParam(k, String.valueOf(v)));
        return uriBuilder.encode().build().toUri();
    }

    private static final class TransientFailure extends RuntimeException {
        TransientFailure(String message) { super(message); }
    }
}
```

（把原实现里 `buildAbsoluteUri` 上方那段解释 RFC 3986 合并语义的注释原样保留搬移。）

- [ ] **Step 4: AgentService 适配 Mono 返回**

`callEnterpriseApi` 改为返回 `Mono<String>`，`executeTool` 相应分流：

```java
private Mono<String> executeTool(LlmClient.ToolCall call, String smartLinkToken) {
    JsonNode args;
    try {
        args = JSON.readTree(call.argumentsJson());
    } catch (Exception e) {
        return Mono.just("tool execution error: " + e.getMessage());
    }
    return switch (call.name()) {
        case "getApiDetail" -> Mono.just(getApiDetail(args));
        case "callEnterpriseApi" -> callEnterpriseApi(args, smartLinkToken);
        default -> Mono.just("unknown tool: " + call.name());
    };
}

private Mono<String> callEnterpriseApi(JsonNode args, String smartLinkToken) {
    String service = args.path("service").asText();
    String method = args.path("method").asText();
    String path = args.path("path").asText();
    Map<String, Object> params = args.has("params")
            ? JSON.convertValue(args.get("params"), new TypeReference<Map<String, Object>>() {})
            : Map.of();

    if (!catalog.isWhitelisted(service, method, path)) {
        log.info("callEnterpriseApi REJECTED service={} method={} path={} params={}", service, method, path, params);
        return Mono.just("调用被拒绝：不在已注册接口白名单中 (" + service + " " + method + " " + path + ")");
    }
    return enterpriseApi.call(service, method, path, params, smartLinkToken)
            .map(Object::toString)
            .doOnNext(result -> log.info("callEnterpriseApi OK service={} method={} path={}", service, method, path))
            .onErrorResume(EnterpriseApiClient.EnterpriseApiException.class,
                    e -> Mono.just("接口调用失败：" + e.getMessage()));
}
```

`AgentServiceTest` 中 `when(enterpriseApi.call(...)).thenReturn(fakeResult)` 改为 `thenReturn(Mono.just(fakeResult))`。

- [ ] **Step 5: 回归 + 提交**

Run: `mvn -q test` Expected: 全部 PASS

```bash
git add -A src/main/java/com/jowo/agent/service src/test/java/com/jowo/agent/service
git commit -m "refactor: enterprise api client to reactive WebClient with retryWhen backoff

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Redis 层响应式（ChatMemory / AuthSessionStore）

**Files:**
- Modify: `src/main/java/com/jowo/agent/service/ChatMemory.java`
- Modify: `src/main/java/com/jowo/agent/service/AuthSessionStore.java`
- Modify: `src/main/java/com/jowo/agent/service/AgentService.java`（去掉 memory 的 boundedElastic 包装）
- Modify: `src/main/java/com/jowo/agent/web/CurrentUserArgumentResolver.java`（临时 `.blockOptional()`，Task 6 移除）
- Modify: `src/main/java/com/jowo/agent/controller/ChatController.java`（messages 端点返回 Mono）
- Modify: `src/main/java/com/jowo/agent/controller/AuthController.java`（临时 `.block()`，Task 5 移除）
- Test: `src/test/java/com/jowo/agent/service/ChatMemoryTest.java`、`AuthSessionStoreTest.java`（改用 Reactive 模板 + `.block()` 断言）
- Test: `src/test/java/com/jowo/agent/service/AgentServiceTest.java`（memory mock 返回 Mono）

**Interfaces:**
- Produces:
  - `ChatMemory.get(String ownerId) : Mono<List<LlmClient.Message>>`（无记录 → `Mono.just(List.of())`）
  - `ChatMemory.append(String ownerId, List<LlmClient.Message>) : Mono<Void>`
  - `AuthSessionStore.create(String userId, String name, String smartLinkToken) : Mono<String>`
  - `AuthSessionStore.resolve(String token) : Mono<AuthUser>`（不存在 → empty Mono）
  - `AuthSessionStore.delete(String token) : Mono<Void>`

- [ ] **Step 1: 改写 ChatMemoryTest（失败测试）**

保持真实 Redis 连接方式，模板换 Reactive，断言处 `.block()`：

```java
// setUp 中：
redisTemplate = new ReactiveStringRedisTemplate(connectionFactory);
memory = new ChatMemory(redisTemplate);
// tearDown 清理改用：
redisTemplate.delete("chatmemory:" + ownerId).block();
// 断言示例：
assertThat(memory.get("nobody").block()).isEmpty();
memory.append("conv-1", List.of(LlmClient.Message.user("hi"))).block();
assertThat(memory.get("conv-1").block()).hasSize(1);
```

`AuthSessionStoreTest` 同样处理（`create(...).block()`、`resolve(...).blockOptional()`、`delete(...).block()`；"resolve 不存在的 token"断言改为 `assertThat(store.resolve("nope").blockOptional()).isEmpty()`）。

Run: `mvn -q -Dtest=ChatMemoryTest,AuthSessionStoreTest test` Expected: COMPILATION ERROR

- [ ] **Step 2: 实现 Reactive 版本**

`ChatMemory.java` 全文：

```java
package com.jowo.agent.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@Component
public class ChatMemory {

    private static final String KEY_PREFIX = "chatmemory:";
    private static final TypeReference<List<LlmClient.Message>> MESSAGE_LIST_TYPE = new TypeReference<>() {};

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();

    public ChatMemory(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<List<LlmClient.Message>> get(String ownerId) {
        return redis.opsForValue().get(KEY_PREFIX + ownerId)
                .map(raw -> parse(ownerId, raw))
                .defaultIfEmpty(List.of());
    }

    public Mono<Void> append(String ownerId, List<LlmClient.Message> newMessages) {
        return get(ownerId)
                .map(existing -> {
                    var merged = new ArrayList<>(existing);
                    merged.addAll(newMessages);
                    return serialize(ownerId, merged);
                })
                .flatMap(payload -> redis.opsForValue().set(KEY_PREFIX + ownerId, payload))
                .then();
    }

    private List<LlmClient.Message> parse(String ownerId, String raw) {
        try {
            return json.readValue(raw, MESSAGE_LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt chat memory JSON for owner " + ownerId, e);
        }
    }

    private String serialize(String ownerId, List<LlmClient.Message> messages) {
        try {
            return json.writeValueAsString(messages);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize chat memory for owner " + ownerId, e);
        }
    }
}
```

`AuthSessionStore.java` 关键改动（结构同现状，模板换 `ReactiveStringRedisTemplate`）：

```java
public Mono<String> create(String userId, String name, String smartLinkToken) {
    String token = randomToken();
    String payload;
    try {
        payload = json.writeValueAsString(new AuthUser(userId, name, smartLinkToken));
    } catch (Exception e) {
        return Mono.error(new IllegalStateException("Failed to serialize auth session for user " + userId, e));
    }
    return redis.opsForValue().set(KEY_PREFIX + token, payload, TTL).thenReturn(token);
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
```

- [ ] **Step 3: 适配消费方**

- `AgentService.chat`：`Mono.fromCallable(() -> memory.get(ownerId)).subscribeOn(boundedElastic()).flatMapMany(...)` → `memory.get(ownerId).flatMapMany(...)`；`saveTurn` 改为 `memory.append(ownerId, List.copyOf(turnMessages))`（直接返回 Mono<Void>，去掉 fromRunnable/boundedElastic）。
- `CurrentUserArgumentResolver`（仍是 MVC）：`return sessions.resolve(token).blockOptional().orElseThrow(UnauthorizedException::new);`（临时，Task 6 删除）。
- `ChatController.getConversationMessages` 返回 `Mono<ResponseEntity<List<Map<String,String>>>>`：

```java
@GetMapping("/conversations/{id}/messages")
Mono<ResponseEntity<List<Map<String, String>>>> getConversationMessages(@PathVariable String id,
        @CurrentUser AuthSessionStore.AuthUser user) {
    if (sessionService.findOwned(id, user.userId()).isEmpty()) {
        return Mono.just(ResponseEntity.notFound().build());
    }
    return memory.get(id).map(all -> {
        var displayMessages = new ArrayList<Map<String, String>>();
        int i = 0;
        for (var m : all) {
            boolean displayable = ("user".equals(m.role()) || "assistant".equals(m.role())) && m.content() != null;
            if (!displayable) continue;
            displayMessages.add(Map.of("id", "msg-" + (i++), "role", m.role(), "content", m.content()));
        }
        return ResponseEntity.ok(displayMessages);
    });
}
```

- `AuthController`：`sessions.create(...).block()`、`sessions.delete(token).block()`（临时，Task 5 重写整个类）。
- `AgentServiceTest`：`when(memory.get("conv-1")).thenReturn(Mono.just(List.of()))`、`when(memory.append(any(), anyList())).thenReturn(Mono.empty())`；验证改 `verify(memory).append(eq("conv-1"), anyList())`。
- `ChatControllerTest`：`chatMemory` mock 返回 `Mono.just(List.of(...))`；`sessions.resolve` mock 返回 `Mono.just(user)` / `Mono.empty()`。

- [ ] **Step 4: 回归 + 提交**

Run: `mvn -q test` Expected: 全部 PASS（Redis 集成测试需能连通 192.168.3.232）

```bash
git add -A src/main/java/com/jowo/agent src/test/java/com/jowo/agent
git commit -m "refactor: chat memory and auth session store to reactive redis

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: SmartLinkClient → WebClient + AuthController 响应式

**Files:**
- Modify: `src/main/java/com/jowo/agent/service/SmartLinkClient.java`
- Modify: `src/main/java/com/jowo/agent/controller/AuthController.java`
- Test: `src/test/java/com/jowo/agent/service/SmartLinkClientTest.java`（MockWebServer 重写）
- Test: `src/test/java/com/jowo/agent/controller/AuthControllerTest.java`（mock 返回 Mono）

**Interfaces:**
- Produces: `SmartLinkClient.login(String, String) : Mono<LoginResult>`；`fetchDisplayName(String) : Mono<String>`。错误信号：4xx/status!=200 → `SmartLinkAuthException`；连接失败/5xx → `SmartLinkUnavailableException`。

- [ ] **Step 1: 重写 SmartLinkClientTest（失败测试）**

沿用 Task 3 的 MockWebServer 模式（setUp 中 `client = new SmartLinkClient(WebClient.builder().baseUrl(server.url("/").toString()))`，为此把包私有构造器参数从 `WebClient.Builder` 收敛为直接 build 后注入，与现状对齐）。保留原断言集：登录成功解析 userId/token、401→SmartLinkAuthException、连接拒绝→SmartLinkUnavailableException、body status!=200→SmartLinkAuthException、fetchDisplayName 解析 name 且带 X-Access-Token 头。代表性用例：

```java
@Test
void loginParsesUserIdAndToken() throws InterruptedException {
    server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
            .setBody("{\"status\":200,\"result\":{\"userId\":\"u-9\",\"token\":\"sl-tok\"}}"));

    StepVerifier.create(client.login("alice", "pw"))
            .assertNext(r -> {
                assertThat(r.userId()).isEqualTo("u-9");
                assertThat(r.smartLinkToken()).isEqualTo("sl-tok");
            })
            .verifyComplete();

    var req = server.takeRequest();
    assertThat(req.getPath()).isEqualTo("/api/authorize/login");
}

@Test
void fourXxLoginBecomesAuthException() {
    server.enqueue(new MockResponse().setResponseCode(401));

    StepVerifier.create(client.login("alice", "bad"))
            .expectError(SmartLinkClient.SmartLinkAuthException.class)
            .verify();
}

@Test
void statusFieldNot200BecomesAuthException() {
    server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
            .setBody("{\"status\":500,\"message\":\"密码错误\"}"));

    StepVerifier.create(client.login("alice", "bad"))
            .expectErrorSatisfies(e -> assertThat(e)
                    .isInstanceOf(SmartLinkClient.SmartLinkAuthException.class)
                    .hasMessage("密码错误"))
            .verify();
}

@Test
void fetchDisplayNameSendsAccessTokenHeader() throws InterruptedException {
    server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
            .setBody("{\"result\":{\"name\":\"张三\"}}"));

    StepVerifier.create(client.fetchDisplayName("sl-tok"))
            .expectNext("张三")
            .verifyComplete();

    assertThat(server.takeRequest().getHeader("X-Access-Token")).isEqualTo("sl-tok");
}
```

Run: `mvn -q -Dtest=SmartLinkClientTest test` Expected: COMPILATION ERROR

- [ ] **Step 2: 实现**

```java
@Component
public class SmartLinkClient {

    private final WebClient http;

    @Autowired
    public SmartLinkClient(SmartLinkProperties props, WebClient.Builder builder) {
        this(builder.clone().baseUrl(props.baseUrl()));
    }

    SmartLinkClient(WebClient.Builder builder) {
        this.http = builder.build();
    }

    public Mono<LoginResult> login(String username, String password) {
        return http.post().uri("/api/authorize/login")
                .bodyValue(Map.of("username", username, "password", password, "expires", -1, "remember", true))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        resp -> Mono.error(new SmartLinkAuthException("用户名或密码错误")))
                .onStatus(HttpStatusCode::isError,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .map(SmartLinkUnavailableException::new))
                .bodyToMono(JsonNode.class)
                .onErrorMap(WebClientRequestException.class, e -> new SmartLinkUnavailableException(e.getMessage()))
                .flatMap(body -> {
                    if (body.path("status").asInt(200) != 200) {
                        return Mono.error(new SmartLinkAuthException(body.path("message").asText("用户名或密码错误")));
                    }
                    var result = body.path("result");
                    return Mono.just(new LoginResult(result.path("userId").asText(), result.path("token").asText()));
                });
    }

    public Mono<String> fetchDisplayName(String smartLinkToken) {
        return http.get().uri("/api/user/detail")
                .header("X-Access-Token", smartLinkToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .map(SmartLinkUnavailableException::new))
                .bodyToMono(JsonNode.class)
                .onErrorMap(WebClientRequestException.class, e -> new SmartLinkUnavailableException(e.getMessage()))
                .map(body -> body.path("result").path("name").asText());
    }
    // LoginResult / 两个异常类保持不变
}
```

- [ ] **Step 3: AuthController 响应式重写**

```java
@PostMapping("/login")
Mono<ResponseEntity<Map<String, ? extends Object>>> login(@RequestBody LoginRequest request) {
    return smartLink.login(request.username(), request.password())
            .flatMap(result -> smartLink.fetchDisplayName(result.smartLinkToken())
                    .flatMap(name -> sessions.create(result.userId(), name, result.smartLinkToken())
                            .map(token -> ResponseEntity.ok(
                                    Map.of("token", token, "userId", result.userId(), "name", name)))))
            .onErrorResume(SmartLinkClient.SmartLinkAuthException.class,
                    e -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()))))
            .onErrorResume(SmartLinkClient.SmartLinkUnavailableException.class,
                    e -> Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", "登录服务暂时不可用"))));
}

@PostMapping("/logout")
Mono<ResponseEntity<Void>> logout(@RequestHeader("Authorization") String authorization) {
    String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
    return sessions.delete(token).thenReturn(ResponseEntity.noContent().build());
}
```

`/me` 不变（参数注入，无 IO）。Spring MVC 支持 controller 返回 Mono（async dispatch），Task 6 切 WebFlux 后同样成立。`AuthControllerTest` 的 mock 相应改为 `Mono.just(...)` / `Mono.error(...)`；MockMvc 对 async 结果需要 `.andExpect(request().asyncStarted())` + `asyncDispatch`，或直接把该测试迁移推迟到 Task 6 的 WebTestClient 重写——**选择后者**：本 Task 仅把 AuthControllerTest 中 mock 类型改到编译通过并用 `asyncDispatch` 模式跑通（Spring 文档模式：`var mvcResult = mvc.perform(...).andExpect(request().asyncStarted()).andReturn(); mvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk())`）。

- [ ] **Step 4: 回归 + 提交**

Run: `mvn -q test` Expected: 全部 PASS

```bash
git add -A src/main/java/com/jowo/agent src/test/java/com/jowo/agent
git commit -m "refactor: smartlink client and auth controller to reactive

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: Web 层切换 WebFlux（Tomcat → Netty）

**Files:**
- Modify: `pom.xml`（移除 `spring-boot-starter-web`）
- Modify: `src/main/java/com/jowo/agent/web/CurrentUserArgumentResolver.java`
- Modify: `src/main/java/com/jowo/agent/web/WebConfig.java`
- Modify: `src/main/java/com/jowo/agent/controller/UploadController.java`
- Modify: `src/main/java/com/jowo/agent/controller/ChatController.java`（sessionService 调用包 boundedElastic，Task 7 移除）
- Modify: `src/main/java/com/jowo/agent/service/FileStorageService.java`
- Modify: `src/main/resources/application.yml`（删 `spring.servlet.multipart` 与 `spring.http.client` 块）
- Test: `src/test/java/com/jowo/agent/controller/ChatControllerTest.java`、`AuthControllerTest.java`、`UploadControllerTest.java`、`src/test/java/com/jowo/agent/web/CurrentUserArgumentResolverTest.java`（全部迁移到 @WebFluxTest / WebTestClient）

**Interfaces:**
- Produces: 
  - `CurrentUserArgumentResolver implements org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver`，`resolveArgument(...) : Mono<Object>`，未认证 → `Mono.error(UnauthorizedException)`。
  - `FileStorageService.save(String sessionId, org.springframework.http.codec.multipart.FilePart part) : Mono<Ref>`（原 MultipartFile 版本删除）。
- 端点路径、请求/响应 JSON 结构全部保持不变（前端零改动）。

- [ ] **Step 1: pom 切换**

删除 `spring-boot-starter-web` 依赖块（webflux 已在 Task 1 加入）。同时删除 `application.yml` 的 `spring.servlet.multipart` 与 `spring.http.client` 配置块。

- [ ] **Step 2: 迁移测试到 WebTestClient（失败测试）**

统一迁移模式：

| MVC 写法 | WebFlux 写法 |
|---|---|
| `@WebMvcTest(X.class)` | `@WebFluxTest(X.class)` + `@Import(WebConfig.class, AuthExceptionHandler.class, CurrentUserArgumentResolver.class)` |
| `@Autowired MockMvc mvc` | `@Autowired WebTestClient client` |
| `mvc.perform(post("/p").contentType(..).content(body))` | `client.post().uri("/p").contentType(..).bodyValue(body).exchange()` |
| `.andExpect(status().isOk())` | `.expectStatus().isOk()` |
| `.andExpect(jsonPath("$.x").value(v))` | `.expectBody().jsonPath("$.x").isEqualTo(v)` |
| `sessions.resolve` mock | 返回 `Mono.just(user)`（未认证场景 `Mono.empty()`） |

`ChatControllerTest` 迁移后的代表用例：

```java
@WebFluxTest(ChatController.class)
@Import({com.jowo.agent.web.WebConfig.class, com.jowo.agent.web.AuthExceptionHandler.class,
         com.jowo.agent.web.CurrentUserArgumentResolver.class})
class ChatControllerTest {

    @Autowired WebTestClient client;
    @MockBean AgentService agentService;
    @MockBean SessionService sessionService;
    @MockBean ChatMemory chatMemory;
    @MockBean AuthSessionStore sessions;

    private void asUser1() {
        when(sessions.resolve("test-token")).thenReturn(
                Mono.just(new AuthSessionStore.AuthUser("user-1", "Test User", "sl-tok")));
    }

    @Test
    void chatCompletionsStreams() {
        asUser1();
        when(sessionService.touchOrCreate("conv-1", "user-1", "hi"))
                .thenReturn(SessionService.AccessResult.OWNED);
        when(agentService.chat("conv-1", "sl-tok", "hi"))
                .thenReturn(Flux.just("{\"type\":\"delta\",\"content\":\"hello back\"}\n"));

        client.post().uri("/api/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token")
                .bodyValue("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"conversationId\":\"conv-1\"}")
                .exchange()
                .expectStatus().isOk();

        verify(agentService).chat("conv-1", "sl-tok", "hi");
    }

    @Test
    void missingAuthHeaderIs401() {
        client.post().uri("/api/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"messages\":[]}")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
```

其余现有用例按上表机械迁移，断言目标不变。`UploadControllerTest` 上传用例改用 `MultipartBodyBuilder`：

```java
var builder = new org.springframework.http.client.MultipartBodyBuilder();
builder.part("sessionId", "s1");
builder.part("files", new org.springframework.core.io.ByteArrayResource(pngBytes) {
    @Override public String getFilename() { return "a.png"; }
}).contentType(MediaType.IMAGE_PNG);
client.post().uri("/api/files/upload")
        .header("Authorization", "Bearer test-token")
        .body(org.springframework.web.reactive.function.BodyInserters.fromMultipartData(builder.build()))
        .exchange()
        .expectStatus().isOk();
```

`CurrentUserArgumentResolverTest` 改为纯单元测试（不起容器）：

```java
@Test
void resolvesUserFromBearerToken() {
    var sessions = mock(AuthSessionStore.class);
    var user = new AuthSessionStore.AuthUser("u1", "N", "sl");
    when(sessions.resolve("tok")).thenReturn(Mono.just(user));
    var resolver = new CurrentUserArgumentResolver(sessions);
    var exchange = org.springframework.mock.web.server.MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/x")
                    .header("Authorization", "Bearer tok").build());

    StepVerifier.create(resolver.resolveArgument(null, null, exchange))
            .expectNext(user)
            .verifyComplete();
}

@Test
void missingHeaderErrorsUnauthorized() {
    var resolver = new CurrentUserArgumentResolver(mock(AuthSessionStore.class));
    var exchange = org.springframework.mock.web.server.MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/x").build());

    StepVerifier.create(resolver.resolveArgument(null, null, exchange))
            .expectError(UnauthorizedException.class)
            .verify();
}
```

（`resolveArgument` 实现不得解引用 parameter/bindingContext 之外再用它们——传 null 可行是因为实现只读 exchange；若实现需要 parameter 判断注解，测试用 `MethodParameter` 真实构造，见实现步骤。supportsParameter 用真实 MethodParameter 测。）

Run: `mvn -q test` Expected: 大面积 COMPILATION ERROR（预期，进入 Step 3）

- [ ] **Step 3: 迁移 resolver / WebConfig / 异常处理**

`CurrentUserArgumentResolver.java` 全文：

```java
package com.jowo.agent.web;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;

import com.jowo.agent.service.AuthSessionStore;

import reactor.core.publisher.Mono;

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
    public Mono<Object> resolveArgument(MethodParameter parameter, BindingContext bindingContext,
                                         ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
        if (token == null) return Mono.error(new UnauthorizedException());
        return sessions.resolve(token)
                .cast(Object.class)
                .switchIfEmpty(Mono.error(new UnauthorizedException()));
    }
}
```

`WebConfig.java` 全文：

```java
package com.jowo.agent.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;

@Configuration
public class WebConfig implements WebFluxConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebConfig(CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
        configurer.addCustomResolver(currentUserArgumentResolver);
    }
}
```

`AuthExceptionHandler` 不改（`@RestControllerAdvice` + `@ExceptionHandler` 在 WebFlux 生效）。

- [ ] **Step 4: UploadController + FileStorageService 迁移**

`FileStorageService.save` 替换为 FilePart 版本（校验逻辑不变，先落临时文件再校验）：

```java
public Mono<Ref> save(String sessionId, org.springframework.http.codec.multipart.FilePart part) {
    return Mono.defer(() -> {
        final Path temp;
        try {
            temp = Files.createTempFile("upload-", ".tmp");
        } catch (IOException e) {
            return Mono.error(e);
        }
        return part.transferTo(temp)
                .then(Mono.fromCallable(() -> finalizeSave(sessionId, part, temp))
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()))
                .doFinally(sig -> {
                    try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
                });
    });
}

private Ref finalizeSave(String sessionId, org.springframework.http.codec.multipart.FilePart part, Path temp)
        throws IOException {
    long size = Files.size(temp);
    if (size > props.getMaxSizeBytes()) {
        throw new RejectedException("FILE_REJECTED", "file exceeds " + props.getMaxSizeMb() + " MB");
    }
    var contentType = part.headers().getContentType();
    var declaredType = contentType == null ? null : contentType.toString();
    if (!props.getAllowedContentTypes().contains(declaredType)) {
        throw new RejectedException("FILE_REJECTED", "content-type " + declaredType + " not allowed");
    }
    var detector = new org.apache.tika.detect.DefaultDetector();
    var metadata = new org.apache.tika.metadata.Metadata();
    String detected;
    try (var is = new java.io.BufferedInputStream(Files.newInputStream(temp))) {
        detected = detector.detect(is, metadata).toString();
    }
    if (!props.getAllowedContentTypes().contains(detected)) {
        throw new RejectedException("MIME_MISMATCH", "real type " + detected + " differs from declared " + declaredType);
    }
    var dir = Path.of(props.getDir(), sanitize(sessionId));
    Files.createDirectories(dir);
    var target = resolveUnique(dir, sanitize(part.filename()));
    Files.copy(temp, target, StandardCopyOption.REPLACE_EXISTING);
    return new Ref(target.getFileName().toString(), target.toAbsolutePath().toString(), size);
}
```

删除 MultipartFile 版 `save` 与其 import。`FileStorageServiceTest`/`FileStorageServiceTestData` 改为构造 FilePart mock（mock `FilePart.transferTo` 为 `Files.copy` + `Mono.empty()`，`filename()`、`headers().getContentType()` 按用例给值）。

`UploadController`：

```java
@PostMapping("/upload")
Mono<ResponseEntity<Object>> upload(
        @RequestPart("sessionId") String sessionId,
        @RequestPart("files") Flux<FilePart> files,
        @CurrentUser AuthSessionStore.AuthUser user) {
    return files.collectList().flatMap(list -> {
        if (list.size() > props.getMaxFilesPerRequest()) {
            return Mono.just(ResponseEntity.badRequest().body((Object) Map.of(
                    "code", "FILE_REJECTED",
                    "reason", "exceeds max files per request: " + props.getMaxFilesPerRequest())));
        }
        return Flux.fromIterable(list)
                .concatMap(f -> storage.save(sessionId, f)
                        .onErrorMap(RejectedException.class, e -> new PerFileRejected(e, f.filename())))
                .collectList()
                .map(refs -> ResponseEntity.ok((Object) refs));
    })
    .onErrorResume(PerFileRejected.class, e -> Mono.just(ResponseEntity.badRequest().body((Object) Map.of(
            "code", e.cause.getCode(), "reason", e.cause.getMessage(), "file", e.filename))))
    .onErrorResume(IOException.class, e -> Mono.just(ResponseEntity.internalServerError().body((Object) Map.of(
            "code", "STORAGE_ERROR", "reason", e.getMessage()))));
}

private static final class PerFileRejected extends RuntimeException {
    final RejectedException cause;
    final String filename;
    PerFileRejected(RejectedException cause, String filename) {
        super(cause.getMessage(), cause);
        this.cause = cause;
        this.filename = filename;
    }
}
```

`download` 端点：签名改 `Mono<ResponseEntity<?>>`，整体包 `Mono.fromCallable(() -> /* 原方法体 */).subscribeOn(Schedulers.boundedElastic())`（内部 `Files.isRegularFile`/`probeContentType` 是阻塞文件 IO）。

`ChatController`：`sessionService.touchOrCreate` / `findOwned` / `listForUser` 仍是阻塞 JDBC，在 WebFlux 下不能直接跑在 Netty 线程——本 Task 先包装：

```java
// chatCompletions 与 chat 端点改为：
return Mono.fromCallable(() -> sessionService.touchOrCreate(ownerId, user.userId(), userMsg))
        .subscribeOn(Schedulers.boundedElastic())
        .map(access -> access == SessionService.AccessResult.FORBIDDEN
                ? ResponseEntity.status(HttpStatus.NOT_FOUND).<Flux<String>>build()
                : ResponseEntity.ok().body(stream));
// listConversations / getConversationMessages 的 findOwned、listForUser 同样用 fromCallable + boundedElastic 包装
```

（Task 7 换 R2DBC 后移除这些包装。）

- [ ] **Step 5: 启动冒烟 + 回归 + 提交**

Run: `mvn -q test` Expected: 全部 PASS
Run: `mvn -q spring-boot:run -Dspring-boot.run.profiles=local &`，观察启动日志出现 `Netty started on port 8088`（而非 Tomcat），然后 `curl -s -o /dev/null -w '%{http_code}' http://localhost:8088/api/models -H 'Authorization: Bearer x'` Expected: `401`（认证链生效）；停止进程。

```bash
git add -A
git commit -m "feat: switch web layer from MVC/Tomcat to WebFlux/Netty

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: SessionRepository → R2DBC

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/com/jowo/agent/repository/SessionRepository.java`
- Modify: `src/main/java/com/jowo/agent/service/SessionService.java`
- Modify: `src/main/java/com/jowo/agent/controller/ChatController.java`（移除 boundedElastic 包装）
- Modify: `src/main/resources/application.yml`、`application-local.yml`
- Test: `src/test/java/com/jowo/agent/repository/SessionRepositoryTest.java`、`src/test/java/com/jowo/agent/service/SessionServiceTest.java`

**Interfaces:**
- Produces:
  - `SessionRepository.findById(String) : Mono<SessionRow>`（无记录 → empty）
  - `SessionRepository.insert(String,String,String) : Mono<Void>`；`touch(String) : Mono<Void>`；`findByUserId(String) : Flux<SessionRow>`
  - `SessionService.touchOrCreate(...) : Mono<AccessResult>`；`findOwned(...) : Mono<SessionRow>`；`listForUser(...) : Flux<SessionRow>`
- Flyway 继续通过 JDBC DataSource 跑迁移（配置保留）。

- [ ] **Step 1: pom 加依赖**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-r2dbc</artifactId>
</dependency>
<dependency>
    <groupId>io.asyncer</groupId>
    <artifactId>r2dbc-mysql</artifactId>
    <version>1.3.0</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.r2dbc</groupId>
    <artifactId>r2dbc-h2</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: 改写 SessionRepositoryTest（失败测试）**

```java
package com.jowo.agent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.r2dbc.core.DatabaseClient;

import io.r2dbc.spi.ConnectionFactories;

class SessionRepositoryTest {

    private SessionRepository repository;

    @BeforeEach
    void setUp() {
        var dbName = "session-repo-test-" + System.nanoTime();
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        Flyway.configure().dataSource(dataSource).load().migrate();
        var connectionFactory = ConnectionFactories.get(
                "r2dbc:h2:mem:///" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        repository = new SessionRepository(DatabaseClient.create(connectionFactory));
    }

    @Test
    void findByIdOnUnknownIdReturnsEmpty() {
        assertThat(repository.findById("no-such-id").blockOptional()).isEmpty();
    }

    @Test
    void insertThenFindByIdReturnsRow() {
        repository.insert("s1", "user-1", "第一次对话").block();

        var row = repository.findById("s1").blockOptional();

        assertThat(row).isPresent();
        assertThat(row.get().id()).isEqualTo("s1");
        assertThat(row.get().userId()).isEqualTo("user-1");
        assertThat(row.get().title()).isEqualTo("第一次对话");
    }

    @Test
    void findByUserIdReturnsNewestFirst() {
        repository.insert("s1", "user-1", "first").block();
        repository.insert("s2", "user-1", "second").block();
        repository.touch("s1").block();

        var rows = repository.findByUserId("user-1").collectList().block();

        assertThat(rows).extracting(SessionRepository.SessionRow::id).containsExactly("s1", "s2");
    }
}
```

（原文件其余用例按 `.block()` 模式同样迁移，断言不变。）

Run: `mvn -q -Dtest=SessionRepositoryTest test` Expected: COMPILATION ERROR

- [ ] **Step 3: 重写 SessionRepository（DatabaseClient）**

```java
package com.jowo.agent.repository;

import java.time.LocalDateTime;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class SessionRepository {

    private final DatabaseClient db;

    public SessionRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<SessionRow> findById(String id) {
        return db.sql("SELECT id, user_id, title, created_at, updated_at FROM sessions WHERE id = :id")
                .bind("id", id)
                .map(SessionRepository::mapRow)
                .one();
    }

    public Mono<Void> insert(String id, String userId, String title) {
        var now = LocalDateTime.now();
        return db.sql("INSERT INTO sessions (id, user_id, title, created_at, updated_at) VALUES (:id, :userId, :title, :createdAt, :updatedAt)")
                .bind("id", id).bind("userId", userId).bind("title", title)
                .bind("createdAt", now).bind("updatedAt", now)
                .fetch().rowsUpdated().then();
    }

    public Mono<Void> touch(String id) {
        return db.sql("UPDATE sessions SET updated_at = :now WHERE id = :id")
                .bind("now", LocalDateTime.now()).bind("id", id)
                .fetch().rowsUpdated().then();
    }

    public Flux<SessionRow> findByUserId(String userId) {
        return db.sql("SELECT id, user_id, title, created_at, updated_at FROM sessions WHERE user_id = :userId ORDER BY updated_at DESC")
                .bind("userId", userId)
                .map(SessionRepository::mapRow)
                .all();
    }

    private static SessionRow mapRow(Readable row) {
        return new SessionRow(
                row.get("id", String.class),
                row.get("user_id", String.class),
                row.get("title", String.class),
                row.get("created_at", LocalDateTime.class),
                row.get("updated_at", LocalDateTime.class));
    }

    public record SessionRow(String id, String userId, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
```

- [ ] **Step 4: SessionService / ChatController 响应式化**

`SessionService`：

```java
public Mono<AccessResult> touchOrCreate(String sessionId, String userId, String firstMessage) {
    return repository.findById(sessionId)
            .flatMap(existing -> existing.userId().equals(userId)
                    ? repository.touch(sessionId).thenReturn(AccessResult.OWNED)
                    : Mono.just(AccessResult.FORBIDDEN))
            .switchIfEmpty(Mono.defer(() ->
                    repository.insert(sessionId, userId, title(firstMessage)).thenReturn(AccessResult.OWNED)));
}

public Mono<SessionRow> findOwned(String sessionId, String userId) {
    return repository.findById(sessionId).filter(s -> s.userId().equals(userId));
}

public Flux<SessionRow> listForUser(String userId) {
    return repository.findByUserId(userId);
}
```

`ChatController`：移除 Task 6 的 fromCallable/boundedElastic 包装，直接链式：

```java
@PostMapping(value = "/chat/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
Mono<ResponseEntity<Flux<String>>> chatCompletions(@RequestBody Map<String, Object> body,
        @CurrentUser AuthSessionStore.AuthUser user) {
    var messages = getMessages(body);
    var userMsg = messages.isEmpty() ? "" : messages.get(messages.size() - 1).getOrDefault("content", "");
    var ownerId = body.getOrDefault("conversationId", UUID.randomUUID().toString()).toString();
    return sessionService.touchOrCreate(ownerId, user.userId(), userMsg).map(access -> {
        if (access == SessionService.AccessResult.FORBIDDEN) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).<Flux<String>>build();
        }
        var chunkId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);
        Flux<String> stream = agentService.chat(ownerId, user.smartLinkToken(), userMsg)
                .flatMap(line -> toContentChunk(line).map(Flux::just).orElseGet(Flux::empty))
                .map(line -> toOpenAiChunk(chunkId, line))
                .concatWithValues("[DONE]");
        return ResponseEntity.ok().body(stream);
    });
}
// legacy /api/chat、listConversations（返回 Flux<ConversationSummary>）、
// getConversationMessages（findOwned.hasElement() 分支）同模式改写
```

`SessionServiceTest` / `ChatControllerTest` mock 与断言改为 Mono/Flux（`when(sessionService.touchOrCreate(...)).thenReturn(Mono.just(AccessResult.OWNED))`）。

- [ ] **Step 5: 配置 R2DBC 数据源**

`application-local.yml` `spring:` 下追加（datasource 块保留给 Flyway）：

```yaml
  r2dbc:
    url: r2dbc:mysql://${JDBC_HOST:127.0.0.1}:${JDBC_PORT:3306}/db_jowo_im?serverZoneId=Asia/Shanghai
    username: ${JDBC_USER:root}
    password: ${JDBC_PASS:123456}
```

`application.yml` 的 flyway 块补充显式绑定（防止 r2dbc 自动配置吞掉 datasource）：

```yaml
spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 0
    url: ${spring.datasource.url:}
    user: ${spring.datasource.username:}
    password: ${spring.datasource.password:}
```

（dev 环境的 datasource 在 Nacos 中配置，需同步在 Nacos 的 `l7-skill-backend-dev.yaml` 里加同构 `spring.r2dbc.*` 三项——列入部署检查单，不在本仓库改。）

- [ ] **Step 6: 回归 + 冒烟 + 提交**

Run: `mvn -q test` Expected: 全部 PASS
Run: 本地起服务（local profile，需本地 MySQL），登录后发一条消息，确认会话列表/持久化正常。

```bash
git add -A
git commit -m "refactor: session persistence from JDBC to R2DBC

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 8: BlockHound 非阻塞守门

**Files:**
- Modify: `pom.xml`
- Test: `src/test/java/com/jowo/agent/ReactiveChainBlockHoundTest.java` (Create)

**Interfaces:**
- Consumes: Task 2-5 产出的响应式 `AgentService` 及各 Mono/Flux 接口。
- Produces: 无生产代码；CI 级守门测试。

- [ ] **Step 1: pom 加 BlockHound 与 JVM 参数**

```xml
<dependency>
    <groupId>io.projectreactor.tools</groupId>
    <artifactId>blockhound</artifactId>
    <version>1.0.9.RELEASE</version>
    <scope>test</scope>
</dependency>
```

`<properties>` 追加（JDK 13+ 运行 BlockHound 必需）：

```xml
<argLine>-XX:+AllowRedefinitionToAddDeleteMethods</argLine>
```

- [ ] **Step 2: 写守门测试**

```java
package com.jowo.agent;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.jowo.agent.service.*;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class ReactiveChainBlockHoundTest {

    @BeforeAll
    static void installBlockHound() {
        BlockHound.install();
    }

    @Test
    void blockHoundIsActive() {
        StepVerifier.create(Mono.delay(Duration.ofMillis(10)).map(t -> {
                    try { Thread.sleep(1); } catch (InterruptedException e) { throw new RuntimeException(e); }
                    return t;
                }))
                .expectErrorMatches(e -> e instanceof Error && e.getMessage().contains("Blocking call"))
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void agentChatChainHasNoBlockingCallsOnParallelScheduler() {
        LlmClient llm = mock(LlmClient.class);
        EnterpriseApiClient enterpriseApi = mock(EnterpriseApiClient.class);
        ApiCatalog catalog = mock(ApiCatalog.class);
        ChatMemory memory = mock(ChatMemory.class);
        when(catalog.compactIndex()).thenReturn("MES | x | GET /a | d\n");
        when(memory.get(any())).thenReturn(Mono.just(List.of()));
        when(memory.append(any(), anyList())).thenReturn(Mono.empty());
        when(llm.chatStream(any(), any())).thenReturn(
                Flux.just(new LlmClient.LlmEvent.ContentDelta("你好"),
                          new LlmClient.LlmEvent.Completed("你好"))
                        .subscribeOn(Schedulers.parallel()));
        var service = new AgentService(llm, enterpriseApi, catalog, memory);

        StepVerifier.create(service.chat("conv-1", "sl-tok", "hi")
                        .subscribeOn(Schedulers.parallel()))
                .expectNextCount(4) // thinking + delta + 完整回答 + done
                .verifyComplete();
    }
}
```

Run: `mvn -q -Dtest=ReactiveChainBlockHoundTest test` Expected: PASS。若第二个测试因链路里残留阻塞调用（如遗漏的 `.block()`）失败，修复对应生产代码后重跑，直到通过。

- [ ] **Step 3: 全量回归 + 提交**

Run: `mvn -q test` Expected: 全部 PASS（BlockHound 全局 install 可能暴露其他测试里的阻塞——只允许修测试代码或真实链路缺陷，不允许放宽 BlockHound）

```bash
git add pom.xml src/test/java/com/jowo/agent/ReactiveChainBlockHoundTest.java
git commit -m "test: add BlockHound gate for reactive chain

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## 阶段一完成验收

1. `mvn -q test` 全绿（含 BlockHound 守门）。
2. 本地启动日志显示 Netty（非 Tomcat）。
3. 前端实测：回答逐字流式输出；登录、会话列表、历史消息、文件上传下载全部正常。
4. `git log` 呈现 Task 1-8 的 8 个独立提交。

阶段二（Redis Streams 多 Agent）另行出计划。
