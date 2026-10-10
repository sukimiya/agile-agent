package io.github.sukimiya.agileagent.mockoauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestController
public final class OAuth2Controller {
    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    private final String clientId;
    private final String clientSecret;
    private final MockIdentityCatalog identities;
    private final AuthorizationRequestValidator authorizeValidator;
    private final AuthorizationCodeStore codes;
    private final TokenStore tokens;

    public OAuth2Controller(@Value("${mock.oauth2.client-id}") String clientId,
                            @Value("${mock.oauth2.client-secret:}") String clientSecret,
                            MockIdentityCatalog identities,
                            AuthorizationRequestValidator authorizeValidator,
                            AuthorizationCodeStore codes, TokenStore tokens) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.identities = identities;
        this.authorizeValidator = authorizeValidator;
        this.codes = codes;
        this.tokens = tokens;
    }

    @PostMapping(path = "/oauth2/authorize", consumes = "application/x-www-form-urlencoded")
    public Mono<ResponseEntity<?>> authorize(ServerWebExchange exchange) {
        return exchange.getFormData().map(this::authorizeForm)
                .defaultIfEmpty(oauthError(HttpStatus.BAD_REQUEST, "invalid_request", "Authorization request is invalid."));
    }

    private ResponseEntity<?> authorizeForm(MultiValueMap<String, String> form) {
        AuthorizationRequest request = authorizeValidator.validate(form);
        String subject = single(form, "subject");
        if (request == null || subject == null || identities.findBySubject(subject).isEmpty()) {
            return oauthError(HttpStatus.BAD_REQUEST, "invalid_request", "Authorization request is invalid.");
        }
        final String code;
        try {
            code = codes.issue(request.clientId(), URI.create(request.redirectUri()), subject,
                    request.codeChallenge(), request.codeChallengeMethod());
        } catch (IllegalArgumentException exception) {
            return oauthError(HttpStatus.BAD_REQUEST, "invalid_request", "Authorization request is invalid.");
        }
        URI callback = UriComponentsBuilder.fromUriString(request.redirectUri())
                .queryParam("code", code).queryParam("state", request.state()).build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(callback).build();
    }

    @PostMapping(path = "/oauth2/token", consumes = "application/x-www-form-urlencoded", produces = "application/json")
    public Mono<ResponseEntity<?>> token(ServerWebExchange exchange) {
        return exchange.getFormData().map(this::exchange).defaultIfEmpty(
                oauthError(HttpStatus.BAD_REQUEST, "invalid_request", "Token request is invalid."));
    }

    private ResponseEntity<?> exchange(MultiValueMap<String, String> form) {
        if (!validClient(form)) {
            return oauthError(HttpStatus.UNAUTHORIZED, "invalid_client", "Client authentication failed.");
        }
        String grantType = single(form, "grant_type");
        if ("authorization_code".equals(grantType)) {
            String code = single(form, "code");
            String redirectUri = single(form, "redirect_uri");
            String verifier = single(form, "code_verifier");
            if (code == null || redirectUri == null || verifier == null) {
                return oauthError(HttpStatus.BAD_REQUEST, "invalid_grant", "Authorization grant is invalid.");
            }
            try {
                String subject = codes.consume(code, clientId, URI.create(redirectUri), verifier);
                return tokenResponse(tokens.issue(subject));
            } catch (AuthorizationCodeStore.InvalidAuthorizationCodeException | IllegalArgumentException
                     | TokenStore.InvalidTokenException exception) {
                return oauthError(HttpStatus.BAD_REQUEST, "invalid_grant", "Authorization grant is invalid.");
            }
        }
        if ("refresh_token".equals(grantType)) {
            String refreshToken = single(form, "refresh_token");
            if (refreshToken == null) {
                return oauthError(HttpStatus.BAD_REQUEST, "invalid_grant", "Refresh grant is invalid.");
            }
            try {
                return tokenResponse(tokens.refresh(refreshToken));
            } catch (TokenStore.InvalidTokenException exception) {
                return oauthError(HttpStatus.BAD_REQUEST, "invalid_grant", "Refresh grant is invalid.");
            }
        }
        return oauthError(HttpStatus.BAD_REQUEST, "unsupported_grant_type", "Grant type is not supported.");
    }

    @GetMapping(path = "/oauth2/userinfo", produces = "application/json")
    public ResponseEntity<?> userInfo(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String header) {
        String accessToken = bearerToken(header);
        if (accessToken == null) {
            return oauthError(HttpStatus.UNAUTHORIZED, "invalid_token", "Access token is invalid.");
        }
        return tokens.findIdentityByAccessToken(accessToken)
                .<ResponseEntity<?>>map(claims -> ResponseEntity.ok().contentType(JSON).body(claims))
                .orElseGet(() -> oauthError(HttpStatus.UNAUTHORIZED, "invalid_token", "Access token is invalid."));
    }

    @PostMapping(path = "/oauth2/revoke", consumes = "application/x-www-form-urlencoded")
    public Mono<ResponseEntity<?>> revoke(ServerWebExchange exchange) {
        return exchange.getFormData().map(form -> {
            if (!validClient(form)) {
                return oauthError(HttpStatus.UNAUTHORIZED, "invalid_client", "Client authentication failed.");
            }
            String token = single(form, "token");
            if (token != null) {
                try {
                    tokens.revoke(token);
                } catch (TokenStore.InvalidTokenException ignored) {
                    // RFC 7009 revocation is deliberately idempotent for unknown tokens.
                }
            }
            return ResponseEntity.ok().build();
        }).defaultIfEmpty(ResponseEntity.ok().build());
    }

    private boolean validClient(MultiValueMap<String, String> form) {
        String requestedClientId = single(form, "client_id");
        String requestedSecret = single(form, "client_secret");
        return clientId.equals(requestedClientId)
                && (clientSecret == null || clientSecret.isBlank() || clientSecret.equals(requestedSecret));
    }

    private ResponseEntity<?> tokenResponse(TokenStore.TokenPair pair) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", pair.accessToken());
        body.put("token_type", "Bearer");
        body.put("expires_in", 3600);
        body.put("refresh_token", pair.refreshToken());
        return ResponseEntity.ok().contentType(JSON).body(body);
    }

    private static ResponseEntity<Map<String, String>> oauthError(HttpStatus status, String error,
                                                                   String description) {
        return ResponseEntity.status(status).contentType(JSON)
                .body(Map.of("error", error, "error_description", description));
    }

    private static String bearerToken(String header) {
        if (header == null) {
            return null;
        }
        int separator = header.indexOf(' ');
        if (separator < 0 || !"bearer".equals(header.substring(0, separator).toLowerCase(Locale.ROOT))) {
            return null;
        }
        String token = header.substring(separator + 1);
        return token.isBlank() || token.chars().anyMatch(Character::isWhitespace) ? null : token;
    }

    private static String single(MultiValueMap<String, String> form, String name) {
        var entries = form.get(name);
        return entries == null || entries.size() != 1 ? null : entries.getFirst();
    }
}
