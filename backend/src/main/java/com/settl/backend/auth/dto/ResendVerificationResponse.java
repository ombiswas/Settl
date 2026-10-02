package com.settl.backend.auth.dto;

public record ResendVerificationResponse(
        String message,
        boolean alreadyVerified
) {}
