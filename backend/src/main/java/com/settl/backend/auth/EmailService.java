package com.settl.backend.auth;

public interface EmailService {

    void sendVerificationEmail(String toEmail, String displayName, String verificationUrl);

    void sendGroupInvitationEmail(String toEmail, String inviterName, String groupName, String actionUrl, boolean isNewUser);
}
