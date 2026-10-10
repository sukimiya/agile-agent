package io.github.sukimiya.agileagent.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.agent.Orchestrator;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.service.ChatMemory;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.SessionService;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver;
import io.github.sukimiya.agileagent.service.TenantLlmConfigRequiredException;
import io.github.sukimiya.agileagent.web.CurrentUser;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
class ChatController {

    private final Orchestrator orchestrator;
    private final SessionService sessionService;
    private final ChatMemory memory;
    private final TenantLlmClientResolver llmResolver;
    private static final ObjectMapper json = new ObjectMapper();

    ChatController(Orchestrator orchestrator, SessionService sessionService, ChatMemory memory,
                   TenantLlmClientResolver llmResolver) {
        this.orchestrator = orchestrator;
        this.sessionService = sessionService;
        this.memory = memory;
        this.llmResolver = llmResolver;
    }

    // OpenAI-compatible chat completions endpoint
    @PostMapping(value = "/chat/completions")
    Mono<ResponseEntity<?>> chatCompletions(@RequestBody Map<String, Object> body,
                                                  @CurrentUser AuthSessionStore.AuthUser user) {
        var messages = getMessages(body);
        var userMsg = messages.isEmpty() ? "" :
                messages.get(messages.size() - 1).getOrDefault("content", "");

        // Use conversationId from frontend as the owner key for session continuity across requests
        var ownerId = body.getOrDefault("conversationId", UUID.randomUUID().toString()).toString();

        var chunkId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);

        return sessionService.touchOrCreate(ownerId, user.userId(), userMsg)
                .flatMap(access -> {
                    if (access == SessionService.AccessResult.FORBIDDEN) {
                        return Mono.<ResponseEntity<?>>just(ResponseEntity.notFound().build());
                    }
                    return llmResolver.resolve(user.identity().tenantId())
                            .<ResponseEntity<?>>map(llm -> ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                                    .body(openAiStream(ownerId, user, userMsg, chunkId, llm)))
                            .onErrorResume(TenantLlmConfigRequiredException.class,
                                    e -> Mono.just(configRequired()));
                });
    }

    private Flux<String> openAiStream(String ownerId, AuthSessionStore.AuthUser user, String userMsg,
                                      String chunkId, LlmClient llm) {
        return orchestrator.chat(ownerId, user.accessToken(), userMsg, user.identity(), llm)
                .flatMap(line -> toOpenAiChunk(line, chunkId).map(Flux::just).orElseGet(Flux::empty))
                .concatWithValues("[DONE]");
    }

    private static ResponseEntity<Map<String, String>> configRequired() {
        return ResponseEntity.status(428).body(Map.of("code", "LLM_CONFIG_REQUIRED",
                "message", "Current tenant has no LLM configuration"));
    }

    static Optional<String> toContentChunk(String line) {
        if (!line.startsWith("{\"type\":")) return Optional.empty();
        try {
            JsonNode n = json.readTree(line);
            String type = n.path("type").asText();
            if ("delta".equals(type) || "error".equals(type)) {
                return Optional.of(n.path("content").asText(""));
            }
        } catch (Exception ignored) {
            // not a control line we understand — drop it
        }
        return Optional.empty();
    }

    static Optional<String> toOpenAiChunk(String line, String chunkId) {
        try {
            JsonNode n = json.readTree(line);
            if ("usage".equals(n.path("type").asText())) {
                var chunk = json.createObjectNode();
                chunk.put("id", chunkId);
                chunk.put("object", "chat.completion.chunk");
                chunk.putArray("choices");
                var usage = chunk.putObject("usage");
                usage.put("prompt_tokens", n.path("prompt_tokens").longValue());
                usage.put("completion_tokens", n.path("completion_tokens").longValue());
                usage.put("total_tokens", n.path("total_tokens").longValue());
                return Optional.of(json.writeValueAsString(chunk));
            }
        } catch (Exception ignored) { }
        return toContentChunk(line).map(content -> {
            var chunk = json.createObjectNode();
            chunk.put("id", chunkId);
            chunk.put("object", "chat.completion.chunk");
            var choice = chunk.putArray("choices").addObject();
            choice.put("index", 0);
            choice.putObject("delta").put("content", content);
            try {
                return json.writeValueAsString(chunk);
            } catch (Exception e) {
                return "";
            }
        });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> getMessages(Map<String, Object> body) {
        return (List<Map<String, String>>) body.getOrDefault("messages", List.of());
    }

    // GET /api/models for chatbot-ui compatibility
    @GetMapping("/models")
    Map<String, Object> models() {
        return Map.of("object", "list",
                "data", List.of(Map.of("id", "agile-agent", "object", "model")));
    }

    // Legacy endpoint for custom frontend
    @PostMapping(value = "/chat")
    Mono<ResponseEntity<?>> chat(@RequestBody ChatRequest request,
                                       @CurrentUser AuthSessionStore.AuthUser user) {
        var ownerId = request.conversationId() != null
                ? request.conversationId()
                : UUID.randomUUID().toString();
        return sessionService.touchOrCreate(ownerId, user.userId(), request.message())
                .flatMap(access -> {
                    if (access == SessionService.AccessResult.FORBIDDEN) {
                        return Mono.<ResponseEntity<?>>just(ResponseEntity.notFound().build());
                    }
                    return llmResolver.resolve(user.identity().tenantId())
                            .<ResponseEntity<?>>map(llm -> ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                                    .body(orchestrator.chat(ownerId,
                                    user.accessToken(), request.message(), user.identity(), llm)))
                            .onErrorResume(TenantLlmConfigRequiredException.class,
                                    e -> Mono.just(configRequired()));
                });
    }

    @GetMapping("/conversations")
    Flux<ConversationSummary> listConversations(@CurrentUser AuthSessionStore.AuthUser user) {
        return sessionService.listForUser(user.userId())
                .map(s -> new ConversationSummary(s.id(), s.title(), s.updatedAt()));
    }

    @GetMapping("/conversations/{id}")
    Object getConversation(@PathVariable String id) {
        return null;
    }

    @GetMapping("/conversations/{id}/messages")
    Mono<ResponseEntity<List<Map<String, String>>>> getConversationMessages(@PathVariable String id,
                                                                        @CurrentUser AuthSessionStore.AuthUser user) {
        return sessionService.findOwned(id, user.userId())
                .flatMap(owned -> memory.get(id).map(messages -> {
                    List<Map<String, String>> displayMessages = new ArrayList<>();
                    int i = 0;
                    for (var m : messages) {
                        // Orchestrator now only ever appends user/assistantText turns, so this filter is
                        // always true for new sessions. Kept for old sessions whose Redis history still
                        // has tool-call/tool-result messages from the pre-Orchestrator single-agent loop.
                        boolean displayable = ("user".equals(m.role()) || "assistant".equals(m.role())) && m.content() != null;
                        if (!displayable) continue;
                        displayMessages.add(Map.of("id", "msg-" + (i++), "role", m.role(), "content", m.content()));
                    }
                    return ResponseEntity.ok(displayMessages);
                }))
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/conversations/{id}")
    void deleteConversation(@PathVariable String id) {
    }

    record ChatRequest(String conversationId, String message) {}

    record ConversationSummary(String id, String title, LocalDateTime updatedAt) {}

}
