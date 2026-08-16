package com.settl.backend.auth;

public interface EmailService {

    void sendVerificationEmail(String toEmail, String displayName, String verificationUrl);
}
