package com.settl.backend.audit;

import com.settl.backend.audit.dto.AuditLogResponse;
import com.settl.backend.auth.CustomUserPrincipal;
import com.settl.backend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/groups/{groupId}/activity")
@Tag(name = "Activity & Audit Log", description = "Group activity feed and audit trail for tracking expenses, settlements, and membership events")
@SecurityRequirement(name = "BearerAuth")
public class ActivityController {

    private final AuditService auditService;

    public ActivityController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "Get group activity feed", description = "Retrieves paginated audit log events for the group (newest first)")
    public ResponseEntity<ApiResponse<Page<AuditLogResponse>>> getGroupActivity(
            @PathVariable("groupId") UUID groupId,
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<AuditLogResponse> response = auditService.getGroupActivity(groupId, principal.id(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "Activity feed retrieved successfully"));
    }
}
