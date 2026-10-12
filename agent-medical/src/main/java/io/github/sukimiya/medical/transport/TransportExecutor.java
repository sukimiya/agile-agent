package io.github.sukimiya.medical.transport;

import io.github.sukimiya.agileagent.spi.capability.ToolResult;
import io.github.sukimiya.medical.hospital.TransportType;
import reactor.core.publisher.Mono;

/**
 * Executes one tool call over one transport channel of schema v1 (spec §4). Channels
 * without a registered executor are neither advertised nor executable (spec §2 决定表).
 */
public interface TransportExecutor {
    TransportType type();

    Mono<ToolResult> execute(TransportRequest request);
}
