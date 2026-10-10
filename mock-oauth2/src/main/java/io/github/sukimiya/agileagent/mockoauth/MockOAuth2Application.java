package io.github.sukimiya.agileagent.mockoauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.security.SecureRandom;
import java.time.Clock;

@SpringBootApplication
public class MockOAuth2Application {
    @Bean
    MockIdentityCatalog mockIdentityCatalog() {
        return new MockIdentityCatalog();
    }

    @Bean
    AuthorizationCodeStore authorizationCodeStore(Clock mockOAuth2Clock, SecureRandom mockOAuth2SecureRandom) {
        return new AuthorizationCodeStore(mockOAuth2Clock, mockOAuth2SecureRandom);
    }

    @Bean
    TokenStore tokenStore(MockIdentityCatalog identities, Clock mockOAuth2Clock,
                          SecureRandom mockOAuth2SecureRandom) {
        return new TokenStore(identities, mockOAuth2Clock, mockOAuth2SecureRandom);
    }

    @Bean
    Clock mockOAuth2Clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom mockOAuth2SecureRandom() {
        return new SecureRandom();
    }

    public static void main(String[] args) {
        SpringApplication.run(MockOAuth2Application.class, args);
    }
}
