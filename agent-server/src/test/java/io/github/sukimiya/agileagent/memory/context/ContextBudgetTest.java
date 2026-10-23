package io.github.sukimiya.agileagent.memory.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryException;
import io.github.sukimiya.agileagent.memory.MemoryException.ErrorCode;
import io.github.sukimiya.agileagent.memory.MemoryProperties;
import io.github.sukimiya.agileagent.service.LlmClient;

/** Budget arithmetic: available input = window - output reserve; fixed parts are charged first (D2, D3). */
class ContextBudgetTest {

    private final MemoryProperties props = new MemoryProperties();
    private final ContextBudget budget = new ContextBudget(props);

    @Test
    void budgetSubtractsFixedPartsAndOutputReserve() {
        var window = new ModelWindow(10_000, 2_000);
        assertThat(budget.availableInput(window)).isEqualTo(8_000);

        var fixed = List.of(LlmClient.Message.system("x".repeat(400)), LlmClient.Message.user("你".repeat(100)));
        // raw 100 + 100 = 200, x1.3 = 260
        assertThat(budget.estimate(fixed)).isEqualTo(260);
        assertThat(budget.historyBudget(window, fixed)).isEqualTo(8_000 - 260);

        assertThat(budget.historyBudget(new ModelWindow(1_200, 1_000), fixed)).as("never negative").isZero();
    }

    @Test
    void nullPresetWindowFallsBackToConservativeDefault() {
        assertThat(budget.windowFor(null, null)).isEqualTo(new ModelWindow(32_768, 4_096));
        assertThat(budget.windowFor(128_000, null)).isEqualTo(new ModelWindow(128_000, 4_096));
        assertThat(budget.windowFor(null, 8_192)).isEqualTo(new ModelWindow(32_768, 8_192));
        assertThat(budget.windowFor(64_000, 16_000)).isEqualTo(new ModelWindow(64_000, 16_000));
        // A small preset window with the default reserve would leave no input: the reserve shrinks to a quarter.
        assertThat(budget.windowFor(4_096, null)).isEqualTo(new ModelWindow(4_096, 1_024));
        assertThat(budget.defaultWindow()).isEqualTo(new ModelWindow(32_768, 4_096));

        props.getContext().setDefaultContextWindowTokens(16_000);
        props.getContext().setDefaultMaxOutputTokens(2_000);
        assertThat(budget.windowFor(null, null)).isEqualTo(new ModelWindow(16_000, 2_000));
        assertThatThrownBy(() -> new ModelWindow(1_000, 1_000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModelWindow(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oversizedFixedPartIsRejectedWithBudgetError() {
        var window = new ModelWindow(2_000, 1_000);
        budget.requireFits(List.of(LlmClient.Message.user("你".repeat(769))), window); // 769 x1.3 = 1000 (ceil)
        assertThatThrownBy(() -> budget.requireFits(List.of(LlmClient.Message.user("你".repeat(770))), window))
                .isInstanceOfSatisfying(MemoryException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.CONTEXT_BUDGET_EXCEEDED));
        assertThat(ErrorCode.CONTEXT_BUDGET_EXCEEDED.httpStatus()).isEqualTo(413);

        props.getContext().setEnabled(false);
        budget.requireFits(List.of(LlmClient.Message.user("你".repeat(100_000))), window);
    }

    @Test
    void toolResultFitsOnlyWithinTheRemainingBudget() {
        var context = new AssembledContext(List.of(LlmClient.Message.user("q")), 900, false, List.of(), 1_000);
        // a tool message costs its content: 76 CJK chars x1.3 = 99 (ceil 98.8)
        assertThat(budget.fitsToolResult(context, "你".repeat(76))).isTrue();
        assertThat(budget.fitsToolResult(context, "你".repeat(78))).isFalse();
        assertThat(ContextBudget.omittedToolResult("call-1", 5_000))
                .contains("结果过长").contains("call-1").doesNotContain("你");

        props.getContext().setEnabled(false);
        assertThat(budget.fitsToolResult(context, "你".repeat(10_000))).as("disabled: M1 behaviour").isTrue();
    }
}
