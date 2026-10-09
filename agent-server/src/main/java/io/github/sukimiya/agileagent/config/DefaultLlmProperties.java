package io.github.sukimiya.agileagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("llm.default")
public record DefaultLlmProperties(String baseUrl, String apiKey, String model) {}
