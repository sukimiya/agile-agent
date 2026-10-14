package io.github.sukimiya.medical.validation;

import java.util.ArrayList;
import java.util.List;

import io.github.sukimiya.medical.hospital.HospitalPackageRow;
import reactor.core.publisher.Mono;

/**
 * Gate 2: row-level semantics — the scope columns must agree with scope_type, or the access
 * policy would silently over- or under-expose the package at runtime (spec §4 租户/机构 scope).
 */
public final class SemanticGate implements PackageGate {

    @Override
    public String name() {
        return "SEMANTIC";
    }

    @Override
    public Mono<List<String>> evaluate(GateContext context) {
        return Mono.fromCallable(() -> {
            HospitalPackageRow row = context.candidate();
            List<String> violations = new ArrayList<>();
            if (row.scopeType() == null) {
                violations.add("scope_type is required (GLOBAL, TENANT, or ORG)");
                return List.copyOf(violations);
            }
            switch (row.scopeType()) {
                case GLOBAL -> {
                    if (row.tenantId() != null || row.tenantName() != null
                            || row.orgId() != null || row.orgName() != null) {
                        violations.add("GLOBAL scope 要求 tenant_id/tenant_name/org_id/org_name 全部为空");
                    }
                }
                case TENANT -> {
                    if (isBlank(row.tenantId()) || isBlank(row.tenantName())) {
                        violations.add("TENANT scope requires tenant_id and tenant_name");
                    }
                    if (row.orgId() != null || row.orgName() != null) {
                        violations.add("TENANT scope requires org_id and org_name to be null");
                    }
                }
                case ORG -> {
                    if (isBlank(row.tenantId()) || isBlank(row.tenantName())
                            || isBlank(row.orgId()) || isBlank(row.orgName())) {
                        violations.add("ORG scope requires tenant_id, tenant_name, org_id, and org_name");
                    }
                }
            }
            return List.copyOf(violations);
        });
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
