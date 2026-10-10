package io.github.sukimiya.agileagent.llmconfig;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.sukimiya.agileagent.service.AuthSessionStore;
import io.github.sukimiya.agileagent.web.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/llm-config")
public class LlmBalanceController {

    private final LlmBalanceService service;

    public LlmBalanceController(LlmBalanceService service) {
        this.service = service;
    }

    @GetMapping("/balance")
    Mono<JsonNode> getBalance(@CurrentUser AuthSessionStore.AuthUser user) {
        return service.getBalance(user.identity());
    }
}
