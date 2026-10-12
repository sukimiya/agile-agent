package io.github.sukimiya.agileagent.medical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.sukimiya.agileagent.PostgresTestDatabase;
import io.github.sukimiya.agileagent.agent.DomainAgent;
import io.github.sukimiya.agileagent.agent.InProcessTaskDispatcher;
import io.github.sukimiya.agileagent.agent.Orchestrator;
import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.service.ChatMemory;
import io.github.sukimiya.agileagent.service.LlmClient;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Proves the C01 claim end-to-end inside the existing orchestration stack: a HOSPITAL
 * provider built purely from a database row is discovered by the untouched
 * CapabilityRegistry, routed to by the untouched Orchestrator, and its curated tool
 * executes against the mock hospital API.
 */
class MedicalOrchestratorRoutingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void routesToTheDataDrivenHospitalCapabilityWithoutOrchestratorChanges() throws Exception {
        try (var db = PostgresTestDatabase.create("medical_routing");
             var apiServer = new MockWebServer()) {
            db.migrate();
            apiServer.start();
            apiServer.enqueue(new MockResponse().setResponseCode(200)
                    .setBody("{\"services\":[\"cardiology-consult\"]}"));
            seedApprovedApiHospital(db, apiServer.url("/api/hospital/searchServices").toString());

            var llm = new ScriptedLlmClient();
            try (var context = new AnnotationConfigApplicationContext()) {
                context.scan("io.github.sukimiya.agileagent.config");
                context.registerBean(HospitalCapabilityProvider.class, () -> provider(db));
                context.registerBean(ChatMemory.class, MedicalOrchestratorRoutingTest::emptyMemory);
                context.register(DomainAgent.class, InProcessTaskDispatcher.class, Orchestrator.class);
                context.refresh();

                assertThat(context.getBean(CapabilityRegistry.class).byCode("HOSPITAL")).isPresent();

                var answer = context.getBean(Orchestrator.class)
                        .chat("conversation-c01", "test-token", "帮我找心内科服务", identity(), llm)
                        .collectList().block(Duration.ofSeconds(30));

                assertThat(llm.routingPrompt).contains("HOSPITAL | 医院服务");
                assertThat(llm.advertisedTools).containsExactly(
                        UnifiedToolCatalog.SCHEMA_V1_TOOLS.stream()
                                .map(name -> "mockapi_" + name).toArray(String[]::new));
                assertThat(answer).contains(
                        "{\"type\":\"tool_call\",\"content\":\"mockapi_searchServices\"}\n");
                assertThat(answer).anyMatch(chunk -> chunk.endsWith("已为您查询到心内科服务\n"));
                assertThat(answer).contains("{\"type\":\"done\"}\n");
                assertThat(llm.toolResult).contains("cardiology-consult");
                assertThat(apiServer.getRequestCount()).isEqualTo(1);
                var recorded = apiServer.takeRequest(5, TimeUnit.SECONDS);
                assertThat(recorded.getMethod()).isEqualTo("POST");
                assertThat(recorded.getPath()).isEqualTo("/api/hospital/searchServices");
            }
        }
    }

    private static void seedApprovedApiHospital(PostgresTestDatabase db, String apiUrl) {
        var codec = new HospitalPackageCodec();
        ObjectNode schema = JSON.createObjectNode().put("type", "object");
        schema.set("properties", JSON.createObjectNode());
        var content = codec.write(new HospitalPackageContent(1, UnifiedToolCatalog.SCHEMA_V1_TOOLS.stream()
                .map(name -> new HospitalToolSpec(name, "描述-" + name, schema,
                        TransportBinding.api(apiUrl)))
                .toList()));
        var row = new HospitalPackageRow("pkg-mockapi", "mockapi", "模拟互联网医院",
                "模拟互联网医院路由描述", HospitalPackageRow.Status.APPROVED, 1, content, "manual",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                HospitalPackageRow.ScopeType.GLOBAL, null, null, null, null, "admin", null);
        new HospitalPackageRepository(db.databaseClient()).insert(row).block(Duration.ofSeconds(10));
    }

    private static HospitalCapabilityProvider provider(PostgresTestDatabase db) {
        return new HospitalCapabilityProvider(
                new HospitalPackageRepository(db.databaseClient()),
                new HospitalPackageAccessPolicy(),
                new HospitalPackageCodec(),
                List.of(new HttpApiTransportExecutor(WebClient.builder()),
                        new HumanTaskTransportExecutor(new MedicalHumanTaskRepository(db.databaseClient()))),
                Clock.systemDefaultZone());
    }

    private static IdentityContext identity() {
        return new IdentityContext("user-1", "用户一", "tenant-1", "租户一", "org-1", "机构一",
                Set.of("USER"), Set.of(), Set.of(), false, false);
    }

    private static ChatMemory emptyMemory() {
        var memory = mock(ChatMemory.class);
        when(memory.get(anyString())).thenReturn(Mono.just(List.of()));
        when(memory.append(anyString(), anyList())).thenReturn(Mono.empty());
        return memory;
    }

    private static final class ScriptedLlmClient implements LlmClient {
        private final AtomicInteger calls = new AtomicInteger();
        private String routingPrompt;
        private List<String> advertisedTools = List.of();
        private String toolResult;

        @Override
        public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools) {
            return switch (calls.getAndIncrement()) {
                case 0 -> {
                    routingPrompt = messages.getFirst().content();
                    yield Flux.just(new LlmEvent.Completed("[\"HOSPITAL\"]"));
                }
                case 1 -> {
                    advertisedTools = tools.stream().map(ToolDefinition::name).toList();
                    yield Flux.just(new LlmEvent.ToolCallsReady(List.of(new ToolCall(
                            "call-1", "mockapi_searchServices", "{\"keyword\":\"cardiology\"}"))));
                }
                default -> {
                    toolResult = messages.stream().filter(message -> "tool".equals(message.role()))
                            .map(Message::content).findFirst().orElse(toolResult);
                    yield Flux.just(new LlmEvent.Completed("已为您查询到心内科服务"));
                }
            };
        }
    }
}
