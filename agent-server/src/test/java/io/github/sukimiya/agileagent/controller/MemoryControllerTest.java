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

import io.github.sukimiya.agileagent.memory.MemoryException;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.SourceType;
import io.github.sukimiya.agileagent.memory.items.ItemStatus;
import io.github.sukimiya.agileagent.memory.items.MemoryItem;
import io.github.sukimiya.agileagent.memory.items.MemoryItemService;
import io.github.sukimiya.agileagent.memory.judgement.ValidatedAction.SourceRef;
import io.github.sukimiya.agileagent.memory.recall.RecallEventRecorder;
import io.github.sukimiya.agileagent.memory.recall.RecallEventRecorder.StoredEvent;
import io.github.sukimiya.agileagent.repository.SessionRepository.SessionRow;
import io.github.sukimiya.agileagent.service.SessionService;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService;
import io.github.sukimiya.agileagent.memory.recall.MemorySettingsService.MemorySettings;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.OAuth2Client;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.AuthExceptionHandler;
import io.github.sukimiya.agileagent.web.CurrentUserArgumentResolver;
import io.github.sukimiya.agileagent.web.MemoryExceptionHandler;
import io.github.sukimiya.agileagent.web.WebConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@code /api/memory/**} (D1=A settings, D15=A items/profile) and the per-turn explain endpoint: authenticated,
 * tenant required, always the caller's own scope.
 */
@WebFluxTest(MemoryController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, MemoryExceptionHandler.class, CurrentUserArgumentResolver.class})
class MemoryControllerTest {

    static final MemoryScope SCOPE = new MemoryScope("tenant-1", "user-1");

    @Autowired WebTestClient client;
    @MockBean MemorySettingsService settings;
    @MockBean MemoryItemService items;
    @MockBean RecallEventRecorder recallEvents;
    @MockBean SessionService sessionService;
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

    static final String CONV = "0a1b2c3d-0000-4000-8000-000000000001";

    WebTestClient.RequestHeadersSpec<?> auth(WebTestClient.RequestHeadersSpec<?> spec) {
        return spec.header("Authorization", "Bearer test-token");
    }

    static MemoryItem item(String id, ItemStatus status) {
        return new MemoryItem(id, SCOPE, null, "PREFERENCE", "喜欢绿茶", status, MemoryItem.Assertion.USER_STATED,
                0.8, 0.9, null, null, null, 1, "m", "r", 3, java.time.LocalDateTime.of(2026, 10, 27, 10, 0),
                java.time.LocalDateTime.of(2026, 10, 27, 10, 0));
    }

    @Test
    void missingTenantReturns403() {
        asUser(null);
        for (var spec : java.util.List.<WebTestClient.RequestHeadersSpec<?>>of(
                client.get().uri("/api/memory/items"),
                client.get().uri("/api/memory/items/i-1"),
                client.post().uri("/api/memory/items/i-1/corrections").contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("{\"content\":\"x\"}"),
                client.delete().uri("/api/memory/items/i-1"),
                client.delete().uri("/api/memory/profile"),
                client.get().uri("/api/conversations/" + CONV + "/turns/t-1/memory"))) {
            auth(spec).exchange().expectStatus().isForbidden().expectBody().jsonPath("$.code")
                    .isEqualTo("TENANT_REQUIRED");
        }
        client.get().uri("/api/memory/items").exchange().expectStatus().isUnauthorized();
        verifyNoInteractions(items, recallEvents, sessionService);
    }

    @Test
    void otherUsersItemsReturn404() {
        asUser("tenant-1");
        var notFound = Mono.<MemoryItemService.ItemDetail>error(new MemoryException(ErrorCode.MEMORY_ITEM_NOT_FOUND));
        when(items.detail(SCOPE, "bob-item")).thenReturn(notFound);
        when(items.delete(SCOPE, "bob-item"))
                .thenReturn(Mono.error(new MemoryException(ErrorCode.MEMORY_ITEM_NOT_FOUND)));
        when(items.correct(SCOPE, "bob-item", "改", null))
                .thenReturn(Mono.error(new MemoryException(ErrorCode.MEMORY_ITEM_NOT_FOUND)));

        auth(client.get().uri("/api/memory/items/bob-item")).exchange().expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("MEMORY_ITEM_NOT_FOUND");
        auth(client.delete().uri("/api/memory/items/bob-item")).exchange().expectStatus().isNotFound();
        auth(client.post().uri("/api/memory/items/bob-item/corrections").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"content\":\"改\"}")).exchange().expectStatus().isNotFound();
    }

    @Test
    void itemsApiListsDetailsCorrectsAndDeletes() {
        asUser("tenant-1");
        when(items.list(SCOPE, ItemStatus.ACTIVE, 2, "c-1")).thenReturn(Mono.just(
                new MemoryItemService.ItemPage(java.util.List.of(item("i-1", ItemStatus.ACTIVE)), "c-2")));
        auth(client.get().uri("/api/memory/items?status=ACTIVE&limit=2&before=c-1")).exchange()
                .expectStatus().isOk().expectBody()
                .jsonPath("$.items[0].id").isEqualTo("i-1")
                .jsonPath("$.items[0].content").isEqualTo("喜欢绿茶")
                .jsonPath("$.items[0].assertion").isEqualTo("USER_STATED")
                .jsonPath("$.items[0].scope").doesNotExist()
                .jsonPath("$.nextBefore").isEqualTo("c-2");
        auth(client.get().uri("/api/memory/items?status=NOPE")).exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_ARGUMENT");
        auth(client.get().uri("/api/memory/items?limit=0")).exchange().expectStatus().isBadRequest();
        auth(client.get().uri("/api/memory/items?limit=101")).exchange().expectStatus().isBadRequest();

        when(items.detail(SCOPE, "i-1")).thenReturn(Mono.just(new MemoryItemService.ItemDetail(
                item("i-1", ItemStatus.ACTIVE),
                java.util.List.of(new SourceRef("m-1", CONV, SourceType.USER_INPUT, "我喜欢绿茶")))));
        auth(client.get().uri("/api/memory/items/i-1")).exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.item.id").isEqualTo("i-1")
                .jsonPath("$.sources[0].messageId").isEqualTo("m-1")
                .jsonPath("$.sources[0].evidenceExcerpt").isEqualTo("我喜欢绿茶");

        when(items.correct(SCOPE, "i-1", "喜欢红茶", 3L)).thenReturn(Mono.just(item("i-2", ItemStatus.ACTIVE)));
        auth(client.post().uri("/api/memory/items/i-1/corrections").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"content\":\"喜欢红茶\",\"expectedRevision\":3}")).exchange()
                .expectStatus().isCreated().expectBody().jsonPath("$.id").isEqualTo("i-2");
        auth(client.post().uri("/api/memory/items/i-1/corrections").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")).exchange().expectStatus().isBadRequest();
        when(items.correct(SCOPE, "i-1", "又改", null))
                .thenReturn(Mono.error(new MemoryException(ErrorCode.MEMORY_ITEM_CONFLICT)));
        auth(client.post().uri("/api/memory/items/i-1/corrections").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"content\":\"又改\"}")).exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("MEMORY_ITEM_CONFLICT");

        when(items.delete(SCOPE, "i-1")).thenReturn(Mono.empty());
        auth(client.delete().uri("/api/memory/items/i-1")).exchange().expectStatus().isNoContent();
        when(items.deleteProfile(SCOPE)).thenReturn(Mono.empty());
        auth(client.delete().uri("/api/memory/profile")).exchange().expectStatus().isNoContent();
        verify(items).deleteProfile(SCOPE);
    }

    @Test
    void explainEndpointScopedToOwner() {
        asUser("tenant-1");
        when(sessionService.findOwned(CONV, SCOPE)).thenReturn(Mono.just(new SessionRow(CONV, "tenant-1", "user-1",
                "t", 0, null, null, null)));
        when(recallEvents.forTurn(SCOPE, CONV, "t-1")).thenReturn(Flux.just(
                new StoredEvent("PROFILE", "7", "PROFILE", null, 1, ""),
                new StoredEvent("ITEM", "i-1", "ITEM", 50.8, 2, "USER_INPUT")));
        auth(client.get().uri("/api/conversations/" + CONV + "/turns/t-1/memory")).exchange()
                .expectStatus().isOk().expectBody()
                .jsonPath("$.turnId").isEqualTo("t-1")
                .jsonPath("$.events[0].kind").isEqualTo("PROFILE")
                .jsonPath("$.events[1].refId").isEqualTo("i-1")
                .jsonPath("$.events[1].score").isEqualTo(50.8)
                .jsonPath("$.events[1].rank").isEqualTo(2)
                .jsonPath("$.events[1].sourceTypes[0]").isEqualTo("USER_INPUT");

        // Someone else's (or a deleted) conversation: 404, and the events are never read.
        var foreign = "0a1b2c3d-0000-4000-8000-000000000002";
        when(sessionService.findOwned(foreign, SCOPE)).thenReturn(Mono.empty());
        auth(client.get().uri("/api/conversations/" + foreign + "/turns/t-1/memory")).exchange()
                .expectStatus().isNotFound().expectBody().jsonPath("$.code").isEqualTo("CONVERSATION_NOT_FOUND");
        auth(client.get().uri("/api/conversations/not-a-uuid/turns/t-1/memory")).exchange()
                .expectStatus().isBadRequest();
        verify(recallEvents, times(1)).forTurn(any(), any(), any());
    }
}
