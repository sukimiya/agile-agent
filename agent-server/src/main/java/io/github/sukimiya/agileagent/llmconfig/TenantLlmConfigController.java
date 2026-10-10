package io.github.sukimiya.agileagent.llmconfig;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.web.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/llm-config")
public class TenantLlmConfigController {

    private final TenantLlmConfigService service;

    public TenantLlmConfigController(TenantLlmConfigService service) {
        this.service = service;
    }

    @GetMapping
    Mono<TenantLlmConfigService.ConfigResponse> get(@CurrentUser AuthSessionStore.AuthUser user) {
        return service.get(user.identity());
    }

    @PutMapping
    Mono<TenantLlmConfigService.ConfigResponse> put(@RequestBody ConfigRequest request,
            @CurrentUser AuthSessionStore.AuthUser user) {
        if (request.hasTenantId()) {
            return Mono.error(LlmConfigValidationException.invalidInput());
        }
        return service.save(user.identity(), new TenantLlmConfigService.ConfigInput(request.presetId(), request.apiKey()));
    }

    @DeleteMapping
    Mono<ResponseEntity<Void>> delete(@CurrentUser AuthSessionStore.AuthUser user) {
        return service.delete(user.identity()).thenReturn(ResponseEntity.noContent().build());
    }

    public static final class ConfigRequest {
        private String presetId;
        private String apiKey;
        private boolean tenantIdPresent;

        public String presetId() {
            return presetId;
        }

        public void setPresetId(String presetId) {
            this.presetId = presetId;
        }

        public String apiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        @JsonAnySetter
        public void captureField(String name, Object value) {
            if ("tenantId".equals(name)) {
                tenantIdPresent = true;
            }
        }

        public boolean hasTenantId() {
            return tenantIdPresent;
        }

        @Override
        public String toString() {
            return "ConfigRequest[presetId=" + presetId + ", apiKey=[REDACTED], tenantIdPresent="
                    + tenantIdPresent + "]";
        }
    }
}
