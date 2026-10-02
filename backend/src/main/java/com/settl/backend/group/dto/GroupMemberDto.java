package com.settl.backend.group.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record GroupMemberDto(
        UUID userId,
        String email,
        String displayName,
        @JsonProperty("isAdmin")
        boolean isAdmin,
        Instant joinedAt
) {
    @JsonProperty("admin")
    public boolean admin() {
        return isAdmin;
    }
}
