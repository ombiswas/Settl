package com.settl.backend.group.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record InvitationPreviewResponse(
        UUID invitationId,
        UUID groupId,
        String groupName,
        String defaultCurrency,
        String email,
        String inviterName,
        @JsonProperty("isAdmin")
        boolean isAdmin,
        Instant expiresAt,
        boolean isExpired
) {
    @JsonProperty("admin")
    public boolean admin() {
        return isAdmin;
    }
}
