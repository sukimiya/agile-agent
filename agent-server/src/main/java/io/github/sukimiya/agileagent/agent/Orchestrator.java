package io.github.sukimiya.agileagent.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.memory.ConversationHistory;
import io.github.sukimiya.agileagent.memory.MemoryException;
import io.github.sukimiya.agileagent.memory.MemoryModel.AcceptedTurn;
import io.github.sukimiya.agileagent.memory.MemoryModel.TokenUsage;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.memory.TurnLifecycle;
import io.github.sukimiya.agileagent.memory.TurnRecorder;
import io.github.sukimiya.agileagent.memory.command.MemoryCommandService;
import io.github.sukimiya.agileagent.memory.context.AssembledContext;
import io.github.sukimiya.agileagent.memory.context.ContextAssembler;
import io.github.sukimiya.agileagent.memory.context.ContextBudget;
import io.github.sukimiya.agileagent.memory.context.ContextPurpose;
import io.github.sukimiya.agileagent.memory.recall.RecallResult;
import io.github.sukimiya.agileagent.memory.context.ModelWindow;
import io.github.sukimiya.agileagent.memory.context.TokenEstimator;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.service.TenantLlmClientResolver.ResolvedModel;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.util.NonBlockingIds;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Entry point for a chat turn: routes the question to zero or more capabilities,
 * fans out via
 * {@link TaskDispatcher}, and aggregates the results into one streamed answer.
 * Output line format (control-line JSON / plain final-text line / done) is unchanged
 * from the single-agent predecessor so the frontend needs no changes.
 *
 * <p>Each call runs one accepted turn through {@link TurnLifecycle#execute}: the prompt history and the
 * question come from durable storage, streamed answer text is checkpointed through the {@link TurnRecorder},
 * and the final text plus all usage observed (including usage after Completed) is committed only when the
 * upstream completes. {@code done} after an answer is emitted only once COMPLETED is durably written; every
 * failure ends with an {@code error} line carrying a stable {@code code} (then {@code done} as the protocol
 * terminator, never as success). Cancelling the subscription (client disconnect) cancels routing, the model
 * stream and the capability dispatch, including tool calls not started yet, and marks the turn CANCELLED.
 */
@Service
public class Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TOTAL_BUDGET = Duration.ofSeconds(180);

    private static final String ROUTING_PROMPT_TEMPLATE = """
            你是智能助手的路由模块。根据用户问题和对话历史，判断需要使用以下哪些能力才能回答。

            可选能力（格式：代码 | 名称 | 说明）：
            %s

            规则：
            1. 只输出一个 JSON 数组，元素为上面代码列表中的代码，不要输出任何其他文字。
            2. 如果问题明显只涉及一个能力，只输出该能力代码。
            3. 如果问题涉及多个能力，输出多个代码。
            4. 如果问题是打招呼、闲聊、询问助手能力等，与任何能力都无关，输出空数组 []。
            5. 不确定时倾向少选而不是多选。
            """;

    private static final String DIRECT_ANSWER_PROMPT = """
            你是智能助手，可以回答一般问题，也可以使用已注册的能力。
            当前问题不涉及已注册能力，请直接自然地回答（如打招呼、介绍自身能力等），不要编造具体数据。
            """;

    private static final String AGGREGATE_PROMPT = """
            你是智能助手，以下是各能力针对用户问题分别给出的结果，请综合这些结果，
            用自然语言给出统一、连贯的最终回答。可用表格/分组呈现，不要原样输出 JSON。
            如果某项能力执行失败或超时，如实告知用户对应部分暂时无法获取，不要编造。
            """;

    private final TaskDispatcher dispatcher;
    private final CapabilityRegistry capabilities;
    private final TurnLifecycle lifecycle;
    private final ConversationHistory history;
    private final ContextAssembler assembler;
    private final ContextBudget budget;
    private final MemoryCommandService commands;

    /** Without a context budget: every call gets the full history (M1 behaviour). */
    public Orchestrator(TaskDispatcher dispatcher, CapabilityRegistry capabilities, TurnLifecycle lifecycle,
                        ConversationHistory history) {
        this(dispatcher, capabilities, lifecycle, history, ContextAssembler.unbudgeted(history));
    }

    public Orchestrator(TaskDispatcher dispatcher, CapabilityRegistry capabilities, TurnLifecycle lifecycle,
                        ConversationHistory history, ContextAssembler assembler) {
        this(dispatcher, capabilities, lifecycle, history, assembler, null);
    }

    /**
     * With in-chat memory commands ("忘记…" / "记起来…", M2b T5 follow-up): the user's input is offered to
     * {@code commands} first (keyword prefilter, then one classification call on the turn's model); a handled
     * command is answered with its fixed text and no routing or answer model call.
     */
    @Autowired
    public Orchestrator(TaskDispatcher dispatcher, CapabilityRegistry capabilities, TurnLifecycle lifecycle,
                        ConversationHistory history, ContextAssembler assembler,
                        @Nullable MemoryCommandService commands) {
        this.commands = commands;
        this.dispatcher = dispatcher;
        this.capabilities = capabilities;
        this.lifecycle = lifecycle;
        this.history = history;
        this.assembler = assembler;
        this.budget = assembler.budget();
    }

    /**
     * Refuses, before the turn is accepted, a question that cannot fit the model's input budget even without any
     * history: {@code CONTEXT_BUDGET_EXCEEDED} (413). Checks the routing and the direct-answer call, whose fixed
     * parts are known up front. Synchronous and cheap (an estimate, no I/O).
     */
    public void requireWithinBudget(String userMessage, ResolvedModel model) {
        var window = windowOf(model);
        var user = LlmClient.Message.user(userMessage);
        budget.requireFits(List.of(routingPrefix().getFirst(), user), window);
        budget.requireFits(List.of(LlmClient.Message.system(DIRECT_ANSWER_PROMPT), user), window);
    }

    private ModelWindow windowOf(ResolvedModel model) {
        return budget.windowFor(model.presetId(), model.contextWindowTokens(), model.maxOutputTokens());
    }

    /** Streams one accepted turn for the tenant's resolved model, budgeting every call to its window. */
    public Flux<String> chat(AcceptedTurn accepted, String accessToken, IdentityContext identity, ResolvedModel model) {
        return chat(accepted, accessToken, identity, model.client(), windowOf(model));
    }

    /**
     * Streams one accepted turn. A completed replay re-emits the stored answer as one delta, the observed usage
     * and done without calling the model; otherwise the turn executes while subscribed (a ticket runs at most
     * once, so a second subscription gets an error line instead of a second model call).
     */
    public Flux<String> chat(AcceptedTurn accepted, String accessToken, IdentityContext identity, LlmClient llm) {
        return chat(accepted, accessToken, identity, llm, budget.defaultWindow());
    }

    private Flux<String> chat(AcceptedTurn accepted, String accessToken, IdentityContext identity, LlmClient llm,
            ModelWindow window) {
        var ticket = accepted.ticket();
        if (accepted.replay()) {
            return replay(accepted);
        }
        return Flux.defer(() -> {
            IdentityContext executionIdentity = identity == null
                    ? new IdentityContext(ticket.scope().subject(), null, ticket.scope().tenantId(), null, null, null,
                            Set.of(), Set.of(), Set.of(), false, false)
                    : identity;
            String traceId = NonBlockingIds.shortUuid();
            return Flux.concat(
                        Flux.just(controlLine("thinking", "正在处理...")),
                        lifecycle.execute(ticket,
                                recorder -> runTurn(recorder, accessToken, executionIdentity, llm, window)),
                        Flux.just(controlLine("done", null)))
                .onErrorResume(e -> {
                    String code = errorCode(e);
                    log.error("Orchestrator error type={} code={}", e.getClass().getSimpleName(), code);
                    return Flux.just(errorLine(code), controlLine("done", null));
                })
                .contextWrite(ctx -> ctx.put("traceId", traceId).put("sessionId", ticket.conversationId()));
        });
    }

    private Flux<String> runTurn(TurnRecorder recorder, String accessToken, IdentityContext identity, LlmClient llm,
            ModelWindow window) {
        var ticket = recorder.ticket();
        var usage = new UsageAccumulator();
        var finalText = new AtomicReference<String>();
        Flux<String> body = history.userInput(ticket)
                .flatMapMany(userMessage -> memoryCommand(ticket, userMessage, llm, usage).flatMapMany(command -> {
                    var answer = new FinalAnswer(recorder, finalText, command.notice());
                    if (command.handled()) {
                        var text = command.answer();
                        return streamFinalAnswer(answer, Flux.just(new LlmClient.LlmEvent.ContentDelta(text),
                                new LlmClient.LlmEvent.Completed(text)));
                    }
                    var call = new Call(ticket, llm, window, usage, assembler.recall(ticket, userMessage));
                    return route(call, userMessage)
                            .flatMapMany(capabilityCodes -> capabilityCodes.isEmpty()
                                    ? answerDirectly(call, answer, userMessage)
                                    : answerViaCapabilities(call, answer, userMessage, capabilityCodes,
                                            accessToken, identity));
                }));
        return Flux.concat(
                body.timeout(TOTAL_BUDGET),
                // Stage the final text with every usage observed so far, including usage after Completed.
                Mono.defer(() -> finalText.get() == null ? Mono.<Void>empty()
                        : recorder.completed(finalText.get(), usage.snapshot())).then(Mono.<String>empty()),
                Flux.defer(() -> usage.observed()
                        ? Flux.just(usageLine(usage.promptTokens(), usage.completionTokens(), usage.totalTokens()))
                        : Flux.empty()));
    }

    /** The in-chat memory command outcome (no commands: always answer normally, no notice). */
    private Mono<MemoryCommandService.Outcome> memoryCommand(TurnTicket ticket, String userMessage, LlmClient llm,
            UsageAccumulator usage) {
        return commands == null ? Mono.just(new MemoryCommandService.Outcome(null, ""))
                : commands.handle(ticket, userMessage, llm, usage::add);
    }

    private Flux<String> replay(AcceptedTurn accepted) {
        var lines = new ArrayList<String>();
        lines.add(controlLine("delta", accepted.answer() == null ? "" : accepted.answer()));
        var usage = accepted.usage();
        if (usage != null && usage.observed()) {
            lines.add(usageLine(usage.promptTokens(), usage.completionTokens(), usage.totalTokens()));
        }
        lines.add(controlLine("done", null));
        return Flux.fromIterable(lines);
    }

    /** Stable, client-visible code for an execution failure; never the underlying message. */
    static String errorCode(Throwable error) {
        if (error instanceof TurnLifecycle.ExecutionRightLostException) {
            return "TURN_INTERRUPTED";
        }
        if (error instanceof MemoryException memory) {
            return memory.code().name();
        }
        return "TURN_FAILED";
    }

    /**
     * Where the final model answer's deltas are checkpointed and its full text staged; {@code prefix} (a cancelled
     * memory pick notice, usually empty) is streamed before the answer and is part of the stored text.
     */
    private record FinalAnswer(TurnRecorder recorder, AtomicReference<String> text, String prefix) {}

    /**
     * What every model call of one turn shares: the turn, the model, its window, the usage total and the turn's
     * recall (run once, on first use; M2a Task 5).
     */
    private record Call(TurnTicket ticket, LlmClient llm, ModelWindow window, UsageAccumulator usage,
            Mono<RecallResult> recall) {}

    private List<LlmClient.Message> routingPrefix() {
        return List.of(LlmClient.Message.system(ROUTING_PROMPT_TEMPLATE.formatted(routingCatalog())));
    }

    /**
     * The request options of a call in this window: the effective output reserve is sent as {@code max_tokens}
     * while the budget is on; with it off nothing is sent (M1 behaviour).
     */
    static Flux<LlmClient.LlmEvent> modelCall(LlmClient llm, List<LlmClient.Message> messages,
            List<LlmClient.ToolDefinition> tools, ContextBudget budget, ModelWindow window) {
        return budget != null && budget.enabled() && window != null
                ? llm.chatStream(messages, tools, new LlmClient.CallOptions(window.maxOutputTokens()))
                : llm.chatStream(messages, tools);
    }

    /** One budgeted model call: adds usage to the turn total and records the estimate deviation. */
    private Flux<LlmClient.LlmEvent> stream(Call call, ContextPurpose purpose, AssembledContext context) {
        return modelCall(call.llm(), context.messages(), List.of(), budget, call.window())
                .doOnNext(event -> {
            call.usage().add(event);
            if (event instanceof LlmClient.LlmEvent.Usage value) {
                assembler.recordUsage(context, purpose, value.promptTokens());
            }
        });
    }

    private Mono<List<String>> route(Call call, String userMessage) {
        long start = System.currentTimeMillis();
        return assembler.assemble(call.ticket(), ContextPurpose.ROUTING, call.window(), routingPrefix(), userMessage,
                        call.recall())
                .flatMapMany(context -> stream(call, ContextPurpose.ROUTING, context))
                .collectList()
                .map(events -> events.stream()
                        .filter(LlmClient.LlmEvent.Completed.class::isInstance)
                        .map(LlmClient.LlmEvent.Completed.class::cast)
                        .map(LlmClient.LlmEvent.Completed::fullText)
                        .findFirst().orElse(""))
                .map(this::parseRoutingCapabilities)
                .doOnNext(codes -> log.info("routing question=\"{}\" capabilities={} elapsedMs={}",
                        truncate(userMessage, 60), codes, System.currentTimeMillis() - start));
    }

    private String routingCatalog() {
        return capabilities.all().stream()
                .map(provider -> provider.code() + " | " + provider.displayName() + " | " + provider.routingDescription())
                .collect(Collectors.joining("\n"));
    }

    private List<String> parseRoutingCapabilities(String text) {
        if (text == null || text.isBlank()) return List.of();
        try {
            JsonNode node = JSON.readTree(text.trim());
            if (!node.isArray()) return List.of();
            var result = new ArrayList<String>();
            for (JsonNode n : node) {
                capabilities.byCode(n.asText("")).ifPresent(provider -> {
                    if (!result.contains(provider.code())) result.add(provider.code());
                });
            }
            return result;
        } catch (Exception e) {
            log.warn("routing JSON parse failed, answering directly instead: {}", e.getMessage());
            return List.of();
        }
    }

    private Flux<String> answerDirectly(Call call, FinalAnswer answer, String userMessage) {
        log.info("routing no-capability, answering directly");
        return assembler.assemble(call.ticket(), ContextPurpose.DIRECT_ANSWER, call.window(),
                        List.of(LlmClient.Message.system(DIRECT_ANSWER_PROMPT)), userMessage, call.recall())
                .flatMapMany(context -> streamFinalAnswer(answer, stream(call, ContextPurpose.DIRECT_ANSWER, context)));
    }

    /**
     * One subscription to the dispatch both streams tool progress and collects the results, so cancelling the
     * returned Flux cancels the dispatch itself (a shared {@code cache()} would keep it running). The aggregate
     * model call starts only after the dispatch completed normally.
     */
    private Flux<String> answerViaCapabilities(Call call, FinalAnswer answer, String userMessage,
                                               List<String> capabilityCodes, String accessToken, IdentityContext identity) {
        var ticket = call.ticket();
        var taskWindow = budget.enabled() ? call.window() : null;
        // Candidate background: the recent completed turns that fit next to the question alone. Each domain agent
        // fits it to its own prompt and tool schemas. Off: no background (M1 behaviour).
        Mono<AssembledContext> candidate = budget.enabled()
                ? assembler.assemble(ticket, ContextPurpose.CAPABILITY, call.window(), List.of(), userMessage,
                        call.recall())
                : Mono.just(new AssembledContext(List.of(LlmClient.Message.user(userMessage)), 0, false, List.of(),
                        0));
        var results = new ArrayList<AgentEvent>();
        Flux<String> progress = candidate.flatMapMany(context -> {
                    var background = context.messages().subList(0, context.messages().size() - 1);
                    var tasks = capabilityCodes.stream()
                            .map(code -> new DomainTask(NonBlockingIds.uuid(), ticket.conversationId(), code,
                                    userMessage, accessToken, identity, ticket, taskWindow, background,
                                    context.historyTruncated()))
                            .toList();
                    return dispatcher.dispatch(tasks, call.llm());
                })
                .doOnNext(call.usage()::add)
                .handle((event, sink) -> {
                    if (event instanceof AgentEvent.ToolCalled called) {
                        sink.next(controlLine("tool_call", called.tool()));
                    } else if (event instanceof AgentEvent.Finished || event instanceof AgentEvent.Failed) {
                        results.add(event);
                    }
                });
        return progress.concatWith(Flux.defer(() ->
                aggregate(call, answer, userMessage, List.copyOf(results))));
    }

    private static final String RESULTS_HEADER = "各能力执行结果：\n";

    private Flux<String> aggregate(Call call, FinalAnswer answer, String userMessage,
                                   List<AgentEvent> capabilityResults) {
        long start = System.currentTimeMillis();
        List<String> capabilityCodes = capabilityResults.stream().map(this::roleOf).toList();
        log.info("aggregate capabilities={}", capabilityCodes);
        var prefix = List.of(LlmClient.Message.system(AGGREGATE_PROMPT));
        String summary = fittedResults(prefix, userMessage, capabilityResults, call.window());

        return assembler.assemble(call.ticket(), ContextPurpose.AGGREGATE, call.window(), prefix, userMessage,
                        List.of(LlmClient.Message.user(RESULTS_HEADER + summary)), call.recall())
                .flatMapMany(context -> streamFinalAnswer(answer, stream(call, ContextPurpose.AGGREGATE, context)))
                .doOnComplete(() -> log.info("aggregate capabilities={} totalElapsedMs={}", capabilityCodes, System.currentTimeMillis() - start));
    }

    /**
     * The capability results for the aggregate call, in order. A result that no longer fits next to the fixed
     * parts and the results before it is replaced by a placeholder (the capability's tool records keep the data).
     */
    private String fittedResults(List<LlmClient.Message> prefix, String userMessage, List<AgentEvent> results,
            ModelWindow window) {
        var parts = new ArrayList<String>();
        var fixed = new ArrayList<>(prefix);
        fixed.add(LlmClient.Message.user(userMessage));
        fixed.add(LlmClient.Message.user(RESULTS_HEADER));
        var available = budget.availableInput(window);
        for (var event : results) {
            var text = describe(event);
            var separator = parts.isEmpty() ? "" : "\n\n";
            var sofar = new AssembledContext(fixed, budget.estimate(fixed), false, List.of(), available);
            if (!budget.fitsToolResult(sofar, separator + text)) {
                var role = event instanceof AgentEvent.Finished f ? f.role() : roleOf(event);
                text = role + "：" + ContextBudget.omittedToolResult(role,
                        budget.scale(TokenEstimator.estimate(text)));
            }
            parts.add(text);
            fixed.set(fixed.size() - 1, LlmClient.Message.user(RESULTS_HEADER + String.join("\n\n", parts)));
        }
        return String.join("\n\n", parts);
    }

    private String describe(AgentEvent event) {
        return switch (event) {
            case AgentEvent.Finished f -> f.role() + "：" + f.text();
            case AgentEvent.Failed f -> f.role() + " 查询失败/超时：" + f.reason();
            case AgentEvent.ToolCalled t -> "";
            case AgentEvent.Delta d -> "";
            case AgentEvent.Usage u -> "";
        };
    }

    private String roleOf(AgentEvent event) {
        return switch (event) {
            case AgentEvent.Finished f -> f.role() + ":OK";
            case AgentEvent.Failed f -> f.role() + ":FAILED";
            case AgentEvent.ToolCalled t -> t.role();
            case AgentEvent.Delta d -> d.role();
            case AgentEvent.Usage u -> u.role();
        };
    }

    /** Streams the final answer: each delta is checkpointed before it is emitted; Completed stages the text. */
    private Flux<String> streamFinalAnswer(FinalAnswer answer, Flux<LlmClient.LlmEvent> events) {
        var prefix = answer.prefix() == null ? "" : answer.prefix();
        Flux<LlmClient.LlmEvent> all = prefix.isEmpty() ? events
                : Flux.concat(Flux.just(new LlmClient.LlmEvent.ContentDelta(prefix)), events);
        return all
                .concatMap(event -> switch (event) {
                    case LlmClient.LlmEvent.ContentDelta d ->
                            answer.recorder().delta(d.text()).thenReturn(controlLine("delta", d.text())).flux();
                    case LlmClient.LlmEvent.Completed c -> {
                        answer.text().set(prefix + c.fullText());
                        yield Flux.just(prefix + c.fullText() + "\n");
                    }
                    case LlmClient.LlmEvent.ToolCallsReady tc -> Flux.<String>empty();
                    case LlmClient.LlmEvent.Usage ignored -> Flux.<String>empty();
                });
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

    private static String errorLine(String code) {
        var node = JSON.createObjectNode();
        node.put("type", "error");
        node.put("content", "TURN_INTERRUPTED".equals(code) ? "执行已中断" : "处理出错");
        node.put("code", code);
        try {
            return JSON.writeValueAsString(node) + "\n";
        } catch (Exception e) {
            return "";
        }
    }

    private static String usageLine(long prompt, long completion, long total) {
        var node = JSON.createObjectNode();
        node.put("type", "usage");
        node.put("prompt_tokens", prompt);
        node.put("completion_tokens", completion);
        node.put("total_tokens", total);
        try {
            return JSON.writeValueAsString(node) + "\n";
        } catch (Exception e) {
            return "";
        }
    }

    private static final class UsageAccumulator {
        private final LongAdder prompt = new LongAdder();
        private final LongAdder completion = new LongAdder();
        private final LongAdder total = new LongAdder();
        private final java.util.concurrent.atomic.AtomicBoolean observed = new java.util.concurrent.atomic.AtomicBoolean();

        void add(LlmClient.LlmEvent event) {
            if (event instanceof LlmClient.LlmEvent.Usage value) {
                prompt.add(value.promptTokens());
                completion.add(value.completionTokens());
                total.add(value.totalTokens());
                observed.set(true);
            }
        }

        void add(AgentEvent event) {
            if (event instanceof AgentEvent.Usage value) {
                prompt.add(value.promptTokens());
                completion.add(value.completionTokens());
                total.add(value.totalTokens());
                observed.set(true);
            }
        }

        boolean observed() { return observed.get(); }
        TokenUsage snapshot() {
            return observed() ? new TokenUsage(true, promptTokens(), completionTokens(), totalTokens())
                    : TokenUsage.notObserved();
        }
        long promptTokens() { return prompt.sum(); }
        long completionTokens() { return completion.sum(); }
        long totalTokens() { return total.sum(); }
    }
}
