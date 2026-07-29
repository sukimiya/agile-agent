package com.jowo.agent.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.fasterxml.jackson.databind.JsonNode;
import com.jowo.agent.config.SmartLinkProperties;

import reactor.core.publisher.Mono;

@Component
public class SmartLinkClient {

    private final WebClient http;

    @Autowired
    public SmartLinkClient(SmartLinkProperties props, WebClient.Builder builder) {
        this(builder.clone().baseUrl(props.baseUrl()));
    }

    SmartLinkClient(WebClient.Builder builder) {
        this.http = builder.build();
    }

    public Mono<LoginResult> login(String username, String password) {
        return http.post().uri("/api/authorize/login")
                .bodyValue(Map.of("username", username, "password", password, "expires", -1, "remember", true))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        resp -> Mono.error(new SmartLinkAuthException("用户名或密码错误")))
                .onStatus(HttpStatusCode::isError,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .map(SmartLinkUnavailableException::new))
                .bodyToMono(JsonNode.class)
                .onErrorMap(WebClientRequestException.class, e -> new SmartLinkUnavailableException(e.getMessage()))
                .flatMap(body -> {
                    if (body.path("status").asInt(200) != 200) {
                        return Mono.error(new SmartLinkAuthException(body.path("message").asText("用户名或密码错误")));
                    }
                    var result = body.path("result");
                    return Mono.just(new LoginResult(result.path("userId").asText(), result.path("token").asText()));
                });
    }

    public Mono<String> fetchDisplayName(String smartLinkToken) {
        return http.get().uri("/api/user/detail")
                .header("X-Access-Token", smartLinkToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .map(SmartLinkUnavailableException::new))
                .bodyToMono(JsonNode.class)
                .onErrorMap(WebClientRequestException.class, e -> new SmartLinkUnavailableException(e.getMessage()))
                .map(body -> body.path("result").path("name").asText());
    }

    public Mono<String> fetchUserType(String smartLinkToken) {
        return http.get().uri("/api/authorize/me")
                .header("X-Access-Token", smartLinkToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError,
                        resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                .map(SmartLinkUnavailableException::new))
                .bodyToMono(JsonNode.class)
                .onErrorMap(WebClientRequestException.class, e -> new SmartLinkUnavailableException(e.getMessage()))
                .map(body -> body.path("result").path("user").path("userType").asText());
    }

    public record LoginResult(String userId, String smartLinkToken) {}

    public static class SmartLinkAuthException extends RuntimeException {
        public SmartLinkAuthException(String message) { super(message); }
    }

    public static class SmartLinkUnavailableException extends RuntimeException {
        public SmartLinkUnavailableException(String message) { super(message); }
    }
}
