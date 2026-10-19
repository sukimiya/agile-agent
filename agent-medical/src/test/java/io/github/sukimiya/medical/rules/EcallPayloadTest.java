package io.github.sukimiya.medical.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class EcallPayloadTest {

    private static final EcallPayload PAYLOAD = new EcallPayload("global-emergency-baseline",
            "EMERGENCY", "患者自述胸痛", 100, 0, List.of(300, 900, 1800),
            "请稍候，专员即将接入。", "专员已接管本次会话。");

    @Test
    void roundTripsThroughJson() {
        assertThat(EcallPayload.fromJson(PAYLOAD.toJson())).contains(PAYLOAD);
    }

    @Test
    void junkAndUnknownFieldsParseToEmpty() {
        assertThat(EcallPayload.fromJson(null)).isEmpty();
        assertThat(EcallPayload.fromJson(" ")).isEmpty();
        assertThat(EcallPayload.fromJson("{oops")).isEmpty();
        assertThat(EcallPayload.fromJson("{\"ruleCode\":\"r\",\"unexpected\":1}")).isEmpty();
    }
}
