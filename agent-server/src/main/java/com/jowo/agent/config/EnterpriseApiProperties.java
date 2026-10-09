package com.jowo.agent.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("enterprise-api")
public record EnterpriseApiProperties(Map<String, ServiceConfig> services) {

    public record ServiceConfig(String baseUrl) {}
}
