package io.github.sukimiya.agileagent.llmconfig;

import static io.github.sukimiya.agileagent.TestAuthUsers.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Set;

import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.OAuth2Client;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.AuthExceptionHandler;
import io.github.sukimiya.agileagent.web.CurrentUserArgumentResolver;
import io.github.sukimiya.agileagent.web.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(TenantLlmConfigController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, CurrentUserArgumentResolver.class})
class TenantLlmConfigControllerTest {

    @Autowired WebTestClient client;
    @MockBean TenantLlmConfigService service;
    @MockBean AuthSessionStore sessions;
    @MockBean OAuth2Client oauth2;
    @MockBean org.springframework.data.redis.core.ReactiveStringRedisTemplate redis;

    @Test
    void getRequiresAuthenticationAndReturnsOnlySafeConfigShape() {
        var identity = identity("tenant-user", "tenant-a", false, false);
        when(sessions.resolve("tenant-token")).thenReturn(Mono.just(user("tenant-user", "Tenant User", "access", "refresh",
                Instant.now().plusSeconds(3600), identity, Instant.now())));
        when(service.get(any())).thenReturn(Mono.just(new TenantLlmConfigService.ConfigResponse(
                "preset-1", "Provider", "model-a", "********")));

        client.get().uri("/api/llm-config").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/llm-config").header("Authorization", "Bearer tenant-token").exchange()
                .expectStatus().isOk().expectBody()
                .json("{\"presetId\":\"preset-1\",\"presetName\":\"Provider\",\"model\":\"model-a\",\"apiKeyMasked\":\"********\"}")
                .consumeWith(result -> org.assertj.core.api.Assertions.assertThat(new String(result.getResponseBody()))
                        .doesNotContain("baseUrl", "\"apiKey\":", "plain-key", "tenantId"));
        verify(service).get(identity);
    }

    @Test
    void putRejectsPresentTenantSelectorEvenWhenNullBeforeCallingService() {
        var identity = identity("tenant-admin", "tenant-a", false, true);
        when(sessions.resolve("tenant-token")).thenReturn(Mono.just(user("tenant-admin", "Tenant Admin", "access", "refresh",
                Instant.now().plusSeconds(3600), identity, Instant.now())));

        for (String selector : new String[] {"null", "\"tenant-a\"", "\"\""}) {
            client.put().uri("/api/llm-config").header("Authorization", "Bearer tenant-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"presetId\":\"preset-1\",\"apiKey\":\"secret-key\",\"tenantId\":" + selector + "}")
                    .exchange().expectStatus().isBadRequest()
                    .expectBody().json("{\"code\":\"INVALID_LLM_CONFIG\",\"message\":\"LLM configuration input is invalid\"}")
                    .consumeWith(result -> org.assertj.core.api.Assertions.assertThat(new String(result.getResponseBody()))
                            .doesNotContain("secret-key", "tenantId"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void putPassesOnlyPresetAndApiKeyAndNeverAcceptsTrustedProviderFields() {
        var identity = identity("tenant-admin", "tenant-a", false, true);
        when(sessions.resolve("tenant-token")).thenReturn(Mono.just(user("tenant-admin", "Tenant Admin", "access", "refresh",
                Instant.now().plusSeconds(3600), identity, Instant.now())));
        when(service.save(any(), any())).thenReturn(Mono.just(new TenantLlmConfigService.ConfigResponse(
                "preset-1", "Provider", "model-a", "********")));

        client.put().uri("/api/llm-config").header("Authorization", "Bearer tenant-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"presetId\":\"preset-1\",\"apiKey\":\"secret-key\",\"baseUrl\":\"https://attacker.test\",\"model\":\"evil\",\"roles\":[\"tenant_admin\"]}")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.apiKeyMasked").isEqualTo("********");

        verify(service).save(eq(identity), argThat(input -> input.presetId().equals("preset-1")
                && input.apiKey().equals("secret-key")));
    }

    @Test
    void deleteReturnsNoContentForAuthenticatedTenant() {
        var identity = identity("tenant-admin", "tenant-a", false, true);
        when(sessions.resolve("tenant-token")).thenReturn(Mono.just(user("tenant-admin", "Tenant Admin", "access", "refresh",
                Instant.now().plusSeconds(3600), identity, Instant.now())));
        when(service.delete(identity)).thenReturn(Mono.empty());

        client.delete().uri("/api/llm-config").header("Authorization", "Bearer tenant-token").exchange()
                .expectStatus().isNoContent();
        verify(service).delete(identity);
    }

    private static IdentityContext identity(String subject, String tenant, boolean platformAdmin, boolean tenantAdmin) {
        return new IdentityContext(subject, subject, tenant, null, null, null, Set.of(), Set.of(), Set.of(),
                platformAdmin, tenantAdmin);
    }
}
