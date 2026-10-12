package io.github.sukimiya.medical.hospital;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class HospitalPackageCodecTest {

    private final HospitalPackageCodec codec = new HospitalPackageCodec();

    @Test
    void roundTripsPackageContent() {
        var schema = new ObjectMapper().valueToTree(Map.of("type", "object"));
        var content = new HospitalPackageContent(1, List.of(
                new HospitalToolSpec("searchServices", "搜索服务", schema,
                        TransportBinding.api("https://api.demo.example/hospital/searchServices"))));

        var parsed = codec.parse(codec.write(content));

        assertThat(parsed).contains(content);
    }

    @Test
    void parseFailsClosedOnMalformedInput() {
        assertThat(codec.parse("{not json")).isEmpty();
        assertThat(codec.parse("")).isEmpty();
        assertThat(codec.parse(null)).isEmpty();
    }

    @Test
    void parseTreatsJsonNullLiteralAsAbsent() {
        assertThat(codec.parse("null")).isEmpty();
    }

    @Test
    void serializedBindingOmitsIrrelevantFields() {
        String json = codec.write(new HospitalPackageContent(1, List.of(
                new HospitalToolSpec("searchServices", "d", new ObjectMapper().valueToTree(Map.of("type", "object")),
                        TransportBinding.api("https://api.demo.example/x")))));

        assertThat(json).contains("\"type\":\"API\"").contains("\"url\"")
                .doesNotContain("channel").doesNotContain("address");
    }

}
