package com.settl.backend.auth.event;

public record VerificationEmailEvent(
        String toEmail,
        String displayName,
        String verificationUrl
) {}
