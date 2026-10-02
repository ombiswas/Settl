package com.settl.backend.auth.event;

public record GroupInvitationEmailEvent(
        String toEmail,
        String inviterName,
        String groupName,
        String actionUrl,
        boolean isNewUser
) {}
