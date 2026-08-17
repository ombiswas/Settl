package com.settl.backend.audit;

import com.settl.backend.audit.dto.AuditLogResponse;
import com.settl.backend.group.Group;
import com.settl.backend.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

public interface AuditService {

    void logActivity(Group group, User actor, AuditAction action, Map<String, Object> details);

    Page<AuditLogResponse> getGroupActivity(UUID groupId, UUID callerId, Pageable pageable);
}
