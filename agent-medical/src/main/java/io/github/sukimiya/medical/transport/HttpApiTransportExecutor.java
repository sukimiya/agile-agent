package io.github.sukimiya.medical.transport;

import java.net.URI;
import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import io.github.sukimiya.medical.hospital.TransportType;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * API transport: POSTs the tool arguments as JSON to the curated binding URL and nothing
 * else. Failure text never echoes the URL, host, or stack (spec §7); oversized bodies are
 * truncated so the LLM context stays bounded.
 */
public final class HttpApiTransportExecutor implements TransportExecutor {

    private static final int MAX_BODY_LENGTH = 8000;
    private static final String TRUNCATION_SUFFIX = "…（已截断）";
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(15);

    private final WebClient client;

    public HttpApiTransportExecutor(WebClient.Builder builder) {
        this.client = builder.clone()
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().responseTimeout(RESPONSE_TIMEOUT)))
                .build();
    }

    @Override
    public TransportType type() {
        return TransportType.API;
    }

    @Override
    public Mono<ToolResult> execute(TransportRequest request) {
        return client.post()
                .uri(URI.create(request.binding().url()))
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
                })
                .onErrorResume(e -> Mono.just(new ToolResult(false,
                        "医院接口暂时不可用，请稍后重试或改用人工通道")));
    }

    private static String truncate(String body) {
        return body.length() <= MAX_BODY_LENGTH ? body : body.substring(0, MAX_BODY_LENGTH) + TRUNCATION_SUFFIX;
    }
}
