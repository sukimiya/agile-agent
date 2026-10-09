package io.github.sukimiya.agileagent.service;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import com.jowo.agent.service.LlmClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import reactor.test.StepVerifier;

class OpenAiCompatibleLlmClientStreamTest {

    private MockWebServer server;
    private OpenAiCompatibleLlmClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new OpenAiCompatibleLlmClient(server.url("/").toString(), "test-key", "configured-model",
                WebClient.builder());
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
            "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"lookup\",\"arguments\":\"{\\\"que\"}}]}}]}",
            "{\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"ry\\\":\\\"item\\\"}\"}}]}}]}",
            "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}]}",
            "[DONE]");

        StepVerifier.create(client.chatStream(
                        java.util.List.of(LlmClient.Message.user("q")), java.util.List.of()))
                .assertNext(e -> {
                    var calls = ((LlmClient.LlmEvent.ToolCallsReady) e).calls();
                    assertThat(calls).hasSize(1);
                    assertThat(calls.get(0).id()).isEqualTo("call_1");
                    assertThat(calls.get(0).name()).isEqualTo("lookup");
                    assertThat(calls.get(0).argumentsJson()).isEqualTo("{\"query\":\"item\"}");
                })
                .verifyComplete();
    }

    @Test
    void httpErrorSurfacesAsLlmExceptionWithStatus() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("rate limited; echoed-secret"));

        StepVerifier.create(client.chatStream(
                        java.util.List.of(LlmClient.Message.user("q")), java.util.List.of()))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(LlmClient.LlmException.class);
                    assertThat(((LlmClient.LlmException) e).status).isEqualTo(429);
                    assertThat(e).hasMessageStartingWith("LLM error");
                    assertThat(e.getMessage()).doesNotContain("echoed-secret");
                })
                .verify();
    }

    @Test
    void interleavedToolCallsAreEmittedInIndexOrder() {
        enqueueSse(
            "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":1,\"id\":\"b\",\"function\":{\"name\":\"second\",\"arguments\":\"{\"}},{\"index\":0,\"id\":\"a\",\"function\":{\"name\":\"first\",\"arguments\":\"{}\"}}]}}]}",
            "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":1,\"function\":{\"arguments\":\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}",
            "[DONE]");
        StepVerifier.create(client.chatStream(java.util.List.of(), java.util.List.of()))
                .expectNext(new LlmClient.LlmEvent.ToolCallsReady(java.util.List.of(
                        new LlmClient.ToolCall("a", "first", "{}"), new LlmClient.ToolCall("b", "second", "{}"))))
                .verifyComplete();
    }

    @Test
    void eachSubscriptionHasItsOwnAggregationStateAndDoneStopsTheStream() {
        for (int i = 0; i < 2; i++) {
            enqueueSse(
                "{\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}",
                "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}",
                "[DONE]", "malformed trailing data");
        }
        var stream = client.chatStream(java.util.List.of(), java.util.List.of());
        for (int i = 0; i < 2; i++) {
            StepVerifier.create(stream)
                    .expectNext(new LlmClient.LlmEvent.ContentDelta("hello"))
                    .expectNext(new LlmClient.LlmEvent.Completed("hello"))
                    .verifyComplete();
        }
    }

    @Test
    void malformedStreamFailsWithoutExposingResponseContent() {
        enqueueSse("echoed-secret");
        StepVerifier.create(client.chatStream(java.util.List.of(), java.util.List.of()))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(LlmClient.LlmException.class).hasMessageStartingWith("LLM error");
                    assertThat(((LlmClient.LlmException) e).status).isEqualTo(502);
                    assertThat(e.getMessage()).doesNotContain("echoed-secret");
                })
                .verify();
    }

    @Test
    void transportFailureIsNeutralAndDoesNotExposeExceptionDetails() {
        var failingClient = new OpenAiCompatibleLlmClient("http://localhost", "test-key", "configured-model",
                WebClient.builder().exchangeFunction(request -> reactor.core.publisher.Mono.error(
                        new IllegalStateException("echoed-secret"))));
        StepVerifier.create(failingClient.chatStream(java.util.List.of(), java.util.List.of()))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(LlmClient.LlmException.class).hasMessageStartingWith("LLM unreachable");
                    assertThat(((LlmClient.LlmException) e).status).isEqualTo(502);
                    assertThat(e.getMessage()).doesNotContain("echoed-secret");
                })
                .verify();
    }
}
