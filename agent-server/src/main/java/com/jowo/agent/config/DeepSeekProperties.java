package com.jowo.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("deepseek")
public record DeepSeekProperties(String baseUrl, String apiKey, String model) {}
