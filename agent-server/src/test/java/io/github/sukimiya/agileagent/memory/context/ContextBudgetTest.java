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
    void reserveFallbackWarnsOncePerPreset() {
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ContextBudget.class);
        logger.addAppender(appender);
        try {
            for (int i = 0; i < 3; i++) {
                assertThat(budget.windowFor("preset-a", 4_096, null)).isEqualTo(new ModelWindow(4_096, 1_024));
            }
            assertThat(warnings(appender)).isEqualTo(1);
            budget.windowFor("preset-b", 4_096, null);
            budget.windowFor("preset-b", 4_096, null);
            assertThat(warnings(appender)).isEqualTo(2);
            // The same preset changed to another configuration that still does not fit: warned again, once.
            budget.windowFor("preset-a", 2_048, 4_000);
            budget.windowFor("preset-a", 2_048, 4_000);
            assertThat(warnings(appender)).isEqualTo(3);
            // A configuration that fits never warns.
            budget.windowFor("preset-c", 64_000, 8_000);
            assertThat(warnings(appender)).isEqualTo(3);
            assertThat(appender.list).allSatisfy(e -> assertThat(e.getFormattedMessage()).contains("preset"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    private static long warnings(
            ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender) {
        return appender.list.stream().filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN).count();
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
