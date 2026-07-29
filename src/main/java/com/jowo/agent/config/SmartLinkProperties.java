package com.jowo.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("smartlink")
public record SmartLinkProperties(String baseUrl) {}
