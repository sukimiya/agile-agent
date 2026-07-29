package com.jowo.agent.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.jowo.agent.agent.Orchestrator;
import com.jowo.agent.repository.SessionRepository.SessionRow;
import com.jowo.agent.service.AuthSessionStore;
import com.jowo.agent.service.ChatMemory;
import com.jowo.agent.service.LlmClient;
import com.jowo.agent.service.OAuth2Client;
import com.jowo.agent.service.SessionService;
import com.jowo.agent.web.AuthExceptionHandler;
import com.jowo.agent.web.CurrentUserArgumentResolver;
import com.jowo.agent.web.WebConfig;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(ChatController.class)
@Import({WebConfig.class, AuthExceptionHandler.class, CurrentUserArgumentResolver.class})
class ChatControllerTest {

    @Autowired WebTestClient client;
    @MockBean Orchestrator orchestrator;
    @MockBean SessionService sessionService;
    @MockBean ChatMemory chatMemory;
    @MockBean AuthSessionStore sessions;
    @MockBean OAuth2Client oauth2;

    private void asUser1() {
        when(sessions.resolve("test-token")).thenReturn(
                Mono.just(new AuthSessionStore.AuthUser("user-1", "Test User", "sl-tok", "refresh-tok", Instant.now().plusSeconds(3600), "test")));
    }

    @Test
    void chatCompletionsStreamsWithoutRequiringAnyBinding() {
        asUser1();
        when(sessionService.touchOrCreate("conv-1", "user-1", "hi")).thenReturn(Mono.just(SessionService.AccessResult.OWNED));
        when(orchestrator.chat(eq("conv-1"), eq("sl-tok"), eq("hi"))).thenReturn(Flux.just("hello back"));

        client.post().uri("/api/chat/completions")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token")
                .bodyValue("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"conversationId\":\"conv-1\"}")
                .exchange()
                .expectStatus().isOk();

        verify(orchestrator).chat("conv-1", "sl-tok", "hi");
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
    }

    @Test
    void chatCompletionsRequiresAuthentication() {
        client.post().uri("/api/chat/completions")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"conversationId\":\"conv-1\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void legacyChatStreamsWithoutRequiringAnyBinding() {
        asUser1();
        when(sessionService.touchOrCreate("conv-1", "user-1", "hi")).thenReturn(Mono.just(SessionService.AccessResult.OWNED));
        when(orchestrator.chat(eq("conv-1"), eq("sl-tok"), eq("hi"))).thenReturn(Flux.just("hello back"));

        client.post().uri("/api/chat")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token")
                .bodyValue("{\"conversationId\":\"conv-1\",\"message\":\"hi\"}")
                .exchange()
                .expectStatus().isOk();

        verify(orchestrator).chat("conv-1", "sl-tok", "hi");
    }

    @Test
    void toContentChunkKeepsOnlyDeltaAndErrorContent() {
        assertThat(ChatController.toContentChunk("plain text")).isEmpty();
        assertThat(ChatController.toContentChunk("{\"type\":\"thinking\",\"content\":\"x\"}")).isEmpty();
        assertThat(ChatController.toContentChunk("{\"type\":\"tool_call\",\"content\":\"x\"}")).isEmpty();
        assertThat(ChatController.toContentChunk("{\"type\":\"done\"}")).isEmpty();
        assertThat(ChatController.toContentChunk("{\"type\":\"delta\",\"content\":\"你好\"}")).contains("你好");
        assertThat(ChatController.toContentChunk("{\"type\":\"error\",\"content\":\"boom\"}")).contains("boom");
    }

    @Test
    void servicesEndpointListsAllFiveServices() {
        asUser1();
        client.get().uri("/api/services").header("Authorization", "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.length()").isEqualTo(5);
    }

    @Test
    void listConversationsReturnsOnlyCurrentUsersSessions() {
        asUser1();
        var now = LocalDateTime.now();
        when(sessionService.listForUser("user-1")).thenReturn(
                Flux.just(new SessionRow("s1", "user-1", "标题一", now, now)));

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
                Mono.just(new SessionRow("s1", "user-1", "标题", now, now)));
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
