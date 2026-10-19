package io.github.sukimiya.medical.hospital;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnifiedToolCatalogTest {

    @Test
    void triageToolDefinitionCarriesTheStrictSchemaV2Contract() {
        var definition = UnifiedToolCatalog.triageToolDefinition();

        assertThat(definition.name()).isEqualTo("submitTriage");
        assertThat(definition.name()).isEqualTo(UnifiedToolCatalog.TRIAGE_TOOL);
        assertThat(definition.description()).contains("分诊").contains("规则引擎");
        var schema = definition.parametersSchema();
        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.at("/required/0").asText()).isEqualTo("triageCategory");
        assertThat(schema.at("/required/1").asText()).isEqualTo("symptomToDisease");
        assertThat(schema.at("/properties/triageCategory/enum").size()).isEqualTo(3);
        assertThat(schema.at("/properties/triageCategory/enum/0").asText()).isEqualTo("EMERGENCY");
        assertThat(schema.at("/properties/triageCategory/enum/1").asText()).isEqualTo("CRITICAL");
        assertThat(schema.at("/properties/triageCategory/enum/2").asText()).isEqualTo("ROUTINE");
        assertThat(schema.at("/properties/symptomToDisease/type").asText()).isEqualTo("array");
        assertThat(schema.at("/properties/symptomToDisease/items/required/0").asText())
                .isEqualTo("symptom");
        assertThat(schema.at("/properties/symptomToDisease/items/required/1").asText())
                .isEqualTo("disease");
        assertThat(schema.at("/properties/symptomToDisease/items/additionalProperties").asBoolean())
                .isFalse();
        assertThat(schema.at("/properties/notes/type").asText()).isEqualTo("string");
    }

    @Test
    void triageToolIsNotPartOfTheSchemaV1PackageToolSet() {
        // submitTriage is capability-level built-in — packages never declare it (spec §5).
        assertThat(UnifiedToolCatalog.SCHEMA_V1_TOOL_SET)
                .doesNotContain(UnifiedToolCatalog.TRIAGE_TOOL);
    }
}
