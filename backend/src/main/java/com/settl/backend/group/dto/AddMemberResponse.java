package com.settl.backend.group.dto;

import java.util.UUID;

public record AddMemberResponse(
        UUID userId,
        String email,
        String displayName,
        boolean isExistingUser,
        boolean isAdmin,
        String message
) {
}
