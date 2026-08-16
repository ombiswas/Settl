package com.settl.backend.group.dto;

import java.time.Instant;
import java.util.UUID;

public record GroupMemberDto(
        UUID userId,
        String email,
        String displayName,
        boolean isAdmin,
        Instant joinedAt
) {
}
