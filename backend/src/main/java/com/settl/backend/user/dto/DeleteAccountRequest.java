package com.settl.backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request payload for permanently deleting an account")
public record DeleteAccountRequest(
        @NotBlank(message = "Password is required to confirm account deletion")
        @Schema(description = "Current user password for re-authentication", example = "SecurePassword123!")
        String password,

        @NotBlank(message = "Confirmation keyword is required")
        @Schema(description = "Must match 'DELETE' or the user's email", example = "DELETE")
        String confirmation
) {}
