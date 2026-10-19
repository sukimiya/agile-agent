package io.github.sukimiya.medical.rules;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import io.github.sukimiya.medical.validation.TextSafetyScreen;

/**
 * Deterministic rule validation (Plan 4 spec §6): strict schema via the codec, scope
 * consistency mirroring SemanticGate, provenance mirroring ProvenanceGate, action-field
 * ranges, and safety screening of user-facing script text. Ruling R2: SAFETY_PATTERN
 * `patterns` entries get regex-compilability checks only — they are data FOR the screen,
 * never input to it. Returns the violation list; empty means green.
 */
public final class MedicalRuleValidator {

    private static final List<String> KNOWN_SOURCES = List.of("import", "manual", "migration");
    private static final long MAX_VALID_FROM_AGE_DAYS = 365;

    private final MedicalRuleCodec codec;
    private final TextSafetyScreen screen;
    private final Clock clock;

    public MedicalRuleValidator(MedicalRuleCodec codec, TextSafetyScreen screen, Clock clock) {
        this.codec = codec;
        this.screen = screen;
        this.clock = clock;
    }

    public List<String> validate(MedicalRuleRow row) {
        List<String> violations = new ArrayList<>();
        var content = codec.parse(row.contentJson());
        if (content.isEmpty()) {
            violations.add("content_json 无法解析：未知字段、缺失 kind 或不支持的 kind");
            return List.copyOf(violations); // everything below needs parsed content
        }
        MedicalRuleContent parsed = content.get();
        if (parsed.schemaVersion() != MedicalRuleCodec.SCHEMA_VERSION) {
            violations.add("schemaVersion must be " + MedicalRuleCodec.SCHEMA_VERSION
                    + " but was: " + parsed.schemaVersion());
        }
        if (!parsed.kind().equals(row.ruleKind())) {
            violations.add("rule_kind does not match content kind: row=" + row.ruleKind()
                    + ", content=" + parsed.kind());
        }
        validateScope(row, violations);
        validateProvenance(row, violations);
        switch (parsed) {
            case EmergencyRuleContent emergency -> validateEmergency(emergency, violations);
            case SafetyPatternRuleContent safety -> validateSafetyPattern(safety, violations);
        }
        return List.copyOf(violations);
    }

    private static void validateScope(MedicalRuleRow row, List<String> violations) {
        if (row.scopeType() == null) {
            violations.add("scope_type is required (GLOBAL, TENANT, or HOSPITAL)");
            return;
        }
        switch (row.scopeType()) {
            case GLOBAL -> {
                if (row.tenantId() != null || row.tenantName() != null || row.orgId() != null
                        || row.orgName() != null || row.hospitalCode() != null) {
                    violations.add("GLOBAL scope 要求 tenant_id/tenant_name/org_id/org_name/"
                            + "hospital_code 全部为空");
                }
            }
            case TENANT -> {
                if (isBlank(row.tenantId()) || isBlank(row.tenantName())) {
                    violations.add("TENANT scope requires tenant_id and tenant_name");
                }
                if (row.orgId() != null || row.orgName() != null || row.hospitalCode() != null) {
                    violations.add("TENANT scope requires org_id/org_name/hospital_code "
                            + "to be null");
                }
            }
            case HOSPITAL -> {
                if (isBlank(row.tenantId()) || isBlank(row.tenantName())
                        || isBlank(row.hospitalCode())) {
                    violations.add("HOSPITAL scope requires tenant_id, tenant_name, and "
                            + "hospital_code");
                }
                if (row.orgId() != null || row.orgName() != null) {
                    violations.add("HOSPITAL scope requires org_id and org_name to be null");
                }
            }
        }
    }

    private void validateProvenance(MedicalRuleRow row, List<String> violations) {
        if (row.source() == null || !KNOWN_SOURCES.contains(row.source())) {
            violations.add("source must be one of " + KNOWN_SOURCES + " but was: " + row.source());
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (row.validUntil() == null || !row.validUntil().isAfter(now)) {
            violations.add("规则有效期已过（valid_until=" + row.validUntil() + "），不得生效");
        }
        if (row.validFrom() != null
                && row.validFrom().isBefore(now.minusDays(MAX_VALID_FROM_AGE_DAYS))) {
            violations.add("valid_from 距今超过 " + MAX_VALID_FROM_AGE_DAYS
                    + " 天，来源文档疑似陈旧，需人工复核");
        }
    }

    private void validateEmergency(EmergencyRuleContent content, List<String> violations) {
        EmergencyCondition condition = content.condition();
        EmergencyAction action = content.action();
        if (condition == null || condition.triageCategories() == null
                || condition.triageCategories().isEmpty()) {
            violations.add("condition.triageCategories 必须非空");
        } else {
            for (String category : condition.triageCategories()) {
                if (!validCategory(category)) {
                    violations.add("condition.triageCategories 含非法类别: " + category
                            + "（允许值 " + java.util.Arrays.toString(TriageCategory.values()) + "）");
                }
            }
        }
        if (condition == null || condition.diseaseNames() == null
                || condition.diseaseNames().isEmpty()) {
            violations.add("condition.diseaseNames 必须非空");
        } else if (condition.diseaseNames().stream().anyMatch(MedicalRuleValidator::isBlank)) {
            violations.add("condition.diseaseNames 含空白条目");
        }
        if (action == null) {
            violations.add("action is required");
            return;
        }
        if (action.priority() <= 0) {
            violations.add("action.priority must be positive but was: " + action.priority());
        }
        if (action.slaSeconds() < 0) {
            violations.add("action.slaSeconds must not be negative but was: "
                    + action.slaSeconds());
        }
        if (action.escalationLevels() == null || action.escalationLevels().isEmpty()) {
            violations.add("action.escalationLevels 必须非空");
        } else if (action.escalationLevels().stream()
                .anyMatch(level -> level == null || level <= 0)) {
            violations.add("action.escalationLevels 每项必须为正整数秒");
        }
        if (isBlank(action.awaitingTakeoverScript())) {
            violations.add("action.awaitingTakeoverScript 必须非空（预批准固定话术）");
        } else {
            violations.addAll(screen.screen("awaitingTakeoverScript",
                    action.awaitingTakeoverScript()));
        }
        if (isBlank(action.takenOverScript())) {
            violations.add("action.takenOverScript 必须非空（预批准固定话术）");
        } else {
            violations.addAll(screen.screen("takenOverScript", action.takenOverScript()));
        }
    }

    private void validateSafetyPattern(SafetyPatternRuleContent content,
            List<String> violations) {
        SafetyPatternCondition condition = content.condition();
        if (condition == null || condition.patterns() == null || condition.patterns().isEmpty()) {
            violations.add("condition.patterns 必须非空");
        } else {
            for (int i = 0; i < condition.patterns().size(); i++) {
                String pattern = condition.patterns().get(i);
                if (isBlank(pattern)) {
                    violations.add("patterns[" + i + "] 必须非空");
                    continue;
                }
                try {
                    java.util.regex.Pattern.compile(pattern);
                } catch (PatternSyntaxException e) {
                    // Ruling R2: compilability only — never screen pattern CONTENT.
                    violations.add("patterns[" + i + "] 非法正则: " + e.getDescription());
                }
            }
        }
        if (condition == null || isBlank(condition.fieldLabel())) {
            violations.add("condition.fieldLabel 必须非空");
        } else {
            violations.addAll(screen.screen("fieldLabel", condition.fieldLabel()));
        }
    }

    private static boolean validCategory(String category) {
        if (category == null) {
            return false;
        }
        for (TriageCategory value : TriageCategory.values()) {
            if (value.name().equals(category)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
