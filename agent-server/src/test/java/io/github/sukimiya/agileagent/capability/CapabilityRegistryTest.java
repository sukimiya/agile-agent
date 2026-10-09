package io.github.sukimiya.agileagent.capability;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.CapabilityDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CapabilityRegistryTest {

    @Test
    void rejectsMissingAndBlankCodesAtConstruction() {
        for (String code : new String[] {null, "", " \t\n"}) {
            assertThatIllegalArgumentException().isThrownBy(() -> new CapabilityRegistry(List.of(provider(code))));
        }
    }

    @Test
    void rejectsDuplicateCodesDeterministically() {
        var providers = List.of(provider("zulu"), provider("alpha"), provider("zulu"), provider("alpha"));

        assertThatIllegalArgumentException().isThrownBy(() -> new CapabilityRegistry(providers))
                .withMessageContaining("alpha");
        assertThatIllegalArgumentException().isThrownBy(() -> new CapabilityRegistry(providers.reversed()))
                .withMessageContaining("alpha");
    }

    @Test
    void ordersProvidersByCodeAndTakesAnImmutableSnapshot() {
        var providers = new ArrayList<>(List.of(provider("zulu"), provider("alpha"), provider("middle")));
        var registry = new CapabilityRegistry(providers);

        providers.clear();

        assertThat(registry.all()).extracting(AgentCapabilityProvider::code).containsExactly("alpha", "middle", "zulu");
        assertThatThrownBy(() -> registry.all().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(registry.byCode("middle")).isPresent();
        assertThat(registry.byCode("unknown")).isEmpty();
        assertThat(registry.byCode(null)).isEmpty();
    }

    @Test
    void defersDynamicResolutionUntilSubscriptionAndValidatesEveryResolution() {
        var resolutions = new AtomicInteger();
        var provider = provider("documents", context -> {
            int resolution = resolutions.incrementAndGet();
            return Mono.just(new CapabilityDefinition("Read documents", resolution == 1
                    ? List.of(tool("read")) : List.of(tool("read"), tool("read"))));
        });
        var registry = new CapabilityRegistry(List.of(provider));
        var registered = registry.byCode("documents").orElseThrow();
        var definition = registered.definition(context());

        assertThat(registry.all()).hasSize(1);
        assertThat(resolutions.get()).isZero();
        StepVerifier.create(definition)
                .assertNext(value -> assertThat(value.tools()).extracting(ToolDefinition::name).containsExactly("read"))
                .verifyComplete();
        StepVerifier.create(definition).expectError(IllegalArgumentException.class).verify();
        assertThat(resolutions.get()).isEqualTo(2);
    }

    @Test
    void rejectsDuplicateToolsFromBothLookupAndOrderedProvidersBeforeDownstreamUse() {
        var registry = new CapabilityRegistry(List.of(provider("documents", context -> Mono.just(
                new CapabilityDefinition("Read documents", List.of(tool("read"), tool("read")))))));
        var downstreamCalls = new AtomicInteger();

        for (var provider : List.of(registry.byCode("documents").orElseThrow(), registry.all().getFirst())) {
            StepVerifier.create(provider.definition(context()).doOnNext(ignored -> downstreamCalls.incrementAndGet()))
                    .expectError(IllegalArgumentException.class).verify();
        }
        assertThat(downstreamCalls.get()).isZero();
    }

    @Test
    void rejectsBlankDynamicToolNamesBeforeDownstreamUse() {
        var registry = new CapabilityRegistry(List.of(provider("documents", context -> Mono.fromSupplier(() ->
                new CapabilityDefinition("Read documents", List.of(tool(" \t")))))));
        var downstreamCalls = new AtomicInteger();

        StepVerifier.create(registry.all().getFirst().definition(context())
                        .doOnNext(ignored -> downstreamCalls.incrementAndGet()))
                .expectError(IllegalArgumentException.class).verify();
        assertThat(downstreamCalls.get()).isZero();
    }

    @Test
    void delegatesExecutionWithTheOriginalInvocationAndContext() {
        var invocation = new ToolInvocation("read", "call-1", JsonNodeFactory.instance.objectNode());
        var executionContext = context();
        var registry = new CapabilityRegistry(List.of(new TestProvider("documents", ignored -> Mono.empty()) {
            @Override
            public Mono<ToolResult> execute(ToolInvocation actualInvocation, AgentExecutionContext actualContext) {
                if (actualInvocation != invocation || actualContext != executionContext) {
                    return Mono.error(new IllegalArgumentException("Execution arguments were replaced"));
                }
                return Mono.just(new ToolResult(true, "Approved document"));
            }
        }));

        StepVerifier.create(registry.byCode("documents").orElseThrow().execute(invocation, executionContext))
                .expectNext(new ToolResult(true, "Approved document")).verifyComplete();
    }

    private static AgentCapabilityProvider provider(String code) {
        return provider(code, ignored -> Mono.just(new CapabilityDefinition("Read documents", List.of())));
    }

    private static AgentCapabilityProvider provider(String code,
            Function<AgentExecutionContext, Mono<CapabilityDefinition>> definitions) {
        return new TestProvider(code, definitions);
    }

    private static ToolDefinition tool(String name) {
        return new ToolDefinition(name, "Read documents", JsonNodeFactory.instance.objectNode().put("type", "object"));
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(new IdentityContext("subject-1", "User", null, null, null, null,
                Set.of(), Set.of(), Set.of(), false, false), "conversation-1",
                () -> { throw new AssertionError("Registry must not resolve access tokens"); });
    }

    private static class TestProvider implements AgentCapabilityProvider {
        private final String code;
        private final Function<AgentExecutionContext, Mono<CapabilityDefinition>> definitions;

        TestProvider(String code, Function<AgentExecutionContext, Mono<CapabilityDefinition>> definitions) {
            this.code = code;
            this.definitions = definitions;
        }

        @Override
        public String code() { return code; }

        @Override
        public String displayName() { return "Documents"; }

        @Override
        public String routingDescription() { return "Read approved documents"; }

        @Override
        public Mono<CapabilityDefinition> definition(AgentExecutionContext context) {
            return definitions.apply(context);
        }

        @Override
        public Mono<ToolResult> execute(ToolInvocation invocation, AgentExecutionContext context) {
            return Mono.just(new ToolResult(false, "Unknown tool"));
        }
    }
}
