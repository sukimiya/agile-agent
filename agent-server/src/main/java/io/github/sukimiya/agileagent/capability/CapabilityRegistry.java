package io.github.sukimiya.agileagent.capability;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;
import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.agileagent.spi.capability.CapabilityDefinition;
import io.github.sukimiya.agileagent.spi.capability.ToolInvocation;
import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import reactor.core.publisher.Mono;

/** Orders registered capabilities and validates dynamic definitions before use. */
public final class CapabilityRegistry {

    private final Map<String, AgentCapabilityProvider> providersByCode;
    private final List<AgentCapabilityProvider> providers;

    public CapabilityRegistry(Collection<AgentCapabilityProvider> providers) {
        var ordered = Objects.requireNonNull(providers, "providers must not be null").stream()
                .map(provider -> {
                    Objects.requireNonNull(provider, "provider must not be null");
                    String code = provider.code();
                    if (code == null || code.isBlank()) {
                        throw new IllegalArgumentException("capability code must not be blank");
                    }
                    return new ValidatedProvider(code, provider);
                })
                .sorted(Comparator.comparing(ValidatedProvider::code))
                .toList();
        var byCode = new LinkedHashMap<String, AgentCapabilityProvider>();
        for (var provider : ordered) {
            if (byCode.putIfAbsent(provider.code(), provider) != null) {
                throw new IllegalArgumentException("Duplicate capability code: " + provider.code());
            }
        }
        this.providersByCode = Map.copyOf(byCode);
        this.providers = List.copyOf(ordered);
    }

    public Optional<AgentCapabilityProvider> byCode(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(providersByCode.get(code));
    }

    public List<AgentCapabilityProvider> all() {
        return providers;
    }

    private static CapabilityDefinition validateDefinition(CapabilityDefinition definition) {
        var names = new HashSet<String>();
        for (var tool : definition.tools()) {
            if (tool.name() == null || tool.name().isBlank()) {
                throw new IllegalArgumentException("tool name must not be blank");
            }
            if (!names.add(tool.name())) {
                throw new IllegalArgumentException("Duplicate tool name in capability definition");
            }
        }
        return definition;
    }

    private record ValidatedProvider(String code, AgentCapabilityProvider delegate) implements AgentCapabilityProvider {

        @Override
        public String displayName() {
            return delegate.displayName();
        }

        @Override
        public String routingDescription() {
            return delegate.routingDescription();
        }

        @Override
        public Mono<CapabilityDefinition> definition(AgentExecutionContext context) {
            return Mono.defer(() -> delegate.definition(context)).map(CapabilityRegistry::validateDefinition);
        }

        @Override
        public Mono<ToolResult> execute(ToolInvocation invocation, AgentExecutionContext context) {
            return delegate.execute(invocation, context);
        }
    }
}
