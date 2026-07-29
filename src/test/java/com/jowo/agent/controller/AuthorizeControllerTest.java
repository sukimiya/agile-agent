package com.jowo.agent.controller;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.SmartLinkClient;
import com.jowo.agent.web.AuthExceptionHandler;
import com.jowo.agent.web.CurrentUserArgumentResolver;
import com.jowo.agent.web.WebConfig;

import reactor.core.publisher.Mono;

@WebFluxTest(AuthorizeController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, CurrentUserArgumentResolver.class})
class AuthorizeControllerTest {

    @Autowired WebTestClient client;
    @MockBean SmartLinkClient smartLink;
    @MockBean AuthSessionStore sessions;

    @Test
    void meReturnsUserTypeForValidToken() {
        when(sessions.resolve("app-tok")).thenReturn(Mono.just(new AuthSessionStore.AuthUser("u1", "周琳琳", "sl-tok")));
        when(smartLink.fetchUserType("sl-tok")).thenReturn(Mono.just("admin"));

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

    @Test
    void meReturns502WhenSmartLinkUnavailable() {
        when(sessions.resolve("app-tok")).thenReturn(Mono.just(new AuthSessionStore.AuthUser("u1", "周琳琳", "sl-tok")));
        when(smartLink.fetchUserType("sl-tok"))
                .thenReturn(Mono.error(new SmartLinkClient.SmartLinkUnavailableException("timeout")));

        client.get().uri("/api/authorize/me").header("Authorization", "Bearer app-tok")
                .exchange()
                .expectStatus().isEqualTo(org.springframework.http.HttpStatus.BAD_GATEWAY);
    }
}
