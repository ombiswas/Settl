package com.settl.backend.audit;

import com.settl.backend.audit.dto.AuditLogResponse;
import com.settl.backend.common.ApiException;
import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditLogRepository auditLogRepository;
    private final GroupMemberRepository groupMemberRepository;

    public AuditServiceImpl(
            AuditLogRepository auditLogRepository,
            GroupMemberRepository groupMemberRepository
    ) {
        this.auditLogRepository = auditLogRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void logActivity(Group group, User actor, AuditAction action, Map<String, Object> details) {
        try {
            AuditLogEntry entry = new AuditLogEntry(group, actor, action, details);
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to record audit log entry for group id={}, action={}", group.getId(), action, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getGroupActivity(UUID groupId, UUID callerId, Pageable pageable) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, callerId)) {
            throw ApiException.forbidden("You must be a member of this group to view its activity feed", "NOT_A_GROUP_MEMBER");
        }

        Page<AuditLogEntry> entries = auditLogRepository.findByGroupIdOrderByCreatedAtDesc(groupId, pageable);
        return entries.map(this::mapToResponse);
    }

    private AuditLogResponse mapToResponse(AuditLogEntry entry) {
        return new AuditLogResponse(
                entry.getId(),
                entry.getGroup().getId(),
                entry.getActor() != null ? entry.getActor().getId() : null,
                entry.getActor() != null ? entry.getActor().getDisplayName() : "System",
                entry.getActor() != null ? entry.getActor().getEmail() : "system@settl.local",
                entry.getAction(),
                entry.getDetails(),
                entry.getCreatedAt()
        );
    }
}
