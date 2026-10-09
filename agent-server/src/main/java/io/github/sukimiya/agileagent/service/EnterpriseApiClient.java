package io.github.sukimiya.agileagent.service;

import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;

public interface EnterpriseApiClient {

    Mono<JsonNode> call(String service, String method, String path, Map<String, Object> params, String accessToken);

    class EnterpriseApiException extends RuntimeException {
        public EnterpriseApiException(String message) {
            super(message);
        }
    }
}
