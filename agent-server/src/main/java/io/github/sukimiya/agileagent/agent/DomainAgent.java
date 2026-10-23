package io.github.sukimiya.agileagent.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.memory.ToolRecordSanitizer;
import io.github.sukimiya.agileagent.memory.TurnToolRecorder;
import io.github.sukimiya.agileagent.memory.context.AssembledContext;
import io.github.sukimiya.agileagent.memory.context.ContextBudget;
import io.github.sukimiya.agileagent.memory.context.TokenEstimator;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.CapabilityDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Runs the tool-calling loop for one domain capability. Every tool call is recorded against the task's turn
 * before it executes and its outcome afterwards ({@link TurnToolRecorder}); a call whose start cannot be recorded
 * is not executed, and a call whose outcome cannot be recorded ends the run instead of being retried. Tool
 * results only ever become tool-role messages: they gain no instruction rights and cannot widen the allowed tools.
 * With a budgeted task ({@link DomainTask#window()}), a result that would overflow the window reaches the model
 * only as a placeholder; the recorded result stays complete.
 */
@Service
public class DomainAgent {

    private static final Logger log = LoggerFactory.getLogger(DomainAgent.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    static final int MAX_ROUNDS = 8;

    private final CapabilityRegistry capabilities;
    private final TurnToolRecorder tools;
    private final ContextBudget budget;

    @Autowired
    public DomainAgent(CapabilityRegistry capabilities, TurnToolRecorder tools, ContextBudget budget) {
        this.capabilities = capabilities;
        this.tools = tools;
        this.budget = budget;
    }

    /** Without a context budget: tool results always reach the model in full (M1 behaviour). */
    public DomainAgent(CapabilityRegistry capabilities, TurnToolRecorder tools) {
        this(capabilities, tools, null);
    }

    public Flux<AgentEvent> run(DomainTask task, LlmClient llm) {
        var provider = capabilities.byCode(task.roleCode());
        if (provider.isEmpty()) {
            return Flux.just(new AgentEvent.Failed(task.roleCode(), "unknown role: " + task.roleCode()));
        }
        AgentCapabilityProvider selected = provider.get();
        var context = new AgentExecutionContext(task.identity(), task.sessionId(), task::accessToken);
        return selected.definition(context)
                .onErrorMap(error -> new IllegalStateException("capability definition failed"))
                .flatMapMany(definition -> {
                    var messages = new ArrayList<LlmClient.Message>();
                    messages.add(LlmClient.Message.system(definition.systemPrompt()));
                    messages.add(LlmClient.Message.user(task.userMessage()));
                    return round(llm, selected, definition, messages, task, context, 0)
                            .contextWrite(ctx -> ctx.put("role", selected.code()));
                });
    }

    private Flux<AgentEvent> round(LlmClient llm, AgentCapabilityProvider provider, CapabilityDefinition definition,
                                   List<LlmClient.Message> messages, DomainTask task,
                                   AgentExecutionContext context, int roundIndex) {
        if (roundIndex >= MAX_ROUNDS) {
            return Flux.just(new AgentEvent.Failed(provider.code(), "已尽力但未能得到结果，请换个问法或重试"));
        }
        var allowedTools = definition.tools().stream().map(t -> t.name()).collect(Collectors.toUnmodifiableSet());
        var llmTools = definition.tools().stream()
                .map(tool -> new LlmClient.ToolDefinition(tool.name(), tool.description(),
                        JSON.convertValue(tool.parametersSchema(),
                                new TypeReference<java.util.Map<String, Object>>() {})))
                .toList();
        return Orchestrator.modelCall(llm, messages, llmTools, budget, task.window())
                .concatMap(event -> switch (event) {
            case LlmClient.LlmEvent.ContentDelta d -> Flux.just(new AgentEvent.Delta(provider.code(), d.text()));
            case LlmClient.LlmEvent.Usage u -> Flux.just(new AgentEvent.Usage(provider.code(), u.promptTokens(),
                    u.completionTokens(), u.totalTokens()));
            case LlmClient.LlmEvent.Completed c -> {
                log.info("round={} finished chars={}", roundIndex, c.fullText().length());
                yield Flux.just(new AgentEvent.Finished(provider.code(), c.fullText()));
            }
            case LlmClient.LlmEvent.ToolCallsReady tc -> {
                log.info("round={} toolCalls={}", roundIndex, tc.calls().stream().map(LlmClient.ToolCall::name).toList());
                messages.add(LlmClient.Message.assistantToolCalls(tc.calls()));
                yield Flux.fromIterable(tc.calls())
                        .concatMap(call -> Flux.concat(
                                Flux.just(new AgentEvent.ToolCalled(provider.code(), call.name(), summarize(call.argumentsJson()))),
                                recordedTool(provider, allowedTools, call, task, context).flatMapMany(result -> {
                                    messages.add(LlmClient.Message.toolResult(call.id(),
                                            forModel(task, messages, llmTools, call, result)));
                                    return Flux.<AgentEvent>empty();
                                })))
                        .concatWith(Flux.defer(() -> round(llm, provider, definition, messages, task, context, roundIndex + 1)));
            }
        });
    }

    /**
     * The tool result as the model gets it: in full while it fits the task's window next to the messages so far
     * and the tool schemas, otherwise a placeholder naming the call id. Checked for every result, so each round
     * is budgeted against what earlier rounds added; the recorder always keeps the full text.
     */
    private String forModel(DomainTask task, List<LlmClient.Message> messages, List<LlmClient.ToolDefinition> llmTools,
                            LlmClient.ToolCall call, String result) {
        if (budget == null || task.window() == null || !budget.enabled() || result == null) {
            return result;
        }
        var sofar = new ArrayList<>(messages);
        sofar.add(LlmClient.Message.system(schemaText(llmTools)));
        var context = new AssembledContext(sofar, budget.estimate(sofar), false, List.of(),
                budget.availableInput(task.window()));
        if (budget.fitsToolResult(context, result)) {
            return result;
        }
        var tokens = budget.scale(TokenEstimator.estimate(result));
        log.info("tool result omitted from context callId={} estimatedTokens={} budgetTokens={}", call.id(), tokens,
                context.budgetTokens());
        return ContextBudget.omittedToolResult(call.id(), tokens);
    }

    private static String schemaText(List<LlmClient.ToolDefinition> llmTools) {
        try {
            return JSON.writeValueAsString(llmTools);
        } catch (Exception e) {
            return llmTools.toString();
        }
    }

    /** Start record, then execution, then outcome record; the tool result text for the model. */
    private Mono<String> recordedTool(AgentCapabilityProvider provider, Set<String> allowedTools,
                                      LlmClient.ToolCall call, DomainTask task, AgentExecutionContext context) {
        var ticket = task.ticket();
        if (ticket == null) {
            return Mono.error(new IllegalStateException("tool call outside an accepted turn"));
        }
        return tools.started(ticket, provider.code(), call)
                .then(Mono.defer(() -> executeTool(provider, allowedTools, call, context)))
                .flatMap(outcome -> tools.finished(ticket, provider.code(), call.id(),
                                outcome.error() == null ? outcome.text() : null, outcome.success(), outcome.errorType())
                        .then(outcome.error() == null ? Mono.just(outcome.text()) : Mono.<String>error(outcome.error())));
    }

    /** What one call produced: model-facing text, or the error that ends the run, plus the recorded error type. */
    private record Outcome(String text, boolean success, String errorType, Throwable error) {
        static Outcome succeeded(String text) {
            return new Outcome(text, true, null, null);
        }

        static Outcome failed(String text, String errorType) {
            return new Outcome(text, false, errorType, null);
        }
    }

    private Mono<Outcome> executeTool(AgentCapabilityProvider provider, Set<String> allowedTools,
                                      LlmClient.ToolCall call, AgentExecutionContext context) {
        if (!allowedTools.contains(call.name())) {
            return Mono.just(Outcome.failed("unknown tool: " + call.name(), "UNKNOWN_TOOL"));
        }
        JsonNode args;
        try {
            args = JSON.readTree(call.argumentsJson());
        } catch (Exception e) {
            return Mono.just(Outcome.failed("tool execution error: invalid arguments JSON", "INVALID_ARGUMENTS"));
        }
        var invocation = new ToolInvocation(call.name(), call.id(), args);
        return provider.execute(invocation, context)
                .map(result -> result.success() ? Outcome.succeeded(result.displayResult())
                        : Outcome.failed(result.displayResult(), "TOOL_REPORTED_FAILURE"))
                .onErrorResume(error -> {
                    var errorType = error.getClass().getSimpleName();
                    log.warn("capability tool failed role={} tool={} errorType={}", provider.code(), call.name(),
                            errorType);
                    return Mono.just(new Outcome(null, false, errorType,
                            new IllegalStateException("capability tool execution failed")));
                });
    }

    private static String summarize(String argumentsJson) {
        var safe = ToolRecordSanitizer.sanitize(argumentsJson);
        if (safe == null) {
            return "";
        }
        return safe.length() > 200 ? safe.substring(0, 200) + "...(truncated)" : safe;
    }
}
