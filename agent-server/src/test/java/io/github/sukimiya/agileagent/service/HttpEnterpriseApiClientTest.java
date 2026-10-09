package io.github.sukimiya.agileagent.service;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import io.github.sukimiya.agileagent.config.EnterpriseApiProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

class HttpEnterpriseApiClientTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private EnterpriseApiProperties props(String baseUrl) {
        return new EnterpriseApiProperties(Map.of("MES", new EnterpriseApiProperties.ServiceConfig(baseUrl)));
    }

    private HttpEnterpriseApiClient clientWithZeroDelayRetry(String baseUrl) {
        return new HttpEnterpriseApiClient(props(baseUrl), WebClient.builder(), Retry.fixedDelay(2, Duration.ZERO));
    }

    @Test
    void getRequestSendsRemainingParamsAsQueryString() throws InterruptedException {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"code\":0,\"data\":[]}"));
        var client = clientWithZeroDelayRetry(server.url("/").toString().replaceAll("/$", ""));

        StepVerifier.create(client.call("MES", "GET", "/api/mes/work-orders/page", Map.of("status", "未完成"), "user-tok"))
                .assertNext(result -> assertThat(result.path("code").asInt()).isEqualTo(0))
                .verifyComplete();

        var req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).isEqualTo("/api/mes/work-orders/page?status=%E6%9C%AA%E5%AE%8C%E6%88%90");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer user-tok");
    }

    @Test
    void getRequestPreservesBaseUrlPathPrefix() throws InterruptedException {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"code\":200}"));
        var baseUrl = server.url("/mes-service").toString().replaceAll("/$", "");
        var client = clientWithZeroDelayRetry(baseUrl);

        StepVerifier.create(client.call("MES", "GET", "/api/mes/producing-work-orders", Map.of("pageSize", "50"), "user-tok"))
                .assertNext(result -> assertThat(result.path("code").asInt()).isEqualTo(200))
                .verifyComplete();

        var req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath()).isEqualTo("/mes-service/api/mes/producing-work-orders?pageSize=50");
    }

    @Test
    void postRequestSendsParamsAsJsonBody() throws InterruptedException {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"code\":0}"));
        var client = clientWithZeroDelayRetry(server.url("/").toString().replaceAll("/$", ""));

        StepVerifier.create(client.call("MES", "POST", "/api/mes/five-s-records/create", Map.of("area", "A1"), "user-tok"))
                .expectNextCount(1)
                .verifyComplete();

        var req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/api/mes/five-s-records/create");
        assertThat(req.getBody().readUtf8()).isEqualTo("{\"area\":\"A1\"}");
    }

    @Test
    void pathParamsAreSubstitutedAndRemovedFromRemainingParams() throws InterruptedException {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"code\":0}"));
        var client = clientWithZeroDelayRetry(server.url("/").toString().replaceAll("/$", ""));

        StepVerifier.create(client.call("MES", "DELETE", "/api/feedback/delete/{id}", Map.of("id", "123"), "user-tok"))
                .expectNextCount(1)
                .verifyComplete();

        var req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("DELETE");
        assertThat(req.getPath()).isEqualTo("/api/feedback/delete/123");
    }

    @Test
    void retriesTransientFailuresThenSucceeds() {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"code\":0}"));
        var client = clientWithZeroDelayRetry(server.url("/").toString().replaceAll("/$", ""));

        StepVerifier.create(client.call("MES", "GET", "/api/x", Map.of(), "user-tok"))
                .assertNext(result -> assertThat(result.path("code").asInt()).isEqualTo(0))
                .verifyComplete();

        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    void doesNotRetryOn4xx() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("not found"));
        var client = clientWithZeroDelayRetry(server.url("/").toString().replaceAll("/$", ""));

        StepVerifier.create(client.call("MES", "GET", "/api/x", Map.of(), "user-tok"))
                .expectError(EnterpriseApiClient.EnterpriseApiException.class)
                .verify();

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void throwsAfterExhaustingAllRetries() {
        server.enqueue(new MockResponse().setResponseCode(502));
        server.enqueue(new MockResponse().setResponseCode(502));
        server.enqueue(new MockResponse().setResponseCode(502));
        var client = clientWithZeroDelayRetry(server.url("/").toString().replaceAll("/$", ""));

        StepVerifier.create(client.call("MES", "GET", "/api/x", Map.of(), "user-tok"))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(EnterpriseApiClient.EnterpriseApiException.class)
                        .hasMessageContaining("多次重试"))
                .verify();

        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    void unknownServiceThrowsWithoutAnyHttpCall() {
        var client = clientWithZeroDelayRetry(server.url("/").toString().replaceAll("/$", ""));

        StepVerifier.create(client.call("CRM", "GET", "/api/x", Map.of(), "user-tok"))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(EnterpriseApiClient.EnterpriseApiException.class)
                        .hasMessageContaining("CRM"))
                .verify();

        assertThat(server.getRequestCount()).isEqualTo(0);
    }
}
