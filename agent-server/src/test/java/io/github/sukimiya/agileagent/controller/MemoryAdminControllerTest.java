package io.github.sukimiya.agileagent.controller;

import static io.github.sukimiya.agileagent.TestAuthUsers.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.github.sukimiya.agileagent.auth.AuthorizationService;
import io.github.sukimiya.agileagent.memory.judgement.RulesReplayHandler;
import io.github.sukimiya.agileagent.memory.judgement.RulesReplayHandler.Replay;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.OAuth2Client;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.AuthExceptionHandler;
import io.github.sukimiya.agileagent.web.CurrentUserArgumentResolver;
import io.github.sukimiya.agileagent.web.MemoryExceptionHandler;
import io.github.sukimiya.agileagent.web.WebConfig;
import reactor.core.publisher.Mono;

/** {@code /api/admin/memory/replays} (M2b Task 7): platform admins only, any tenant. */
@WebFluxTest(MemoryAdminController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, MemoryExceptionHandler.class, CurrentUserArgumentResolver.class,
        AuthorizationService.class})
class MemoryAdminControllerTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 12, 0);
    static final Replay PENDING = new Replay("r-1", "tenant-9", null, 2, "PENDING", "admin-1", 0, 0, 0, NOW, NOW,
            null);

    @Autowired WebTestClient client;
    @MockBean RulesReplayHandler replays;
    @MockBean AuthSessionStore sessions;
    @MockBean OAuth2Client oauth2;
    @MockBean org.springframework.data.redis.core.ReactiveStringRedisTemplate redis;

    void as(String subject, String tenantId, boolean platformAdmin, boolean tenantAdmin) {
        var identity = new IdentityContext(subject, "Test", tenantId, tenantId == null ? null : "Tenant", null, null,
                Set.of("test"), Set.of(), Set.of(), platformAdmin, tenantAdmin);
        when(sessions.resolve("test-token")).thenReturn(Mono.just(user(subject, "Test", "sl-tok", "refresh-tok",
                Instant.now().plusSeconds(3600), identity, Instant.now())));
    }

    WebTestClient.ResponseSpec post(String body) {
        return client.post().uri("/api/admin/memory/replays").header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    WebTestClient.ResponseSpec get(String id) {
        return client.get().uri("/api/admin/memory/replays/" + id).header("Authorization", "Bearer test-token")
                .exchange();
    }

    @Test
    void nonAdminGets403() {
        client.post().uri("/api/admin/memory/replays").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"tenantId\":\"tenant-9\"}").exchange().expectStatus().isUnauthorized();

        as("user-1", "tenant-9", false, false);
        post("{\"tenantId\":\"tenant-9\"}").expectStatus().isForbidden().expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
        get("r-1").expectStatus().isForbidden();

        as("tadmin-1", "tenant-9", false, true);
        post("{\"tenantId\":\"tenant-9\"}").expectStatus().isForbidden();
        get("r-1").expectStatus().isForbidden();

        verify(replays, never()).request(anyString(), any(), anyString());
        verify(replays, never()).find(anyString());
    }

    @Test
    void platformAdminStartsAReplayForAnyTenant() {
        as("admin-1", null, true, false);
        when(replays.request(eq("tenant-9"), isNull(), eq("admin-1"))).thenReturn(Mono.just(PENDING));
        post("{\"tenantId\":\"tenant-9\"}").expectStatus().isAccepted().expectBody()
                .jsonPath("$.replayId").isEqualTo("r-1")
                .jsonPath("$.status").isEqualTo("PENDING")
                .jsonPath("$.toRulesVersion").isEqualTo(2);

        when(replays.request(eq("tenant-9"), eq(1), eq("admin-1"))).thenReturn(Mono.just(PENDING));
        post("{\"tenantId\":\"tenant-9\",\"fromRulesVersion\":1}").expectStatus().isAccepted();
        verify(replays).request("tenant-9", 1, "admin-1");
    }

    @Test
    void invalidRequestIs400() {
        as("admin-1", null, true, false);
        for (var body : new String[] {"{}", "{\"tenantId\":\"\"}", "{\"tenantId\":7}",
                "{\"tenantId\":\"tenant-9\",\"fromRulesVersion\":0}",
                "{\"tenantId\":\"tenant-9\",\"fromRulesVersion\":\"1\"}"}) {
            post(body).expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("INVALID_ARGUMENT");
        }
        verify(replays, never()).request(anyString(), any(), anyString());
    }

    @Test
    void progressShowsCountsAndUnknownIs404() {
        as("admin-1", null, true, false);
        var running = new Replay("r-1", "tenant-9", 1, 2, "RUNNING", "admin-1", 3, 1, 2, NOW, NOW, null);
        when(replays.find("r-1")).thenReturn(Mono.just(Optional.of(running)));
        when(replays.find("nope")).thenReturn(Mono.just(Optional.empty()));

        get("r-1").expectStatus().isOk().expectBody()
                .jsonPath("$.replayId").isEqualTo("r-1")
                .jsonPath("$.tenantId").isEqualTo("tenant-9")
                .jsonPath("$.fromRulesVersion").isEqualTo(1)
                .jsonPath("$.toRulesVersion").isEqualTo(2)
                .jsonPath("$.status").isEqualTo("RUNNING")
                .jsonPath("$.processed").isEqualTo(6)
                .jsonPath("$.superseded").isEqualTo(3)
                .jsonPath("$.inactivated").isEqualTo(1)
                .jsonPath("$.skipped").isEqualTo(2);
        get("nope").expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo("MEMORY_REPLAY_NOT_FOUND");
    }
}
