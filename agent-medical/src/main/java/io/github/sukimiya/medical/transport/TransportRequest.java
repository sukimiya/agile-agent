package io.github.sukimiya.medical.transport;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.sukimiya.agileagent.spi.capability.AgentExecutionContext;
import io.github.sukimiya.medical.hospital.TransportBinding;

/**
 * One curated execution request. The binding is the only channel configuration an
 * executor may act on; arguments are inert payload data (spec §7 curated-endpoint red line).
 */
public record TransportRequest(
        String hospitalCode, String hospitalName, String toolName,
        TransportBinding binding, JsonNode arguments, AgentExecutionContext context) {
}
