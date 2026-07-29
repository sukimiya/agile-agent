package com.jowo.agent.controller;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.web.AuthExceptionHandler;
import com.jowo.agent.web.CurrentUserArgumentResolver;
import com.jowo.agent.web.WebConfig;

import reactor.core.publisher.Mono;

import static org.mockito.Mockito.when;

@WebFluxTest(AuthorizeController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, CurrentUserArgumentResolver.class})
class AuthorizeControllerTest {

    @Autowired WebTestClient client;
    @MockBean AuthSessionStore sessions;
    @MockBean com.jowo.agent.service.OAuth2Client oauth2;

    @Test
    void meReturnsUserTypeFromSession() {
        when(sessions.resolve("app-tok")).thenReturn(Mono.just(
                new AuthSessionStore.AuthUser("u1", "周琳琳", "at-1", "rt-1", Instant.now().plusSeconds(3600), "admin")));

        client.get().uri("/api/authorize/me").header("Authorization", "Bearer app-tok")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.result.user.userType").isEqualTo("admin");
    }

    @Test
    void meReturns401WithoutToken() {
        client.get().uri("/api/authorize/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
