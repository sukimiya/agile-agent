package io.github.sukimiya.medical.hospital;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.medical.transport.TransportExecutor;
import io.github.sukimiya.medical.transport.TransportRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class HospitalCapabilityProviderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 10, 0);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HospitalPackageCodec CODEC = new HospitalPackageCodec();
    private static final TransportBinding API_BINDING =
            TransportBinding.api("https://api.mock.example/api/hospital/unified");
    private static final TransportBinding HUMAN_BINDING = TransportBinding.human("wechat-desk");
    private static final TransportBinding EMAIL_BINDING = TransportBinding.email("desk@mock.example");

    private final HospitalPackageRepository packages = mock(HospitalPackageRepository.class);
    private final RecordingExecutor apiExecutor = new RecordingExecutor(TransportType.API);
    private final RecordingExecutor humanExecutor = new RecordingExecutor(TransportType.HUMAN);
    private final AtomicInteger tokenReads = new AtomicInteger();
    private final HospitalCapabilityProvider provider = new HospitalCapabilityProvider(
            packages, new HospitalPackageAccessPolicy(), CODEC,
            List.of(apiExecutor, humanExecutor), FIXED_CLOCK);

    private static final class RecordingExecutor implements TransportExecutor {
        private final TransportType type;
        private final List<TransportRequest> requests = new ArrayList<>();

        RecordingExecutor(TransportType type) {
            this.type = type;
        }

        @Override
        public TransportType type() {
            return type;
        }

        @Override
        public Mono<ToolResult> execute(TransportRequest request) {
            requests.add(request);
            return Mono.just(new ToolResult(true, "recorded:" + request.toolName()));
        }
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

    private static HospitalPackageRow row(String code, String name, HospitalPackageRow.Status status,
            HospitalPackageRow.ScopeType scope, String tenantId,
            LocalDateTime validFrom, LocalDateTime validUntil, String contentJson) {
        return new HospitalPackageRow("pkg-" + code, code, name, name + "路由描述", status, 1, contentJson,
                "manual", validFrom, validUntil, scope, tenantId, tenantId == null ? null : "租户",
                null, null, "admin", LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    private static HospitalPackageRow approvedRow(String code, String name, TransportBinding binding) {
        return row(code, name, HospitalPackageRow.Status.APPROVED, HospitalPackageRow.ScopeType.GLOBAL,
                null, NOW.minusDays(1), NOW.plusDays(1), content(binding));
    }

    private static IdentityContext identity(String tenantId) {
        return new IdentityContext("user-1", "用户一", tenantId, "租户一", "org-1", "机构一",
                Set.of("USER"), Set.of(), Set.of("org-1"), false, false);
    }

    private AgentExecutionContext context(String tenantId) {
        return new AgentExecutionContext(identity(tenantId), "conv-1",
                () -> {
                    tokenReads.incrementAndGet();
                    return "token";
                });
    }

    private void seed(HospitalPackageRow... rows) {
        when(packages.findApproved()).thenReturn(Flux.fromArray(rows)
                .filter(r -> r.status() == HospitalPackageRow.Status.APPROVED));
        when(packages.findByHospitalCode(anyString())).thenAnswer(invocation ->
                Flux.fromArray(rows)
                        .filter(r -> r.hospitalCode().equals(invocation.getArgument(0, String.class)))
                        .next());
    }

    @Test
    void definitionAssemblesTheUnifiedContractAcrossHospitals() {
        seed(approvedRow("mockapi", "模拟互联网医院", API_BINDING),
                approvedRow("mockhuman", "模拟人工医院", HUMAN_BINDING));

        var definition = provider.definition(context("tenant-1")).block(Duration.ofSeconds(10));

        assertThat(definition.tools()).hasSize(10);
        assertThat(definition.tools()).extracting(t -> t.name())
                .containsExactlyInAnyOrder(
                        "mockapi_searchServices", "mockapi_checkAvailability", "mockapi_requestQuotation",
                        "mockapi_requestConsultation", "mockapi_submitMedicalRecords",
                        "mockhuman_searchServices", "mockhuman_checkAvailability", "mockhuman_requestQuotation",
                        "mockhuman_requestConsultation", "mockhuman_submitMedicalRecords");
        for (String unified : UnifiedToolCatalog.SCHEMA_V1_TOOLS) {
            var apiSchema = definition.tools().stream()
                    .filter(t -> t.name().equals("mockapi_" + unified)).findFirst().orElseThrow()
                    .parametersSchema();
            var humanSchema = definition.tools().stream()
                    .filter(t -> t.name().equals("mockhuman_" + unified)).findFirst().orElseThrow()
                    .parametersSchema();
            assertThat(apiSchema).isEqualTo(humanSchema);
        }
        assertThat(definition.systemPrompt())
                .contains("mockapi | 模拟互联网医院")
                .contains("mockhuman | 模拟人工医院")
                .contains("绝不给出医疗建议")
                .contains("在医院确认之前")
                .contains("医院代码_工具名");
        assertThat(definition.systemPrompt().indexOf("mockapi"))
                .isLessThan(definition.systemPrompt().indexOf("mockhuman"));
    }

    @Test
    void channelsWithoutExecutorsAreNotAdvertised() {
        seed(approvedRow("mockmail", "模拟邮件医院", EMAIL_BINDING));

        var definition = provider.definition(context("tenant-1")).block(Duration.ofSeconds(10));

        assertThat(definition.tools()).isEmpty();
        assertThat(definition.systemPrompt()).doesNotContain("mockmail");
    }

    @Test
    void definitionHidesNonApprovedOutOfWindowAndForeignScopePackages() {
        seed(approvedRow("mockapi", "模拟互联网医院", API_BINDING),
                row("mockpend", "待定医院", HospitalPackageRow.Status.PENDING,
                        HospitalPackageRow.ScopeType.GLOBAL, null,
                        NOW.minusDays(1), NOW.plusDays(1), content(API_BINDING)),
                row("mockrev", "吊销医院", HospitalPackageRow.Status.REVOKED,
                        HospitalPackageRow.ScopeType.GLOBAL, null,
                        NOW.minusDays(1), NOW.plusDays(1), content(API_BINDING)),
                row("mockexp", "过期医院", HospitalPackageRow.Status.APPROVED,
                        HospitalPackageRow.ScopeType.GLOBAL, null,
                        NOW.minusDays(2), NOW.minusSeconds(1), content(API_BINDING)),
                row("mockfut", "未生效医院", HospitalPackageRow.Status.APPROVED,
                        HospitalPackageRow.ScopeType.GLOBAL, null,
                        NOW.plusSeconds(1), NOW.plusDays(2), content(API_BINDING)),
                row("mocktn", "他租户医院", HospitalPackageRow.Status.APPROVED,
                        HospitalPackageRow.ScopeType.TENANT, "tenant-2",
                        NOW.minusDays(1), NOW.plusDays(1), content(API_BINDING)));

        var definition = provider.definition(context("tenant-1")).block(Duration.ofSeconds(10));

        assertThat(definition.tools()).hasSize(5);
        assertThat(definition.tools()).extracting(t -> t.name())
                .allSatisfy(name -> assertThat(name).startsWith("mockapi_"));
        assertThat(definition.systemPrompt())
                .doesNotContain("mockpend").doesNotContain("mockrev").doesNotContain("mockexp")
                .doesNotContain("mockfut").doesNotContain("mocktn");
    }

    @Test
    void validityWindowIsInclusiveOnBothEnds() {
        var fromNow = row("edgea", "边界医院A", HospitalPackageRow.Status.APPROVED,
                HospitalPackageRow.ScopeType.GLOBAL, null, NOW, NOW.plusDays(1), content(API_BINDING));
        var untilNow = row("edgeb", "边界医院B", HospitalPackageRow.Status.APPROVED,
                HospitalPackageRow.ScopeType.GLOBAL, null, NOW.minusDays(1), NOW, content(API_BINDING));
        seed(fromNow, untilNow);

        var definition = provider.definition(context("tenant-1")).block(Duration.ofSeconds(10));

        assertThat(definition.tools()).hasSize(10);
        assertThat(definition.tools()).extracting(t -> t.name())
                .anyMatch(name -> name.startsWith("edgea_"))
                .anyMatch(name -> name.startsWith("edgeb_"));
    }

    @Test
    void unparsableContentSkipsTheHospitalFailClosed() {
        var broken = row("mockbad", "坏数据医院", HospitalPackageRow.Status.APPROVED,
                HospitalPackageRow.ScopeType.GLOBAL, null,
                NOW.minusDays(1), NOW.plusDays(1), "{not json");
        seed(broken, approvedRow("mockapi", "模拟互联网医院", API_BINDING));

        var definition = provider.definition(context("tenant-1")).block(Duration.ofSeconds(10));

        assertThat(definition.tools()).hasSize(5);
        assertThat(definition.systemPrompt()).doesNotContain("mockbad").contains("mockapi");
    }

    @Test
    void executeRoutesToTheMatchingExecutorWithTheCuratedRequest() {
        seed(approvedRow("mockapi", "模拟互联网医院", API_BINDING));
        var arguments = JSON.createObjectNode().put("keyword", "cardiology");
        var context = context("tenant-1");

        var result = provider.execute(new ToolInvocation("mockapi_searchServices", "call-1", arguments), context)
                .block(Duration.ofSeconds(10));

        assertThat(result.success()).isTrue();
        assertThat(result.displayResult()).isEqualTo("recorded:searchServices");
        assertThat(apiExecutor.requests).hasSize(1);
        assertThat(humanExecutor.requests).isEmpty();
        var sent = apiExecutor.requests.get(0);
        assertThat(sent.hospitalCode()).isEqualTo("mockapi");
        assertThat(sent.hospitalName()).isEqualTo("模拟互联网医院");
        assertThat(sent.toolName()).isEqualTo("searchServices");
        assertThat(sent.binding()).isEqualTo(API_BINDING);
        assertThat(sent.arguments().get("keyword").asText()).isEqualTo("cardiology");
        assertThat(sent.context()).isEqualTo(context);
    }

    @Test
    void executeRejectsUnknownToolShapesWithoutTouchingExecutors() {
        seed(approvedRow("mockapi", "模拟互联网医院", API_BINDING));
        var context = context("tenant-1");
        var arguments = JSON.createObjectNode();

        for (String shape : List.of("searchServices", "mockapi_", "_searchServices",
                "mockapi_unknownTool", "mockapi_search Services")) {
            var result = provider.execute(new ToolInvocation(shape, "call-x", arguments), context)
                    .block(Duration.ofSeconds(10));
            assertThat(result.success()).isFalse();
            assertThat(result.displayResult()).isEqualTo("unknown tool: " + shape);
        }
        assertThat(apiExecutor.requests).isEmpty();
        assertThat(humanExecutor.requests).isEmpty();
        assertThat(tokenReads).hasValue(0);
    }

    @Test
    void executeRefusesWhenPackageMissingOrGateClosed() {
        var expired = row("mockexp", "过期医院", HospitalPackageRow.Status.APPROVED,
                HospitalPackageRow.ScopeType.GLOBAL, null,
                NOW.minusDays(2), NOW.minusSeconds(1), content(API_BINDING));
        var pending = row("mockpend", "待定医院", HospitalPackageRow.Status.PENDING,
                HospitalPackageRow.ScopeType.GLOBAL, null,
                NOW.minusDays(1), NOW.plusDays(1), content(API_BINDING));
        var foreign = row("mocktn", "他租户医院", HospitalPackageRow.Status.APPROVED,
                HospitalPackageRow.ScopeType.TENANT, "tenant-2",
                NOW.minusDays(1), NOW.plusDays(1), content(API_BINDING));
        seed(expired, pending, foreign);
        var context = context("tenant-1");
        var arguments = JSON.createObjectNode();

        var missing = provider.execute(
                new ToolInvocation("nope_searchServices", "call-1", arguments), context)
                .block(Duration.ofSeconds(10));
        assertThat(missing.success()).isFalse();
        assertThat(missing.displayResult()).isEqualTo("医院能力不可用: nope");

        for (String code : List.of("mockexp", "mockpend", "mocktn")) {
            var result = provider.execute(
                    new ToolInvocation(code + "_searchServices", "call-1", arguments), context)
                    .block(Duration.ofSeconds(10));
            assertThat(result.success()).isFalse();
            assertThat(result.displayResult()).isEqualTo("医院能力包暂不可用: " + code);
        }
        assertThat(apiExecutor.requests).isEmpty();
        assertThat(tokenReads).hasValue(0);
    }

    @Test
    void executeReportsUnimplementedChannelInsteadOfGuessing() {
        seed(approvedRow("mockmail", "模拟邮件医院", EMAIL_BINDING));

        var result = provider.execute(
                new ToolInvocation("mockmail_searchServices", "call-1", JSON.createObjectNode()),
                context("tenant-1")).block(Duration.ofSeconds(10));

        assertThat(result.success()).isFalse();
        assertThat(result.displayResult()).isEqualTo("该服务通道暂未开放，请改用其他方式或联系人工客服");
        assertThat(apiExecutor.requests).isEmpty();
        assertThat(humanExecutor.requests).isEmpty();
    }

    @Test
    void providerNeverReadsTheAccessToken() {
        seed(approvedRow("mockapi", "模拟互联网医院", API_BINDING));
        var context = context("tenant-1");

        provider.definition(context).block(Duration.ofSeconds(10));
        provider.execute(new ToolInvocation("mockapi_searchServices", "call-1", JSON.createObjectNode()),
                context).block(Duration.ofSeconds(10));

        assertThat(tokenReads).hasValue(0);
        assertThat(apiExecutor.requests).hasSize(1);
    }
}
