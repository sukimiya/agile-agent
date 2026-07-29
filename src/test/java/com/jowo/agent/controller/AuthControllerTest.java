package com.jowo.agent.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.SmartLinkClient;
import com.jowo.agent.web.AuthExceptionHandler;
import com.jowo.agent.web.CurrentUserArgumentResolver;
import com.jowo.agent.web.WebConfig;

import reactor.core.publisher.Mono;

@WebFluxTest(AuthController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, CurrentUserArgumentResolver.class})
class AuthControllerTest {

    @Autowired WebTestClient client;
    @MockBean SmartLinkClient smartLink;
    @MockBean AuthSessionStore sessions;

    @Test
    void loginReturnsTokenOnSuccess() {
        when(smartLink.login("sukimiya", "pw")).thenReturn(Mono.just(new SmartLinkClient.LoginResult("u1", "sl-tok")));
        when(smartLink.fetchDisplayName("sl-tok")).thenReturn(Mono.just("周琳琳"));
        when(sessions.create("u1", "周琳琳", "sl-tok")).thenReturn(Mono.just("app-tok"));

        client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"sukimiya\",\"password\":\"pw\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("app-tok")
                .jsonPath("$.userId").isEqualTo("u1")
                .jsonPath("$.name").isEqualTo("周琳琳");
    }

    @Test
    void loginReturns401OnBadCredentials() {
        when(smartLink.login("sukimiya", "wrong"))
                .thenReturn(Mono.error(new SmartLinkClient.SmartLinkAuthException("用户名或密码错误")));

        client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"sukimiya\",\"password\":\"wrong\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void loginReturns502WhenSmartLinkUnavailable() {
        when(smartLink.login(anyString(), anyString()))
                .thenReturn(Mono.error(new SmartLinkClient.SmartLinkUnavailableException("timeout")));

        client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"sukimiya\",\"password\":\"pw\"}")
                .exchange()
                .expectStatus().isEqualTo(org.springframework.http.HttpStatus.BAD_GATEWAY);
    }

    @Test
    void meReturnsCurrentUserForValidToken() {
        when(sessions.resolve("app-tok")).thenReturn(Mono.just(new AuthSessionStore.AuthUser("u1", "周琳琳", "sl-tok")));

        client.get().uri("/api/auth/me").header("Authorization", "Bearer app-tok")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo("u1")
                .jsonPath("$.name").isEqualTo("周琳琳");
    }

    @Test
    void meReturns401WithoutToken() {
        client.get().uri("/api/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void logoutDeletesSessionAndReturns204() {
        when(sessions.delete("app-tok")).thenReturn(Mono.empty());

        client.post().uri("/api/auth/logout").header("Authorization", "Bearer app-tok")
                .exchange()
                .expectStatus().isNoContent();

        verify(sessions).delete("app-tok");
    }
}
