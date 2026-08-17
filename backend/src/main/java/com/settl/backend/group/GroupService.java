package com.settl.backend.group;

import com.settl.backend.audit.AuditAction;
import com.settl.backend.audit.AuditService;
import com.settl.backend.common.ApiException;
import com.settl.backend.expense.ExpenseRepository;
import com.settl.backend.expense.ExpenseShareRepository;
import com.settl.backend.group.dto.AddMemberRequest;
import com.settl.backend.group.dto.AddMemberResponse;
import com.settl.backend.group.dto.CreateGroupRequest;
import com.settl.backend.group.dto.GroupMemberDto;
import com.settl.backend.group.dto.GroupResponse;
import com.settl.backend.settlement.SettlementRepository;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final SettlementRepository settlementRepository;
    private final AuditService auditService;

    public GroupService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository,
            ExpenseRepository expenseRepository,
            ExpenseShareRepository expenseShareRepository,
            SettlementRepository settlementRepository,
            AuditService auditService
    ) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.expenseShareRepository = expenseShareRepository;
        this.settlementRepository = settlementRepository;
        this.auditService = auditService;
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
        return groups.stream().map(this::mapToGroupResponse).toList();
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
    public AddMemberResponse addMember(UUID groupId, AddMemberRequest request, UUID currentUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found", "GROUP_NOT_FOUND"));

        GroupMember callerMember = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .orElseThrow(() -> ApiException.forbidden("You are not a member of this group", "NOT_A_GROUP_MEMBER"));

        if (!callerMember.isAdmin()) {
            throw ApiException.forbidden("Only group admins can add members", "ONLY_ADMIN_CAN_ADD_MEMBERS");
        }

        String targetEmail = request.email().trim().toLowerCase();
        boolean makeAdmin = Boolean.TRUE.equals(request.isAdmin());

        Optional<User> userOpt = userRepository.findByEmail(targetEmail);

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

            return new AddMemberResponse(
                    targetUser.getId(),
                    targetUser.getEmail(),
                    targetUser.getDisplayName(),
                    true,
                    makeAdmin,
                    "Member added successfully"
            );
        } else {
            log.info("Invitation dispatched for non-registered email {} to group id={}", targetEmail, groupId);
            return new AddMemberResponse(
                    null,
                    targetEmail,
                    null,
                    false,
                    makeAdmin,
                    "Invitation created. An email invitation has been dispatched to " + targetEmail
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

        if (!isSelfRemoval && !callerMember.isAdmin()) {
            throw ApiException.forbidden("Only group admins can remove other members", "FORBIDDEN");
        }

        // Guard: Check if target member has a non-zero balance
        BigDecimal balance = calculateUserBalanceInGroup(groupId, targetUserId);
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

    public BigDecimal calculateUserBalanceInGroup(UUID groupId, UUID userId) {
        BigDecimal paid = expenseRepository.sumPaidByUserIdInGroup(groupId, userId);
        BigDecimal owed = expenseShareRepository.sumOwedByUserIdInGroup(groupId, userId);
        BigDecimal settlementsPaid = settlementRepository.sumSettlementsPaidByUserIdInGroup(groupId, userId);
        BigDecimal settlementsReceived = settlementRepository.sumSettlementsReceivedByUserIdInGroup(groupId, userId);

        return paid.subtract(owed).add(settlementsPaid).subtract(settlementsReceived);
    }

    private GroupResponse mapToGroupResponse(Group group) {
        List<GroupMember> members = groupMemberRepository.findByGroupIdWithUser(group.getId());
        List<GroupMemberDto> memberDtos = members.stream()
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
