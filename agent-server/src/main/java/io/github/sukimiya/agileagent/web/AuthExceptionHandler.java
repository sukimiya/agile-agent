package io.github.sukimiya.agileagent.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.sukimiya.agileagent.auth.IdentityMappingException;
import io.github.sukimiya.agileagent.service.OAuth2Client.OAuth2UnavailableException;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("UNAUTHORIZED", "Authentication required"));
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ApiError> handleForbidden(ForbiddenException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", "Access denied"));
    }

    @ExceptionHandler(IdentityMappingException.class)
    ResponseEntity<ApiError> handleIdentityMapping(IdentityMappingException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError(IdentityMappingException.CODE, "Identity claims are invalid"));
    }

    @ExceptionHandler(OAuth2UnavailableException.class)
    ResponseEntity<ApiError> handleIdentityProviderUnavailable(OAuth2UnavailableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("IDENTITY_PROVIDER_UNAVAILABLE", "Identity provider unavailable"));
    }
}
