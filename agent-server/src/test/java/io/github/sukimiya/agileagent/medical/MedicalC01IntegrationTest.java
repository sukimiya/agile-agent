package io.github.sukimiya.agileagent.medical;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.sukimiya.agileagent.PostgresTestDatabase;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.CapabilityDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.hospital.HospitalCapabilityProvider;
import io.github.sukimiya.medical.hospital.HospitalPackageAccessPolicy;
import io.github.sukimiya.medical.hospital.HospitalPackageCodec;
import io.github.sukimiya.medical.hospital.HospitalPackageContent;
import io.github.sukimiya.medical.hospital.HospitalPackageRepository;
import io.github.sukimiya.medical.hospital.HospitalPackageRow;
import io.github.sukimiya.medical.hospital.HospitalToolSpec;
import io.github.sukimiya.medical.hospital.MedicalHumanTaskRepository;
import io.github.sukimiya.medical.hospital.TransportBinding;
import io.github.sukimiya.medical.hospital.UnifiedToolCatalog;
import io.github.sukimiya.medical.transport.HumanTaskTransportExecutor;
import io.github.sukimiya.medical.transport.HttpApiTransportExecutor;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * C01 acceptance (spec §9): two mock hospitals share one unified tool contract over
 * different transports, wired manually against a real PostgreSQL database — no Spring
 * context, no orchestrator; Task 9 proves the orchestrator half.
 */
class MedicalC01IntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HospitalPackageCodec CODEC = new HospitalPackageCodec();

    private PostgresTestDatabase db;
    private MockWebServer apiServer;
    private HospitalPackageRepository packages;
    private HospitalCapabilityProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        db = PostgresTestDatabase.create("medical_c01");
        db.migrate();
        apiServer = new MockWebServer();
        apiServer.start();
        packages = new HospitalPackageRepository(db.databaseClient());
        var tasks = new MedicalHumanTaskRepository(db.databaseClient());
        provider = new HospitalCapabilityProvider(packages, new HospitalPackageAccessPolicy(), CODEC,
                List.of(new HttpApiTransportExecutor(WebClient.builder()),
                        new HumanTaskTransportExecutor(tasks)),
                Clock.systemDefaultZone());
    }

    @AfterEach
    void tearDown() throws Exception {
        apiServer.shutdown();
        db.close();
    }

    private static JsonNode objectSchema() {
        ObjectNode schema = JSON.createObjectNode().put("type", "object");
        schema.set("properties", JSON.createObjectNode());
        return schema;
    }

    private static String content(TransportBinding binding) {
        return CODEC.write(new HospitalPackageContent(1, UnifiedToolCatalog.SCHEMA_V1_TOOLS.stream()
                .map(name -> new HospitalToolSpec(name, "描述-" + name, objectSchema(), binding))
                .toList()));
    }

    private HospitalPackageRow seedApproved(String code, String name, TransportBinding binding,
            HospitalPackageRow.ScopeType scope, String tenantId) {
        var row = new HospitalPackageRow("pkg-" + code, code, name, name + "路由描述",
                HospitalPackageRow.Status.APPROVED, 1, content(binding), "manual",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                scope, tenantId, tenantId == null ? null : "租户", null, null, "admin", null);
        packages.insert(row).block(Duration.ofSeconds(10));
        return row;
    }

    private TransportBinding apiBinding(String path) {
        return TransportBinding.api(apiServer.url(path).toString());
    }

    private static AgentExecutionContext context(String tenantId) {
        var identity = new IdentityContext("user-1", "用户一", tenantId, "租户", "org-1", "机构一",
                Set.of("USER"), Set.of(), Set.of(), false, false);
        return new AgentExecutionContext(identity, "conversation-c01", () -> "token");
    }

    private static JsonNode schemaOf(CapabilityDefinition definition, String name) {
        return definition.tools().stream().filter(tool -> tool.name().equals(name))
                .findFirst().orElseThrow().parametersSchema();
    }

    @Test
    void bothHospitalsExposeTheSameUnifiedToolContract() {
        seedApproved("mockapi", "模拟互联网医院", apiBinding("/api/hospital/unified"),
                HospitalPackageRow.ScopeType.GLOBAL, null);
        seedApproved("mockhuman", "模拟人工医院", TransportBinding.human("wechat-desk"),
                HospitalPackageRow.ScopeType.GLOBAL, null);

        var definition = provider.definition(context("tenant-1")).block(Duration.ofSeconds(10));

        assertThat(definition.tools()).hasSize(10);
        var suffixesByHospital = definition.tools().stream()
                .collect(Collectors.groupingBy(
                        tool -> tool.name().substring(0, tool.name().indexOf('_')),
                        Collectors.mapping(
                                tool -> tool.name().substring(tool.name().indexOf('_') + 1),
                                Collectors.toSet())));
        assertThat(suffixesByHospital.get("mockapi")).isEqualTo(UnifiedToolCatalog.SCHEMA_V1_TOOL_SET);
        assertThat(suffixesByHospital.get("mockhuman")).isEqualTo(UnifiedToolCatalog.SCHEMA_V1_TOOL_SET);
        for (String unified : UnifiedToolCatalog.SCHEMA_V1_TOOLS) {
            assertThat(schemaOf(definition, "mockapi_" + unified))
                    .isEqualTo(schemaOf(definition, "mockhuman_" + unified));
        }
        assertThat(definition.systemPrompt())
                .contains("mockapi | 模拟互联网医院")
                .contains("mockhuman | 模拟人工医院")
                .contains("绝不给出医疗建议");
    }

    @Test
    void apiHospitalExecutesAgainstTheCuratedEndpointOnly() throws Exception {
        seedApproved("mockapi", "模拟互联网医院", apiBinding("/api/hospital/searchServices"),
                HospitalPackageRow.ScopeType.GLOBAL, null);
        apiServer.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"services\":[\"cardiology-consult\"]}"));
        var arguments = JSON.createObjectNode()
                .put("keyword", "cardiology")
                .put("url", "http://evil.example/hijack");

        var result = provider.execute(new ToolInvocation("mockapi_searchServices", "call-1", arguments),
                context("tenant-1")).block(Duration.ofSeconds(15));

        assertThat(result.success()).isTrue();
        assertThat(result.displayResult()).contains("cardiology-consult");
        assertThat(apiServer.getRequestCount()).isEqualTo(1);
        var recorded = apiServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/api/hospital/searchServices");
        var sentBody = JSON.readTree(recorded.getBody().readUtf8());
        assertThat(sentBody.get("url").asText()).isEqualTo("http://evil.example/hijack");
        assertThat(sentBody.get("keyword").asText()).isEqualTo("cardiology");
    }

    @Test
    void humanHospitalReturnsPendingAndPersistsTaskWithoutClaimingConfirmation() {
        seedApproved("mockhuman", "模拟人工医院", TransportBinding.human("wechat-desk"),
                HospitalPackageRow.ScopeType.GLOBAL, null);

        var result = provider.execute(new ToolInvocation("mockhuman_requestConsultation", "call-1",
                        JSON.createObjectNode().put("department", "cardiology")),
                context("tenant-1")).block(Duration.ofSeconds(15));

        assertThat(result.success()).isTrue();
        assertThat(result.displayResult())
                .contains("模拟人工医院")
                .contains("尚未确认任何预约")
                .doesNotContain("已预约");
        var rows = db.jdbc().queryForList(
                "SELECT conversation_id, hospital_code, tool_name, status, created_by "
                        + "FROM medical_human_tasks");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("conversation_id", "conversation-c01")
                .containsEntry("hospital_code", "mockhuman")
                .containsEntry("tool_name", "requestConsultation")
                .containsEntry("status", "PENDING")
                .containsEntry("created_by", "user-1");
    }

    @Test
    void revokedAndExpiredPackagesDisappearFromDefinitionAndExecution() {
        var binding = apiBinding("/api/hospital/searchServices");
        var revoked = seedApproved("mockrev", "吊销医院", binding,
                HospitalPackageRow.ScopeType.GLOBAL, null);
        var expired = seedApproved("mockexp", "过期医院", binding,
                HospitalPackageRow.ScopeType.GLOBAL, null);
        packages.updateStatus(revoked.id(), HospitalPackageRow.Status.REVOKED)
                .block(Duration.ofSeconds(10));
        db.jdbc().update("UPDATE hospital_capability_packages "
                + "SET valid_until = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE id = ?", expired.id());

        var definition = provider.definition(context("tenant-1")).block(Duration.ofSeconds(10));

        assertThat(definition.tools()).isEmpty();
        assertThat(definition.systemPrompt()).doesNotContain("mockrev").doesNotContain("mockexp");

        for (String code : List.of("mockrev", "mockexp")) {
            var result = provider.execute(new ToolInvocation(code + "_searchServices", "call-1",
                            JSON.createObjectNode()), context("tenant-1"))
                    .block(Duration.ofSeconds(10));
            assertThat(result.success()).isFalse();
            assertThat(result.displayResult()).isEqualTo("医院能力包暂不可用: " + code);
        }
        assertThat(apiServer.getRequestCount()).isZero();
    }

    @Test
    void tenantScopedPackageIsVisibleOnlyToItsTenant() {
        seedApproved("mocktn", "租户医院", apiBinding("/api/hospital/searchServices"),
                HospitalPackageRow.ScopeType.TENANT, "tenant-2");

        var outsider = provider.definition(context("tenant-1")).block(Duration.ofSeconds(10));
        assertThat(outsider.tools()).isEmpty();

        var refused = provider.execute(new ToolInvocation("mocktn_searchServices", "call-1",
                        JSON.createObjectNode()), context("tenant-1"))
                .block(Duration.ofSeconds(10));
        assertThat(refused.success()).isFalse();
        assertThat(refused.displayResult()).isEqualTo("医院能力包暂不可用: mocktn");
        assertThat(apiServer.getRequestCount()).isZero();

        var insider = provider.definition(context("tenant-2")).block(Duration.ofSeconds(10));
        assertThat(insider.tools()).hasSize(5);
        assertThat(insider.systemPrompt()).contains("mocktn | 租户医院");
    }

    @Test
    void channelsWithoutExecutorsAreNotAdvertisedAndRefuseExecution() {
        seedApproved("mockmail", "邮件医院", TransportBinding.email("desk@mock.example"),
                HospitalPackageRow.ScopeType.GLOBAL, null);

        var definition = provider.definition(context("tenant-1")).block(Duration.ofSeconds(10));

        assertThat(definition.tools()).isEmpty();
        assertThat(definition.systemPrompt()).doesNotContain("mockmail");

        var result = provider.execute(new ToolInvocation("mockmail_searchServices", "call-1",
                        JSON.createObjectNode()), context("tenant-1"))
                .block(Duration.ofSeconds(10));
        assertThat(result.success()).isFalse();
        assertThat(result.displayResult()).isEqualTo("该服务通道暂未开放，请改用其他方式或联系人工客服");
    }

    @Test
    void unknownToolShapesFailSafely() {
        seedApproved("mockapi", "模拟互联网医院", apiBinding("/api/hospital/searchServices"),
                HospitalPackageRow.ScopeType.GLOBAL, null);

        for (String shape : List.of("searchServices", "mockapi_", "_searchServices",
                "mockapi_unknownTool", "nope_searchServices")) {
            var result = provider.execute(new ToolInvocation(shape, "call-x", JSON.createObjectNode()),
                    context("tenant-1")).block(Duration.ofSeconds(10));
            assertThat(result.success()).isFalse();
        }
        assertThat(apiServer.getRequestCount()).isZero();
    }
}
