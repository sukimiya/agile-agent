package io.github.sukimiya.medical.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.hospital.HospitalPackageContent;
import io.github.sukimiya.medical.hospital.HospitalPackageRow;
import io.github.sukimiya.medical.hospital.HospitalToolSpec;
import io.github.sukimiya.medical.hospital.TransportBinding;

class CapabilityGateTest {

    private static final IdentityContext PLATFORM_ADMIN = new IdentityContext("admin-1", "平台管理员",
            null, null, null, null, Set.of("ADMIN"), Set.of(), Set.of(), true, false);
    private static final IdentityContext USER = new IdentityContext("user-1", "用户一",
            "tenant-1", "租户一", null, null, Set.of("USER"), Set.of(), Set.of(), false, false);

    // Resolver seam: every hostname resolves to a public address, so only the URL's own
    // shape can fail screening — unit tests never touch live DNS.
    private final EndpointScreeningPolicy publicDns = new EndpointScreeningPolicy(Set.of(),
            host -> new InetAddress[]{address("93.184.216.34")});
    private final CapabilityGate gate = new CapabilityGate(publicDns);

    private static HospitalPackageRow globalRow() {
        return new HospitalPackageRow("pkg-1", "demo", "演示医院", "路由描述",
                HospitalPackageRow.Status.PENDING, 1, "{}", "manual",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                HospitalPackageRow.ScopeType.GLOBAL, null, null, null, null, "admin-1", null);
    }

    private static HospitalPackageContent content(TransportBinding binding) {
        var schema = new ObjectMapper().createObjectNode().put("type", "object");
        return new HospitalPackageContent(1, List.of(
                new HospitalToolSpec("searchServices", "搜索服务", schema, binding)));
    }

    private List<String> evaluate(HospitalPackageContent parsed, IdentityContext submitter) {
        return gate.evaluate(new GateContext(globalRow(), parsed, submitter)).block(Duration.ofSeconds(5));
    }

    @Test
    void cleanPublicEndpointPackagePasses() {
        assertThat(gate.name()).isEqualTo("CAPABILITY");
        assertThat(evaluate(content(TransportBinding.api("https://api.demo.example/x")), PLATFORM_ADMIN))
                .isEmpty();
    }

    @Test
    void unauthorizedSubmitterIsAViolation() {
        assertThat(evaluate(content(TransportBinding.api("https://api.demo.example/x")), USER))
                .anyMatch(v -> v.contains("提交者无权提交 GLOBAL 范围的能力包"));
    }

    @Test
    void internalAndMetadataEndpointsAreViolations() {
        // IP literals are screened by address even with the "public DNS" resolver seam.
        assertThat(evaluate(content(TransportBinding.api("http://169.254.169.254/latest/meta-data")),
                PLATFORM_ADMIN)).anyMatch(v -> v.contains("端点主机未通过 SSRF 筛查"));
        assertThat(evaluate(content(TransportBinding.api("http://10.0.0.5/internal")), PLATFORM_ADMIN))
                .anyMatch(v -> v.contains("SSRF 筛查"));
        assertThat(evaluate(content(TransportBinding.portal("ftp://demo.example/x")), PLATFORM_ADMIN))
                .anyMatch(v -> v.contains("SSRF 筛查"));
    }

    @Test
    void humanAndEmailBindingsAreNotEndpointScreened() {
        assertThat(evaluate(content(TransportBinding.human("hotline")), PLATFORM_ADMIN)).isEmpty();
        assertThat(evaluate(content(TransportBinding.email("desk@demo.example")), PLATFORM_ADMIN)).isEmpty();
    }

    @Test
    void unparsableContentStillChecksAuthority() {
        // parsedContent == null (upstream short-circuit); the authority half still runs.
        assertThat(evaluate(null, USER)).anyMatch(v -> v.contains("提交者无权提交"));
    }

    private static InetAddress address(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }
}
