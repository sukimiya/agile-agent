package io.github.sukimiya.medical.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class TriageSubmissionTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void parsesAFullSubmission() throws Exception {
        var parsed = TriageSubmission.parse(JSON.readTree("""
                {"triageCategory":"EMERGENCY",
                 "symptomToDisease":[{"symptom":"胸痛","disease":"急性心肌梗死"}],
                 "notes":"患者自述持续胸痛"}
                """));

        assertThat(parsed).isPresent();
        assertThat(parsed.get().triageCategory()).isEqualTo(TriageCategory.EMERGENCY);
        assertThat(parsed.get().symptomToDisease())
                .containsExactly(new SymptomDisease("胸痛", "急性心肌梗死"));
        assertThat(parsed.get().notes()).isEqualTo("患者自述持续胸痛");
    }

    @Test
    void notesAreOptionalAndMayBeNull() throws Exception {
        assertThat(TriageSubmission.parse(JSON.readTree(
                "{\"triageCategory\":\"ROUTINE\",\"symptomToDisease\":[]}"))).isPresent();
        assertThat(TriageSubmission.parse(JSON.readTree(
                "{\"triageCategory\":\"ROUTINE\",\"symptomToDisease\":[],\"notes\":null}")))
                .get().extracting(TriageSubmission::notes).isNull();
    }

    @Test
    void strictOnUnknownOrMissingOrMalformed() throws Exception {
        assertThat(TriageSubmission.parse(JSON.readTree(
                "{\"triageCategory\":\"EMERGENCY\",\"symptomToDisease\":[],\"extra\":1}")))
                .isEmpty();                                        // unknown top-level field
        assertThat(TriageSubmission.parse(JSON.readTree(
                "{\"triageCategory\":\"EMERGENCY\",\"symptomToDisease\":[{\"symptom\":\"a\","
                        + "\"disease\":\"b\",\"extra\":true}]}"))).isEmpty(); // unknown pair field
        assertThat(TriageSubmission.parse(JSON.readTree(
                "{\"symptomToDisease\":[]}"))).isEmpty();          // missing category
        assertThat(TriageSubmission.parse(JSON.readTree(
                "{\"triageCategory\":\"URGENT\",\"symptomToDisease\":[]}"))).isEmpty(); // bad enum
        assertThat(TriageSubmission.parse(JSON.readTree(
                "{\"triageCategory\":\"EMERGENCY\"}"))).isEmpty(); // missing pairs
        assertThat(TriageSubmission.parse(JSON.readTree(
                "{\"triageCategory\":\"EMERGENCY\",\"symptomToDisease\":{\"a\":1}}")))
                .isEmpty();                                        // pairs not an array
        assertThat(TriageSubmission.parse(JSON.readTree(
                "{\"triageCategory\":\"EMERGENCY\",\"symptomToDisease\":[{\"symptom\":\"a\"}]}")))
                .isEmpty();                                        // pair missing disease
        assertThat(TriageSubmission.parse(JSON.readTree(
                "{\"triageCategory\":42,\"symptomToDisease\":[]}"))).isEmpty(); // non-textual
        assertThat(TriageSubmission.parse(null)).isEmpty();
        assertThat(TriageSubmission.parse(JSON.readTree("[1,2]"))).isEmpty(); // not an object
    }
}
