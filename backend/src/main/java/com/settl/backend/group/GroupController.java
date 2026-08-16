package com.settl.backend.group;

import com.settl.backend.auth.CustomUserPrincipal;
import com.settl.backend.common.ApiResponse;
import com.settl.backend.group.dto.AddMemberRequest;
import com.settl.backend.group.dto.AddMemberResponse;
import com.settl.backend.group.dto.CreateGroupRequest;
import com.settl.backend.group.dto.GroupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
@Tag(name = "Groups", description = "Group management, membership, and member role administration")
@SecurityRequirement(name = "BearerAuth")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    @Operation(summary = "Create a new group", description = "Creates a group and automatically registers the caller as group admin")
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        GroupResponse response = groupService.createGroup(request, principal.id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Group created successfully"));
    }

    @GetMapping
    @Operation(summary = "List user groups", description = "Lists all groups the authenticated user is a member of")
    public ResponseEntity<ApiResponse<List<GroupResponse>>> getUserGroups(
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        List<GroupResponse> groups = groupService.getUserGroups(principal.id());
        return ResponseEntity.ok(ApiResponse.success(groups));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get group details", description = "Fetches full details and member list for a group. Forbidden if caller is not a member.")
    public ResponseEntity<ApiResponse<GroupResponse>> getGroupDetails(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        GroupResponse response = groupService.getGroupDetails(id, principal.id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Add member to group", description = "Adds a registered user by email or creates an invitation for an unregistered user. Caller must be group admin.")
    public ResponseEntity<ApiResponse<AddMemberResponse>> addMember(
            @PathVariable("id") UUID id,
            @Valid @RequestBody AddMemberRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        AddMemberResponse response = groupService.addMember(id, request, principal.id());
        return ResponseEntity.ok(ApiResponse.success(response, response.message()));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Operation(summary = "Remove member from group", description = "Removes a member from the group. Guarded by admin check and zero-balance requirement.")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable("id") UUID id,
            @PathVariable("userId") UUID userId,
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        groupService.removeMember(id, userId, principal.id());
        return ResponseEntity.ok(ApiResponse.success(null, "Member removed successfully"));
    }
}
