package io.github.sukimiya.medical;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operator configuration of the medical module. {@code medical.hospital.endpoint-allowlist}
 * is the escape hatch for hosts that bypass SSRF screening (local development against
 * 127.0.0.1, intranet test endpoints). Production deployments leave it empty — screening
 * then denies every non-public address by default (fail closed).
 */
@ConfigurationProperties(prefix = "medical.hospital")
public class MedicalHospitalProperties {

    private Set<String> endpointAllowlist = new LinkedHashSet<>();

    public Set<String> getEndpointAllowlist() {
        return endpointAllowlist;
    }

    public void setEndpointAllowlist(Set<String> endpointAllowlist) {
        this.endpointAllowlist = endpointAllowlist;
    }
}
