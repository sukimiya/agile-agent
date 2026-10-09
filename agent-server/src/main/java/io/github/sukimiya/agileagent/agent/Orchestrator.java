package io.github.sukimiya.agileagent.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.service.ChatMemory;
import io.github.sukimiya.agileagent.service.LlmClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Entry point for a chat turn: routes the question to zero or more domain agents
 * (one per enterprise API service, plus the knowledge base), fans out via
 * {@link TaskDispatcher}, and aggregates the results into one streamed answer.
 * Output line format (control-line JSON / plain final-text line / done) is unchanged
 * from the single-agent predecessor so the frontend needs no changes.
 */
@Service
public class Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TOTAL_BUDGET = Duration.ofSeconds(180);

    private static final String ROUTING_PROMPT_TEMPLATE = """
            你是企业内部智能助手的路由模块。根据用户问题和对话历史，判断需要查询以下哪些系统/知识库才能回答。

            可选系统（格式：代码 | 名称 | 说明）：
            %s

            规则：
            1. 只输出一个 JSON 数组，元素为上面代码列表中的代码，不要输出任何其他文字。
            2. 如果问题明显只涉及一个系统，只输出该系统代码，如 ["MES"]。
            3. 如果问题涉及多个系统的数据，输出多个代码，如 ["MES","CRM"]。
            4. 如果问题是打招呼、闲聊、询问助手能力等，与任何系统都无关，输出空数组 []。
            5. 不确定时倾向少选而不是多选。
            """;

    private static final String DIRECT_ANSWER_PROMPT = """
            你是企业内部智能助手，可以帮助用户查询 MES/CRM/WMS/QMS/SRM 等系统数据，以及已审核通过的知识库文档。
            当前问题不涉及具体系统查询，请直接自然地回答（如打招呼、介绍自身能力等），不要编造具体数据。
            """;

    private static final String AGGREGATE_PROMPT = """
            你是企业内部智能助手，以下是各系统/知识库针对用户问题分别给出的查询结果，请综合这些结果，
            用自然语言给出统一、连贯的最终回答。可用表格/分组呈现，不要原样输出 JSON。
            如果某个系统查询失败或超时，如实告知用户对应部分暂时无法获取，不要编造。
            """;

    private final LlmClient llm;
    private final TaskDispatcher dispatcher;
    private final AgentRoleRegistry roles;
    private final ChatMemory memory;

    public Orchestrator(LlmClient llm, TaskDispatcher dispatcher, AgentRoleRegistry roles, ChatMemory memory) {
        this.llm = llm;
        this.dispatcher = dispatcher;
        this.roles = roles;
        this.memory = memory;
    }

    public Flux<String> chat(String ownerId, String accessToken, String userMessage) {
        Flux<String> body = memory.get(ownerId)
                .flatMapMany(history -> route(history, userMessage)
                        .flatMapMany(roleCodes -> roleCodes.isEmpty()
                                ? answerDirectly(ownerId, history, userMessage)
                                : answerViaDomains(ownerId, history, userMessage, roleCodes, accessToken)));
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        return Flux.concat(
                        Flux.just(controlLine("thinking", "正在处理...")),
                        body.timeout(TOTAL_BUDGET),
                        Flux.just(controlLine("done", null)))
                .onErrorResume(e -> {
                    log.error("Orchestrator error", e);
                    return Flux.just(controlLine("error", "处理出错: " + e.getMessage()), controlLine("done", null));
                })
                .contextWrite(ctx -> ctx.put("traceId", traceId).put("sessionId", ownerId));
    }

    private Mono<List<String>> route(List<LlmClient.Message> history, String userMessage) {
        var messages = new ArrayList<LlmClient.Message>();
        messages.add(LlmClient.Message.system(ROUTING_PROMPT_TEMPLATE.formatted(roles.routingCatalog())));
        messages.addAll(history);
        messages.add(LlmClient.Message.user(userMessage));
        long start = System.currentTimeMillis();
        return llm.chatStream(messages, List.of())
                .ofType(LlmClient.LlmEvent.Completed.class)
                .map(LlmClient.LlmEvent.Completed::fullText)
                .next()
                .defaultIfEmpty("")
                .map(this::parseRoutingRoles)
                .doOnNext(codes -> log.info("routing question=\"{}\" roles={} elapsedMs={}",
                        truncate(userMessage, 60), codes, System.currentTimeMillis() - start));
    }

    private List<String> parseRoutingRoles(String text) {
        if (text == null || text.isBlank()) return List.of();
        try {
            JsonNode node = JSON.readTree(text.trim());
            if (!node.isArray()) return List.of();
            var result = new ArrayList<String>();
            for (JsonNode n : node) {
                roles.byCode(n.asText("")).ifPresent(role -> {
                    if (!result.contains(role.code())) result.add(role.code());
                });
            }
            return result;
        } catch (Exception e) {
            log.warn("routing JSON parse failed, answering directly instead: {}", e.getMessage());
            return List.of();
        }
    }

    private Flux<String> answerDirectly(String ownerId, List<LlmClient.Message> history, String userMessage) {
        log.info("routing no-domain, answering directly");
        var messages = new ArrayList<LlmClient.Message>();
        messages.add(LlmClient.Message.system(DIRECT_ANSWER_PROMPT));
        messages.addAll(history);
        messages.add(LlmClient.Message.user(userMessage));
        return streamFinalAnswer(ownerId, userMessage, messages);
    }

    private Flux<String> answerViaDomains(String ownerId, List<LlmClient.Message> history, String userMessage,
                                           List<String> roleCodes, String accessToken) {
        var tasks = roleCodes.stream()
                .map(code -> new DomainTask(UUID.randomUUID().toString(), ownerId, code, userMessage, accessToken))
                .toList();
        Flux<AgentEvent> events = dispatcher.dispatch(tasks).cache();

        Flux<String> progress = events.filter(e -> e instanceof AgentEvent.ToolCalled)
                .map(e -> controlLine("tool_call", ((AgentEvent.ToolCalled) e).tool()));

        Mono<List<AgentEvent>> results = events
                .filter(e -> e instanceof AgentEvent.Finished || e instanceof AgentEvent.Failed)
                .collectList();

        return progress.concatWith(Flux.defer(() ->
                results.flatMapMany(r -> aggregate(ownerId, history, userMessage, r))));
    }

    private Flux<String> aggregate(String ownerId, List<LlmClient.Message> history, String userMessage,
                                    List<AgentEvent> domainResults) {
        long start = System.currentTimeMillis();
        String summary = domainResults.stream().map(this::describe).collect(Collectors.joining("\n\n"));
        List<String> roleCodes = domainResults.stream().map(this::roleOf).toList();
        log.info("aggregate roles={}", roleCodes);

        var messages = new ArrayList<LlmClient.Message>();
        messages.add(LlmClient.Message.system(AGGREGATE_PROMPT));
        messages.addAll(history);
        messages.add(LlmClient.Message.user(userMessage));
        messages.add(LlmClient.Message.user("各系统查询结果：\n" + summary));

        return streamFinalAnswer(ownerId, userMessage, messages)
                .doOnComplete(() -> log.info("aggregate roles={} totalElapsedMs={}", roleCodes, System.currentTimeMillis() - start));
    }

    private String describe(AgentEvent event) {
        return switch (event) {
            case AgentEvent.Finished f -> f.role() + "：" + f.text();
            case AgentEvent.Failed f -> f.role() + " 查询失败/超时：" + f.reason();
            case AgentEvent.ToolCalled t -> "";
            case AgentEvent.Delta d -> "";
        };
    }

    private String roleOf(AgentEvent event) {
        return switch (event) {
            case AgentEvent.Finished f -> f.role() + ":OK";
            case AgentEvent.Failed f -> f.role() + ":FAILED";
            case AgentEvent.ToolCalled t -> t.role();
            case AgentEvent.Delta d -> d.role();
        };
    }

    private Flux<String> streamFinalAnswer(String ownerId, String userMessage, List<LlmClient.Message> messages) {
        var finalText = new AtomicReference<>("");
        return llm.chatStream(messages, List.of())
                .concatMap(event -> switch (event) {
                    case LlmClient.LlmEvent.ContentDelta d -> Flux.just(controlLine("delta", d.text()));
                    case LlmClient.LlmEvent.Completed c -> {
                        finalText.set(c.fullText());
                        yield Flux.just(c.fullText() + "\n");
                    }
                    case LlmClient.LlmEvent.ToolCallsReady tc -> Flux.<String>empty();
                })
                .concatWith(Flux.defer(() -> saveTurn(ownerId, userMessage, finalText.get()).thenMany(Flux.empty())));
    }

    private Mono<Void> saveTurn(String ownerId, String userMessage, String answer) {
        return memory.append(ownerId, List.of(LlmClient.Message.user(userMessage), LlmClient.Message.assistantText(answer)));
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private static String controlLine(String type, String content) {
        var node = JSON.createObjectNode();
        node.put("type", type);
        if (content != null) node.put("content", content);
        try {
            return JSON.writeValueAsString(node) + "\n";
        } catch (Exception e) {
            return "";
        }
    }
}
