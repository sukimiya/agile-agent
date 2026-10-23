package io.github.sukimiya.agileagent.memory.context;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.memory.ConversationHistory;
import io.github.sukimiya.agileagent.memory.ConversationHistory.HistoryMessage;
import io.github.sukimiya.agileagent.memory.ConversationHistory.HistoryTurn;
import io.github.sukimiya.agileagent.memory.MemoryMetrics;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.service.LlmClient;
import reactor.core.publisher.Mono;

/**
 * Builds the messages of one model call within its budget, in the order
 * {@code fixedPrefix, history, user(userMessage), fixedSuffix}.
 * <ul>
 * <li>The fixed parts are charged first; when they alone exceed the input budget the call fails with
 * {@code CONTEXT_BUDGET_EXCEEDED} (the controller already refuses such questions before acceptance).</li>
 * <li>History is the recent window: completed turns of the conversation, added whole from newest to oldest until
 * the next one would not fit. A turn is never split, so tool_call/tool pairs stay together, and the window is
 * contiguous (an older, smaller turn is not pulled in past a dropped one).</li>
 * <li>Until summaries exist (Task 4) dropped turns are only reported: {@code historyTruncated}, the
 * {@code memory.context.history.truncated} counter and one log line without content.</li>
 * <li>With {@code app.memory.context.enabled=false} every call gets the full history (M1 behaviour).</li>
 * </ul>
 * Injected-history metrics are recorded once per turn, for the answer call (DIRECT_ANSWER or AGGREGATE).
 */
@Component
public class ContextAssembler {

    private static final Logger log = LoggerFactory.getLogger(ContextAssembler.class);

    private final ConversationHistory history;
    private final ContextBudget budget;
    private final MemoryMetrics metrics;

    @Autowired
    public ContextAssembler(ConversationHistory history, ContextBudget budget, MemoryMetrics metrics) {
        this.history = history;
        this.budget = budget;
        this.metrics = metrics;
    }

    /** No budget at all (M1 behaviour), for wiring without memory properties. */
    public static ContextAssembler unbudgeted(ConversationHistory history) {
        var props = new MemoryProperties();
        props.getContext().setEnabled(false);
        return new ContextAssembler(history, new ContextBudget(props), null);
    }

    public ContextBudget budget() {
        return budget;
    }

    public Mono<AssembledContext> assemble(TurnTicket ticket, ContextPurpose purpose, ModelWindow window,
            List<LlmClient.Message> fixedPrefix, String userMessage) {
        return assemble(ticket, purpose, window, fixedPrefix, userMessage, List.of());
    }

    public Mono<AssembledContext> assemble(TurnTicket ticket, ContextPurpose purpose, ModelWindow window,
            List<LlmClient.Message> fixedPrefix, String userMessage, List<LlmClient.Message> fixedSuffix) {
        return Mono.defer(() -> {
            var fixed = new ArrayList<>(fixedPrefix);
            fixed.add(LlmClient.Message.user(userMessage));
            fixed.addAll(fixedSuffix);
            budget.requireFits(fixed, window);
            return history.completedTurnsBefore(ticket)
                    .map(turns -> build(purpose, window, fixedPrefix, userMessage, fixedSuffix, fixed, turns));
        });
    }

    private AssembledContext build(ContextPurpose purpose, ModelWindow window, List<LlmClient.Message> fixedPrefix,
            String userMessage, List<LlmClient.Message> fixedSuffix, List<LlmClient.Message> fixed,
            List<HistoryTurn> turns) {
        var available = budget.availableInput(window);
        var kept = new ArrayList<HistoryTurn>();
        if (!budget.enabled()) {
            kept.addAll(turns);
        } else {
            long raw = TokenEstimator.estimate(fixed);
            for (int i = turns.size() - 1; i >= 0; i--) {
                var turn = turns.get(i);
                long turnRaw = TokenEstimator.estimate(turn.messages().stream().map(HistoryMessage::message).toList());
                if (budget.scale(raw + turnRaw) > available) {
                    break;
                }
                raw += turnRaw;
                kept.addFirst(turn);
            }
        }
        var injected = kept.stream().flatMap(t -> t.messages().stream()).toList();
        var historyMessages = injected.stream().map(HistoryMessage::message).toList();
        var messages = new ArrayList<LlmClient.Message>(fixedPrefix);
        messages.addAll(historyMessages);
        messages.add(LlmClient.Message.user(userMessage));
        messages.addAll(fixedSuffix);
        var truncated = kept.size() < turns.size();
        // A CAPABILITY assembly is only the candidate background; the domain agent fits it to the capability's
        // own prompt and tool schemas and reports truncation there.
        if (truncated && purpose != ContextPurpose.CAPABILITY) {
            log.info("memory.context history truncated purpose={} keptTurns={} droppedTurns={} budgetTokens={}",
                    purpose, kept.size(), turns.size() - kept.size(), available);
            if (metrics != null) {
                metrics.historyTruncated(purpose.name());
            }
        }
        if (purpose == ContextPurpose.DIRECT_ANSWER || purpose == ContextPurpose.AGGREGATE) {
            history.recordInjected(historyMessages);
        }
        return new AssembledContext(messages, budget.estimate(messages), truncated,
                injected.stream().map(HistoryMessage::id).toList(), available);
    }

    /** Records the provider's prompt tokens for a call against the raw estimate of its messages. */
    public void recordUsage(AssembledContext context, ContextPurpose purpose, long promptTokens) {
        var raw = TokenEstimator.estimate(context.messages());
        if (metrics != null && raw > 0 && promptTokens > 0) {
            metrics.estimateRatio(purpose.name(), (double) promptTokens / raw);
        }
    }
}
