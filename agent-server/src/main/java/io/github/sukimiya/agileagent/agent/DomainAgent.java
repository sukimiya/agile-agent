package io.github.sukimiya.agileagent.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.knowledge.KnowledgeCatalog;
import io.github.sukimiya.agileagent.service.ApiCatalog;
import io.github.sukimiya.agileagent.service.EnterpriseApiClient;
import io.github.sukimiya.agileagent.service.LlmClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Runs the tool-calling loop for one {@link DomainTask} against a single {@link AgentRole}.
 * Each run is a fresh, one-shot context (no chat history) — the calling Orchestrator owns
 * conversation memory.
 */
@Service
public class DomainAgent {

    private static final Logger log = LoggerFactory.getLogger(DomainAgent.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    static final int MAX_ROUNDS = 8;

    private final LlmClient llm;
    private final EnterpriseApiClient enterpriseApi;
    private final ApiCatalog catalog;
    private final KnowledgeCatalog knowledgeCatalog;
    private final AgentRoleRegistry roles;

    public DomainAgent(LlmClient llm, EnterpriseApiClient enterpriseApi, ApiCatalog catalog,
                        KnowledgeCatalog knowledgeCatalog, AgentRoleRegistry roles) {
        this.llm = llm;
        this.enterpriseApi = enterpriseApi;
        this.catalog = catalog;
        this.knowledgeCatalog = knowledgeCatalog;
        this.roles = roles;
    }

    public Flux<AgentEvent> run(DomainTask task) {
        return roles.byCode(task.roleCode())
                .map(role -> {
                    var messages = new ArrayList<LlmClient.Message>();
                    messages.add(LlmClient.Message.system(role.systemPrompt()));
                    messages.add(LlmClient.Message.user(task.userMessage()));
                    return round(role, messages, task, 0)
                            .contextWrite(ctx -> ctx.put("role", role.code()));
                })
                .orElseGet(() -> Flux.just(new AgentEvent.Failed(task.roleCode(), "unknown role: " + task.roleCode())));
    }

    private Flux<AgentEvent> round(AgentRole role, List<LlmClient.Message> messages, DomainTask task, int roundIndex) {
        if (roundIndex >= MAX_ROUNDS) {
            return Flux.just(new AgentEvent.Failed(role.code(), "已尽力但未能得到结果，请换个问法或重试"));
        }
        return llm.chatStream(messages, role.tools()).concatMap(event -> switch (event) {
            case LlmClient.LlmEvent.ContentDelta d -> Flux.just(new AgentEvent.Delta(role.code(), d.text()));
            case LlmClient.LlmEvent.Completed c -> {
                log.info("round={} finished chars={}", roundIndex, c.fullText().length());
                yield Flux.just(new AgentEvent.Finished(role.code(), c.fullText()));
            }
            case LlmClient.LlmEvent.ToolCallsReady tc -> {
                log.info("round={} toolCalls={}", roundIndex, tc.calls().stream().map(LlmClient.ToolCall::name).toList());
                messages.add(LlmClient.Message.assistantToolCalls(tc.calls()));
                yield Flux.fromIterable(tc.calls())
                        .concatMap(call -> Flux.concat(
                                Flux.just(new AgentEvent.ToolCalled(role.code(), call.name(), summarize(call.argumentsJson()))),
                                executeTool(call, task.accessToken()).flatMapMany(result -> {
                                    messages.add(LlmClient.Message.toolResult(call.id(), result));
                                    return Flux.<AgentEvent>empty();
                                })))
                        .concatWith(Flux.defer(() -> round(role, messages, task, roundIndex + 1)));
            }
        });
    }

    private Mono<String> executeTool(LlmClient.ToolCall call, String accessToken) {
        JsonNode args;
        try {
            args = JSON.readTree(call.argumentsJson());
        } catch (Exception e) {
            return Mono.just("tool execution error: " + e.getMessage());
        }
        return switch (call.name()) {
            case "getApiDetail" -> Mono.just(getApiDetail(args));
            case "callEnterpriseApi" -> callEnterpriseApi(args, accessToken);
            case "readKnowledgeDoc" -> readKnowledgeDoc(args);
            default -> Mono.just("unknown tool: " + call.name());
        };
    }

    private String getApiDetail(JsonNode args) {
        String service = args.path("service").asText();
        String path = args.path("path").asText();
        var detail = catalog.detail(service, path);
        log.info("getApiDetail service={} path={} found={}", service, path, detail.isPresent());
        return detail.orElse("接口不存在: " + service + " " + path);
    }

    private Mono<String> callEnterpriseApi(JsonNode args, String accessToken) {
        String service = args.path("service").asText();
        String method = args.path("method").asText();
        String path = args.path("path").asText();
        Map<String, Object> params = args.has("params")
                ? JSON.convertValue(args.get("params"), new TypeReference<Map<String, Object>>() {})
                : Map.of();

        if (!catalog.isWhitelisted(service, method, path)) {
            log.info("callEnterpriseApi REJECTED service={} method={} path={} params={}", service, method, path, params);
            return Mono.just("调用被拒绝：不在已注册接口白名单中 (" + service + " " + method + " " + path + ")");
        }
        return enterpriseApi.call(service, method, path, params, accessToken)
                .map(Object::toString)
                .doOnNext(result -> log.info("callEnterpriseApi OK service={} method={} path={} params={} result={}",
                        service, method, path, params,
                        result.length() > 500 ? result.substring(0, 500) + "...(truncated)" : result))
                .onErrorResume(EnterpriseApiClient.EnterpriseApiException.class, e -> {
                    log.info("callEnterpriseApi FAILED service={} method={} path={} params={} error={}", service,
                            method, path, params, e.getMessage());
                    return Mono.just("接口调用失败：" + e.getMessage());
                });
    }

    private Mono<String> readKnowledgeDoc(JsonNode args) {
        String id = args.path("id").asText();
        return knowledgeCatalog.content(id)
                .defaultIfEmpty("")
                .doOnNext(content -> log.info("readKnowledgeDoc id={} found={} chars={}", id, !content.isEmpty(), content.length()))
                .map(content -> content.isEmpty() ? "文档不存在: " + id : content);
    }

    private static String summarize(String argumentsJson) {
        return argumentsJson.length() > 200 ? argumentsJson.substring(0, 200) + "...(truncated)" : argumentsJson;
    }
}
