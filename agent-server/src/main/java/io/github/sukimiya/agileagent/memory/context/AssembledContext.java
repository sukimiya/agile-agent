package io.github.sukimiya.agileagent.memory.context;

import java.util.List;

import io.github.sukimiya.agileagent.service.LlmClient;

/**
 * Messages for one model call. {@code estimatedTokens} is the budgeted estimate (safety factor applied) of all
 * {@code messages}; {@code budgetTokens} the input budget it had to fit (window minus output reserve).
 * {@code historyTruncated} says older completed turns were left out; {@code includedMessageIds} are the stored
 * history messages that went in, in prompt order.
 */
public record AssembledContext(List<LlmClient.Message> messages, int estimatedTokens, boolean historyTruncated,
        List<String> includedMessageIds, int budgetTokens) {

    public AssembledContext {
        messages = List.copyOf(messages);
        includedMessageIds = List.copyOf(includedMessageIds);
    }
}
