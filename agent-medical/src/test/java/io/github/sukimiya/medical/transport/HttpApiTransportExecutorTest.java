package io.github.sukimiya.medical.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.hospital.TransportBinding;
import io.github.sukimiya.medical.validation.EndpointScreeningPolicy;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class HttpApiTransportExecutorTest {

    private final HttpApiTransportExecutor executor = new HttpApiTransportExecutor(
            WebClient.builder(), new EndpointScreeningPolicy(Set.of("127.0.0.1", "localhost")));
    private MockWebServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.shutdown();
    }

    private static TransportRequest request(String url, ObjectNode arguments) {
        var identity = new IdentityContext("user-1", "用户一", "tenant-1", "租户一", "org-1", "机构一",
                Set.of("USER"), Set.of(), Set.of(), false, false);
        var context = new AgentExecutionContext(identity, "conv-1", () -> "token");
        return new TransportRequest("demo", "演示医院", "searchServices",
                TransportBinding.api(url), arguments, context);
    }

    @Test
    void postsToTheCuratedBindingUrlAndTreatsInjectedUrlsAsInertData() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"services\":[\"cardiology-consult\"]}"));
        var arguments = new ObjectMapper().createObjectNode()
                .put("keyword", "cardiology")
                .put("url", "http://evil.example/hijack")
                .put("endpoint", "http://evil.example/other");

        var result = executor.execute(request(server.url("/api/hospital/searchServices").toString(), arguments))
                .block(Duration.ofSeconds(15));

        assertThat(result.success()).isTrue();
        assertThat(result.displayResult()).contains("cardiology-consult");
        assertThat(server.getRequestCount()).isEqualTo(1);
        var recorded = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/api/hospital/searchServices");
        assertThat(recorded.getHeader("Content-Type")).contains("application/json");
        var sentBody = new ObjectMapper().readTree(recorded.getBody().readUtf8());
        assertThat(sentBody.get("keyword").asText()).isEqualTo("cardiology");
        assertThat(sentBody.get("url").asText()).isEqualTo("http://evil.example/hijack");
    }

    @Test
    void non2xxBecomesSafeFailureWithoutUrlOrStack() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("internal boom"));

        var result = executor.execute(request(server.url("/api/hospital/searchServices").toString(),
                        new ObjectMapper().createObjectNode()))
                .block(Duration.ofSeconds(15));

        assertThat(result.success()).isFalse();
        assertThat(result.displayResult()).isEqualTo("医院接口调用失败（HTTP 500），请稍后重试");
        assertThat(result.displayResult()).doesNotContain("127.0.0.1").doesNotContain("boom");
    }

    @Test
    void unreachableEndpointBecomesRetryableFailureWithoutHost() {
        var result = executor.execute(request("http://127.0.0.1:1/api/hospital/searchServices",
                        new ObjectMapper().createObjectNode()))
                .block(Duration.ofSeconds(20));

        assertThat(result.success()).isFalse();
        assertThat(result.displayResult()).isEqualTo("医院接口暂时不可用，请稍后重试或改用人工通道");
        assertThat(result.displayResult()).doesNotContain("127.0.0.1");
    }

    @Test
    void oversizedBodyIsTruncatedAt8000Chars() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("x".repeat(9000)));

        var result = executor.execute(request(server.url("/api/hospital/searchServices").toString(),
                        new ObjectMapper().createObjectNode()))
                .block(Duration.ofSeconds(15));

        assertThat(result.success()).isTrue();
        assertThat(result.displayResult()).endsWith("…（已截断）");
        assertThat(result.displayResult()).hasSize(8000 + "…（已截断）".length());
    }

    @Test
    void emptySuccessBodyYieldsEmptyDisplayResult() {
        server.enqueue(new MockResponse().setResponseCode(200));

        var result = executor.execute(request(server.url("/api/hospital/searchServices").toString(),
                        new ObjectMapper().createObjectNode()))
                .block(Duration.ofSeconds(15));

        assertThat(result.success()).isTrue();
        assertThat(result.displayResult()).isEmpty();
    }

    @Test
    void malformedUrlBecomesSafeFailureNotException() {
        var result = executor.execute(request("http://not a uri %%%",
                        new ObjectMapper().createObjectNode()))
                .block(Duration.ofSeconds(15));

        assertThat(result.success()).isFalse();
        assertThat(result.displayResult()).isEqualTo("医院接口暂时不可用，请稍后重试或改用人工通道");
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void refusesScreenedHostWithoutHttpCall() {
        // The cloud metadata endpoint (link-local) must never receive a packet:
        // runtime screening happens before the request is even assembled.
        var result = executor.execute(request("http://169.254.169.254/latest/meta-data",
                        new ObjectMapper().createObjectNode()))
                .block(Duration.ofSeconds(15));

        assertThat(result.success()).isFalse();
        assertThat(result.displayResult()).isEqualTo("医院接口暂时不可用，请稍后重试或改用人工通道");
        assertThat(result.displayResult()).doesNotContain("169.254.169.254");
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void emptyAllowlistRejectsEvenLoopbackFailClosed() {
        // Review focus #2: an empty or misspelled allowlist must never widen into "allow all".
        var strict = new HttpApiTransportExecutor(WebClient.builder(),
                new EndpointScreeningPolicy(Set.of()));
        var result = strict.execute(request(server.url("/api/hospital/searchServices").toString(),
                        new ObjectMapper().createObjectNode()))
                .block(Duration.ofSeconds(15));

        assertThat(result.success()).isFalse();
        assertThat(result.displayResult()).isEqualTo("医院接口暂时不可用，请稍后重试或改用人工通道");
        assertThat(server.getRequestCount()).isZero();
    }
}
