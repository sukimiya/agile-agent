package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import reactor.test.StepVerifier;

class SmartLinkClientTest {

    private MockWebServer server;
    private SmartLinkClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new SmartLinkClient(WebClient.builder().baseUrl(server.url("/").toString()));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

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
    void loginThrowsAuthExceptionWhenBodyStatusIsNot200() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"用户名或密码错误\",\"status\":401}"));

        StepVerifier.create(client.login("sukimiya", "wrong"))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(SmartLinkClient.SmartLinkAuthException.class)
                        .hasMessage("用户名或密码错误"))
                .verify();
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
    void loginThrowsUnavailableExceptionOn5xx() {
        server.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(client.login("sukimiya", "pw"))
                .expectError(SmartLinkClient.SmartLinkUnavailableException.class)
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

    @Test
    void fetchUserTypeSendsAccessTokenHeaderAndParsesResult() throws InterruptedException {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"result\":{\"user\":{\"userType\":\"admin\"}}}"));

        StepVerifier.create(client.fetchUserType("sl-tok"))
                .expectNext("admin")
                .verifyComplete();

        var req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/api/authorize/me");
        assertThat(req.getHeader("X-Access-Token")).isEqualTo("sl-tok");
    }

    @Test
    void fetchUserTypeThrowsUnavailableExceptionOn5xx() {
        server.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(client.fetchUserType("sl-tok"))
                .expectError(SmartLinkClient.SmartLinkUnavailableException.class)
                .verify();
    }
}
