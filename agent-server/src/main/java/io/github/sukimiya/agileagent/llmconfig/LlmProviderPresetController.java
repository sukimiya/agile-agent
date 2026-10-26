package io.github.sukimiya.agileagent.llmconfig;

import java.net.URI;
import java.time.LocalDateTime;

import io.github.sukimiya.agileagent.llmconfig.LlmProviderPresetRepository.LlmProviderPresetRow;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.spi.identity.IdentityContext;
import io.github.sukimiya.agileagent.web.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/llm-providers")
public class LlmProviderPresetController {

    private final LlmProviderPresetService service;

    public LlmProviderPresetController(LlmProviderPresetService service) {
        this.service = service;
    }

    @GetMapping
    Flux<PresetResponse> list(@CurrentUser AuthSessionStore.AuthUser user) {
        return service.list(user.identity()).map(LlmProviderPresetController::toResponse);
    }

    @PostMapping
    Mono<ResponseEntity<PresetResponse>> create(@RequestBody PresetRequest request,
            @CurrentUser AuthSessionStore.AuthUser user) {
        return service.create(user.identity(), request.toInput())
                .map(row -> ResponseEntity.created(URI.create("/api/llm-providers/" + row.id()))
                        .body(toResponse(row)));
    }

    @PutMapping("/{id}")
    Mono<PresetResponse> update(@PathVariable String id, @RequestBody PresetRequest request,
            @CurrentUser AuthSessionStore.AuthUser user) {
        return service.update(user.identity(), id, request.toInput()).map(LlmProviderPresetController::toResponse);
    }

    @DeleteMapping("/{id}")
    Mono<ResponseEntity<Void>> delete(@PathVariable String id, @CurrentUser AuthSessionStore.AuthUser user) {
        return service.delete(user.identity(), id).thenReturn(ResponseEntity.noContent().build());
    }

    private static PresetResponse toResponse(LlmProviderPresetRow row) {
        return new PresetResponse(row.id(), row.name(), row.baseUrl(), row.model(), row.balancePath(),
                row.createdBySubject(), row.createdAt(), row.updatedAt(), row.contextWindowTokens(), row.maxOutputTokens());
    }

    /** {@code contextWindowTokens} and {@code maxOutputTokens} are optional; omitted means the configured default. */
    public record PresetRequest(String name, String baseUrl, String model, String balancePath,
            Integer contextWindowTokens, Integer maxOutputTokens) {
        LlmProviderPresetService.PresetInput toInput() {
            return new LlmProviderPresetService.PresetInput(name, baseUrl, model, balancePath, contextWindowTokens,
                    maxOutputTokens);
        }
    }

    public record PresetResponse(String id, String name, String baseUrl, String model, String balancePath,
            String createdBySubject, LocalDateTime createdAt, LocalDateTime updatedAt,
            Integer contextWindowTokens, Integer maxOutputTokens) { }
}
