package io.github.sukimiya.agileagent.memory.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.MemoryMetrics;
import io.github.sukimiya.agileagent.service.LlmClient;

/** D3: the M1 heuristic, moved to {@link TokenEstimator}, with the budget safety factor applied on top. */
class TokenEstimatorTest {

    @Test
    void estimatorMatchesM1Heuristic() {
        // CJK (and full-width punctuation) one token per character, everything else four characters per token
        // rounded up, whitespace ignored.
        assertThat(TokenEstimator.estimate("你好世界")).isEqualTo(4);
        assertThat(TokenEstimator.estimate("hello world")).isEqualTo(3);
        assertThat(TokenEstimator.estimate("车牌 A7-3921，谢谢")).isEqualTo(2 + 2 + 1 + 2);
        assertThat(TokenEstimator.estimate((String) null)).isZero();
        assertThat(TokenEstimator.estimate("")).isZero();

        var messages = List.of(LlmClient.Message.system("规则"), LlmClient.Message.user("hello world"),
                LlmClient.Message.assistantToolCalls(List.of(new LlmClient.ToolCall("c1", "search", "{\"q\":\"x\"}"))),
                LlmClient.Message.toolResult("c1", "结果"));
        assertThat(TokenEstimator.estimate(messages)).isEqualTo(2 + 3 + 2 + 3 + 2);

        for (var text : List.of("你好世界", "hello world", "mixed 混合 text ２０２６", "　 \n")) {
            assertThat(MemoryMetrics.estimateTokens(text)).as(text).isEqualTo(TokenEstimator.estimate(text));
        }
        assertThat(MemoryMetrics.estimateTokens(messages)).isEqualTo(TokenEstimator.estimate(messages));
    }

    @Test
    void budgetedEstimateAppliesTheSafetyFactorRoundingUp() {
        assertThat(TokenEstimator.budgeted(0, 1.3)).isZero();
        assertThat(TokenEstimator.budgeted(10, 1.3)).isEqualTo(13);
        assertThat(TokenEstimator.budgeted(11, 1.3)).isEqualTo(15); // 14.3 rounds up
        assertThat(TokenEstimator.budgeted(1000, 1.0)).isEqualTo(1000);
    }
}
