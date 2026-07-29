package com.jowo.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("oauth2")
public record OAuth2Properties(
        String authorizationUri,
        String tokenUri,
        String userInfoUri,
        String revocationUri,
        String clientId,
        String clientSecret,
        String redirectUri,
        String scope,
        String userTypeClaim,
        String frontendRedirectUri) {}
