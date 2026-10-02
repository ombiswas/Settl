package com.settl.backend.group;

import com.settl.backend.audit.AuditAction;
import com.settl.backend.audit.AuditService;
import com.settl.backend.common.ApiException;
import com.settl.backend.group.dto.AddMemberRequest;
import com.settl.backend.group.dto.AddMemberResponse;
import com.settl.backend.group.dto.CreateGroupRequest;
import com.settl.backend.group.dto.GroupMemberDto;
import com.settl.backend.group.dto.GroupResponse;
import com.settl.backend.group.dto.UpdateGroupRequest;
import com.settl.backend.settlement.BalanceService;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import com.settl.backend.auth.AuthService;
import com.settl.backend.group.dto.GroupInvitationDto;
import com.settl.backend.group.dto.InvitationPreviewResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupInvitationRepository groupInvitationRepository;
    private final UserRepository userRepository;
    private final BalanceService balanceService;
    private final AuditService auditService;
    private final com.settl.backend.auth.EmailService emailService;

    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String appBaseUrl = "http://localhost:5173";

    public GroupService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            GroupInvitationRepository groupInvitationRepository,
            UserRepository userRepository,
            BalanceService balanceService,
            AuditService auditService,
            com.settl.backend.auth.EmailService emailService
    ) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupInvitationRepository = groupInvitationRepository;
        this.userRepository = userRepository;
        this.balanceService = balanceService;
        this.auditService = auditService;
        this.emailService = emailService;
    }

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, UUID currentUserId) {
        String currencyCode = validateAndNormalizeCurrency(request.defaultCurrency());

        User caller = userRepository.findById(currentUserId)
                .orElseThrow(() -> ApiException.notFound("User not found", "USER_NOT_FOUND"));

        Group group = new Group(request.name().trim(), currencyCode, caller);
        Group savedGroup = groupRepository.save(group);

        GroupMember adminMember = new GroupMember(savedGroup, caller, true);
        groupMemberRepository.save(adminMember);

        log.info("Group '{}' (id={}) created by user id={}", savedGroup.getName(), savedGroup.getId(), currentUserId);

        Map<String, Object> details = new HashMap<>();
        details.put("groupName", savedGroup.getName());
        details.put("currency", savedGroup.getDefaultCurrency());
        auditService.logActivity(savedGroup, caller, AuditAction.GROUP_CREATED, details);

        GroupMemberDto memberDto = new GroupMemberDto(
                caller.getId(),
                caller.getEmail(),
                caller.getDisplayName(),
                true,
                adminMember.getJoinedAt()
        );

        return new GroupResponse(
                savedGroup.getId(),
                savedGroup.getName(),
                savedGroup.getDefaultCurrency(),
                savedGroup.getCreatedBy().getId(),
                savedGroup.getCreatedAt(),
                List.of(memberDto),
                1
        );
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> getUserGroups(UUID currentUserId) {
        List<Group> groups = groupRepository.findGroupsByUserId(currentUserId);
        if (groups.isEmpty()) {
            return List.of();
        }

        List<UUID> groupIds = groups.stream().map(Group::getId).toList();
        List<GroupMember> allMembers = groupMemberRepository.findByGroupIdInWithUser(groupIds);

        Map<UUID, List<GroupMember>> membersByGroupId = allMembers.stream()
                .collect(Collectors.groupingBy(gm -> gm.getId().getGroupId()));

        return groups.stream()
                .map(group -> mapToGroupResponse(group, membersByGroupId.getOrDefault(group.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroupDetails(UUID groupId, UUID currentUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found", "GROUP_NOT_FOUND"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, currentUserId)) {
            throw ApiException.forbidden("You are not a member of this group", "NOT_A_GROUP_MEMBER");
        }

        return mapToGroupResponse(group);
    }

    @Transactional
    public GroupResponse updateGroup(UUID groupId, UpdateGroupRequest request, UUID currentUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found", "GROUP_NOT_FOUND"));

        GroupMember callerMember = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this group", "NOT_A_GROUP_MEMBER"));

        boolean isCreator = group.getCreatedBy() != null && group.getCreatedBy().getId().equals(currentUserId);
        if (!callerMember.isAdmin() && !isCreator) {
            throw ApiException.forbidden("Only group admins can update group details", "ONLY_ADMIN_CAN_UPDATE_GROUP");
        }

        String oldName = group.getName();
        String newName = request.name().trim();
        group.setName(newName);

        String oldCurrency = group.getDefaultCurrency();
        if (request.defaultCurrency() != null && !request.defaultCurrency().isBlank()) {
            String newCurrency = validateAndNormalizeCurrency(request.defaultCurrency());
            group.setDefaultCurrency(newCurrency);
        }

        Group savedGroup = groupRepository.save(group);
        log.info("Group id={} updated by admin user id={}: name='{}', currency='{}'",
                groupId, currentUserId, savedGroup.getName(), savedGroup.getDefaultCurrency());

        Map<String, Object> details = new HashMap<>();
        details.put("oldName", oldName);
        details.put("newName", newName);
        details.put("oldCurrency", oldCurrency);
        details.put("newCurrency", savedGroup.getDefaultCurrency());
        auditService.logActivity(savedGroup, callerMember.getUser(), AuditAction.GROUP_UPDATED, details);

        return mapToGroupResponse(savedGroup);
    }

    @Transactional
    public AddMemberResponse addMember(UUID groupId, AddMemberRequest request, UUID currentUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found", "GROUP_NOT_FOUND"));

        GroupMember callerMember = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this group", "NOT_A_GROUP_MEMBER"));

        boolean isCreator = group.getCreatedBy() != null && group.getCreatedBy().getId().equals(currentUserId);
        if (!callerMember.isAdmin() && !isCreator) {
            throw ApiException.forbidden("Only group admins can add members", "ONLY_ADMIN_CAN_ADD_MEMBERS");
        }

        String targetEmail = request.email().trim().toLowerCase();
        boolean makeAdmin = Boolean.TRUE.equals(request.isAdmin());

        Optional<User> userOpt = userRepository.findByEmail(targetEmail);

        String baseUrl = appBaseUrl.split(",")[0].trim();
        String inviterName = callerMember.getUser().getDisplayName();

        if (userOpt.isPresent()) {
            User targetUser = userOpt.get();

            if (groupMemberRepository.existsByGroupIdAndUserId(groupId, targetUser.getId())) {
                throw ApiException.conflict("User is already a member of this group", "MEMBER_ALREADY_EXISTS");
            }

            GroupMember newMember = new GroupMember(group, targetUser, makeAdmin);
            groupMemberRepository.save(newMember);

            log.info("User id={} added to group id={} by caller id={}", targetUser.getId(), groupId, currentUserId);

            Map<String, Object> details = new HashMap<>();
            details.put("addedUserId", targetUser.getId().toString());
            details.put("addedUserEmail", targetUser.getEmail());
            details.put("addedUserName", targetUser.getDisplayName());
            details.put("isAdmin", makeAdmin);
            auditService.logActivity(group, callerMember.getUser(), AuditAction.MEMBER_JOINED, details);

            String groupUrl = baseUrl + "/groups/" + groupId;
            emailService.sendGroupInvitationEmail(targetUser.getEmail(), inviterName, group.getName(), groupUrl, false);

            return new AddMemberResponse(
                    targetUser.getId(),
                    targetUser.getEmail(),
                    targetUser.getDisplayName(),
                    true,
                    makeAdmin,
                    "Member added successfully and notification email dispatched"
            );
        } else {
            String rawToken = generateSecureToken();
            String hashedToken = AuthService.hashToken(rawToken);
            Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);

            Optional<GroupInvitation> existingOpt = groupInvitationRepository
                    .findByGroupIdAndEmailAndStatus(groupId, targetEmail, GroupInvitationStatus.PENDING);

            GroupInvitation invitation;
            if (existingOpt.isPresent()) {
                invitation = existingOpt.get();
                invitation.setTokenHash(hashedToken);
                invitation.setExpiresAt(expiresAt);
                invitation.setAdmin(makeAdmin);
                invitation.setInvitedBy(callerMember.getUser());
            } else {
                invitation = new GroupInvitation(group, targetEmail, callerMember.getUser(), hashedToken, makeAdmin, expiresAt);
            }
            groupInvitationRepository.save(invitation);

            log.info("Invitation dispatched for non-registered email {} to group id={}", targetEmail, groupId);
            String joinUrl = baseUrl + "/join?token=" + rawToken;
            emailService.sendGroupInvitationEmail(targetEmail, inviterName, group.getName(), joinUrl, true);

            return new AddMemberResponse(
                    null,
                    targetEmail,
                    null,
                    false,
                    makeAdmin,
                    "Invitation dispatched. An email with a secure join link has been sent to " + targetEmail
            );
        }
    }

    @Transactional
    public void removeMember(UUID groupId, UUID targetUserId, UUID currentUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found", "GROUP_NOT_FOUND"));

        GroupMember callerMember = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this group", "NOT_A_GROUP_MEMBER"));

        GroupMember targetMember = groupMemberRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> ApiException.notFound("Target member not found in this group", "MEMBER_NOT_FOUND"));

        boolean isSelfRemoval = currentUserId.equals(targetUserId);
        boolean isCreator = group.getCreatedBy() != null && group.getCreatedBy().getId().equals(currentUserId);

        if (!isSelfRemoval && !callerMember.isAdmin() && !isCreator) {
            throw ApiException.forbidden("Only group admins can remove other members", "FORBIDDEN");
        }

        // Guard: Check if target member has a non-zero balance
        BigDecimal balance = balanceService.calculateUserBalanceInGroup(groupId, targetUserId);
        if (balance.abs().compareTo(new BigDecimal("0.005")) >= 0) {
            throw ApiException.badRequest(
                    "Cannot remove member with non-zero balance (" + balance.toPlainString() + " " + group.getDefaultCurrency() + "). All debts must be settled first.",
                    "UNSETTLED_BALANCE"
            );
        }

        // Guard: Prevent removing the only admin if group has other members
        if (targetMember.isAdmin()) {
            long totalAdmins = groupMemberRepository.countAdminsInGroup(groupId);
            long totalMembers = groupMemberRepository.countMembersInGroup(groupId);

            if (totalAdmins <= 1 && totalMembers > 1) {
                throw ApiException.badRequest(
                        "Cannot remove the only group admin. Promote another member to admin before leaving.",
                        "LAST_ADMIN_CANNOT_LEAVE"
                );
            }
        }

        Map<String, Object> details = new HashMap<>();
        details.put("removedUserId", targetMember.getUser().getId().toString());
        details.put("removedUserName", targetMember.getUser().getDisplayName());
        details.put("removedBy", callerMember.getUser().getDisplayName());
        auditService.logActivity(group, callerMember.getUser(), AuditAction.MEMBER_REMOVED, details);

        groupMemberRepository.deleteByGroupIdAndUserId(groupId, targetUserId);
        log.info("Member id={} removed from group id={} by caller id={}", targetUserId, groupId, currentUserId);
    }

    @Transactional
    public void deleteGroup(UUID groupId, UUID currentUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found", "GROUP_NOT_FOUND"));

        GroupMember callerMember = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this group", "NOT_A_GROUP_MEMBER"));

        boolean isCreator = group.getCreatedBy() != null && group.getCreatedBy().getId().equals(currentUserId);
        if (!callerMember.isAdmin() && !isCreator) {
            throw ApiException.forbidden("Only group admins can delete the group", "ONLY_ADMIN_CAN_DELETE_GROUP");
        }

        // Guard: Check if any member has an unsettled non-zero balance
        Map<UUID, BigDecimal> netBalances = balanceService.getGroupNetBalances(groupId);
        for (BigDecimal balance : netBalances.values()) {
            if (balance.abs().compareTo(new BigDecimal("0.005")) >= 0) {
                throw ApiException.badRequest(
                        "Cannot delete group with unsettled balances. All member debts must be settled first.",
                        "UNSETTLED_GROUP_BALANCES"
                );
            }
        }

        groupMemberRepository.deleteAllByGroupId(groupId);
        groupRepository.deleteGroupById(groupId);
        log.info("Group '{}' (id={}) deleted by admin user id={}", group.getName(), groupId, currentUserId);
    }

    private GroupResponse mapToGroupResponse(Group group) {
        List<GroupMember> members = groupMemberRepository.findByGroupIdWithUser(group.getId());
        return mapToGroupResponse(group, members);
    }

    private GroupResponse mapToGroupResponse(Group group, List<GroupMember> members) {
        List<GroupMemberDto> memberDtos = (members != null ? members : List.<GroupMember>of()).stream()
                .map(gm -> new GroupMemberDto(
                        gm.getUser().getId(),
                        gm.getUser().getEmail(),
                        gm.getUser().getDisplayName(),
                        gm.isAdmin(),
                        gm.getJoinedAt()
                ))
                .toList();

        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDefaultCurrency(),
                group.getCreatedBy() != null ? group.getCreatedBy().getId() : null,
                group.getCreatedAt(),
                memberDtos,
                memberDtos.size()
        );
    }

    @Transactional(readOnly = true)
    public List<GroupInvitationDto> getPendingInvitations(UUID groupId, UUID currentUserId) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, currentUserId)) {
            throw ApiException.forbidden("You must be a member of this group to view invitations", "NOT_A_GROUP_MEMBER");
        }

        List<GroupInvitation> invitations = groupInvitationRepository
                .findByGroupIdAndStatusWithInviter(groupId, GroupInvitationStatus.PENDING);

        Instant now = Instant.now();
        return invitations.stream()
                .filter(inv -> !inv.getExpiresAt().isBefore(now))
                .map(this::mapToInvitationDto)
                .toList();
    }

    @Transactional
    public GroupInvitationDto resendInvitation(UUID groupId, UUID invitationId, UUID currentUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found", "GROUP_NOT_FOUND"));

        GroupMember caller = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this group", "NOT_A_GROUP_MEMBER"));

        boolean isCreator = group.getCreatedBy() != null && group.getCreatedBy().getId().equals(currentUserId);
        if (!caller.isAdmin() && !isCreator) {
            throw ApiException.forbidden("Only group admins can manage invitations", "ONLY_ADMIN_CAN_MANAGE_INVITATIONS");
        }

        GroupInvitation invitation = groupInvitationRepository.findById(invitationId)
                .orElseThrow(() -> ApiException.notFound("Invitation not found", "INVITATION_NOT_FOUND"));

        if (!invitation.getGroup().getId().equals(groupId)) {
            throw ApiException.badRequest("Invitation does not belong to this group", "INVALID_INVITATION");
        }

        if (invitation.getStatus() != GroupInvitationStatus.PENDING) {
            throw ApiException.badRequest("Only pending invitations can be resent", "INVITATION_NOT_PENDING");
        }

        String rawToken = generateSecureToken();
        String hashedToken = AuthService.hashToken(rawToken);
        invitation.setTokenHash(hashedToken);
        invitation.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        invitation.setInvitedBy(caller.getUser());
        GroupInvitation saved = groupInvitationRepository.save(invitation);

        String baseUrl = appBaseUrl.split(",")[0].trim();
        String joinUrl = baseUrl + "/join?token=" + rawToken;
        emailService.sendGroupInvitationEmail(saved.getEmail(), caller.getUser().getDisplayName(), group.getName(), joinUrl, true);

        return mapToInvitationDto(saved);
    }

    @Transactional
    public void revokeInvitation(UUID groupId, UUID invitationId, UUID currentUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found", "GROUP_NOT_FOUND"));

        GroupMember caller = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this group", "NOT_A_GROUP_MEMBER"));

        boolean isCreator = group.getCreatedBy() != null && group.getCreatedBy().getId().equals(currentUserId);
        if (!caller.isAdmin() && !isCreator) {
            throw ApiException.forbidden("Only group admins can manage invitations", "ONLY_ADMIN_CAN_MANAGE_INVITATIONS");
        }

        GroupInvitation invitation = groupInvitationRepository.findById(invitationId)
                .orElseThrow(() -> ApiException.notFound("Invitation not found", "INVITATION_NOT_FOUND"));

        if (!invitation.getGroup().getId().equals(groupId)) {
            throw ApiException.badRequest("Invitation does not belong to this group", "INVALID_INVITATION");
        }

        invitation.setStatus(GroupInvitationStatus.REVOKED);
        groupInvitationRepository.save(invitation);
    }

    @Transactional(readOnly = true)
    public InvitationPreviewResponse previewInvitation(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.badRequest("Invitation token is required", "INVALID_TOKEN");
        }

        String tokenHash = AuthService.hashToken(rawToken.trim());
        GroupInvitation invitation = groupInvitationRepository.findByTokenHashWithGroupAndInviter(tokenHash)
                .orElseThrow(() -> ApiException.notFound("Invitation not found or invalid", "INVITATION_NOT_FOUND"));

        if (invitation.getStatus() == GroupInvitationStatus.REVOKED) {
            throw ApiException.badRequest("This invitation has been revoked by the group admin", "INVITATION_REVOKED");
        }

        if (invitation.getStatus() == GroupInvitationStatus.ACCEPTED) {
            throw ApiException.badRequest("This invitation has already been accepted", "INVITATION_ALREADY_ACCEPTED");
        }

        boolean expired = invitation.isExpired();
        return new InvitationPreviewResponse(
                invitation.getId(),
                invitation.getGroup().getId(),
                invitation.getGroup().getName(),
                invitation.getGroup().getDefaultCurrency(),
                invitation.getEmail(),
                invitation.getInvitedBy().getDisplayName(),
                invitation.isAdmin(),
                invitation.getExpiresAt(),
                expired
        );
    }

    @Transactional
    public GroupResponse acceptInvitation(String rawToken, UUID userId) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.badRequest("Invitation token is required", "INVALID_TOKEN");
        }

        String tokenHash = AuthService.hashToken(rawToken.trim());
        GroupInvitation invitation = groupInvitationRepository.findByTokenHashWithGroupAndInviter(tokenHash)
                .orElseThrow(() -> ApiException.notFound("Invitation not found or invalid", "INVITATION_NOT_FOUND"));

        if (invitation.getStatus() != GroupInvitationStatus.PENDING) {
            throw ApiException.badRequest("Invitation is no longer active", "INVITATION_INACTIVE");
        }

        if (invitation.isExpired()) {
            invitation.setStatus(GroupInvitationStatus.EXPIRED);
            groupInvitationRepository.save(invitation);
            throw ApiException.badRequest("Invitation token has expired", "INVITATION_EXPIRED");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found", "USER_NOT_FOUND"));

        if (!user.getEmail().equalsIgnoreCase(invitation.getEmail())) {
            throw ApiException.forbidden("This invitation was not sent to your email", "INVITATION_EMAIL_MISMATCH");
        }

        Group group = invitation.getGroup();

        if (!groupMemberRepository.existsByGroupIdAndUserId(group.getId(), user.getId())) {
            GroupMember member = new GroupMember(group, user, invitation.isAdmin());
            groupMemberRepository.save(member);

            Map<String, Object> details = new HashMap<>();
            details.put("addedUserId", user.getId().toString());
            details.put("addedUserEmail", user.getEmail());
            details.put("addedUserName", user.getDisplayName());
            details.put("isAdmin", invitation.isAdmin());
            details.put("viaInvitation", true);
            auditService.logActivity(group, user, AuditAction.MEMBER_JOINED, details);
        }

        invitation.setStatus(GroupInvitationStatus.ACCEPTED);
        groupInvitationRepository.save(invitation);

        return mapToGroupResponse(group);
    }

    private GroupInvitationDto mapToInvitationDto(GroupInvitation inv) {
        return new GroupInvitationDto(
                inv.getId(),
                inv.getGroup().getId(),
                inv.getEmail(),
                inv.getInvitedBy().getId(),
                inv.getInvitedBy().getDisplayName(),
                inv.isAdmin(),
                inv.getStatus(),
                inv.getExpiresAt(),
                inv.getCreatedAt()
        );
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return UUID.randomUUID().toString().replace("-", "") + HexFormat.of().formatHex(randomBytes);
    }

    private String validateAndNormalizeCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.trim().length() != 3) {
            throw ApiException.badRequest("Currency code must be a 3-letter ISO-4217 code", "INVALID_CURRENCY");
        }
        String normalized = currencyCode.trim().toUpperCase();
        try {
            Currency.getInstance(normalized);
            return normalized;
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Invalid ISO-4217 currency code: " + normalized, "INVALID_CURRENCY");
        }
    }
}
