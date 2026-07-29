package com.jowo.agent.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class OAuth2PropertiesTest {

    @Test
    void bindsAllFieldsFromPrefixOauth2() {
        var source = new MapConfigurationPropertySource(java.util.Map.of(
                "oauth2.authorization-uri", "https://idp.example.com/oauth2/authorize",
                "oauth2.token-uri", "https://idp.example.com/oauth2/token",
                "oauth2.user-info-uri", "https://idp.example.com/oauth2/userinfo",
                "oauth2.client-id", "agile-agent",
                "oauth2.client-secret", "s3cr3t",
                "oauth2.redirect-uri", "http://localhost:8088/api/auth/oauth2/callback",
                "oauth2.scope", "openid profile",
                "oauth2.user-type-claim", "userType",
                "oauth2.frontend-redirect-uri", "http://localhost:5173/login/callback"));

        var props = new Binder(source).bind("oauth2", OAuth2Properties.class).get();

        assertThat(props.authorizationUri()).isEqualTo("https://idp.example.com/oauth2/authorize");
        assertThat(props.tokenUri()).isEqualTo("https://idp.example.com/oauth2/token");
        assertThat(props.userInfoUri()).isEqualTo("https://idp.example.com/oauth2/userinfo");
        assertThat(props.clientId()).isEqualTo("agile-agent");
        assertThat(props.clientSecret()).isEqualTo("s3cr3t");
        assertThat(props.redirectUri()).isEqualTo("http://localhost:8088/api/auth/oauth2/callback");
        assertThat(props.scope()).isEqualTo("openid profile");
        assertThat(props.userTypeClaim()).isEqualTo("userType");
        assertThat(props.frontendRedirectUri()).isEqualTo("http://localhost:5173/login/callback");
    }
}
