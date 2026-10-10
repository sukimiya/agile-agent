package io.github.sukimiya.agileagent.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sukimiya.agileagent.auth.IdentityMappingException;
import io.github.sukimiya.agileagent.auth.JsonPointerOAuth2IdentityMapper;
import io.github.sukimiya.agileagent.config.OAuth2Properties;
import io.github.sukimiya.agileagent.service.OAuth2Client;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuthExceptionHandlerTest {

    private final AuthExceptionHandler handler = new AuthExceptionHandler();

    @Test
    void authenticationAbsenceReturnsStableJson401() {
        var response = handler.handleUnauthorized(new UnauthorizedException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(new ApiError("UNAUTHORIZED", "Authentication required"));
    }

    @Test
    void authorizationDenialReturnsStableJson403() {
        var response = handler.handleForbidden(new ForbiddenException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isEqualTo(new ApiError("FORBIDDEN", "Access denied"));
    }

    @Test
    void invalidIdentityClaimsReturnGenericStableJson502() throws Exception {
        var mapper = new JsonPointerOAuth2IdentityMapper(new OAuth2Properties(null, null, null, null,
                null, null, null, null, null, null));
        IdentityMappingException exception = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> mapper.map(new ObjectMapper().readTree("{\"name\":\"sensitive display\",\"roles\":[]}")),
                IdentityMappingException.class);

        var response = handler.handleIdentityMapping(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isEqualTo(new ApiError("IDENTITY_CLAIMS_INVALID", "Identity claims are invalid"));
        assertThat(response.getBody().toString()).doesNotContain("sensitive", "subject", "/sub");
    }

    @Test
    void identityProviderFailureReturnsGenericStableJson502() {
        var exception = new OAuth2Client.OAuth2UnavailableException("provider leaked secret token and tenant-id");

        var response = handler.handleIdentityProviderUnavailable(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isEqualTo(
                new ApiError("IDENTITY_PROVIDER_UNAVAILABLE", "Identity provider unavailable"));
        assertThat(response.getBody().toString()).doesNotContain("secret", "tenant-id", "provider leaked");
    }
}
