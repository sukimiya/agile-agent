package com.jowo.agent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.jowo.agent.config.OAuth2Properties;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import reactor.test.StepVerifier;

class OAuth2ClientTest {

    private MockWebServer server;
    private OAuth2Client client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String base = server.url("/").toString();
        var props = new OAuth2Properties(
                base + "oauth2/authorize", base + "oauth2/token", base + "oauth2/userinfo",
                "agile-agent", "s3cr3t", "http://localhost:8088/api/auth/oauth2/callback",
                "openid profile", "userType", "http://localhost:5173/login/callback");
        client = new OAuth2Client(props, WebClient.builder().baseUrl(base));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void buildAuthorizationUrlIncludesPkceAndState() {
        String url = client.buildAuthorizationUrl("state-1", "challenge-1");

        assertThat(url).startsWith(server.url("/oauth2/authorize").toString());
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("client_id=agile-agent");
        assertThat(url).contains("state=state-1");
        assertThat(url).contains("code_challenge=challenge-1");
        assertThat(url).contains("code_challenge_method=S256");
    }

    @Test
    void exchangeCodePostsAuthorizationCodeGrantAndParsesTokens() throws InterruptedException {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"at-1\",\"refresh_token\":\"rt-1\",\"expires_in\":3600}"));

        StepVerifier.create(client.exchangeCode("code-1", "verifier-1"))
                .assertNext(r -> {
                    assertThat(r.accessToken()).isEqualTo("at-1");
                    assertThat(r.refreshToken()).isEqualTo("rt-1");
                    assertThat(r.expiresInSeconds()).isEqualTo(3600);
                })
                .verifyComplete();

        var req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/oauth2/token");
        String body = req.getBody().readUtf8();
        assertThat(body).contains("grant_type=authorization_code");
        assertThat(body).contains("code=code-1");
        assertThat(body).contains("code_verifier=verifier-1");
    }

    @Test
    void exchangeCodeThrowsAuthExceptionOn4xx() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("invalid_grant"));

        StepVerifier.create(client.exchangeCode("bad-code", "verifier-1"))
                .expectError(OAuth2Client.OAuth2AuthException.class)
                .verify();
    }

    @Test
    void refreshPostsRefreshTokenGrant() throws InterruptedException {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"at-2\",\"refresh_token\":\"rt-2\",\"expires_in\":1800}"));

        StepVerifier.create(client.refresh("rt-1"))
                .assertNext(r -> assertThat(r.accessToken()).isEqualTo("at-2"))
                .verifyComplete();

        var req = server.takeRequest();
        assertThat(req.getBody().readUtf8()).contains("grant_type=refresh_token").contains("refresh_token=rt-1");
    }

    @Test
    void refreshThrowsUnavailableExceptionOn5xx() {
        server.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(client.refresh("rt-1"))
                .expectError(OAuth2Client.OAuth2UnavailableException.class)
                .verify();
    }

    @Test
    void fetchUserInfoSendsBearerHeaderAndParsesConfiguredUserTypeClaim() throws InterruptedException {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"sub\":\"u-9\",\"name\":\"张三\",\"userType\":\"admin\"}"));

        StepVerifier.create(client.fetchUserInfo("at-1"))
                .assertNext(info -> {
                    assertThat(info.subject()).isEqualTo("u-9");
                    assertThat(info.name()).isEqualTo("张三");
                    assertThat(info.userType()).isEqualTo("admin");
                })
                .verifyComplete();

        var req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/oauth2/userinfo");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer at-1");
    }
}
