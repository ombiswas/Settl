package com.settl.backend.group.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.settl.backend.group.GroupInvitationStatus;

import java.time.Instant;
import java.util.UUID;

public record GroupInvitationDto(
        UUID id,
        UUID groupId,
        String email,
        UUID invitedById,
        String invitedByName,
        @JsonProperty("isAdmin")
        boolean isAdmin,
        GroupInvitationStatus status,
        Instant expiresAt,
        Instant createdAt
) {
    @JsonProperty("admin")
    public boolean admin() {
        return isAdmin;
    }
}
