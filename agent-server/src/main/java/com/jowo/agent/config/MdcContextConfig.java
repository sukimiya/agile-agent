package com.jowo.agent.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;

import io.micrometer.context.ContextRegistry;
import reactor.core.publisher.Hooks;

/**
 * Bridges Reactor {@code Context} values ("traceId", "role") into SLF4J's MDC so that
 * {@code log.info(...)} calls anywhere in the reactive chain — regardless of which thread
 * happens to run them — pick up the request's trace id and, inside a domain agent, its role.
 * Wired once at startup; {@link #configure()} is also callable directly from tests.
 */
@Configuration
public class MdcContextConfig {

    static final String TRACE_ID_KEY = "traceId";
    static final String ROLE_KEY = "role";

    @PostConstruct
    public void init() {
        configure();
    }

    public static void configure() {
        ContextRegistry.getInstance()
                .registerThreadLocalAccessor(TRACE_ID_KEY, () -> MDC.get(TRACE_ID_KEY),
                        v -> MDC.put(TRACE_ID_KEY, v), () -> MDC.remove(TRACE_ID_KEY))
                .registerThreadLocalAccessor(ROLE_KEY, () -> MDC.get(ROLE_KEY),
                        v -> MDC.put(ROLE_KEY, v), () -> MDC.remove(ROLE_KEY));
        Hooks.enableAutomaticContextPropagation();
    }
}
