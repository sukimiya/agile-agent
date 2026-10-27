package io.github.sukimiya.agileagent.memory.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.command.MemoryCommand.Kind;

/**
 * In-chat forget / restore (M2b T5 follow-up): a deterministic rule on the user's own current input. A forget
 * phrase that is not the instruction itself (a statement, a question, quoted text, a document) is not a command.
 */
class MemoryCommandTest {

    @Test
    void forgetInstructionsAreRecognisedWithTheirTopic() {
        for (var text : List.of("忘记我下周三去口腔医院复诊", "请忘记我下周三去口腔医院复诊。", "帮我忘掉我下周三去口腔医院复诊吧",
                "把我下周三去口腔医院复诊忘掉", "把我下周三去口腔医院复诊这件事忘了吧！", "  忘了我下周三去口腔医院复诊 ",
                "不要再记着我下周三去口腔医院复诊了")) {
            assertThat(MemoryCommand.parse(text)).as(text).hasValueSatisfying(c -> {
                assertThat(c.kind()).isEqualTo(Kind.FORGET);
                assertThat(c.topic()).isEqualTo("下周三去口腔医院复诊");
            });
        }
    }

    @Test
    void restoreInstructionsAreRecognisedWithTheirTopic() {
        for (var text : List.of("记起来我下周三去口腔医院复诊", "请记起来：我下周三去口腔医院复诊", "把我下周三去口腔医院复诊记起来",
                "重新记住我下周三去口腔医院复诊吧")) {
            assertThat(MemoryCommand.parse(text)).as(text).hasValueSatisfying(c -> {
                assertThat(c.kind()).isEqualTo(Kind.RESTORE);
                assertThat(c.topic()).isEqualTo("下周三去口腔医院复诊");
            });
        }
    }

    @Test
    void statementsQuestionsAndEmbeddedTextAreNotCommands() {
        for (var text : List.of("我忘了带钥匙", "你忘记了吗？", "忘记", "请忘记", "忘了吗", "忘记了什么？",
                "这份文档里写着：忘记我下周三去口腔医院复诊", "网页上说“忘记我下周三去口腔医院复诊”", "今天天气怎么样",
                "记起来了吗？", "我记起来了")) {
            assertThat(MemoryCommand.parse(text)).as(text).isEmpty();
        }
        assertThat(MemoryCommand.parse(null)).isEmpty();
    }

    @Test
    void selectionRepliesPickNumbersOrAll() {
        assertThat(MemoryCommand.selection("2", 3)).hasValue(List.of(2));
        assertThat(MemoryCommand.selection(" 第1条、3 ", 3)).hasValue(List.of(1, 3));
        assertThat(MemoryCommand.selection("1,2，3", 3)).hasValue(List.of(1, 2, 3));
        assertThat(MemoryCommand.selection("全部", 3)).hasValue(List.of(1, 2, 3));
        assertThat(MemoryCommand.selection("都忘掉吧", 2)).hasValue(List.of(1, 2));
        assertThat(MemoryCommand.selection("4", 3)).as("out of range").isEmpty();
        assertThat(MemoryCommand.selection("0", 3)).isEmpty();
        assertThat(MemoryCommand.selection("今天天气怎么样", 3)).isEmpty();
        assertThat(MemoryCommand.selection("我有 2 个问题", 3)).isEmpty();
    }

    @Test
    void matchScoreIsTheShareOfTopicTermsInTheItem() {
        var topic = "下周三去口腔医院复诊";
        assertThat(MemoryCommand.score(topic, "用户下周三要去口腔医院复诊")).isGreaterThanOrEqualTo(0.5);
        assertThat(MemoryCommand.score(topic, "用户下周三要开会")).isLessThan(0.5);
        assertThat(MemoryCommand.score("车牌 A7-3921", "用户的车牌是 A7-3921")).isEqualTo(1.0);
        assertThat(MemoryCommand.score("", "任何内容")).isZero();
    }
}
