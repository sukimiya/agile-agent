package io.github.sukimiya.agileagent.service;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.sukimiya.agileagent.config.EnterpriseApiProperties;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

@Component
public class HttpEnterpriseApiClient implements EnterpriseApiClient {

    private static final Pattern PATH_PARAM = Pattern.compile("\\{(\\w+)}");

    private final Map<String, WebClient> clients;
    private final Map<String, String> baseUrls;
    private final Retry retrySpec;

    @Autowired
    public HttpEnterpriseApiClient(EnterpriseApiProperties props, WebClient.Builder builder) {
        this(props, builder.clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(15))
                                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000))),
                Retry.backoff(2, Duration.ofMillis(500)));
    }

    HttpEnterpriseApiClient(EnterpriseApiProperties props, WebClient.Builder builder, RetryBackoffSpec baseRetry) {
        this.clients = new HashMap<>();
        this.baseUrls = new HashMap<>();
        this.retrySpec = baseRetry
                .filter(TransientFailure.class::isInstance)
                .onRetryExhaustedThrow((spec, signal) -> new EnterpriseApiException(
                        "企业接口多次重试后仍不可用: " + signal.failure().getMessage()));
        props.services().forEach((name, cfg) -> {
            baseUrls.put(name, cfg.baseUrl());
            clients.put(name, builder.clone().baseUrl(cfg.baseUrl()).build());
        });
    }

    @Override
    public Mono<JsonNode> call(String service, String method, String path,
                                Map<String, Object> params, String accessToken) {
        var http = clients.get(service);
        if (http == null) return Mono.error(new EnterpriseApiException("unknown service: " + service));
        String baseUrl = baseUrls.get(service);

        var remaining = new LinkedHashMap<String, Object>(params == null ? Map.of() : params);
        String resolvedPath = resolvePathParams(path, remaining);

        return exchange(http, baseUrl, method, resolvedPath, remaining, accessToken)
                .retryWhen(retrySpec);
    }

    private Mono<JsonNode> exchange(WebClient http, String baseUrl, String method, String path,
                                     Map<String, Object> params, String accessToken) {
        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
        WebClient.RequestBodySpec spec;
        if (httpMethod == HttpMethod.GET || httpMethod == HttpMethod.DELETE) {
            spec = http.method(httpMethod).uri(buildAbsoluteUri(baseUrl, path, params));
        } else {
            spec = http.method(httpMethod).uri(path);
        }
        if (accessToken != null) spec = spec.headers(h -> h.setBearerAuth(accessToken));
        WebClient.RequestHeadersSpec<?> ready =
                (httpMethod == HttpMethod.GET || httpMethod == HttpMethod.DELETE) ? spec : spec.bodyValue(params);
        return ready.retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, resp ->
                        resp.bodyToMono(String.class).defaultIfEmpty("")
                            .map(b -> new TransientFailure("HTTP " + resp.statusCode().value() + " " + b)))
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).defaultIfEmpty("")
                            .map(b -> new EnterpriseApiException(
                                    "企业接口返回错误 " + resp.statusCode().value() + ": " + b)))
                .bodyToMono(JsonNode.class)
                .onErrorMap(WebClientRequestException.class, e -> new TransientFailure(e.getMessage()));
    }

    private static String resolvePathParams(String path, Map<String, Object> params) {
        var matcher = PATH_PARAM.matcher(path);
        var sb = new StringBuilder();
        while (matcher.find()) {
            Object value = params.remove(matcher.group(1));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // Builds a fully absolute URI (scheme+host+baseUrl's own path+api path+encoded query) so
    // WebClient's .uri(URI) treats it as already-absolute and never attempts to merge it
    // against the client's configured baseUrl. Merging an absolute-path reference like
    // "/api/..." against a baseUrl that itself has a path (e.g. "http://host:port/mes-service")
    // follows RFC 3986 replace-not-append semantics and silently drops the baseUrl's own path
    // segment — confirmed by direct reproduction against a real backend during Task 10's
    // manual smoke test. Concatenating the strings ourselves before parsing avoids relying on
    // that merge behavior at all, while UriComponentsBuilder still performs exactly one
    // percent-encoding pass over the query values (the fix from Task 5's double-encoding bug).
    private static URI buildAbsoluteUri(String baseUrl, String path, Map<String, Object> params) {
        var uriBuilder = UriComponentsBuilder.fromUriString(baseUrl + path);
        params.forEach((k, v) -> uriBuilder.queryParam(k, String.valueOf(v)));
        return uriBuilder.encode().build().toUri();
    }

    private static final class TransientFailure extends RuntimeException {
        TransientFailure(String message) {
            super(message);
        }
    }
}
