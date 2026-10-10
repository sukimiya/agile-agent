package io.github.sukimiya.agileagent.llmconfig;

import static org.mockito.Mockito.*;
import static io.github.sukimiya.agileagent.TestAuthUsers.user;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.OAuth2Client;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.AuthExceptionHandler;
import io.github.sukimiya.agileagent.web.CurrentUserArgumentResolver;
import io.github.sukimiya.agileagent.web.WebConfig;
import reactor.core.publisher.Mono;

@WebFluxTest(LlmBalanceController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, CurrentUserArgumentResolver.class})
class LlmBalanceControllerTest {

    @Autowired WebTestClient client;
    @MockBean LlmBalanceService service;
    @MockBean AuthSessionStore sessions;
    @MockBean OAuth2Client oauth2;
    @MockBean org.springframework.data.redis.core.ReactiveStringRedisTemplate redis;

    private IdentityContext identity() {
        return new IdentityContext("user-1", "User", "tenant-1", "Tenant", null, null,
                Set.of("tenant_admin"), Set.of(), Set.of(), false, true);
    }

    private void authenticate() {
        when(sessions.resolve("token")).thenReturn(Mono.just(user("user-1", "User", "access", "refresh",
                Instant.now().plusSeconds(3600), identity(), Instant.now())));
    }

    @Test
    void balanceEndpointUsesAuthenticatedIdentityAndIgnoresTenantQuerySelector() throws Exception {
        authenticate();
        when(service.getBalance(identity())).thenReturn(Mono.just(new ObjectMapper().readTree("{\"remaining\":12}")));

        client.get().uri("/api/llm-config/balance?tenantId=other")
                .header("Authorization", "Bearer token")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"remaining\":12}");

        verify(service).getBalance(identity());
    }

    @Test
    void unsupportedBalanceMapsToStable404() {
        authenticate();
        when(service.getBalance(identity())).thenReturn(Mono.error(LlmBalanceException.unsupported()));

        client.get().uri("/api/llm-config/balance")
                .header("Authorization", "Bearer token")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().json("{\"code\":\"LLM_BALANCE_UNSUPPORTED\",\"message\":\"Provider does not support balance lookup\"}");
    }
}
