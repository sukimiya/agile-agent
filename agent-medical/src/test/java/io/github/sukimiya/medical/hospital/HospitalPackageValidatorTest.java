package io.github.sukimiya.medical.hospital;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class HospitalPackageValidatorTest {

    private final HospitalPackageCodec codec = new HospitalPackageCodec();
    private final HospitalPackageValidator validator = new HospitalPackageValidator(codec);

    private static List<HospitalToolSpec> unifiedTools(Function<String, TransportBinding> bindingFor) {
        return List.of(
                new HospitalToolSpec("searchServices", "搜索可预约的科室与服务", objectSchema("keyword"),
                        bindingFor.apply("searchServices")),
                new HospitalToolSpec("checkAvailability", "查询可用时段", objectSchema("serviceId"),
                        bindingFor.apply("checkAvailability")),
                new HospitalToolSpec("requestQuotation", "请求费用报价（行政协调）", objectSchema("serviceId"),
                        bindingFor.apply("requestQuotation")),
                new HospitalToolSpec("requestConsultation", "发起咨询请求", objectSchema("question"),
                        bindingFor.apply("requestConsultation")),
                new HospitalToolSpec("submitMedicalRecords", "提交病历资料（最小化、需授权）", objectSchema("recordsRef"),
                        bindingFor.apply("submitMedicalRecords")));
    }

    private static JsonNode objectSchema(String requiredField) {
        return new ObjectMapper().valueToTree(Map.of(
                "type", "object",
                "properties", Map.of(requiredField, Map.of("type", "string")),
                "required", List.of(requiredField)));
    }

    private HospitalPackageRow row(String code, int schemaVersion, String content, String source,
            LocalDateTime validFrom, LocalDateTime validUntil) {
        return new HospitalPackageRow("pkg-1", code, "演示医院国际部", "国际部服务",
                HospitalPackageRow.Status.PENDING, schemaVersion, content, source,
                validFrom, validUntil, HospitalPackageRow.ScopeType.GLOBAL,
                null, null, null, null, "seeder", LocalDateTime.now());
    }

    private HospitalPackageRow validRow(Function<String, TransportBinding> bindingFor) {
        return row("demo", 1, codec.write(new HospitalPackageContent(1, unifiedTools(bindingFor))),
                "hospital contract 2026-09", LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30));
    }

    @Test
    void acceptsConformingApiPackage() {
        assertThat(validator.validate(validRow(
                tool -> TransportBinding.api("https://api.demo.example/hospital/" + tool)))).isEmpty();
    }

    @Test
    void acceptsConformingHumanPackage() {
        assertThat(validator.validate(validRow(
                tool -> TransportBinding.human("international-department-hotline")))).isEmpty();
    }

    @Test
    void rejectsHospitalCodesThatWouldBreakToolNameSplitting() {
        for (String code : List.of("_demo", "Demo", "de_mo", "1demo", "")) {
            var rejected = row(code, 1, codec.write(new HospitalPackageContent(1,
                    unifiedTools(t -> TransportBinding.human("hotline")))),
                    "source", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
            assertThat(validator.validate(rejected)).as("code '%s'", code)
                    .anyMatch(v -> v.contains("hospital_code"));
        }
    }

    @Test
    void rejectsMissingProvenance() {
        var noSource = row("demo", 1, codec.write(new HospitalPackageContent(1,
                unifiedTools(t -> TransportBinding.human("hotline")))),
                " ", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        assertThat(validator.validate(noSource)).anyMatch(v -> v.contains("source"));
    }

    @Test
    void rejectsInvertedOrEqualValidityWindow() {
        var inverted = row("demo", 1, codec.write(new HospitalPackageContent(1,
                unifiedTools(t -> TransportBinding.human("hotline")))),
                "source", LocalDateTime.now().plusDays(1), LocalDateTime.now());
        assertThat(validator.validate(inverted)).anyMatch(v -> v.contains("valid_until"));
    }

    @Test
    void rejectsUnknownSchemaVersion() {
        var v2 = row("demo", 2, codec.write(new HospitalPackageContent(2,
                unifiedTools(t -> TransportBinding.human("hotline")))),
                "source", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        assertThat(validator.validate(v2)).anyMatch(v -> v.contains("unsupported schema_version: 2"));
    }

    @Test
    void rejectsMalformedContent() {
        var broken = row("demo", 1, "{not json", "source",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        assertThat(validator.validate(broken)).anyMatch(v -> v.contains("content is not valid package JSON"));
    }

    @Test
    void rejectsContentSchemaVersionDisagreement() {
        var mismatch = row("demo", 1, codec.write(new HospitalPackageContent(99,
                unifiedTools(t -> TransportBinding.human("hotline")))),
                "source", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        assertThat(validator.validate(mismatch)).anyMatch(v -> v.contains("disagrees"));
    }

    @Test
    void rejectsIncompleteToolSet() {
        var partial = unifiedTools(t -> TransportBinding.human("hotline")).subList(0, 4);
        var incomplete = row("demo", 1, codec.write(new HospitalPackageContent(1, partial)),
                "source", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        assertThat(validator.validate(incomplete)).anyMatch(v -> v.contains("unified tool set"));
    }

    @Test
    void rejectsDuplicateTools() {
        var tools = new ArrayList<>(unifiedTools(t -> TransportBinding.human("hotline")));
        tools.set(1, tools.get(0));
        var duplicated = row("demo", 1, codec.write(new HospitalPackageContent(1, tools)),
                "source", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        assertThat(validator.validate(duplicated)).anyMatch(v -> v.contains("unified tool set"));
    }

    @Test
    void rejectsApiBindingWithoutAbsoluteHttpUrl() {
        for (TransportBinding binding : List.of(
                TransportBinding.api("ftp://demo.example/x"),
                TransportBinding.api("demo.example/no-scheme"),
                TransportBinding.api(" "))) {
            var bad = row("demo", 1, codec.write(new HospitalPackageContent(1, unifiedTools(t -> binding))),
                    "source", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
            assertThat(validator.validate(bad)).as("binding %s", binding)
                    .anyMatch(v -> v.contains("absolute http(s) url"));
        }
    }

    @Test
    void rejectsHumanBindingWithoutChannel() {
        var bad = row("demo", 1, codec.write(new HospitalPackageContent(1,
                unifiedTools(t -> TransportBinding.human(" ")))),
                "source", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        assertThat(validator.validate(bad)).anyMatch(v -> v.contains("HUMAN transport requires a channel"));
    }

    @Test
    void rejectsNonObjectParametersSchema() {
        var tools = new ArrayList<>(unifiedTools(t -> TransportBinding.human("hotline")));
        tools.set(0, new HospitalToolSpec("searchServices", "d",
                new ObjectMapper().valueToTree(List.of("not", "an", "object")), tools.get(0).transport()));
        var bad = row("demo", 1, codec.write(new HospitalPackageContent(1, tools)),
                "source", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        assertThat(validator.validate(bad)).anyMatch(v -> v.contains("parametersSchema must be a JSON object"));
    }

    @Test
    void rejectsEqualBoundaryValidityWindow() {
        var same = LocalDateTime.now();
        var equal = row("demo", 1, codec.write(new HospitalPackageContent(1,
                unifiedTools(t -> TransportBinding.human("hotline")))),
                "source", same, same);
        assertThat(validator.validate(equal))
                .anyMatch(v -> v.contains("valid_until must be after valid_from"));
    }

    @Test
    void rejectsNullValidityWindow() {
        var nullWindow = row("demo", 1, codec.write(new HospitalPackageContent(1,
                unifiedTools(t -> TransportBinding.human("hotline")))),
                "source", null, null);
        assertThat(validator.validate(nullWindow))
                .anyMatch(v -> v.contains("valid_from and valid_until are required"));
    }

    @Test
    void rejectsEmailBindingWithoutAddress() {
        var bad = row("demo", 1, codec.write(new HospitalPackageContent(1,
                unifiedTools(t -> TransportBinding.email(" ")))),
                "source", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        assertThat(validator.validate(bad)).anyMatch(v -> v.contains("EMAIL transport requires an address"));
    }

    @Test
    void rejectsNullTransportBinding() {
        var bad = row("demo", 1, codec.write(new HospitalPackageContent(1,
                unifiedTools(t -> null))),
                "source", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        assertThat(validator.validate(bad))
                .anyMatch(v -> v.contains("transport binding with a type is required"));
    }

    @Test
    void rejectsBlankToolDescription() {
        var tools = new ArrayList<>(unifiedTools(t -> TransportBinding.human("hotline")));
        tools.set(0, new HospitalToolSpec("searchServices", " ",
                tools.get(0).parametersSchema(), tools.get(0).transport()));
        var bad = row("demo", 1, codec.write(new HospitalPackageContent(1, tools)),
                "source", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        assertThat(validator.validate(bad)).anyMatch(v -> v.contains("description must not be blank"));
    }

    @Test
    void rejectsNullParametersSchema() {
        // I-1 regression companion: Java-null and explicit JSON null (which round-trips to
        // NullNode) must both fail schema validation, so no null-ish schema can ever reach
        // the advertising path again.
        var tools = new ArrayList<>(unifiedTools(t -> TransportBinding.human("hotline")));
        tools.set(0, new HospitalToolSpec("searchServices", "d", null, tools.get(0).transport()));
        var bad = row("demo", 1, codec.write(new HospitalPackageContent(1, tools)),
                "source", LocalDateTime.now(), LocalDateTime.now().plusDays(1));
        assertThat(validator.validate(bad))
                .anyMatch(v -> v.contains("parametersSchema must be a JSON object"));
    }
}
