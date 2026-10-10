package io.github.sukimiya.agileagent.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;

@Configuration
class AgentConfig {

    @Bean
    CapabilityRegistry capabilityRegistry(List<AgentCapabilityProvider> providers) {
        return new CapabilityRegistry(providers);
    }
}
