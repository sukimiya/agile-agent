package io.github.sukimiya.agileagent.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.core.codec.DecodingException;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.sukimiya.agileagent.auth.IdentityMappingException;
import io.github.sukimiya.agileagent.auth.OAuth2IdentityMapper;
import io.github.sukimiya.agileagent.config.OAuth2Properties;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component
public class OAuth2Client {

    private final OAuth2Properties props;
    private final WebClient http;
    private final OAuth2IdentityMapper identityMapper;

    @Autowired
    public OAuth2Client(OAuth2Properties props, WebClient.Builder builder, OAuth2IdentityMapper identityMapper) {
        this(props, builder.clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(15))
                                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000)))
                .build(), identityMapper);
    }

    OAuth2Client(OAuth2Properties props, WebClient http, OAuth2IdentityMapper identityMapper) {
        this.props = props;
        this.http = http;
        this.identityMapper = identityMapper;
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

    public Mono<IdentityContext> fetchIdentity(String accessToken) {
        return http.get().uri(props.userInfoUri())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp -> {
                    int status = resp.statusCode().value();
                    return resp.releaseBody().then(Mono.error(new OAuth2UnavailableException(
                            "OAuth2 userinfo request failed (HTTP " + status + ")")));
                })
                .bodyToMono(JsonNode.class)
                .onErrorMap(WebClientRequestException.class,
                        e -> new OAuth2UnavailableException("OAuth2 userinfo provider request failed"))
                .onErrorMap(DecodingException.class,
                        e -> new OAuth2UnavailableException("OAuth2 userinfo response could not be read"))
                .onErrorMap(UnsupportedMediaTypeException.class,
                        e -> new OAuth2UnavailableException("OAuth2 userinfo response could not be read"))
                .switchIfEmpty(Mono.error(new OAuth2UnavailableException("OAuth2 userinfo response was empty")))
                .flatMap(body -> {
                    try {
                        IdentityContext identity = identityMapper.map(body);
                        if (identity == null) {
                            return Mono.error(new OAuth2UnavailableException(
                                    "OAuth2 userinfo response could not be mapped"));
                        }
                        return Mono.just(identity);
                    } catch (IdentityMappingException exception) {
                        return Mono.error(exception);
                    } catch (RuntimeException exception) {
                        return Mono.error(new OAuth2UnavailableException(
                                "OAuth2 userinfo response could not be mapped"));
                    }
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
                .onStatus(HttpStatusCode::isError, resp -> {
                    int status = resp.statusCode().value();
                    return resp.releaseBody().then(Mono.error(new OAuth2UnavailableException(
                            "OAuth2 revocation request failed (HTTP " + status + ")")));
                })
                .bodyToMono(Void.class)
                .onErrorMap(WebClientRequestException.class,
                        e -> new OAuth2UnavailableException("OAuth2 revocation provider request failed"));
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
                .onStatus(HttpStatusCode::is5xxServerError, resp -> {
                    int status = resp.statusCode().value();
                    return resp.releaseBody().then(Mono.error(new OAuth2UnavailableException(
                            "OAuth2 token endpoint unavailable (HTTP " + status + ")")));
                })
                .onStatus(HttpStatusCode::isError, resp -> {
                    int status = resp.statusCode().value();
                    return resp.releaseBody().then(Mono.error(new OAuth2AuthException(
                            "OAuth2 token endpoint rejected request (HTTP " + status + ")")));
                })
                .bodyToMono(JsonNode.class)
                .onErrorMap(WebClientRequestException.class,
                        e -> new OAuth2UnavailableException("OAuth2 token provider request failed"))
                .map(OAuth2Client::toTokenResult);
    }

    private static TokenResult toTokenResult(JsonNode body) {
        return new TokenResult(
                body.path("access_token").asText(),
                body.path("refresh_token").asText(null),
                body.path("expires_in").asInt(3600));
    }

    public record TokenResult(String accessToken, String refreshToken, int expiresInSeconds) {
        @Override
        public String toString() {
            return "TokenResult[accessToken=[REDACTED], refreshToken=[REDACTED], expiresInSeconds="
                    + expiresInSeconds + "]";
        }
    }

    public static class OAuth2AuthException extends RuntimeException {
        public OAuth2AuthException(String message) { super(message); }
    }

    public static class OAuth2UnavailableException extends RuntimeException {
        public OAuth2UnavailableException(String message) { super(message); }
    }
}
