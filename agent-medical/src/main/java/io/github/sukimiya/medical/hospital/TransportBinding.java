package io.github.sukimiya.medical.hospital;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Curated transport binding of one tool. Only the field of the declared type is meaningful:
 * API/PORTAL use {@code url}, HUMAN uses {@code channel}, EMAIL uses {@code address}.
 * Bindings are approved knowledge data — executors never accept URLs from anywhere else
 * (spec §7 curated-endpoint red line).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransportBinding(TransportType type, String url, String channel, String address) {

    public static TransportBinding api(String url) {
        return new TransportBinding(TransportType.API, url, null, null);
    }

    public static TransportBinding human(String channel) {
        return new TransportBinding(TransportType.HUMAN, null, channel, null);
    }

    public static TransportBinding email(String address) {
        return new TransportBinding(TransportType.EMAIL, null, null, address);
    }

    public static TransportBinding portal(String url) {
        return new TransportBinding(TransportType.PORTAL, url, null, null);
    }
}
