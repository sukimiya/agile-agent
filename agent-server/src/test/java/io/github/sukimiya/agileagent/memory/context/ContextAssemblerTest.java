package io.github.sukimiya.agileagent.memory.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.memory.MemoryModel.TurnStatus;
import io.github.sukimiya.agileagent.memory.MemoryModel.TurnTicket;
import io.github.sukimiya.agileagent.memory.MemoryTestStack;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.util.NonBlockingIds;

/**
 * Recent-window assembly against the real memory stack (H2): fixed parts first, then whole completed turns from
 * newest to oldest until the history budget is used; a turn is never split, so tool_call/tool pairs stay together.
 */
class ContextAssemblerTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final ObjectMapper JSON = new ObjectMapper();

    private MemoryTestStack stack = MemoryTestStack.h2();
    private final String conversation = MemoryTestStack.newConversationId();

    @AfterEach
    void tearDown() {
        stack.close();
    }

    private ContextAssembler assembler() {
        return new ContextAssembler(stack.history(), new ContextBudget(stack.props()), stack.metrics());
    }

    /** One completed turn; returns its turn id. */
    private String completedTurn(String question, String answer) {
        var ticket = stack.accept(conversation, question).ticket();
        assertThat(stack.finalizer().finish(ticket, TurnStatus.COMPLETED, answer).block(WAIT)).isTrue();
        return ticket.turnId();
    }

    private TurnTicket currentTurn(String question) {
        return stack.accept(conversation, question).ticket();
    }

    private List<String> messageIds(String turnId) {
        return stack.rows("SELECT id FROM chat_messages WHERE turn_id = ? ORDER BY sequence", turnId).stream()
                .map(row -> (String) row.get("ID")).toList();
    }

    private static List<String> contents(AssembledContext context) {
        return context.messages().stream().map(m -> m.role() + ":" + m.content()).toList();
    }

    private AssembledContext assemble(TurnTicket ticket, ContextPurpose purpose, ModelWindow window, String prompt,
            String question) {
        return assembler().assemble(ticket, purpose, window, List.of(LlmClient.Message.system(prompt)), question)
                .block(WAIT);
    }

    @Test
    void routingContextKeepsRecentReferences() {
        completedTurn("背景一" + "甲".repeat(400), "答一" + "乙".repeat(400));
        completedTurn("背景二" + "丙".repeat(400), "答二" + "丁".repeat(400));
        var recent = completedTurn("帮我查订单 A7-3921", "订单 A7-3921 已发货");
        var ticket = currentTurn("它现在到哪了？");

        // 1,000 input tokens: prompt + question + the newest turn fit, an older turn (~1,050 budgeted) does not.
        var context = assemble(ticket, ContextPurpose.ROUTING, new ModelWindow(1_500, 500), "路由规则", "它现在到哪了？");

        assertThat(contents(context)).containsExactly("system:路由规则", "user:帮我查订单 A7-3921",
                "assistant:订单 A7-3921 已发货", "user:它现在到哪了？");
        assertThat(context.historyTruncated()).isTrue();
        assertThat(context.includedMessageIds()).containsExactlyElementsOf(messageIds(recent));
        assertThat(context.estimatedTokens()).isLessThanOrEqualTo(1_000)
                .isEqualTo(new ContextBudget(stack.props()).estimate(context.messages()));
        assertThat(context.budgetTokens()).isEqualTo(1_000);
    }

    @Test
    void oldestHistoryDroppedAsWholeTurns() throws Exception {
        var oldest = completedTurn("最早的问题", "最早的回答");
        // A turn whose question alone would still fit, but not with its long answer: dropped as a whole.
        completedTurn("中间的问题", "长".repeat(600));
        var toolTurn = completedTurn("查天气", "晴");
        // The tool exchange of toolTurn sits between its question and answer (sequence order).
        insertBeforeAnswer(toolTurn, LlmClient.Message.assistantToolCalls(
                        List.of(new LlmClient.ToolCall("call-1", "weather", "{\"city\":\"上海\"}"))),
                LlmClient.Message.toolResult("call-1", "上海 晴 20 度"));
        var ticket = currentTurn("那明天呢？");

        var context = assemble(ticket, ContextPurpose.DIRECT_ANSWER, new ModelWindow(1_000, 500), "直接回答", "那明天呢？");

        var roles = context.messages().stream().map(LlmClient.Message::role).toList();
        assertThat(roles).containsExactly("system", "user", "assistant", "tool", "assistant", "user");
        assertThat(context.messages().get(2).toolCalls()).singleElement()
                .satisfies(call -> assertThat(call.id()).isEqualTo("call-1"));
        assertThat(context.messages().get(3).toolCallId()).isEqualTo("call-1");
        assertThat(context.includedMessageIds()).containsExactlyElementsOf(messageIds(toolTurn))
                .doesNotContainAnyElementsOf(messageIds(oldest));
        assertThat(context.historyTruncated()).isTrue();
    }

    @Test
    void everythingFitsLeavesHistoryUntruncated() {
        var first = completedTurn("一", "二");
        var second = completedTurn("三", "四");
        var ticket = currentTurn("五");

        var context = assemble(ticket, ContextPurpose.ROUTING, new ModelWindow(32_768, 4_096), "p", "五");

        assertThat(contents(context)).containsExactly("system:p", "user:一", "assistant:二", "user:三", "assistant:四",
                "user:五");
        assertThat(context.historyTruncated()).isFalse();
        assertThat(context.includedMessageIds()).containsExactlyElementsOf(
                java.util.stream.Stream.concat(messageIds(first).stream(), messageIds(second).stream()).toList());
    }

    @Test
    void fixedSuffixIsChargedBeforeHistory() {
        completedTurn("旧问题", "旧".repeat(200));
        var ticket = currentTurn("汇总");
        var results = LlmClient.Message.user("各能力执行结果：\n" + "果".repeat(400));

        var context = assembler().assemble(ticket, ContextPurpose.AGGREGATE, new ModelWindow(1_200, 500),
                List.of(LlmClient.Message.system("汇总规则")), "汇总", List.of(results)).block(WAIT);

        assertThat(contents(context)).containsExactly("system:汇总规则", "user:汇总", "user:" + results.content());
        assertThat(context.historyTruncated()).isTrue();
    }

    @Test
    void disabledFlagRestoresFullHistory() {
        stack.close();
        stack = MemoryTestStack.h2(p -> p.getContext().setEnabled(false));
        completedTurn("一" + "长".repeat(2_000), "二" + "长".repeat(2_000));
        completedTurn("三", "四");
        var ticket = currentTurn("五");

        var context = assemble(ticket, ContextPurpose.ROUTING, new ModelWindow(1_000, 500), "p", "五");

        assertThat(context.messages()).hasSize(6);
        assertThat(context.historyTruncated()).isFalse();
        assertThat(context.estimatedTokens()).isGreaterThan(1_000);
    }

    @Test
    void injectedTokensMetricUsesAssembledSize() {
        completedTurn("很早" + "早".repeat(800), "很早的回答" + "早".repeat(800));
        var kept = completedTurn("最近", "最近的回答");
        var ticket = currentTurn("现在");
        var window = new ModelWindow(1_000, 500);

        assemble(ticket, ContextPurpose.ROUTING, window, "p", "现在");
        var answer = assemble(ticket, ContextPurpose.DIRECT_ANSWER, window, "p", "现在");

        // One reading per turn (the answer call), counting only the injected history, not the dropped turn.
        var messages = stack.meters().find("memory.history.injected.messages").summary();
        var tokens = stack.meters().find("memory.history.injected.tokens").summary();
        assertThat(messages.count()).isEqualTo(1);
        assertThat(messages.totalAmount()).isEqualTo(2);
        var injected = answer.messages().subList(1, 3);
        assertThat(tokens.totalAmount()).isEqualTo(TokenEstimator.estimate(injected));
        assertThat(answer.includedMessageIds()).containsExactlyElementsOf(messageIds(kept));
        assertThat(stack.meters().find("memory.context.history.truncated").tag("purpose", "DIRECT_ANSWER").counter()
                .count()).isEqualTo(1);

        // Estimate deviation: provider prompt tokens over the raw (unscaled) estimate of the call.
        assembler().recordUsage(answer, ContextPurpose.DIRECT_ANSWER, 2L * TokenEstimator.estimate(answer.messages()));
        var ratio = stack.meters().find("memory.context.estimate.ratio").tag("purpose", "DIRECT_ANSWER").summary();
        assertThat(ratio.count()).isEqualTo(1);
        assertThat(ratio.totalAmount()).isEqualTo(2.0);
    }

    /** Stores tool messages between the turn's question and its answer (the answer moves up in sequence). */
    private void insertBeforeAnswer(String turnId, LlmClient.Message... messages) throws Exception {
        var answer = stack.rows("SELECT sequence FROM chat_messages WHERE turn_id = ? AND role = 'assistant'", turnId)
                .getFirst();
        long sequence = ((Number) answer.get("SEQUENCE")).longValue();
        stack.sql("UPDATE chat_messages SET sequence = sequence + 100 WHERE turn_id = ? AND role = 'assistant'", turnId);
        for (var message : messages) {
            stack.sql("INSERT INTO chat_messages (id, tenant_id, subject, conversation_id, turn_id, sequence, role,"
                    + " source_type, payload, created_at, updated_at) VALUES (?, 'tenant-1', 'alice', ?, ?, ?, ?, ?, ?,"
                    + " CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))", NonBlockingIds.uuid(), conversation, turnId,
                    sequence++, message.role(), "tool".equals(message.role()) ? "TOOL_RESULT" : "ASSISTANT",
                    JSON.writeValueAsString(message));
        }
    }
}
