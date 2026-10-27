package io.github.sukimiya.agileagent.memory.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;


/**
 * In-chat forget / restore: the keyword prefilter before the model classification, numbered picks and the
 * match score.
 */
class MemoryCommandTest {

    @Test
    void prefilterLetsOnlyPossibleCommandsReachTheModel() {
        for (var text : List.of("忘记我下周三去口腔医院复诊", "那个复诊的事你就别记了", "记起来我下周三去口腔医院复诊",
                "把它恢复吧", "please forget my plate number", "把复诊那条删掉", "删除我的车牌记忆", "那条删了吧",
                "把这个抹掉", "抹去刚才说的地址", "please delete that", "erase my plate number")) {
            assertThat(MemoryCommandClassifier.prefilter(text)).as(text).isTrue();
        }
        for (var text : List.of("今天天气怎么样", "帮我查一下 A7-3921 的违章", "", "   ")) {
            assertThat(MemoryCommandClassifier.prefilter(text)).as(text).isFalse();
        }
        assertThat(MemoryCommandClassifier.prefilter(null)).isFalse();
        assertThat(MemoryCommandClassifier.prefilter("忘记" + "长".repeat(600))).as("pasted material").isFalse();
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
