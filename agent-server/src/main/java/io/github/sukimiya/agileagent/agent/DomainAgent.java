package io.github.sukimiya.agileagent.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.CapabilityDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Runs the tool-calling loop for one domain capability. */
@Service
public class DomainAgent {

    private static final Logger log = LoggerFactory.getLogger(DomainAgent.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    static final int MAX_ROUNDS = 8;

    private final CapabilityRegistry capabilities;

    public DomainAgent(CapabilityRegistry capabilities) {
        this.capabilities = capabilities;
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
        return llm.chatStream(messages, llmTools).concatMap(event -> switch (event) {
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
                                executeTool(provider, allowedTools, call, context).flatMapMany(result -> {
                                    messages.add(LlmClient.Message.toolResult(call.id(), result));
                                    return Flux.<AgentEvent>empty();
                                })))
                        .concatWith(Flux.defer(() -> round(llm, provider, definition, messages, task, context, roundIndex + 1)));
            }
        });
    }

    private Mono<String> executeTool(AgentCapabilityProvider provider, Set<String> allowedTools,
                                     LlmClient.ToolCall call, AgentExecutionContext context) {
        if (!allowedTools.contains(call.name())) {
            return Mono.just("unknown tool: " + call.name());
        }
        JsonNode args;
        try {
            args = JSON.readTree(call.argumentsJson());
        } catch (Exception e) {
            return Mono.just("tool execution error: " + e.getMessage());
        }
        var invocation = new ToolInvocation(call.name(), call.id(), args);
        return provider.execute(invocation, context)
                .map(result -> result.displayResult())
                .onErrorMap(error -> {
                    log.warn("capability tool failed role={} tool={} errorType={}", provider.code(), call.name(),
                            error.getClass().getSimpleName());
                    return new IllegalStateException("capability tool execution failed");
                });
    }

    private static String summarize(String argumentsJson) {
        return argumentsJson.length() > 200 ? argumentsJson.substring(0, 200) + "...(truncated)" : argumentsJson;
    }
}
