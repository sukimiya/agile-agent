package com.jowo.agent.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.jowo.agent.config.OAuth2Properties;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component
public class OAuth2Client {

    private final OAuth2Properties props;
    private final WebClient http;

    @Autowired
    public OAuth2Client(OAuth2Properties props, WebClient.Builder builder) {
        this(props, builder.clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(15))
                                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000)))
                .build());
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
                .onErrorMap(WebClientRequestException.class, e -> new OAuth2UnavailableException(e.getMessage()))
                .flatMap(body -> {
                    // The subject is the ownership key for conversations and knowledge documents, so a
                    // missing/blank claim must fail loudly instead of collapsing every such user into "".
                    String subject = body.path("sub").asText(null);
                    if (subject == null || subject.isBlank()) {
                        return Mono.error(new OAuth2AuthException("userinfo response missing subject claim"));
                    }
                    return Mono.just(new UserInfo(subject, body.path("name").asText(),
                            props.userTypeClaim() == null || props.userTypeClaim().isBlank()
                                    ? null : body.path(props.userTypeClaim()).asText(null)));
                });
    }

    /**
     * RFC 7009 token revocation. No-op when no revocation endpoint is configured, since revocation is
     * optional for a provider to support and logout must still succeed without it.
     */
    public Mono<Void> revoke(String token) {
        if (props.revocationUri() == null || props.revocationUri().isBlank() || token == null) {
            return Mono.empty();
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("token", token);
        form.add("client_id", props.clientId());
        addClientSecretIfPresent(form);
        return http.post().uri(props.revocationUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                        .map(OAuth2UnavailableException::new))
                .bodyToMono(Void.class)
                .onErrorMap(WebClientRequestException.class, e -> new OAuth2UnavailableException(e.getMessage()));
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
                .onErrorMap(WebClientRequestException.class, e -> new OAuth2UnavailableException(e.getMessage()))
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
