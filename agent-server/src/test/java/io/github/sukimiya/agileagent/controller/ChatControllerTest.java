package io.github.sukimiya.agileagent.controller;

import static org.assertj.core.api.Assertions.*;
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
import org.springframework.test.web.reactive.server.WebTestClient;

import io.github.sukimiya.agileagent.agent.Orchestrator;
import io.github.sukimiya.agileagent.repository.SessionRepository.SessionRow;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.ChatMemory;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.OAuth2Client;
import io.github.sukimiya.agileagent.service.SessionService;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.service.TenantLlmConfigRequiredException;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.AuthExceptionHandler;
import io.github.sukimiya.agileagent.web.CurrentUserArgumentResolver;
import io.github.sukimiya.agileagent.web.WebConfig;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(ChatController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, CurrentUserArgumentResolver.class})
class ChatControllerTest {

    @Autowired WebTestClient client;
    @MockBean Orchestrator orchestrator;
    @MockBean SessionService sessionService;
    @MockBean ChatMemory chatMemory;
    @MockBean TenantLlmClientResolver llmResolver;
    @MockBean AuthSessionStore sessions;
    @MockBean OAuth2Client oauth2;
    @MockBean org.springframework.data.redis.core.ReactiveStringRedisTemplate redis;

    private void asUser1() {
        when(sessions.resolve("test-token")).thenReturn(Mono.just(user("user-1", "Test User", "sl-tok",
                "refresh-tok", Instant.now().plusSeconds(3600), user1Identity(), Instant.now())));
    }

    private static IdentityContext user1Identity() {
        return new IdentityContext("user-1", "Test User", "tenant-1", "Tenant One", "org-1", "Org One",
                Set.of("tenant_admin", "test"), Set.of("org-1"), Set.of("org-2"), false, true);
    }

    @Test
    void chatCompletionsStreamsWithoutRequiringAnyBinding() {
        asUser1();
        var llm = mock(LlmClient.class);
        when(llmResolver.resolve("tenant-1")).thenReturn(Mono.just(llm));
        when(sessionService.touchOrCreate("conv-1", "user-1", "hi")).thenReturn(Mono.just(SessionService.AccessResult.OWNED));
        when(orchestrator.chat(eq("conv-1"), eq("sl-tok"), eq("hi"), eq(user1Identity()), same(llm)))
                .thenReturn(Flux.just("hello back"));

        client.post().uri("/api/chat/completions")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token")
                .bodyValue("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"conversationId\":\"conv-1\"}")
                .exchange()
                .expectStatus().isOk();

        verify(orchestrator).chat("conv-1", "sl-tok", "hi", user1Identity(), llm);
    }

    @Test
    void chatCompletionsReturns404WhenSessionOwnedByAnotherUser() {
        asUser1();
        when(sessionService.touchOrCreate("conv-1", "user-1", "hi")).thenReturn(Mono.just(SessionService.AccessResult.FORBIDDEN));

        client.post().uri("/api/chat/completions")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token")
                .bodyValue("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"conversationId\":\"conv-1\"}")
                .exchange()
                .expectStatus().isNotFound();

        verifyNoInteractions(orchestrator);
        verifyNoInteractions(llmResolver);
    }

    @Test
    void bothChatEndpointsReturnExact428BeforeStartingSseWhenTenantHasNoLlm() {
        asUser1();
        when(sessionService.touchOrCreate("conv-1", "user-1", "hi"))
                .thenReturn(Mono.just(SessionService.AccessResult.OWNED));
        when(llmResolver.resolve("tenant-1")).thenReturn(Mono.error(new TenantLlmConfigRequiredException()));

        client.post().uri("/api/chat/completions")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token")
                .bodyValue("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"conversationId\":\"conv-1\"}")
                .exchange()
                .expectStatus().isEqualTo(428)
                .expectBody().json("{\"code\":\"LLM_CONFIG_REQUIRED\",\"message\":\"Current tenant has no LLM configuration\"}");

        client.post().uri("/api/chat")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token")
                .bodyValue("{\"conversationId\":\"conv-1\",\"message\":\"hi\"}")
                .exchange()
                .expectStatus().isEqualTo(428)
                .expectBody().json("{\"code\":\"LLM_CONFIG_REQUIRED\",\"message\":\"Current tenant has no LLM configuration\"}");

        verifyNoInteractions(orchestrator);
    }

    @Test
    void chatCompletionsRequiresAuthentication() {
        client.post().uri("/api/chat/completions")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"conversationId\":\"conv-1\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().json("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}");
    }

    @Test
    void legacyChatStreamsWithoutRequiringAnyBinding() {
        asUser1();
        var llm = mock(LlmClient.class);
        when(llmResolver.resolve("tenant-1")).thenReturn(Mono.just(llm));
        when(sessionService.touchOrCreate("conv-1", "user-1", "hi")).thenReturn(Mono.just(SessionService.AccessResult.OWNED));
        when(orchestrator.chat(eq("conv-1"), eq("sl-tok"), eq("hi"), eq(user1Identity()), same(llm)))
                .thenReturn(Flux.just("hello back"));

        client.post().uri("/api/chat")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token")
                .bodyValue("{\"conversationId\":\"conv-1\",\"message\":\"hi\"}")
                .exchange()
                .expectStatus().isOk();

        verify(orchestrator).chat("conv-1", "sl-tok", "hi", user1Identity(), llm);
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
        when(sessionService.listForUser("user-1")).thenReturn(
                Flux.just(new SessionRow("s1", null, "user-1", "标题一", 0, now, now, null)));

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
        when(sessionService.findOwned("s1", "user-1")).thenReturn(Mono.empty());

        client.get().uri("/api/conversations/s1/messages").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getConversationMessagesFiltersToUserAndAssistantRoles() {
        asUser1();
        var now = LocalDateTime.now();
        when(sessionService.findOwned("s1", "user-1")).thenReturn(
                Mono.just(new SessionRow("s1", null, "user-1", "标题", 0, now, now, null)));
        when(chatMemory.get("s1")).thenReturn(Mono.just(List.of(
                LlmClient.Message.system("system prompt"),
                LlmClient.Message.user("hi"),
                LlmClient.Message.assistantText("hello"))));

        client.get().uri("/api/conversations/s1/messages").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].role").isEqualTo("user")
                .jsonPath("$[0].content").isEqualTo("hi")
                .jsonPath("$[1].role").isEqualTo("assistant")
                .jsonPath("$[1].content").isEqualTo("hello");
    }
}
