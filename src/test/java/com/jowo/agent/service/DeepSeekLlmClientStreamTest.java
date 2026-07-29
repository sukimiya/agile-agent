package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.*;
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
        client = new DeepSeekLlmClient(props, WebClient.builder());
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
