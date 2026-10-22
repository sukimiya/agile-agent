package io.github.sukimiya.agileagent.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static io.github.sukimiya.agileagent.TestAuthUsers.user;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.github.sukimiya.agileagent.agent.Orchestrator;
import io.github.sukimiya.agileagent.memory.ConversationHistory;
import io.github.sukimiya.agileagent.memory.MemoryException;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryModel.AcceptedTurn;
import io.github.sukimiya.agileagent.memory.MemoryModel.HistoryPage;
import io.github.sukimiya.agileagent.memory.MemoryModel.StoredMessage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnCommand;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.memory.MemoryScope;
import io.github.sukimiya.agileagent.memory.SourceType;
import io.github.sukimiya.agileagent.memory.TurnLifecycle;
import io.github.sukimiya.agileagent.repository.SessionRepository.SessionRow;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.OAuth2Client;
import io.github.sukimiya.agileagent.service.SessionService;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.service.TenantLlmConfigRequiredException;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.AuthExceptionHandler;
import io.github.sukimiya.agileagent.web.CurrentUserArgumentResolver;
import io.github.sukimiya.agileagent.web.MemoryExceptionHandler;
import io.github.sukimiya.agileagent.web.WebConfig;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(ChatController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, MemoryExceptionHandler.class, CurrentUserArgumentResolver.class})
class ChatControllerTest {

    private static final MemoryScope SCOPE = new MemoryScope("tenant-1", "user-1");

    @Autowired WebTestClient client;
    @MockBean Orchestrator orchestrator;
    @MockBean SessionService sessionService;
    @MockBean TurnLifecycle lifecycle;
    @MockBean ConversationHistory history;
    @MockBean TenantLlmClientResolver llmResolver;
    @MockBean AuthSessionStore sessions;
    @MockBean OAuth2Client oauth2;
    @MockBean org.springframework.data.redis.core.ReactiveStringRedisTemplate redis;

    private void asUser1() {
        asUser(user1Identity());
    }

    private void asUser(IdentityContext identity) {
        when(sessions.resolve("test-token")).thenReturn(Mono.just(user("user-1", "Test User", "sl-tok",
                "refresh-tok", Instant.now().plusSeconds(3600), identity, Instant.now())));
    }

    private static IdentityContext user1Identity() {
        return new IdentityContext("user-1", "Test User", "tenant-1", "Tenant One", "org-1", "Org One",
                Set.of("tenant_admin", "test"), Set.of("org-1"), Set.of("org-2"), false, true);
    }

    private static AcceptedTurn accepted(String conversationId) {
        return new AcceptedTurn(new TurnTicket(SCOPE, conversationId, "turn-1", 1), false, null,
                TokenUsage.notObserved());
    }

    private static TurnCommand command(String conversationId, String text) {
        return argThat(c -> c != null && c.scope().equals(SCOPE) && c.conversationId().equals(conversationId)
                && c.text().equals(text));
    }

    private WebTestClient.RequestHeadersSpec<?> postCompletions(String conversationId) {
        return client.post().uri("/api/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token")
                .bodyValue("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"conversationId\":\""
                        + conversationId + "\"}");
    }

    private WebTestClient.RequestHeadersSpec<?> postLegacy(String conversationId) {
        return client.post().uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token")
                .bodyValue("{\"conversationId\":\"" + conversationId + "\",\"message\":\"hi\"}");
    }

    private LlmClient stubAcceptedTurn(String conversationId) {
        var llm = mock(LlmClient.class);
        when(sessionService.verifyAccess(conversationId, SCOPE)).thenReturn(Mono.empty());
        when(llmResolver.resolve("tenant-1")).thenReturn(Mono.just(llm));
        when(lifecycle.accept(command(conversationId, "hi"))).thenReturn(Mono.just(accepted(conversationId)));
        when(orchestrator.chat(eq(accepted(conversationId)), eq("sl-tok"), eq(user1Identity()), same(llm)))
                .thenReturn(Flux.just("hello back"));
        return llm;
    }

    @Test
    void chatCompletionsAcceptsTheTurnBeforeStreaming() {
        asUser1();
        var llm = stubAcceptedTurn("conv-1");

        postCompletions("conv-1").exchange().expectStatus().isOk();

        var order = inOrder(sessionService, llmResolver, lifecycle, orchestrator);
        order.verify(sessionService).verifyAccess("conv-1", SCOPE);
        order.verify(llmResolver).resolve("tenant-1");
        order.verify(lifecycle).accept(command("conv-1", "hi"));
        order.verify(orchestrator).chat(accepted("conv-1"), "sl-tok", user1Identity(), llm);
    }

    @Test
    void legacyChatAcceptsTheTurnBeforeStreaming() {
        asUser1();
        var llm = stubAcceptedTurn("conv-1");

        postLegacy("conv-1").exchange().expectStatus().isOk();

        verify(lifecycle).accept(command("conv-1", "hi"));
        verify(orchestrator).chat(accepted("conv-1"), "sl-tok", user1Identity(), llm);
    }

    @Test
    void idempotencyKeyHeaderBecomesTheRequestId() {
        asUser1();
        stubAcceptedTurn("conv-1");

        postCompletions("conv-1").header("Idempotency-Key", "retry-1").exchange().expectStatus().isOk();
        postLegacy("conv-1").header("Idempotency-Key", "retry-2").exchange().expectStatus().isOk();

        verify(lifecycle).accept(argThat(c -> c != null && "retry-1".equals(c.requestId())));
        verify(lifecycle).accept(argThat(c -> c != null && "retry-2".equals(c.requestId())));
    }

    @Test
    void invalidIdempotencyKeyIs400BeforeAnyWork() {
        asUser1();

        postCompletions("conv-1").header("Idempotency-Key", "has space").exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_IDEMPOTENCY_KEY");

        verifyNoInteractions(lifecycle, orchestrator, llmResolver);
    }

    @Test
    void chatCompletionsReturns404WhenSessionOwnedByAnotherUser() {
        asUser1();
        when(sessionService.verifyAccess("conv-1", SCOPE))
                .thenReturn(Mono.error(new MemoryException(ErrorCode.CONVERSATION_NOT_FOUND)));

        postCompletions("conv-1").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("CONVERSATION_NOT_FOUND");

        verifyNoInteractions(orchestrator, llmResolver, lifecycle);
    }

    @Test
    void missingTenantIs403WithoutTouchingStorage() {
        asUser(new IdentityContext("user-1", "Test User", null, null, null, null,
                Set.of(), Set.of(), Set.of(), false, false));

        postLegacy("conv-1").exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("TENANT_REQUIRED");

        verifyNoInteractions(sessionService, lifecycle, orchestrator, llmResolver);
    }

    @Test
    void activeTurnReturns409WithTurnIdOnBothEndpoints() {
        asUser1();
        when(sessionService.verifyAccess("conv-1", SCOPE)).thenReturn(Mono.empty());
        when(llmResolver.resolve("tenant-1")).thenReturn(Mono.just(mock(LlmClient.class)));
        when(lifecycle.accept(any())).thenReturn(Mono.error(MemoryException.turnInProgress("turn-9")));

        postCompletions("conv-1").exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().json("{\"code\":\"TURN_IN_PROGRESS\",\"turnId\":\"turn-9\"}");
        postLegacy("conv-1").exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().json("{\"code\":\"TURN_IN_PROGRESS\",\"turnId\":\"turn-9\"}");

        verifyNoInteractions(orchestrator);
    }

    @Test
    void storageFailureIs503WithoutInternalDetail() {
        asUser1();
        when(sessionService.verifyAccess("conv-1", SCOPE)).thenReturn(Mono.empty());
        when(llmResolver.resolve("tenant-1")).thenReturn(Mono.just(mock(LlmClient.class)));
        when(lifecycle.accept(any())).thenReturn(Mono.error(new MemoryException(ErrorCode.STORAGE_UNAVAILABLE,
                new IllegalStateException("jdbc:secret-host"))));

        var body = postLegacy("conv-1").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(body).contains("STORAGE_UNAVAILABLE").doesNotContain("secret").doesNotContain("turnId");
    }

    @Test
    void bothChatEndpointsReturnExact428BeforeStartingSseWhenTenantHasNoLlm() {
        asUser1();
        when(sessionService.verifyAccess("conv-1", SCOPE)).thenReturn(Mono.empty());
        when(llmResolver.resolve("tenant-1")).thenReturn(Mono.error(new TenantLlmConfigRequiredException()));

        postCompletions("conv-1").exchange()
                .expectStatus().isEqualTo(428)
                .expectBody().json("{\"code\":\"LLM_CONFIG_REQUIRED\",\"message\":\"Current tenant has no LLM configuration\"}", true);

        postLegacy("conv-1").exchange()
                .expectStatus().isEqualTo(428)
                .expectBody().json("{\"code\":\"LLM_CONFIG_REQUIRED\",\"message\":\"Current tenant has no LLM configuration\"}", true);

        verifyNoInteractions(orchestrator, lifecycle);
    }

    @Test
    void chatCompletionsRequiresAuthentication() {
        client.post().uri("/api/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"conversationId\":\"conv-1\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().json("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");
    }

    @Test
    void toContentChunkKeepsOnlyDeltaAndErrorContent() throws Exception {
        assertThat(ChatController.toContentChunk("plain text")).isEmpty();
        assertThat(ChatController.toContentChunk("{\"type\":\"thinking\",\"content\":\"x\"}")).isEmpty();
        assertThat(ChatController.toContentChunk("{\"type\":\"tool_call\",\"content\":\"x\"}")).isEmpty();
        assertThat(ChatController.toContentChunk("{\"type\":\"done\"}")).isEmpty();
        assertThat(ChatController.toContentChunk("{\"type\":\"delta\",\"content\":\"你好\"}")).contains("你好");
        assertThat(ChatController.toContentChunk("{\"type\":\"error\",\"content\":\"boom\"}")).contains("boom");
        String usageLine = "{\"type\":\"usage\",\"prompt_tokens\":7,\"completion_tokens\":3,\"total_tokens\":10}";
        assertThat(ChatController.toContentChunk(usageLine)).isEmpty();
        var usageChunk = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                ChatController.toOpenAiChunk(usageLine, "chatcmpl-test").orElseThrow());
        assertThat(usageChunk.path("choices")).isEmpty();
        assertThat(usageChunk.path("usage").path("prompt_tokens").asInt()).isEqualTo(7);
        assertThat(usageChunk.path("usage").path("completion_tokens").asInt()).isEqualTo(3);
        assertThat(usageChunk.path("usage").path("total_tokens").asInt()).isEqualTo(10);
    }

    @Test
    void listConversationsReturnsOnlyCurrentUsersSessions() {
        asUser1();
        var now = LocalDateTime.now();
        when(sessionService.listForUser(SCOPE)).thenReturn(
                Flux.just(new SessionRow("s1", "tenant-1", "user-1", "标题一", 0, now, now, null)));

        client.get().uri("/api/conversations").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo("s1")
                .jsonPath("$[0].title").isEqualTo("标题一");
    }

    @Test
    void getConversationMessagesReturns404WhenNotOwned() {
        asUser1();
        when(history.page(SCOPE, "s1", null, 100))
                .thenReturn(Mono.error(new MemoryException(ErrorCode.CONVERSATION_NOT_FOUND)));

        client.get().uri("/api/conversations/s1/messages").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("CONVERSATION_NOT_FOUND");
    }

    @Test
    void getConversationMessagesPagesUserAndAssistantMessagesWithCursorHeaders() {
        asUser1();
        when(history.page(SCOPE, "s1", 50L, 2)).thenReturn(Mono.just(new HistoryPage(List.of(
                stored("m-7", 7, LlmClient.Message.user("hi")),
                stored("m-8", 8, LlmClient.Message.assistantText("hello"))), 7L, true)));

        client.get().uri("/api/conversations/s1/messages?beforeSequence=50&limit=2")
                .header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Has-More", "true")
                .expectHeader().valueEquals("X-Next-Cursor", "7")
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo("m-7")
                .jsonPath("$[0].role").isEqualTo("user")
                .jsonPath("$[0].content").isEqualTo("hi")
                .jsonPath("$[1].id").isEqualTo("m-8")
                .jsonPath("$[1].role").isEqualTo("assistant")
                .jsonPath("$[1].content").isEqualTo("hello");
    }

    @Test
    void lastPageHasNoCursorHeader() {
        asUser1();
        when(history.page(SCOPE, "s1", null, 100)).thenReturn(Mono.just(new HistoryPage(List.of(
                stored("m-1", 1, LlmClient.Message.user("hi"))), null, false)));

        client.get().uri("/api/conversations/s1/messages").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Has-More", "false")
                .expectHeader().doesNotExist("X-Next-Cursor");
    }

    @Test
    void invalidHistoryLimitIs400InvalidArgument() {
        asUser1();
        when(history.page(eq(SCOPE), eq("s1"), any(), eq(0)))
                .thenReturn(Mono.error(new MemoryException(ErrorCode.INVALID_ARGUMENT)));

        client.get().uri("/api/conversations/s1/messages?limit=0").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_ARGUMENT");
    }

    private static StoredMessage stored(String id, long sequence, LlmClient.Message payload) {
        return new StoredMessage(id, sequence, payload,
                "user".equals(payload.role()) ? SourceType.USER_INPUT : SourceType.ASSISTANT,
                TurnStatus.COMPLETED, Instant.now());
    }
}
