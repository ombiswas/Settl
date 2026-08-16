package com.settl.backend.auth.dto;

public record VerifyEmailResponse(
        boolean verified,
        String message
) {
}
