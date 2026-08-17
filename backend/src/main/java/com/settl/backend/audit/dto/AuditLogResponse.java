package com.settl.backend.audit.dto;

import com.settl.backend.audit.AuditAction;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID groupId,
        UUID actorId,
        String actorName,
        String actorEmail,
        AuditAction action,
        Map<String, Object> details,
        Instant createdAt
) {
}
