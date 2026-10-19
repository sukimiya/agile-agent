package io.github.sukimiya.medical.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class MedicalRuleCodecTest {

    private static final MedicalRuleCodec CODEC = new MedicalRuleCodec();

    private static final String EMERGENCY = """
            {"schemaVersion":1,"kind":"EMERGENCY",
             "condition":{"triageCategories":["EMERGENCY","CRITICAL"],
                          "diseaseNames":["急性心肌梗死","stroke"]},
             "action":{"priority":100,"slaSeconds":0,"escalationLevels":[300,900,1800],
                       "awaitingTakeoverScript":"等待话术","takenOverScript":"接管话术"}}
            """;

    private static final String SAFETY = """
            {"schemaVersion":1,"kind":"SAFETY_PATTERN",
             "condition":{"patterns":["免费义诊"],"fieldLabel":"规则数据"},
             "action":{}}
            """;

    @Test
    void parsesEmergencyContentFieldByField() {
        var content = CODEC.parse(EMERGENCY).orElseThrow();

        assertThat(content).isInstanceOf(EmergencyRuleContent.class);
        assertThat(content.schemaVersion()).isEqualTo(1);
        assertThat(content.kind()).isEqualTo("EMERGENCY");
        var emergency = (EmergencyRuleContent) content;
        assertThat(emergency.condition().triageCategories()).containsExactly("EMERGENCY", "CRITICAL");
        assertThat(emergency.condition().diseaseNames()).containsExactly("急性心肌梗死", "stroke");
        assertThat(emergency.action().priority()).isEqualTo(100);
        assertThat(emergency.action().slaSeconds()).isZero();
        assertThat(emergency.action().escalationLevels()).containsExactly(300, 900, 1800);
        assertThat(emergency.action().awaitingTakeoverScript()).isEqualTo("等待话术");
        assertThat(emergency.action().takenOverScript()).isEqualTo("接管话术");
    }

    @Test
    void parsesSafetyPatternContentWithEmptyAction() {
        var content = CODEC.parse(SAFETY).orElseThrow();

        assertThat(content).isInstanceOf(SafetyPatternRuleContent.class);
        var safety = (SafetyPatternRuleContent) content;
        assertThat(safety.condition().patterns()).containsExactly("免费义诊");
        assertThat(safety.condition().fieldLabel()).isEqualTo("规则数据");
        assertThat(safety.action()).isEqualTo(new SafetyPatternAction());
    }

    @Test
    void rejectsUnknownFieldsUnknownKindMissingKindAndJunk() {
        // Strict parse: unknown fields are violations, not ignorable noise (spec §6).
        assertThat(CODEC.parse("""
                {"schemaVersion":1,"kind":"EMERGENCY","extra":1,
                 "condition":{"triageCategories":["EMERGENCY"],"diseaseNames":["x"]},
                 "action":{"priority":1,"slaSeconds":0,"escalationLevels":[1],
                           "awaitingTakeoverScript":"a","takenOverScript":"b"}}
                """)).isEmpty();
        assertThat(CODEC.parse("""
                {"schemaVersion":1,"kind":"SAFETY_PATTERN",
                 "condition":{"patterns":["x"],"fieldLabel":"l","surprise":true},
                 "action":{}}
                """)).isEmpty();
        assertThat(CODEC.parse("""
                {"schemaVersion":1,"kind":"WEATHER",
                 "condition":{},"action":{}}
                """)).isEmpty();
        assertThat(CODEC.parse("{\"schemaVersion\":1,\"condition\":{},\"action\":{}}")).isEmpty();
        assertThat(CODEC.parse("{ not json")).isEmpty();
        assertThat(CODEC.parse("")).isEmpty();
        assertThat(CODEC.parse(null)).isEmpty();
    }

    @Test
    void writeRoundTripsAndRejectsNull() {
        var parsed = CODEC.parse(EMERGENCY).orElseThrow();
        var roundTripped = CODEC.parse(CODEC.write(parsed)).orElseThrow();
        assertThat(roundTripped).isEqualTo(parsed);
        assertThatThrownBy(() -> CODEC.write(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sealedInterfacePermitsExactlyTwoShapes() {
        assertThat(MedicalRuleContent.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(EmergencyRuleContent.class, SafetyPatternRuleContent.class);
        assertThat(List.of(new EmergencyRuleContent(1, "EMERGENCY", null, null),
                new SafetyPatternRuleContent(1, "SAFETY_PATTERN", null, null)))
                .allSatisfy(content -> assertThat(content.schemaVersion()).isEqualTo(1));
    }
}
