package io.github.sukimiya.agileagent.mockoauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/oauth2/authorize")
public final class LoginPageController {
    private final MockIdentityCatalog identities;
    private final AuthorizationRequestValidator validator;
    private final TemplateEngine templates;

    public LoginPageController(MockIdentityCatalog identities, AuthorizationRequestValidator validator,
                               TemplateEngine templates) {
        this.identities = identities;
        this.validator = validator;
        this.templates = templates;
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public Mono<ResponseEntity<String>> authorizePage(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> renderOrReject(validator.validate(exchange.getRequest().getQueryParams())))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ResponseEntity<String> renderOrReject(AuthorizationRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"invalid_request\"}");
        }
        Context context = new Context();
        context.setVariable("request", request);
        context.setVariable("identities", identities.identities());
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(templates.process("login", context));
    }
}

record AuthorizationRequest(String responseType, String clientId, String redirectUri, String state,
                            String codeChallenge, String codeChallengeMethod) {
}

@org.springframework.stereotype.Component
final class AuthorizationRequestValidator {
    private final String clientId;
    private final Set<String> redirectUris;

    AuthorizationRequestValidator(@Value("${mock.oauth2.client-id}") String clientId,
                                  @Value("${mock.oauth2.redirect-uris}") String configuredRedirectUris) {
        this.clientId = clientId;
        this.redirectUris = Arrays.stream(configuredRedirectUris.split(",", -1))
                .collect(Collectors.toUnmodifiableSet());
    }

    AuthorizationRequest validate(MultiValueMap<String, String> parameters) {
        return validate(single(parameters, "response_type"), single(parameters, "client_id"),
                single(parameters, "redirect_uri"), single(parameters, "state"),
                single(parameters, "code_challenge"), single(parameters, "code_challenge_method"));
    }

    AuthorizationRequest validate(String responseType, String requestedClientId, String redirectUri,
                                  String state, String challenge, String challengeMethod) {
        if (!"code".equals(responseType) || !clientId.equals(requestedClientId)
                || redirectUri == null || !redirectUris.contains(redirectUri)
                || state == null || state.isBlank()
                || !PkceVerifier.isValidS256Challenge(challenge) || !"S256".equals(challengeMethod)) {
            return null;
        }
        return new AuthorizationRequest(responseType, requestedClientId, redirectUri, state, challenge,
                challengeMethod);
    }

    private static String single(MultiValueMap<String, String> values, String name) {
        var entries = values.get(name);
        return entries == null || entries.size() != 1 ? null : entries.getFirst();
    }
}
