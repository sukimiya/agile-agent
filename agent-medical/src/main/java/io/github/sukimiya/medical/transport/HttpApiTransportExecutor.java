package io.github.sukimiya.medical.transport;

import java.net.URI;
import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import io.github.sukimiya.medical.hospital.TransportType;
import io.github.sukimiya.medical.validation.EndpointScreeningPolicy;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

/**
 * API transport: POSTs the tool arguments as JSON to the curated binding URL and nothing
 * else. The runtime SSRF double-check (spec §14 resolution 4) re-screens the host on every
 * call, so rows approved before a DNS change or an allowlist tightening cannot be used to
 * reach internal addresses. Failure text never echoes the URL, host, or stack (spec §7);
 * oversized bodies are truncated so the LLM context stays bounded. The whole chain is lazy
 * (Plan 2 final review M-1): even a synchronously throwing URI.create surfaces as the safe
 * ToolResult, and the blocking DNS check runs on boundedElastic, never on an event loop.
 */
public final class HttpApiTransportExecutor implements TransportExecutor {

    private static final int MAX_BODY_LENGTH = 8000;
    private static final String TRUNCATION_SUFFIX = "…（已截断）";
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(15);
    private static final String UNAVAILABLE = "医院接口暂时不可用，请稍后重试或改用人工通道";

    private final WebClient client;
    private final EndpointScreeningPolicy endpoints;

    public HttpApiTransportExecutor(WebClient.Builder builder, EndpointScreeningPolicy endpoints) {
        this.client = builder.clone()
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().responseTimeout(RESPONSE_TIMEOUT)))
                .build();
        this.endpoints = endpoints;
    }

    @Override
    public TransportType type() {
        return TransportType.API;
    }

    @Override
    public Mono<ToolResult> execute(TransportRequest request) {
        return Mono.fromCallable(() -> endpoints.permits(request.binding().url()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(permitted -> permitted
                        ? post(request.binding().url().trim(), request)
                        : Mono.just(new ToolResult(false, UNAVAILABLE)))
                .onErrorResume(e -> Mono.just(new ToolResult(false, UNAVAILABLE)));
    }

    private Mono<ToolResult> post(String url, TransportRequest request) {
        return client.post()
                .uri(URI.create(url))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request.arguments().toString())
                .exchangeToMono(response -> {
                    int status = response.statusCode().value();
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> status >= 200 && status < 300
                                    ? new ToolResult(true, truncate(body))
                                    : new ToolResult(false,
                                            "医院接口调用失败（HTTP " + status + "），请稍后重试"));
                });
    }

    private static String truncate(String body) {
        return body.length() <= MAX_BODY_LENGTH ? body : body.substring(0, MAX_BODY_LENGTH) + TRUNCATION_SUFFIX;
    }
}
