package io.github.sukimiya.agileagent.controller;

import static io.github.sukimiya.agileagent.TestAuthUsers.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService.MemorySettings;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.OAuth2Client;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.AuthExceptionHandler;
import io.github.sukimiya.agileagent.web.CurrentUserArgumentResolver;
import io.github.sukimiya.agileagent.web.MemoryExceptionHandler;
import io.github.sukimiya.agileagent.web.WebConfig;
import reactor.core.publisher.Mono;

/** {@code GET/PUT /api/memory/settings} (D1=A): authenticated, tenant required, always the caller's own scope. */
@WebFluxTest(MemoryController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, MemoryExceptionHandler.class, CurrentUserArgumentResolver.class})
class MemoryControllerTest {

    static final MemoryScope SCOPE = new MemoryScope("tenant-1", "user-1");

    @Autowired WebTestClient client;
    @MockBean MemorySettingsService settings;
    @MockBean AuthSessionStore sessions;
    @MockBean OAuth2Client oauth2;
    @MockBean org.springframework.data.redis.core.ReactiveStringRedisTemplate redis;

    void asUser(String tenantId) {
        var identity = new IdentityContext("user-1", "Test User", tenantId, tenantId == null ? null : "Tenant",
                null, null, Set.of("test"), Set.of(), Set.of(), false, tenantId != null);
        when(sessions.resolve("test-token")).thenReturn(Mono.just(user("user-1", "Test User", "sl-tok",
                "refresh-tok", Instant.now().plusSeconds(3600), identity, Instant.now())));
    }

    WebTestClient.RequestHeadersSpec<?> get() {
        return client.get().uri("/api/memory/settings").header("Authorization", "Bearer test-token");
    }

    WebTestClient.RequestHeadersSpec<?> put(String body) {
        return client.put().uri("/api/memory/settings").header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body);
    }

    @Test
    void settingsApiRequiresTenantAndOwnsScope() {
        client.get().uri("/api/memory/settings").exchange().expectStatus().isUnauthorized();
        client.put().uri("/api/memory/settings").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"crossConversationEnabled\":true}").exchange().expectStatus().isUnauthorized();

        asUser(null);
        get().exchange().expectStatus().isForbidden().expectBody().jsonPath("$.code").isEqualTo("TENANT_REQUIRED");
        put("{\"crossConversationEnabled\":true}").exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("TENANT_REQUIRED");
        verifyNoInteractions(settings);

        asUser("tenant-1");
        when(settings.get(SCOPE)).thenReturn(Mono.just(new MemorySettings(false, 0)));
        get().exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.crossConversationEnabled").isEqualTo(false).jsonPath("$.revision").isEqualTo(0);

        when(settings.update(SCOPE, true)).thenReturn(Mono.just(new MemorySettings(true, 1)));
        // A scope in the body is ignored: the scope is always the caller's.
        put("{\"crossConversationEnabled\":true,\"tenantId\":\"tenant-2\",\"subject\":\"bob\"}").exchange()
                .expectStatus().isOk().expectBody()
                .jsonPath("$.crossConversationEnabled").isEqualTo(true).jsonPath("$.revision").isEqualTo(1);
        verify(settings).update(SCOPE, true);

        put("{}").exchange().expectStatus().isBadRequest().expectBody().jsonPath("$.code")
                .isEqualTo("INVALID_ARGUMENT");
        put("{\"crossConversationEnabled\":\"yes\"}").exchange().expectStatus().isBadRequest();
        verify(settings, times(1)).update(any(), anyBoolean());
    }
}
