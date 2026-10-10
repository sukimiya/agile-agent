package io.github.sukimiya.agileagent.hint;

import java.net.URI;

import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.web.CurrentUser;
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
@RequestMapping("/api/chat/hints")
public class HintController {

    private final HintService service;

    public HintController(HintService service) {
        this.service = service;
    }

    @GetMapping
    Flux<HintService.HintSummary> list(@CurrentUser AuthSessionStore.AuthUser user) {
        return service.list(user.identity());
    }

    @PostMapping
    Mono<ResponseEntity<HintService.HintSummary>> create(@RequestBody HintRequest request,
            @CurrentUser AuthSessionStore.AuthUser user) {
        return service.create(user.identity(), new HintService.HintInput(request.content()))
                .map(summary -> ResponseEntity.created(URI.create("/api/chat/hints/" + summary.id())).body(summary));
    }

    @PutMapping("/{id}")
    Mono<HintService.HintSummary> update(@PathVariable String id, @RequestBody HintRequest request,
            @CurrentUser AuthSessionStore.AuthUser user) {
        return service.update(user.identity(), id, new HintService.HintInput(request.content()));
    }

    @DeleteMapping("/{id}")
    Mono<ResponseEntity<Void>> delete(@PathVariable String id, @CurrentUser AuthSessionStore.AuthUser user) {
        return service.delete(user.identity(), id).thenReturn(ResponseEntity.noContent().build());
    }

    public record HintRequest(String content) { }
}
