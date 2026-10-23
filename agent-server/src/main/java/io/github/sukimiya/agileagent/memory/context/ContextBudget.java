package io.github.sukimiya.agileagent.memory.context;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.sukimiya.agileagent.memory.MemoryException;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.service.LlmClient;

/**
 * Budget arithmetic for model calls: {@code available input = context window - output reserve}, estimates use the
 * M1 heuristic times {@code app.memory.context.estimate-factor} (D3, default 1.3; the factor is the safety margin).
 * Fixed parts (system prompt, tool schemas, the current question, capability results) are charged first; history
 * gets what is left. With {@code app.memory.context.enabled=false} nothing is enforced (M1 behaviour).
 */
@Component
public class ContextBudget {

    private static final Logger log = LoggerFactory.getLogger(ContextBudget.class);

    /** Upper bound on remembered warnings; presets are few, this only guards against unbounded growth. */
    private static final int MAX_WARNED = 1_024;

    private final MemoryProperties props;
    /** Preset configurations whose reserve fallback was already logged: {@code presetKey:window:output}. */
    private final java.util.Set<String> warned = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public ContextBudget(MemoryProperties props) {
        this.props = props;
    }

    public boolean enabled() {
        return props.getContext().isEnabled();
    }

    public ModelWindow defaultWindow() {
        return windowFor(null, null);
    }

    /**
     * The preset's window with defaults for unset values. When only the window is set and the default reserve
     * would leave no input, the reserve becomes a quarter of the window.
     */
    /** {@link #windowFor(String, Integer, Integer)} for a window not tied to a preset (the configured defaults). */
    public ModelWindow windowFor(Integer contextWindowTokens, Integer maxOutputTokens) {
        return windowFor(null, contextWindowTokens, maxOutputTokens);
    }

    /**
     * The window for one preset; unset values fall back to the configured defaults. When the reserve does not fit
     * the window, a quarter of the window is reserved instead and a WARN is logged once per preset configuration
     * ({@code presetKey} with the resulting values; a changed preset that still does not fit is logged again).
     */
    public ModelWindow windowFor(String presetKey, Integer contextWindowTokens, Integer maxOutputTokens) {
        var context = props.getContext();
        int window = contextWindowTokens != null ? contextWindowTokens : context.getDefaultContextWindowTokens();
        int output = maxOutputTokens != null ? maxOutputTokens : context.getDefaultMaxOutputTokens();
        if (output >= window) {
            var reduced = Math.max(1, window / 4);
            var key = (presetKey == null ? "default" : presetKey) + ":" + window + ":" + output;
            if (warned.size() >= MAX_WARNED) {
                warned.clear();
            }
            if (warned.add(key)) {
                log.warn("memory.context output reserve {} does not fit window {} of preset {}; using {}", output,
                        window, presetKey == null ? "default" : presetKey, reduced);
            }
            output = reduced;
        }
        return new ModelWindow(window, output);
    }

    public int availableInput(ModelWindow window) {
        return window.contextWindowTokens() - window.maxOutputTokens();
    }

    /** Budgeted estimate (safety factor applied) of {@code messages}. */
    public int estimate(List<LlmClient.Message> messages) {
        return scale(TokenEstimator.estimate(messages));
    }

    /** Budgeted estimate of {@code raw} heuristic tokens. */
    public int scale(long raw) {
        return TokenEstimator.budgeted(raw, props.getContext().getEstimateFactor());
    }

    /** What is left for history after the fixed parts; never negative. */
    public int historyBudget(ModelWindow window, List<LlmClient.Message> fixed) {
        return Math.max(0, availableInput(window) - estimate(fixed));
    }

    /** Fails with {@code CONTEXT_BUDGET_EXCEEDED} (413) when the fixed parts alone exceed the input budget. */
    public void requireFits(List<LlmClient.Message> fixed, ModelWindow window) {
        if (enabled() && estimate(fixed) > availableInput(window)) {
            throw new MemoryException(ErrorCode.CONTEXT_BUDGET_EXCEEDED);
        }
    }

    /** Whether one more tool message with {@code result} still fits the context's input budget. */
    public boolean fitsToolResult(AssembledContext context, String result) {
        return !enabled() || context.estimatedTokens() + scale(TokenEstimator.estimate(result))
                <= context.budgetTokens();
    }

    /**
     * What the model sees instead of a result that does not fit: no part of the original, only its size and
     * {@code reference} (the tool call id or capability), under which the full text stays recorded.
     */
    public static String omittedToolResult(String reference, int tokens) {
        return "结果过长（约 " + tokens + " Token），已省略，未发送给模型；原文已完整记录（引用：" + reference
                + "）。如需具体内容，请让用户缩小查询范围或要求查看其中的具体部分。";
    }
}
