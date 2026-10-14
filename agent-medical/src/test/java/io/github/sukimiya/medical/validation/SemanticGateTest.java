package io.github.sukimiya.medical.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.sukimiya.medical.hospital.HospitalPackageContent;
import io.github.sukimiya.medical.hospital.HospitalPackageRow;

class SemanticGateTest {

    private final SemanticGate gate = new SemanticGate();

    private static HospitalPackageRow row(HospitalPackageRow.ScopeType scope,
            String tenantId, String tenantName, String orgId, String orgName) {
        return new HospitalPackageRow("pkg-1", "demo", "演示医院", "路由描述",
                HospitalPackageRow.Status.PENDING, 1, "{}", "manual",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                scope, tenantId, tenantName, orgId, orgName, "seeder", null);
    }

    private List<String> evaluate(HospitalPackageRow row) {
        return gate.evaluate(new GateContext(row, new HospitalPackageContent(1, List.of()), null))
                .block(Duration.ofSeconds(5));
    }

    @Test
    void conformingScopesPass() {
        assertThat(gate.name()).isEqualTo("SEMANTIC");
        assertThat(evaluate(row(HospitalPackageRow.ScopeType.GLOBAL, null, null, null, null))).isEmpty();
        assertThat(evaluate(row(HospitalPackageRow.ScopeType.TENANT, "t1", "租户一", null, null))).isEmpty();
        assertThat(evaluate(row(HospitalPackageRow.ScopeType.ORG, "t1", "租户一", "o1", "机构一"))).isEmpty();
    }

    @Test
    void globalScopeRejectsAnyScopeColumns() {
        assertThat(evaluate(row(HospitalPackageRow.ScopeType.GLOBAL, "t1", null, null, null)))
                .anyMatch(v -> v.contains("GLOBAL scope 要求 tenant_id/tenant_name/org_id/org_name 全部为空"));
    }

    @Test
    void tenantScopeRequiresTenantColumnsAndNoOrgColumns() {
        assertThat(evaluate(row(HospitalPackageRow.ScopeType.TENANT, null, null, null, null)))
                .anyMatch(v -> v.contains("TENANT scope requires tenant_id and tenant_name"));
        assertThat(evaluate(row(HospitalPackageRow.ScopeType.TENANT, "t1", "租户一", "o1", "机构一")))
                .anyMatch(v -> v.contains("TENANT scope requires org_id and org_name to be null"));
    }

    @Test
    void orgScopeRequiresAllFourColumns() {
        assertThat(evaluate(row(HospitalPackageRow.ScopeType.ORG, "t1", "租户一", null, null)))
                .anyMatch(v -> v.contains("ORG scope requires tenant_id, tenant_name, org_id, and org_name"));
    }

    @Test
    void nullScopeTypeRejected() {
        assertThat(evaluate(row(null, null, null, null, null)))
                .anyMatch(v -> v.contains("scope_type is required"));
    }
}
