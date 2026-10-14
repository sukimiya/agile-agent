package io.github.sukimiya.medical.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.hospital.HospitalPackageAccessPolicy;
import io.github.sukimiya.medical.hospital.HospitalPackageCodec;
import io.github.sukimiya.medical.hospital.HospitalPackageContent;
import io.github.sukimiya.medical.hospital.HospitalPackageRow;
import io.github.sukimiya.medical.hospital.HospitalToolSpec;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRepository;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRow;
import io.github.sukimiya.medical.hospital.TransportBinding;
import io.github.sukimiya.medical.hospital.UnifiedToolCatalog;
import io.github.sukimiya.medical.transport.HumanTaskTransportExecutor;
import io.github.sukimiya.medical.transport.HttpApiTransportExecutor;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import reactor.core.publisher.Mono;

class SimulationGateTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HospitalPackageCodec CODEC = new HospitalPackageCodec();

    private MockWebServer apiServer;
    private MedicalHumanTaskRepository tasks;
    private SimulationGate gate;

    @BeforeEach
    void setUp() throws Exception {
        apiServer = new MockWebServer();
        // Always-200 dispatcher: the gate fires many probes; queued responses would run dry.
        apiServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(200).setBody("{\"ok\":true}");
            }
        });
        apiServer.start();
        tasks = mock(MedicalHumanTaskRepository.class);
        when(tasks.insertOrFindExisting(any(), any())).thenAnswer(invocation ->
                Mono.just(invocation.getArgument(0, MedicalHumanTaskRow.class)));
        when(tasks.countByConversationId(anyString())).thenReturn(Mono.just(1L));
        when(tasks.deleteByConversationId(anyString())).thenReturn(Mono.just(1L));
        gate = new SimulationGate(new HospitalPackageAccessPolicy(), CODEC,
                List.of(new HttpApiTransportExecutor(WebClient.builder(),
                                new EndpointScreeningPolicy(Set.of("127.0.0.1", "localhost"))),
                        new HumanTaskTransportExecutor(tasks)),
                tasks);
    }

    @AfterEach
    void tearDown() throws Exception {
        apiServer.shutdown();
    }

    private static IdentityContext submitter() {
        return new IdentityContext("admin-1", "管理员一", null, null, null, null,
                Set.of("PLATFORM_ADMIN"), Set.of(), Set.of(), true, false);
    }

    private static ObjectNode objectSchema() {
        ObjectNode schema = JSON.createObjectNode().put("type", "object");
        schema.set("properties", JSON.createObjectNode());
        return schema;
    }

    private static HospitalPackageRow candidate(String code, TransportBinding binding,
            HospitalPackageRow.ScopeType scope, String tenantId, String tenantName) {
        String content = CODEC.write(new HospitalPackageContent(1, UnifiedToolCatalog.SCHEMA_V1_TOOLS
                .stream()
                .map(name -> new HospitalToolSpec(name, "描述-" + name, objectSchema(), binding))
                .toList()));
        boolean orgScoped = scope == HospitalPackageRow.ScopeType.ORG;
        return new HospitalPackageRow("pkg-" + code, code, "模拟医院", "模拟路由描述",
                HospitalPackageRow.Status.PENDING, 1, content, "manual",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                scope, tenantId, tenantName,
                orgScoped ? "org-1" : null, orgScoped ? "机构一" : null,
                "admin-1", null);
    }

    private List<String> evaluate(HospitalPackageRow row) {
        var content = CODEC.parse(row.content()).orElseThrow();
        return gate.evaluate(new GateContext(row, content, submitter()))
                .block(Duration.ofSeconds(20));
    }

    @Test
    void greenApiPackagePassesAllProbes() {
        var row = candidate("simapi",
                TransportBinding.api(apiServer.url("/api/hospital/unified").toString()),
                HospitalPackageRow.ScopeType.GLOBAL, null, null);

        var violations = evaluate(row);

        assertThat(violations).isEmpty();
        assertThat(apiServer.getRequestCount()).isGreaterThan(0);
    }

    @Test
    void humanPackageDeduplicatesAndCleansUp() {
        var row = candidate("simhuman", TransportBinding.human("wechat-desk"),
                HospitalPackageRow.ScopeType.GLOBAL, null, null);

        var violations = evaluate(row);

        assertThat(violations).isEmpty();
        verify(tasks).countByConversationId(startsWith("c02-simulation-"));
        verify(tasks).deleteByConversationId(startsWith("c02-simulation-"));
    }

    @Test
    void duplicateProbeViolationStillCleansUp() {
        when(tasks.countByConversationId(anyString())).thenReturn(Mono.just(2L));
        var row = candidate("simhuman", TransportBinding.human("wechat-desk"),
                HospitalPackageRow.ScopeType.GLOBAL, null, null);

        var violations = evaluate(row);

        assertThat(violations).anyMatch(violation -> violation.startsWith("重复提交探针"));
        verify(tasks).deleteByConversationId(startsWith("c02-simulation-"));
    }

    @Test
    void emailOnlyPackageHasNoExecutableTransport() {
        var row = candidate("simmail", TransportBinding.email("desk@mock.example"),
                HospitalPackageRow.ScopeType.GLOBAL, null, null);

        var violations = evaluate(row);

        assertThat(violations).anyMatch(violation -> violation.startsWith("无可用传输通道"));
    }

    @Test
    void tenantScopedPackageSurvivesCrossTenantProbe() {
        var row = candidate("simtn",
                TransportBinding.api(apiServer.url("/api/hospital/unified").toString()),
                HospitalPackageRow.ScopeType.TENANT, "tenant-9", "租户九");

        var violations = evaluate(row);

        assertThat(violations).isEmpty();
    }
}
