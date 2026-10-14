package io.github.sukimiya.medical.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.hospital.HospitalPackageCodec;
import io.github.sukimiya.medical.hospital.HospitalPackageContent;
import io.github.sukimiya.medical.hospital.HospitalPackageRow;
import io.github.sukimiya.medical.hospital.HospitalPackageValidator;
import io.github.sukimiya.medical.hospital.HospitalToolSpec;
import io.github.sukimiya.medical.hospital.TransportBinding;
import io.github.sukimiya.medical.hospital.UnifiedToolCatalog;

class SchemaGateTest {

    private final HospitalPackageCodec codec = new HospitalPackageCodec();
    private final SchemaGate gate = new SchemaGate(new HospitalPackageValidator(codec));

    static IdentityContext submitter() {
        return new IdentityContext("admin-1", "平台管理员", null, null, null, null,
                Set.of("ADMIN"), Set.of(), Set.of(), true, false);
    }

    static HospitalPackageRow row(String content) {
        return new HospitalPackageRow("pkg-1", "demo", "演示医院国际部", "国际部服务",
                HospitalPackageRow.Status.PENDING, 1, content, "manual",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30),
                HospitalPackageRow.ScopeType.GLOBAL, null, null, null, null, "seeder", null);
    }

    static String validContent(HospitalPackageCodec codec) {
        var schema = new ObjectMapper().createObjectNode().put("type", "object");
        return codec.write(new HospitalPackageContent(1, UnifiedToolCatalog.SCHEMA_V1_TOOLS.stream()
                .map(name -> new HospitalToolSpec(name, "描述-" + name, schema,
                        TransportBinding.human("hotline")))
                .toList()));
    }

    @Test
    void conformingPackagePasses() {
        var candidate = row(validContent(codec));
        var context = new GateContext(candidate, codec.parse(candidate.content()).orElse(null), submitter());

        assertThat(gate.name()).isEqualTo("SCHEMA");
        assertThat(gate.evaluate(context).block(Duration.ofSeconds(5))).isEmpty();
    }

    @Test
    void malformedContentProducesSchemaViolations() {
        var candidate = row("{not json");
        var context = new GateContext(candidate, null, submitter());

        assertThat(gate.evaluate(context).block(Duration.ofSeconds(5)))
                .anyMatch(v -> v.contains("content is not valid package JSON"));
    }
}
