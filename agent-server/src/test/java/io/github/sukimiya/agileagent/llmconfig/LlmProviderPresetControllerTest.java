package io.github.sukimiya.agileagent.llmconfig;

import static io.github.sukimiya.agileagent.TestAuthUsers.user;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.OAuth2Client;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.AuthExceptionHandler;
import io.github.sukimiya.agileagent.web.CurrentUserArgumentResolver;
import io.github.sukimiya.agileagent.web.ForbiddenException;
import io.github.sukimiya.agileagent.web.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(LlmProviderPresetController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, CurrentUserArgumentResolver.class})
class LlmProviderPresetControllerTest {

    @Autowired WebTestClient client;
    @MockBean LlmProviderPresetService service;
    @MockBean AuthSessionStore sessions;
    @MockBean OAuth2Client oauth2;
    @MockBean org.springframework.data.redis.core.ReactiveStringRedisTemplate redis;

    @Test
    void listRequiresAuthenticationAndReturnsSecretFreePresets() {
        var row = row("preset-1", "provider-admin", "Provider", "https://api.example.test/v1", "model-a", "/balance");
        when(sessions.resolve("test-token")).thenReturn(Mono.just(user("reader", "Reader", "access", "refresh",
                Instant.now().plusSeconds(3600), Set.of(), Instant.now())));
        when(service.list(any())).thenReturn(Flux.just(row));

        client.get().uri("/api/llm-providers").exchange()
                .expectStatus().isUnauthorized()
                .expectBody().json("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");

        client.get().uri("/api/llm-providers").header("Authorization", "Bearer test-token").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("preset-1")
                .jsonPath("$[0].name").isEqualTo("Provider")
                .jsonPath("$[0].baseUrl").isEqualTo("https://api.example.test/v1")
                .jsonPath("$[0].model").isEqualTo("model-a")
                .jsonPath("$[0].balancePath").isEqualTo("/balance")
                .consumeWith(result -> org.assertj.core.api.Assertions.assertThat(
                        new String(result.getResponseBody())).doesNotContain("apiKey", "secret", "token"));
    }

    @Test
    void createUsesCurrentIdentityAndIgnoresClientSuppliedTrustedFields() {
        var identity = new IdentityContext("real-admin", "Admin", "tenant-1", null, null, null,
                Set.of("platform_admin"), Set.of(), Set.of(), true, false);
        when(sessions.resolve("admin-token")).thenReturn(Mono.just(user("real-admin", "Admin", "access", "refresh",
                Instant.now().plusSeconds(3600), identity, Instant.now())));
        when(service.create(any(), any())).thenAnswer(invocation -> {
            var request = invocation.getArgument(1, LlmProviderPresetService.PresetInput.class);
            return Mono.just(row("server-id", "real-admin", request.name(), request.baseUrl(), request.model(), request.balancePath()));
        });

        client.post().uri("/api/llm-providers").header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"id":"attacker-id","name":"Provider","baseUrl":"https://api.example.test/v1",
                         "model":"model-a","balancePath":null,"createdBySubject":"attacker",
                         "roles":["platform_admin"],"apiKey":"secret-value"}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "/api/llm-providers/server-id")
                .expectBody()
                .jsonPath("$.id").isEqualTo("server-id")
                .jsonPath("$.createdBySubject").isEqualTo("real-admin")
                .jsonPath("$.name").isEqualTo("Provider")
                .consumeWith(result -> org.assertj.core.api.Assertions.assertThat(
                        new String(result.getResponseBody())).doesNotContain("secret-value", "apiKey", "roles"));

        var identityCaptor = org.mockito.ArgumentCaptor.forClass(IdentityContext.class);
        verify(service).create(identityCaptor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(identityCaptor.getValue()).isEqualTo(identity);
        verify(service).create(any(), argThat(input -> input.name().equals("Provider")
                && input.baseUrl().equals("https://api.example.test/v1") && input.model().equals("model-a")));
    }

    @Test
    void updateAndDeleteUseResourcePathsAndReturnNoContentOrStableNotFound() {
        var identity = new IdentityContext("admin", "Admin", "tenant-1", null, null, null,
                Set.of("platform_admin"), Set.of(), Set.of(), true, false);
        when(sessions.resolve("admin-token")).thenReturn(Mono.just(user("admin", "Admin", "access", "refresh",
                Instant.now().plusSeconds(3600), identity, Instant.now())));
        var updated = row("preset-1", "admin", "Provider", "https://api.example.test", "model-a", null);
        when(service.update(any(), eq("preset-1"), any())).thenReturn(Mono.just(updated));
        when(service.delete(any(), eq("preset-2"))).thenReturn(Mono.empty());

        client.put().uri("/api/llm-providers/preset-1").header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Provider\",\"baseUrl\":\"https://api.example.test\",\"model\":\"model-a\"}")
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo("preset-1");

        client.delete().uri("/api/llm-providers/preset-2").header("Authorization", "Bearer admin-token")
                .exchange().expectStatus().isNoContent();

        when(service.update(any(), eq("missing"), any())).thenReturn(Mono.error(new LlmProviderPresetNotFoundException()));
        when(service.delete(any(), eq("missing"))).thenReturn(Mono.error(new LlmProviderPresetNotFoundException()));

        client.put().uri("/api/llm-providers/missing").header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Provider\",\"baseUrl\":\"https://api.example.test\",\"model\":\"model-a\"}")
                .exchange().expectStatus().isNotFound()
                .expectBody().json("{\"code\":\"LLM_PROVIDER_PRESET_NOT_FOUND\",\"message\":\"Provider preset not found\"}");

        client.delete().uri("/api/llm-providers/missing").header("Authorization", "Bearer admin-token")
                .exchange().expectStatus().isNotFound()
                .expectBody().json("{\"code\":\"LLM_PROVIDER_PRESET_NOT_FOUND\",\"message\":\"Provider preset not found\"}");
    }

    @Test
    void invalidInputReturnsStableSanitized400() {
        var identity = new IdentityContext("admin", "Admin", "tenant-1", null, null, null,
                Set.of("platform_admin"), Set.of(), Set.of(), true, false);
        when(sessions.resolve("admin-token")).thenReturn(Mono.just(user("admin", "Admin", "access", "refresh",
                Instant.now().plusSeconds(3600), identity, Instant.now())));
        when(service.create(any(), any())).thenReturn(Mono.error(new LlmProviderPresetValidationException()));

        client.post().uri("/api/llm-providers").header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Provider\",\"baseUrl\":\"http://secret-host\",\"model\":\"model-a\"}")
                .exchange().expectStatus().isBadRequest()
                .expectBody().json("{\"code\":\"INVALID_LLM_PROVIDER_PRESET\",\"message\":\"Provider preset input is invalid\"}")
                .consumeWith(result -> org.assertj.core.api.Assertions.assertThat(
                        new String(result.getResponseBody())).doesNotContain("secret-host", "URI", "http://"));
    }

    @Test
    void tenantAdminAndRegularUserAreDeniedForWrites() {
        var tenantAdmin = new IdentityContext("tenant-admin", "Tenant Admin", "tenant-1", null, null, null,
                Set.of("tenant_admin"), Set.of(), Set.of(), false, true);
        when(sessions.resolve("tenant-token")).thenReturn(Mono.just(user("tenant-admin", "Tenant Admin", "access", "refresh",
                Instant.now().plusSeconds(3600), tenantAdmin, Instant.now())));
        when(service.create(any(), any())).thenReturn(Mono.error(new ForbiddenException()));

        client.post().uri("/api/llm-providers").header("Authorization", "Bearer tenant-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Provider\",\"baseUrl\":\"https://api.example.test\",\"model\":\"model-a\"}")
                .exchange().expectStatus().isForbidden()
                .expectBody().json("{\"code\":\"FORBIDDEN\",\"message\":\"Access denied\"}");
    }

    private static LlmProviderPresetRepository.LlmProviderPresetRow row(
            String id, String creator, String name, String baseUrl, String model, String balancePath) {
        var now = LocalDateTime.parse("2026-10-09T10:00:00");
        return new LlmProviderPresetRepository.LlmProviderPresetRow(
                id, name, baseUrl, model, balancePath, creator, now, now);
    }
}
