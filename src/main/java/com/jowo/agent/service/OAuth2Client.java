package com.jowo.agent.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.jowo.agent.config.OAuth2Properties;

import reactor.core.publisher.Mono;

@Component
public class OAuth2Client {

    private final OAuth2Properties props;
    private final WebClient http;

    @Autowired
    public OAuth2Client(OAuth2Properties props, WebClient.Builder builder) {
        this(props, builder.build());
    }

    OAuth2Client(OAuth2Properties props, WebClient http) {
        this.props = props;
        this.http = http;
    }

    public String buildAuthorizationUrl(String state, String codeChallenge) {
        return UriComponentsBuilder.fromUriString(props.authorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", props.clientId())
                .queryParam("redirect_uri", props.redirectUri())
                .queryParam("scope", props.scope())
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build().encode().toUriString();
    }

    public Mono<TokenResult> exchangeCode(String code, String codeVerifier) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", props.redirectUri());
        form.add("client_id", props.clientId());
        form.add("code_verifier", codeVerifier);
        addClientSecretIfPresent(form);
        return postForm(props.tokenUri(), form);
    }

    public Mono<TokenResult> refresh(String refreshToken) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", props.clientId());
        addClientSecretIfPresent(form);
        return postForm(props.tokenUri(), form);
    }

    public Mono<UserInfo> fetchUserInfo(String accessToken) {
        return http.get().uri(props.userInfoUri())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                        .map(OAuth2UnavailableException::new))
                .bodyToMono(JsonNode.class)
                .map(body -> new UserInfo(
                        body.path("sub").asText(),
                        body.path("name").asText(),
                        props.userTypeClaim() == null || props.userTypeClaim().isBlank()
                                ? null : body.path(props.userTypeClaim()).asText(null)));
    }

    private void addClientSecretIfPresent(LinkedMultiValueMap<String, String> form) {
        if (props.clientSecret() != null && !props.clientSecret().isBlank()) {
            form.add("client_secret", props.clientSecret());
        }
    }

    private Mono<TokenResult> postForm(String uri, LinkedMultiValueMap<String, String> form) {
        return http.post().uri(uri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                        .map(OAuth2UnavailableException::new))
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                        .map(OAuth2AuthException::new))
                .bodyToMono(JsonNode.class)
                .map(OAuth2Client::toTokenResult);
    }

    private static TokenResult toTokenResult(JsonNode body) {
        return new TokenResult(
                body.path("access_token").asText(),
                body.path("refresh_token").asText(null),
                body.path("expires_in").asInt(3600));
    }

    public record TokenResult(String accessToken, String refreshToken, int expiresInSeconds) {}

    public record UserInfo(String subject, String name, String userType) {}

    public static class OAuth2AuthException extends RuntimeException {
        public OAuth2AuthException(String message) { super(message); }
    }

    public static class OAuth2UnavailableException extends RuntimeException {
        public OAuth2UnavailableException(String message) { super(message); }
    }
}
