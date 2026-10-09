package io.github.sukimiya.agileagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.AgentApplication;
import io.github.sukimiya.agileagent.config.DefaultLlmProperties;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import reactor.test.StepVerifier;

@SpringBootTest(classes = AgentApplication.class)
@TestPropertySource(properties = {
        "llm.default.base-url=http://localhost/v1",
        "llm.default.api-key=test-key",
        "llm.default.model=configured-model"
})
class OpenAiCompatibleLlmClientTest {
    @Autowired private DefaultLlmProperties properties;
    @Autowired private ApplicationContext context;
    private final ObjectMapper json = new ObjectMapper();
    private MockWebServer server;
    private OpenAiCompatibleLlmClient client;

    @Test
    void temporaryConfigurationBindsAndExposesExactlyOneDefaultClient() {
        assertThat(properties.baseUrl()).isEqualTo("http://localhost/v1");
        assertThat(properties.apiKey()).isEqualTo("test-key");
        assertThat(properties.model()).isEqualTo("configured-model");
        assertThat(context.getBeansOfType(LlmClient.class)).hasSize(1);
        assertThat(context.getBean(LlmClient.class)).isInstanceOf(OpenAiCompatibleLlmClient.class);
    }

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new OpenAiCompatibleLlmClient(server.url("/v1").toString(), "test-key", "configured-model",
                WebClient.builder());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void emptyToolListOmitsToolsAndToolChoice() throws Exception {
        JsonNode body = sendAndReadRequest(List.of(LlmClient.Message.user("hello")), List.of());
        assertThat(body.has("tools")).isFalse();
        assertThat(body.has("tool_choice")).isFalse();
        assertThat(body.path("messages").get(0).path("content").asText()).isEqualTo("hello");
    }

    @Test
    void nonEmptyToolListIncludesToolsAndAutoChoiceWithMessageHistory() throws Exception {
        var messages = List.of(
                LlmClient.Message.system("sys"),
                LlmClient.Message.user("hello"),
                LlmClient.Message.assistantToolCalls(List.of(new LlmClient.ToolCall("c1", "lookup", "{\"a\":1}"))),
                LlmClient.Message.toolResult("c1", "detail text"));
        var tools = List.of(new LlmClient.ToolDefinition("lookup", "desc",
                Map.of("type", "object", "properties", Map.of())));
        JsonNode body = sendAndReadRequest(messages, tools);
        assertThat(body.path("messages")).hasSize(4);
        assertThat(body.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("messages").get(2).has("content")).isFalse();
        assertThat(body.path("messages").get(2).path("tool_calls").get(0).path("function").path("name").asText())
                .isEqualTo("lookup");
        assertThat(body.path("messages").get(2).path("tool_calls").get(0).path("function").path("arguments").asText())
                .isEqualTo("{\"a\":1}");
        assertThat(body.path("messages").get(3).path("tool_call_id").asText()).isEqualTo("c1");
        assertThat(body.path("tools")).hasSize(1);
        assertThat(body.path("tools").get(0).path("function").path("name").asText()).isEqualTo("lookup");
        assertThat(body.path("tool_choice").asText()).isEqualTo("auto");
    }

    @Test
    void configuredModelIsSentInStreamingRequest() throws Exception {
        JsonNode body = sendAndReadRequest(List.of(LlmClient.Message.user("hello")), List.of());
        assertThat(body.path("model").asText()).isEqualTo("configured-model");
        assertThat(body.path("stream").asBoolean()).isTrue();
    }

    private JsonNode sendAndReadRequest(List<LlmClient.Message> messages, List<LlmClient.ToolDefinition> tools)
            throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"));
        StepVerifier.create(client.chatStream(messages, tools))
                .expectNext(new LlmClient.LlmEvent.Completed(""))
                .verifyComplete();
        var request = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
        return json.readTree(request.getBody().readUtf8());
    }
}
