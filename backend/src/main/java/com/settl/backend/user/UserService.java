package com.settl.backend.user;

import com.settl.backend.audit.AuditAction;
import com.settl.backend.audit.AuditService;
import com.settl.backend.auth.AuthService;
import com.settl.backend.auth.RefreshTokenRepository;
import com.settl.backend.common.ApiException;
import com.settl.backend.common.CookieFactory;
import com.settl.backend.expense.ExpenseRepository;
import com.settl.backend.expense.ExpenseShareRepository;
import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMember;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.group.GroupService;
import com.settl.backend.recurring.RecurringExpenseRepository;
import com.settl.backend.settlement.BalanceService;
import com.settl.backend.settlement.SettlementRepository;
import com.settl.backend.user.dto.DeleteAccountRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final SettlementRepository settlementRepository;
    private final BalanceService balanceService;
    private final RecurringExpenseRepository recurringExpenseRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final CookieFactory cookieFactory;
    private final com.settl.backend.settlement.GroupBalanceCacheEvictor groupBalanceCacheEvictor;

    public UserService(
            UserRepository userRepository,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            ExpenseRepository expenseRepository,
            ExpenseShareRepository expenseShareRepository,
            SettlementRepository settlementRepository,
            BalanceService balanceService,
            RecurringExpenseRepository recurringExpenseRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuditService auditService,
            PasswordEncoder passwordEncoder,
            CookieFactory cookieFactory,
            com.settl.backend.settlement.GroupBalanceCacheEvictor groupBalanceCacheEvictor
    ) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.expenseRepository = expenseRepository;
        this.expenseShareRepository = expenseShareRepository;
        this.settlementRepository = settlementRepository;
        this.balanceService = balanceService;
        this.recurringExpenseRepository = recurringExpenseRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.cookieFactory = cookieFactory;
        this.groupBalanceCacheEvictor = groupBalanceCacheEvictor;
    }

    @Transactional
    public ResponseCookie deleteAccount(UUID userId, DeleteAccountRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found", "USER_NOT_FOUND"));

        // Guard 1: Verify current password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Incorrect password. Account deletion aborted.", "INVALID_CREDENTIALS");
        }

        // Guard 2: Verify confirmation keyword or email
        String conf = request.confirmation() != null ? request.confirmation().trim() : "";
        if (!conf.equalsIgnoreCase("DELETE") && !conf.equalsIgnoreCase(user.getEmail())) {
            throw ApiException.badRequest("Please type 'DELETE' or your email to confirm deletion.", "INVALID_CONFIRMATION");
        }

        // Guard 3: Analyze all groups user is a member of
        List<GroupMember> memberships = groupMemberRepository.findAllByUserIdWithGroup(userId);
        List<String> unsettledGroups = new ArrayList<>();
        List<String> soleAdminGroups = new ArrayList<>();
        List<Group> soloGroupsToDelete = new ArrayList<>();
        List<GroupMember> multiMemberGroupsToLeave = new ArrayList<>();

        for (GroupMember gm : memberships) {
            Group group = gm.getGroup();
            UUID groupId = group.getId();

            // Check balance in group
            BigDecimal balance = balanceService.calculateUserBalanceInGroup(groupId, userId);
            if (balance.abs().compareTo(new BigDecimal("0.005")) >= 0) {
                String formatted = balance.compareTo(BigDecimal.ZERO) > 0
                        ? "+" + balance.toPlainString()
                        : balance.toPlainString();
                unsettledGroups.add(String.format("'%s' (%s %s)", group.getName(), formatted, group.getDefaultCurrency()));
                continue;
            }

            long totalMembers = groupMemberRepository.countMembersInGroup(groupId);
            long totalAdmins = groupMemberRepository.countAdminsInGroup(groupId);

            if (totalMembers <= 1) {
                // Solo group where user is the only member -> delete entire group cleanly
                soloGroupsToDelete.add(group);
            } else {
                // Multi-member group: check if user is the sole admin
                if (gm.isAdmin() && totalAdmins <= 1) {
                    soleAdminGroups.add("'" + group.getName() + "'");
                } else {
                    multiMemberGroupsToLeave.add(gm);
                }
            }
        }

        if (!unsettledGroups.isEmpty()) {
            throw ApiException.badRequest(
                    "Cannot delete account: You have unsettled balances in: " + String.join(", ", unsettledGroups)
                            + ". Please settle all debts before deleting your account.",
                    "UNSETTLED_GROUP_BALANCES"
            );
        }

        if (!soleAdminGroups.isEmpty()) {
            throw ApiException.badRequest(
                    "Cannot delete account: You are the sole administrator of active group(s): " + String.join(", ", soleAdminGroups)
                            + ". Please promote another member to admin or delete the group first.",
                    "SOLE_ADMIN_RESTRICTION"
            );
        }

        // Action 1: Remove user from active multi-member groups
        for (GroupMember gm : multiMemberGroupsToLeave) {
            Map<String, Object> details = new HashMap<>();
            details.put("removedUserId", user.getId().toString());
            details.put("removedUserName", user.getDisplayName());
            details.put("reason", "Account deleted by user");
            auditService.logActivity(gm.getGroup(), null, AuditAction.MEMBER_REMOVED, details);

            groupMemberRepository.deleteByGroupIdAndUserId(gm.getGroup().getId(), userId);
        }

        // Action 2: Delete solo groups where user was the only member
        for (Group soloGroup : soloGroupsToDelete) {
            groupMemberRepository.deleteAllByGroupId(soloGroup.getId());
            groupRepository.deleteGroupById(soloGroup.getId());
            log.info("Deleted solo group id='{}' (name='{}') on account deletion of user id={}",
                    soloGroup.getId(), soloGroup.getName(), userId);
        }

        // Action 3: Permanently wipe private personal expenses
        expenseRepository.deletePersonalExpensesByUserId(userId);

        // Action 4: Delete recurring expenses scheduled by user
        recurringExpenseRepository.deleteByPaidById(userId);

        // Action 5: Revoke and delete all active sessions & refresh tokens
        refreshTokenRepository.deleteAllByUser(user);

        // Action 6: Handle User record (Hard Delete vs PII Anonymization)
        long sharedExpenses = expenseRepository.countGroupExpensesPaidByUserId(userId);
        long sharedShares = expenseShareRepository.countGroupExpenseSharesByUserId(userId);
        long sharedSettlements = settlementRepository.countSettlementsByUserId(userId);

        if (sharedExpenses == 0 && sharedShares == 0 && sharedSettlements == 0) {
            // Zero historical shared expenses in other groups -> Full hard delete
            groupMemberRepository.deleteAllByUserId(userId);
            userRepository.deleteUserById(userId);
            log.info("User id={} permanently deleted from database (no shared history)", userId);
        } else {
            // Has shared historical records in groups -> Scrub PII & release original email for re-registration
            String scrubbedEmail = "deleted_" + user.getId().toString().replace("-", "") + "@deleted.settl.local";
            user.setEmail(scrubbedEmail);
            user.setDisplayName("Former Member");
            user.setPasswordHash("DELETED_" + UUID.randomUUID());
            user.setEmailVerified(false);
            user.setVerificationToken(null);
            user.setVerificationTokenExpiresAt(null);
            userRepository.save(user);
            log.info("User id={} PII scrubbed and anonymized, freeing original email", userId);
        }

        List<UUID> affectedGroupIds = memberships.stream()
                .map(gm -> gm.getGroup().getId())
                .distinct()
                .toList();
        groupBalanceCacheEvictor.evictGroupBalances(affectedGroupIds);

        // Clear refresh token cookie
        return cookieFactory.createClearRefreshTokenCookie();
    }
}
