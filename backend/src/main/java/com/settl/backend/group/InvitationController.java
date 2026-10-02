package com.settl.backend.group;

import com.settl.backend.auth.CustomUserPrincipal;
import com.settl.backend.common.ApiResponse;
import com.settl.backend.group.dto.GroupResponse;
import com.settl.backend.group.dto.InvitationPreviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations")
@Tag(name = "Invitations", description = "Endpoints for previewing and accepting group invitations")
public class InvitationController {

    private final GroupService groupService;

    public InvitationController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/preview")
    @Operation(summary = "Preview invitation details", description = "Public endpoint to retrieve group and inviter details from an invite token before registration.")
    public ResponseEntity<ApiResponse<InvitationPreviewResponse>> previewInvitation(@RequestParam("token") String token) {
        InvitationPreviewResponse preview = groupService.previewInvitation(token);
        return ResponseEntity.ok(ApiResponse.success(preview, "Invitation preview retrieved"));
    }

    @PostMapping("/accept")
    @Operation(summary = "Accept invitation", description = "Allows an authenticated user to accept an invitation token and join the group.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<GroupResponse>> acceptInvitation(
            @RequestParam("token") String token,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        GroupResponse response = groupService.acceptInvitation(token, principal.id());
        return ResponseEntity.ok(ApiResponse.success(response, "You have joined the group successfully"));
    }
}
