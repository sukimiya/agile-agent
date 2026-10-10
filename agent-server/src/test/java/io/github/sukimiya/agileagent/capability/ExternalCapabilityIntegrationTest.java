package io.github.sukimiya.agileagent.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.sukimiya.agileagent.agent.DomainAgent;
import io.github.sukimiya.agileagent.agent.InProcessTaskDispatcher;
import io.github.sukimiya.agileagent.agent.Orchestrator;
import io.github.sukimiya.agileagent.capability.fixture.ExternalFixtureCapabilityProvider;
import io.github.sukimiya.agileagent.service.ChatMemory;
import io.github.sukimiya.agileagent.service.LlmClient;
import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ExternalCapabilityIntegrationTest {

    @Test
    void discoversExternalSpringProviderRoutesAndExecutesItsValidatedTool() {
        var llm = new ScriptedLlmClient();

        try (var context = orchestrationContext(llm, OrchestrationTestConfiguration.class)) {

            var registry = context.getBean(CapabilityRegistry.class);
            assertThat(registry.byCode("FIXTURE")).isPresent();
            assertThat(context.getBean(ExternalFixtureCapabilityProvider.class)).isNotNull();

            var answer = context.getBean(Orchestrator.class)
                    .chat("conversation-1", "test-token", "use the fixture", fixtureIdentity(), llm)
                    .collectList().block();

            assertThat(answer).containsExactly(
                    "{\"type\":\"thinking\",\"content\":\"正在处理...\"}\n",
                    "{\"type\":\"tool_call\",\"content\":\"returnValue\"}\n",
                    "fixture complete\n", "{\"type\":\"done\"}\n");
            assertThat(llm.routingPrompt).contains("FIXTURE | External fixture | Test-only SPI provider");
            assertThat(llm.advertisedTools).containsExactly("returnValue");
            assertThat(llm.advertisedToolSchema.path("required").get(0).asText()).isEqualTo("value");
            assertThat(llm.toolResult).isEqualTo("fixture:sample-value");
        }
    }

    @Test
    void rejectsDuplicateDynamicToolNamesBeforeCallingTheModel() {
        var llm = new ScriptedLlmClient();

        try (var context = orchestrationContext(llm, InvalidDefinitionTestConfiguration.class)) {
            context.getBean(Orchestrator.class)
                    .chat("conversation-1", "test-token", "use the fixture", fixtureIdentity(), llm)
                    .collectList().block();

            assertThat(llm.calls.get()).isEqualTo(2);
            assertThat(llm.advertisedTools).isEmpty();
        }
    }

    private static AnnotationConfigApplicationContext orchestrationContext(
            ScriptedLlmClient llm, Class<?> providerConfiguration) {
        var context = new AnnotationConfigApplicationContext();
        context.scan("io.github.sukimiya.agileagent.config");
        context.register(providerConfiguration, DomainAgent.class,
                InProcessTaskDispatcher.class, Orchestrator.class);
        context.refresh();
        return context;
    }

    private static io.github.sukimiya.agileagent.spi.identity.IdentityContext fixtureIdentity() {
        return new io.github.sukimiya.agileagent.spi.identity.IdentityContext("fixture-user", null,
                null, null, null, null, java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), false, false);
    }

    @Configuration(proxyBeanMethods = false)
    static class OrchestrationTestConfiguration {

        @Bean
        ExternalFixtureCapabilityProvider externalFixtureCapabilityProvider() {
            return new ExternalFixtureCapabilityProvider();
        }

        @Bean
        ChatMemory chatMemory() {
            return emptyMemory();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InvalidDefinitionTestConfiguration {

        @Bean
        AgentCapabilityProvider invalidExternalCapabilityProvider() {
            return new ExternalFixtureCapabilityProvider(true);
        }

        @Bean
        ChatMemory chatMemory() {
            return emptyMemory();
        }
    }

    private static ChatMemory emptyMemory() {
        var memory = mock(ChatMemory.class);
        when(memory.get(anyString())).thenReturn(Mono.just(List.of()));
        when(memory.append(anyString(), org.mockito.ArgumentMatchers.anyList())).thenReturn(Mono.empty());
        return memory;
    }

    private static final class ScriptedLlmClient implements LlmClient {
        private final AtomicInteger calls = new AtomicInteger();
        private String routingPrompt;
        private List<String> advertisedTools = List.of();
        private com.fasterxml.jackson.databind.JsonNode advertisedToolSchema;
        private String toolResult;

        @Override
        public Flux<LlmEvent> chatStream(List<Message> messages, List<ToolDefinition> tools) {
            return switch (calls.getAndIncrement()) {
                case 0 -> {
                    routingPrompt = messages.getFirst().content();
                    yield Flux.just(new LlmEvent.Completed("[\"FIXTURE\"]"));
                }
                case 1 -> {
                    advertisedTools = tools.stream().map(ToolDefinition::name).toList();
                    advertisedToolSchema = new com.fasterxml.jackson.databind.ObjectMapper()
                            .valueToTree(tools.getFirst().parametersSchema());
                    yield Flux.just(new LlmEvent.ToolCallsReady(List.of(
                            new ToolCall("fixture-call", "returnValue", "{\"value\":\"sample-value\"}"))));
                }
                case 2 -> {
                    toolResult = messages.stream().filter(message -> "tool".equals(message.role()))
                            .map(Message::content).findFirst().orElse(null);
                    yield Flux.just(new LlmEvent.Completed("fixture complete"));
                }
                default -> Flux.just(new LlmEvent.Completed("fixture complete"));
            };
        }
    }
}
