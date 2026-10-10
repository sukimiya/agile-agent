package io.github.sukimiya.agileagent.hint;

import static io.github.sukimiya.agileagent.TestAuthUsers.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDateTime;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(HintController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, CurrentUserArgumentResolver.class})
class HintControllerTest {

    @Autowired WebTestClient client;
    @MockBean HintService service;
    @MockBean AuthSessionStore sessions;
    @MockBean OAuth2Client oauth2;
    @MockBean org.springframework.data.redis.core.ReactiveStringRedisTemplate redis;

    @Test
    void listRequiresAuthenticationAndAllowsAuthenticatedRolesWithoutTenant() {
        var regular = identity("regular", false, false);
        when(sessions.resolve("user-token")).thenReturn(Mono.just(user("regular", "Regular", "access", "refresh",
                Instant.now().plusSeconds(3600), regular, Instant.now())));
        var createdAt = LocalDateTime.parse("2026-10-01T00:00:00");
        when(service.list(any())).thenReturn(Flux.just(new HintService.HintSummary(
                "hint-1", "Ask about inventory", "creator", createdAt)));

        client.get().uri("/api/chat/hints").exchange().expectStatus().isUnauthorized()
                .expectBody().json("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");

        for (var identity : new IdentityContext[] {regular, identity("tenant-admin", false, true),
                identity("platform-admin", true, false)}) {
            String token = identity.subject() + "-token";
            when(sessions.resolve(token)).thenReturn(Mono.just(user(identity.subject(), identity.subject(), "access",
                    "refresh", Instant.now().plusSeconds(3600), identity, Instant.now())));
            client.get().uri("/api/chat/hints").header("Authorization", "Bearer " + token).exchange()
                    .expectStatus().isOk().expectBody()
                    .json("[{\"id\":\"hint-1\",\"content\":\"Ask about inventory\","
                            + "\"createdBySubject\":\"creator\",\"createdAt\":\"2026-10-01T00:00:00\"}]");
            verify(service).list(identity);
        }
    }

    @Test
    void createIgnoresRequestSuppliedIdCreatorAndTime() {
        var identity = identity("trusted-admin", true, false);
        when(sessions.resolve("admin-token")).thenReturn(Mono.just(user("trusted-admin", "Admin", "access", "refresh",
                Instant.now().plusSeconds(3600), identity, Instant.now())));
        var createdAt = LocalDateTime.parse("2026-10-09T10:00:00");
        when(service.create(any(), any())).thenReturn(Mono.just(new HintService.HintSummary(
                "server-id", "Suggestion", "trusted-admin", createdAt)));

        client.post().uri("/api/chat/hints").header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"id\":\"attacker-id\",\"content\":\"Suggestion\","
                        + "\"createdBySubject\":\"attacker\",\"createdAt\":\"2000-01-01T00:00:00\"}")
                .exchange().expectStatus().isCreated().expectHeader().valueEquals("Location", "/api/chat/hints/server-id")
                .expectBody().jsonPath("$.id").isEqualTo("server-id")
                .jsonPath("$.createdBySubject").isEqualTo("trusted-admin")
                .jsonPath("$.createdAt").isEqualTo("2026-10-09T10:00:00");
        verify(service).create(eq(identity), argThat(input -> input.content().equals("Suggestion")));
    }

    @Test
    void updatePreservesMetadataAndDeleteHasNoContentShape() {
        var identity = identity("trusted-admin", true, false);
        when(sessions.resolve("admin-token")).thenReturn(Mono.just(user("trusted-admin", "Admin", "access", "refresh",
                Instant.now().plusSeconds(3600), identity, Instant.now())));
        var createdAt = LocalDateTime.parse("2026-10-01T00:00:00");
        when(service.update(any(), eq("hint-1"), any())).thenReturn(Mono.just(new HintService.HintSummary(
                "hint-1", "Updated", "original-creator", createdAt)));
        when(service.delete(any(), eq("hint-1"))).thenReturn(Mono.empty());

        client.put().uri("/api/chat/hints/hint-1").header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"content\":\"Updated\",\"createdBySubject\":\"attacker\"}")
                .exchange().expectStatus().isOk().expectBody()
                .json("{\"id\":\"hint-1\",\"content\":\"Updated\",\"createdBySubject\":\"original-creator\","
                        + "\"createdAt\":\"2026-10-01T00:00:00\"}");
        client.delete().uri("/api/chat/hints/hint-1").header("Authorization", "Bearer admin-token")
                .exchange().expectStatus().isNoContent().expectBody().isEmpty();
    }

    @Test
    void invalidAndMissingResourcesHaveStableSanitizedErrors() {
        var identity = identity("trusted-admin", true, false);
        when(sessions.resolve("admin-token")).thenReturn(Mono.just(user("trusted-admin", "Admin", "access", "refresh",
                Instant.now().plusSeconds(3600), identity, Instant.now())));
        when(service.create(any(), any())).thenReturn(Mono.error(new HintValidationException()));
        when(service.update(any(), eq("missing"), any())).thenReturn(Mono.error(new HintNotFoundException()));
        when(service.delete(any(), eq("missing"))).thenReturn(Mono.error(new HintNotFoundException()));

        for (String body : new String[] {"{}", "{\"content\":null}", "{\"content\":\"  \"}"}) {
            client.post().uri("/api/chat/hints").header("Authorization", "Bearer admin-token")
                    .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange().expectStatus().isBadRequest()
                    .expectBody().json("{\"code\":\"INVALID_CHAT_HINT\",\"message\":\"Hint content is required\"}");
        }
        client.put().uri("/api/chat/hints/missing").header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"content\":\"New\"}").exchange()
                .expectStatus().isNotFound().expectBody()
                .json("{\"code\":\"CHAT_HINT_NOT_FOUND\",\"message\":\"Chat hint not found\"}");
        client.delete().uri("/api/chat/hints/missing").header("Authorization", "Bearer admin-token").exchange()
                .expectStatus().isNotFound().expectBody()
                .json("{\"code\":\"CHAT_HINT_NOT_FOUND\",\"message\":\"Chat hint not found\"}");
        verify(service, times(3)).create(eq(identity), any());
    }

    @Test
    void tenantAdminAndRegularUserCannotWrite() {
        var tenantAdmin = identity("tenant-admin", false, true);
        when(sessions.resolve("tenant-token")).thenReturn(Mono.just(user("tenant-admin", "Tenant Admin", "access", "refresh",
                Instant.now().plusSeconds(3600), tenantAdmin, Instant.now())));
        when(service.create(any(), any())).thenReturn(Mono.error(new io.github.sukimiya.agileagent.web.ForbiddenException()));

        client.post().uri("/api/chat/hints").header("Authorization", "Bearer tenant-token")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"content\":\"hint\"}")
                .exchange().expectStatus().isForbidden().expectBody()
                .json("{\"code\":\"FORBIDDEN\",\"message\":\"Access denied\"}");
    }

    private static IdentityContext identity(String subject, boolean platformAdmin, boolean tenantAdmin) {
        return new IdentityContext(subject, subject, null, null, null, null, Set.of(), Set.of(), Set.of(),
                platformAdmin, tenantAdmin);
    }
}
