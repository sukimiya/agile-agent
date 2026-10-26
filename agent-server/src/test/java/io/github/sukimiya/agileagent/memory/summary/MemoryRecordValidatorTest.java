package io.github.sukimiya.agileagent.memory.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.agileagent.memory.ToolRecordSanitizer;
import io.github.sukimiya.agileagent.memory.summary.MemoryRecord.Entity;
import io.github.sukimiya.agileagent.memory.summary.MemoryRecord.KeyFact;
import io.github.sukimiya.agileagent.memory.summary.MemoryRecord.MemoryType;

/**
 * The {@code memory-record-v1} output check (D18/D23): schema, verbatim entities and key facts, limits, masking.
 * Only entity names and key fact texts are checked against the source; the summary and keywords are not.
 */
class MemoryRecordValidatorTest {

    static final String SOURCE = "用户：我叫王小明，车牌是 A7-3921，帮我查 2026-10-01 的违章\n助手：已查到一条违章，罚款 200 元";

    @Test
    void validRecordIsParsed() {
        var content = MemoryRecordValidator.validate("""
                {"schema":"memory-record-v1","summary":"王小明查询车牌违章","entities":[{"name":"王小明","type":"PERSON"}],
                 "key_facts":[{"text":"A7-3921","kind":"CODE"},{"text":"200 元","kind":"AMOUNT"}],
                 "decision":false,"keywords":["违章","车牌"],"memory_type":"EVENT"}""", SOURCE);

        assertThat(content.summary()).isEqualTo("王小明查询车牌违章");
        assertThat(content.entities()).containsExactly(new Entity("王小明", "PERSON"));
        assertThat(content.keyFacts()).containsExactly(new KeyFact("A7-3921", "CODE"), new KeyFact("200 元", "AMOUNT"));
        assertThat(content.decision()).isFalse();
        assertThat(content.keywords()).containsExactly("违章", "车牌");
        assertThat(content.memoryType()).isEqualTo(MemoryType.EVENT);
    }

    @Test
    void entitiesAndFactsNotFoundVerbatimAreDropped() {
        var content = MemoryRecordValidator.validate("""
                {"summary":"用户 李四 的车","entities":[{"name":"王小明","type":"PERSON"},{"name":"李四","type":"PERSON"},
                 {"name":"王 小明","type":"PERSON"}],
                 "key_facts":[{"text":"A7-3921","kind":"CODE"},{"text":"a7-3921","kind":"CODE"},{"text":"B8-0000","kind":"CODE"}],
                 "decision":true,"keywords":["交通罚单"],"memory_type":"FACT"}""", SOURCE);

        assertThat(content.entities()).extracting(Entity::name).containsExactly("王小明");
        assertThat(content.keyFacts()).extracting(KeyFact::text).containsExactly("A7-3921");
        // Not checked against the source: the summary line and the keywords.
        assertThat(content.summary()).isEqualTo("用户 李四 的车");
        assertThat(content.keywords()).containsExactly("交通罚单");
    }

    @Test
    void schemaViolationsAreInvalidOutput() {
        for (var output : new String[] { "", "not json", "[]", "{\"summary\":\"x\"}",
                "{\"summary\":\"\",\"entities\":[],\"key_facts\":[],\"decision\":false,\"keywords\":[],\"memory_type\":\"FACT\"}",
                "{\"summary\":\"x\",\"entities\":[],\"key_facts\":[],\"decision\":\"no\",\"keywords\":[],\"memory_type\":\"FACT\"}",
                "{\"summary\":\"x\",\"entities\":[],\"key_facts\":[],\"decision\":false,\"keywords\":[],\"memory_type\":\"OTHER\"}",
                "{\"summary\":\"x\",\"entities\":{},\"key_facts\":[],\"decision\":false,\"keywords\":[],\"memory_type\":\"FACT\"}" }) {
            assertThatThrownBy(() -> MemoryRecordValidator.validate(output, SOURCE)).as(output)
                    .isInstanceOf(MemoryRecordValidator.InvalidRecordException.class);
        }
    }

    @Test
    void limitsAndUnknownKindsAreNormalized() {
        var many = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            many.append(i == 0 ? "" : ",").append("\"k").append(i).append('"');
        }
        var content = MemoryRecordValidator.validate("{\"summary\":\"" + "长".repeat(200) + "\",\"entities\":"
                + "[{\"name\":\"王小明\",\"type\":\"ALIEN\"}],\"key_facts\":[{\"text\":\"A7-3921\"}],"
                + "\"decision\":false,\"keywords\":[" + many + ",\"k1\",\" \"],\"memory_type\":\"fact\"}", SOURCE);

        assertThat(content.summary()).hasSize(MemoryRecordValidator.MAX_SUMMARY_CHARS);
        assertThat(content.entities()).containsExactly(new Entity("王小明", "OTHER"));
        assertThat(content.keyFacts()).containsExactly(new KeyFact("A7-3921", "OTHER"));
        assertThat(content.keywords()).hasSize(MemoryRecordValidator.MAX_KEYWORDS).doesNotHaveDuplicates()
                .doesNotContain(" ", "");
        assertThat(content.memoryType()).isEqualTo(MemoryType.FACT);
    }

    @Test
    void secretsAreMaskedEverywhere() {
        var source = "用户：我的 api_key=sk-abcdefghijklmnopqrstuvwxyz 是什么";
        var content = MemoryRecordValidator.validate("""
                {"summary":"用户提供了 api_key=sk-abcdefghijklmnopqrstuvwxyz","entities":[],
                 "key_facts":[{"text":"api_key=sk-abcdefghijklmnopqrstuvwxyz","kind":"CODE"}],"decision":false,
                 "keywords":["Bearer abc.def.ghi"],"memory_type":"FACT"}""", source);

        var all = content.summary() + content.keyFacts() + content.keywords();
        assertThat(all).doesNotContain("sk-abcdefghijklmnopqrstuvwxyz").doesNotContain("abc.def.ghi")
                .contains(ToolRecordSanitizer.REDACTED);
    }
}
