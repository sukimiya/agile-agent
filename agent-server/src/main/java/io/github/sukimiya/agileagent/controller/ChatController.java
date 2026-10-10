package io.github.sukimiya.agileagent.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import io.github.sukimiya.agileagent.service.SessionService;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
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
    private static final ObjectMapper json = new ObjectMapper();

    ChatController(Orchestrator orchestrator, SessionService sessionService, ChatMemory memory) {
        this.orchestrator = orchestrator;
        this.sessionService = sessionService;
        this.memory = memory;
    }

    // OpenAI-compatible chat completions endpoint
    @PostMapping(value = "/chat/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Mono<ResponseEntity<Flux<String>>> chatCompletions(@RequestBody Map<String, Object> body,
                                                  @CurrentUser AuthSessionStore.AuthUser user) {
        var messages = getMessages(body);
        var userMsg = messages.isEmpty() ? "" :
                messages.get(messages.size() - 1).getOrDefault("content", "");

        // Use conversationId from frontend as the owner key for session continuity across requests
        var ownerId = body.getOrDefault("conversationId", UUID.randomUUID().toString()).toString();

        var chunkId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 8);

        return sessionService.touchOrCreate(ownerId, user.userId(), userMsg)
                .map(access -> {
                    if (access == SessionService.AccessResult.FORBIDDEN) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).<Flux<String>>build();
                    }
                    Flux<String> stream = orchestrator.chat(ownerId, user.accessToken(), userMsg, identity(user))
                            .flatMap(line -> toContentChunk(line).map(Flux::just).orElseGet(Flux::empty))
                            .map(line -> {
                                var chunk = json.createObjectNode();
                                chunk.put("id", chunkId);
                                chunk.put("object", "chat.completion.chunk");
                                var choice = chunk.putArray("choices").addObject();
                                choice.put("index", 0);
                                choice.putObject("delta").put("content", line);
                                try {
                                    return json.writeValueAsString(chunk);
                                } catch (Exception e) {
                                    return "";
                                }
                            })
                            .concatWithValues("[DONE]");
                    return ResponseEntity.ok().body(stream);
                });
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
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Mono<ResponseEntity<Flux<String>>> chat(@RequestBody ChatRequest request,
                                       @CurrentUser AuthSessionStore.AuthUser user) {
        var ownerId = request.conversationId() != null
                ? request.conversationId()
                : UUID.randomUUID().toString();
        return sessionService.touchOrCreate(ownerId, user.userId(), request.message())
                .map(access -> access == SessionService.AccessResult.FORBIDDEN
                        ? ResponseEntity.status(HttpStatus.NOT_FOUND).<Flux<String>>build()
                        : ResponseEntity.ok().body(orchestrator.chat(ownerId, user.accessToken(),
                                request.message(), identity(user))));
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

    @GetMapping("/services")
    List<ServiceInfo> listServices() {
        return List.of(
                new ServiceInfo("MES", "制造执行系统", "/api/mes"),
                new ServiceInfo("CRM", "客户关系管理系统", "/api/crm"),
                new ServiceInfo("WMS", "仓储管理系统", "/api/wms"),
                new ServiceInfo("QMS", "质量管理系统", "/api/qms"),
                new ServiceInfo("SRM", "供应商关系管理", "/api/srm")
        );
    }

    record ChatRequest(String conversationId, String message) {}

    private static IdentityContext identity(AuthSessionStore.AuthUser user) {
        return new IdentityContext(user.userId(), user.name(), null, null, null, null,
                user.userType() == null ? Set.of() : Set.of(user.userType()), Set.of(), Set.of(), false, false);
    }

    record ConversationSummary(String id, String title, LocalDateTime updatedAt) {}

    record ServiceInfo(String code, String name, String basePath) {}
}
