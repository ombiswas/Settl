package com.settl.backend.group.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String name,
        String defaultCurrency,
        UUID createdBy,
        Instant createdAt,
        List<GroupMemberDto> members,
        int memberCount
) {
}
