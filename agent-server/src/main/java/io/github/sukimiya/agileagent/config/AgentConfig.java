package io.github.sukimiya.agileagent.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.sukimiya.agileagent.agent.AgentRoleRegistry;
import io.github.sukimiya.agileagent.capability.CapabilityRegistry;
import io.github.sukimiya.agileagent.service.EnterpriseApiClient;
import io.github.sukimiya.agileagent.spi.capability.AgentCapabilityProvider;

@Configuration
class AgentConfig {

    @Bean
    CapabilityRegistry capabilityRegistry(List<AgentCapabilityProvider> providers,
                                          AgentRoleRegistry roles, EnterpriseApiClient enterpriseApi) {
        var allProviders = new ArrayList<>(providers);
        allProviders.addAll(roles.apiCapabilityProviders(enterpriseApi));
        return new CapabilityRegistry(allProviders);
    }
}
