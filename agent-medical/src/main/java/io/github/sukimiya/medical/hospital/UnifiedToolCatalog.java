package io.github.sukimiya.medical.hospital;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.spi.capability.ToolDefinition;

/**
 * The schema v1 unified tool set (spec §11 resolution 4). Every v1 package must declare
 * exactly these five tools. Fine-grained tools extend the catalog in a later schema version.
 *
 * <p>Plan 4 (spec §5) adds the capability-level built-in tool submitTriage: not bound to a
 * hospital, never declared by package data, never routed through transport executors. The
 * provider advertises it in every non-restricted definition and routes its execution to the
 * deterministic RuleEngine — the engine trusts only this structured signal (ruling 3: no
 * keyword scanning of free-text tool arguments anywhere else).
 */
public final class UnifiedToolCatalog {

    public static final List<String> SCHEMA_V1_TOOLS = List.of(
            "searchServices", "checkAvailability", "requestQuotation",
            "requestConsultation", "submitMedicalRecords");

    public static final Set<String> SCHEMA_V1_TOOL_SET = Set.copyOf(SCHEMA_V1_TOOLS);

    public static final String TRIAGE_TOOL = "submitTriage";

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final ToolDefinition TRIAGE_DEFINITION = new ToolDefinition(TRIAGE_TOOL,
            "结构化分诊提交：行政协调涉及病情语义（症状、疾病名、紧急程度）时必须先调用本工具。"
                    + "triageCategory 为分诊类别，symptomToDisease 为症状到病名映射，"
                    + "notes 为可选备注（仅入审计）。是否紧急由平台规则引擎判定，你无权自行判断。",
            triageSchema());

    private UnifiedToolCatalog() {
    }

    public static ToolDefinition triageToolDefinition() {
        return TRIAGE_DEFINITION;
    }

    /** Strict schema v2 (spec §5): unknown fields rejected, enum categories curated in code. */
    private static JsonNode triageSchema() {
        try {
            return JSON.readTree("""
                    {
                      "type": "object",
                      "properties": {
                        "triageCategory": {
                          "type": "string",
                          "enum": ["EMERGENCY", "CRITICAL", "ROUTINE"],
                          "description": "分诊类别：EMERGENCY 急诊、CRITICAL 重症、ROUTINE 普通"
                        },
                        "symptomToDisease": {
                          "type": "array",
                          "description": "症状到病名映射",
                          "items": {
                            "type": "object",
                            "properties": {
                              "symptom": {"type": "string", "description": "症状"},
                              "disease": {"type": "string", "description": "病名"}
                            },
                            "required": ["symptom", "disease"],
                            "additionalProperties": false
                          }
                        },
                        "notes": {
                          "type": "string",
                          "description": "可选自由文本备注——仅入审计事件，绝不参与命中判定"
                        }
                      },
                      "required": ["triageCategory", "symptomToDisease"],
                      "additionalProperties": false
                    }
                    """);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("submitTriage schema is malformed", e);
        }
    }
}
