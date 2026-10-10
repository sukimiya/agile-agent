package io.github.sukimiya.agileagent.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.sukimiya.agileagent.auth.IdentityMappingException;
import io.github.sukimiya.agileagent.llmconfig.LlmProviderPresetNotFoundException;
import io.github.sukimiya.agileagent.llmconfig.LlmProviderPresetValidationException;
import io.github.sukimiya.agileagent.llmconfig.LlmConfigValidationException;
import io.github.sukimiya.agileagent.llmconfig.LlmBalanceException;
import io.github.sukimiya.agileagent.service.TenantLlmConfigRequiredException;
import io.github.sukimiya.agileagent.service.OAuth2Client.OAuth2UnavailableException;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(TenantLlmConfigRequiredException.class)
    ResponseEntity<ApiError> handleTenantLlmConfigRequired(TenantLlmConfigRequiredException exception) {
        return ResponseEntity.status(428)
                .body(new ApiError("LLM_CONFIG_REQUIRED", "Current tenant has no LLM configuration"));
    }

    @ExceptionHandler(LlmBalanceException.class)
    ResponseEntity<ApiError> handleLlmBalance(LlmBalanceException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiError(exception.code(), exception.getMessage()));
    }

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

    @ExceptionHandler(LlmProviderPresetValidationException.class)
    ResponseEntity<ApiError> handleInvalidProviderPreset(LlmProviderPresetValidationException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_LLM_PROVIDER_PRESET", "Provider preset input is invalid"));
    }

    @ExceptionHandler(LlmProviderPresetNotFoundException.class)
    ResponseEntity<ApiError> handleProviderPresetNotFound(LlmProviderPresetNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("LLM_PROVIDER_PRESET_NOT_FOUND", "Provider preset not found"));
    }

    @ExceptionHandler(LlmConfigValidationException.class)
    ResponseEntity<ApiError> handleLlmConfigValidation(LlmConfigValidationException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiError(exception.code(), exception.getMessage()));
    }
}
