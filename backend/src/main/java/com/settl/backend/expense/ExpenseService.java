package com.settl.backend.expense;

import com.settl.backend.audit.AuditAction;
import com.settl.backend.audit.AuditService;
import com.settl.backend.common.ApiException;
import com.settl.backend.common.CurrencyValidator;
import com.settl.backend.common.PageResponse;
import com.settl.backend.expense.dto.CreateExpenseRequest;
import com.settl.backend.expense.dto.ExpenseResponse;
import com.settl.backend.expense.dto.ExpenseShareDto;
import com.settl.backend.expense.dto.ExpenseSplitDto;
import com.settl.backend.expense.dto.UpdateExpenseRequest;
import com.settl.backend.expense.split.SplitCalculator;
import com.settl.backend.expense.split.SplitParam;
import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMember;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final SplitCalculator splitCalculator;
    private final AuditService auditService;
    private final com.settl.backend.settlement.GroupBalanceCacheEvictor groupBalanceCacheEvictor;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            ExpenseShareRepository expenseShareRepository,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository,
            SplitCalculator splitCalculator,
            AuditService auditService,
            com.settl.backend.settlement.GroupBalanceCacheEvictor groupBalanceCacheEvictor
    ) {
        this.expenseRepository = expenseRepository;
        this.expenseShareRepository = expenseShareRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.splitCalculator = splitCalculator;
        this.auditService = auditService;
        this.groupBalanceCacheEvictor = groupBalanceCacheEvictor;
    }

    @Transactional
    public ExpenseResponse createGroupExpense(UUID groupId, UUID callerId, CreateExpenseRequest request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found", "GROUP_NOT_FOUND"));

        // Guard 1: Caller must be a member of the group
        GroupMember callerMembership = groupMemberRepository.findByGroupIdAndUserId(groupId, callerId)
                .orElseThrow(() -> ApiException.forbidden("You must be a member of this group to add expenses", "NOT_A_GROUP_MEMBER"));

        // Guard 2: Determine payer and ensure payer is a group member
        UUID payerId = request.paidByUserId() != null ? request.paidByUserId() : callerId;
        User payer = userRepository.findById(payerId)
                .orElseThrow(() -> ApiException.notFound("Payer user not found", "USER_NOT_FOUND"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, payerId)) {
            throw ApiException.badRequest("Payer must be an active member of the group", "PAYER_NOT_IN_GROUP");
        }

        // Validate currency
        String currency = request.currency() != null && !request.currency().isBlank()
                ? request.currency().trim().toUpperCase()
                : group.getDefaultCurrency();
        CurrencyValidator.validate(currency);

        // Fetch all group members for validation and auto-splits
        List<GroupMember> groupMembers = groupMemberRepository.findByGroupIdWithUser(groupId);
        Set<UUID> memberUserIds = groupMembers.stream().map(gm -> gm.getUser().getId()).collect(Collectors.toSet());
        Map<UUID, User> userMap = groupMembers.stream().collect(Collectors.toMap(gm -> gm.getUser().getId(), GroupMember::getUser));

        List<SplitParam> splitParams = resolveSplitParams(request.splitType(), request.splits(), groupMembers, memberUserIds);

        // Calculate exact shares using strategy engine
        Map<UUID, BigDecimal> computedShares = splitCalculator.calculate(
                request.splitType(),
                request.amount(),
                splitParams,
                payer.getId()
        );

        Expense expense = new Expense(
                group,
                payer,
                request.description(),
                request.amount(),
                currency,
                request.category(),
                request.splitType(),
                request.receiptUrl()
        );

        for (Map.Entry<UUID, BigDecimal> entry : computedShares.entrySet()) {
            User shareUser = userMap.get(entry.getKey());
            if (shareUser == null) {
                shareUser = userRepository.findById(entry.getKey())
                        .orElseThrow(() -> ApiException.notFound("Split user not found", "USER_NOT_FOUND"));
            }
            ExpenseShare share = new ExpenseShare(expense, shareUser, entry.getValue());
            expense.addShare(share);
        }

        Expense savedExpense = expenseRepository.save(expense);

        // Audit log
        Map<String, Object> details = new HashMap<>();
        details.put("expenseId", savedExpense.getId().toString());
        details.put("description", savedExpense.getDescription());
        details.put("amount", savedExpense.getAmount().toString());
        details.put("currency", savedExpense.getCurrency());
        details.put("category", savedExpense.getCategory().name());
        details.put("splitType", savedExpense.getSplitType().name());
        details.put("paidBy", payer.getDisplayName());
        auditService.logActivity(group, payer, AuditAction.EXPENSE_CREATED, details);

        groupBalanceCacheEvictor.evictGroupBalances(groupId);

        return mapToExpenseResponse(savedExpense);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> getGroupExpenses(UUID groupId, UUID callerId, int page, int size) {
        verifyMembership(groupId, callerId);

        int clampedPage = Math.max(0, page);
        int clampedSize = Math.clamp(size, 1, 100);
        Pageable pageable = PageRequest.of(clampedPage, clampedSize);

        Page<Expense> expensePage = expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId, pageable);
        List<Expense> expenses = expensePage.getContent();
        if (expenses.isEmpty()) {
            return new PageResponse<>(
                    List.of(),
                    expensePage.getNumber(),
                    expensePage.getSize(),
                    expensePage.getTotalElements(),
                    expensePage.getTotalPages()
            );
        }

        List<UUID> expenseIds = expenses.stream().map(Expense::getId).toList();
        List<ExpenseShare> shares = expenseShareRepository.findByExpenseIdIn(expenseIds);
        Map<UUID, List<ExpenseShare>> sharesByExpenseId = shares.stream()
                .collect(Collectors.groupingBy(s -> s.getExpense().getId()));

        List<ExpenseResponse> dtos = expenses.stream()
                .map(exp -> mapToExpenseResponse(exp, sharesByExpenseId.getOrDefault(exp.getId(), List.of())))
                .toList();

        return new PageResponse<>(
                dtos,
                expensePage.getNumber(),
                expensePage.getSize(),
                expensePage.getTotalElements(),
                expensePage.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ExpenseResponse getGroupExpenseById(UUID groupId, UUID expenseId, UUID callerId) {
        verifyMembership(groupId, callerId);
        Expense expense = expenseRepository.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> ApiException.notFound("Expense not found in this group", "EXPENSE_NOT_FOUND"));
        return mapToExpenseResponse(expense);
    }

    @Transactional
    public ExpenseResponse updateGroupExpense(UUID groupId, UUID expenseId, UUID callerId, UpdateExpenseRequest request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found", "GROUP_NOT_FOUND"));

        GroupMember callerMembership = groupMemberRepository.findByGroupIdAndUserId(groupId, callerId)
                .orElseThrow(() -> ApiException.forbidden("You must be a member of this group to edit expenses", "NOT_A_GROUP_MEMBER"));

        Expense expense = expenseRepository.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> ApiException.notFound("Expense not found in this group", "EXPENSE_NOT_FOUND"));

        // Authorization: Only the creator/payer OR a group admin/creator can edit
        boolean isCreatorOrPayer = expense.getPaidBy().getId().equals(callerId);
        boolean isGroupCreator = expense.getGroup() != null && expense.getGroup().getCreatedBy() != null
                && expense.getGroup().getCreatedBy().getId().equals(callerId);
        boolean isAdmin = callerMembership.isAdmin() || isGroupCreator;
        if (!isCreatorOrPayer && !isAdmin) {
            throw ApiException.forbidden("Only the creator of this expense or a group admin can edit it", "INSUFFICIENT_PERMISSIONS");
        }

        UUID payerId = request.paidByUserId() != null ? request.paidByUserId() : expense.getPaidBy().getId();
        User payer = userRepository.findById(payerId)
                .orElseThrow(() -> ApiException.notFound("Payer user not found", "USER_NOT_FOUND"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, payerId)) {
            throw ApiException.badRequest("Payer must be an active member of the group", "PAYER_NOT_IN_GROUP");
        }

        String currency = request.currency() != null && !request.currency().isBlank()
                ? request.currency().trim().toUpperCase()
                : expense.getCurrency();
        CurrencyValidator.validate(currency);

        List<GroupMember> groupMembers = groupMemberRepository.findByGroupIdWithUser(groupId);
        Set<UUID> memberUserIds = groupMembers.stream().map(gm -> gm.getUser().getId()).collect(Collectors.toSet());
        Map<UUID, User> userMap = groupMembers.stream().collect(Collectors.toMap(gm -> gm.getUser().getId(), GroupMember::getUser));

        List<SplitParam> splitParams = resolveSplitParams(request.splitType(), request.splits(), groupMembers, memberUserIds);
        Map<UUID, BigDecimal> computedShares = splitCalculator.calculate(
                request.splitType(),
                request.amount(),
                splitParams,
                payer.getId()
        );

        expense.setDescription(request.description());
        expense.setAmount(request.amount());
        expense.setCurrency(currency);
        expense.setCategory(request.category());
        expense.setSplitType(request.splitType());
        expense.setReceiptUrl(request.receiptUrl());
        expense.setPaidBy(payer);

        // Update existing shares, remove omitted ones, and add new ones to avoid unique constraint violation on flush
        Map<UUID, ExpenseShare> existingShareMap = expense.getShares().stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), Function.identity()));

        expense.getShares().removeIf(s -> !computedShares.containsKey(s.getUser().getId()));

        for (Map.Entry<UUID, BigDecimal> entry : computedShares.entrySet()) {
            UUID shareUserId = entry.getKey();
            BigDecimal amountOwed = entry.getValue();
            ExpenseShare existing = existingShareMap.get(shareUserId);
            if (existing != null) {
                existing.setAmountOwed(amountOwed);
            } else {
                User shareUser = userMap.get(shareUserId);
                if (shareUser == null) {
                    shareUser = userRepository.findById(shareUserId)
                            .orElseThrow(() -> ApiException.notFound("Split user not found", "USER_NOT_FOUND"));
                }
                ExpenseShare share = new ExpenseShare(expense, shareUser, amountOwed);
                expense.addShare(share);
            }
        }

        Expense updatedExpense = expenseRepository.save(expense);

        // Audit log
        Map<String, Object> details = new HashMap<>();
        details.put("expenseId", updatedExpense.getId().toString());
        details.put("description", updatedExpense.getDescription());
        details.put("amount", updatedExpense.getAmount().toString());
        details.put("currency", updatedExpense.getCurrency());
        details.put("editedBy", callerMembership.getUser().getDisplayName());
        auditService.logActivity(group, callerMembership.getUser(), AuditAction.EXPENSE_UPDATED, details);

        groupBalanceCacheEvictor.evictGroupBalances(groupId);

        return mapToExpenseResponse(updatedExpense);
    }

    @Transactional
    public void deleteGroupExpense(UUID groupId, UUID expenseId, UUID callerId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found", "GROUP_NOT_FOUND"));

        GroupMember callerMembership = groupMemberRepository.findByGroupIdAndUserId(groupId, callerId)
                .orElseThrow(() -> ApiException.forbidden("You must be a member of this group to delete expenses", "NOT_A_GROUP_MEMBER"));

        Expense expense = expenseRepository.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> ApiException.notFound("Expense not found in this group", "EXPENSE_NOT_FOUND"));

        boolean isCreatorOrPayer = expense.getPaidBy().getId().equals(callerId);
        boolean isGroupCreator = expense.getGroup() != null && expense.getGroup().getCreatedBy() != null
                && expense.getGroup().getCreatedBy().getId().equals(callerId);
        boolean isAdmin = callerMembership.isAdmin() || isGroupCreator;
        if (!isCreatorOrPayer && !isAdmin) {
            throw ApiException.forbidden("Only the creator of this expense or a group admin can delete it", "INSUFFICIENT_PERMISSIONS");
        }

        // Audit log before delete
        Map<String, Object> details = new HashMap<>();
        details.put("expenseId", expense.getId().toString());
        details.put("description", expense.getDescription());
        details.put("amount", expense.getAmount().toString());
        details.put("deletedBy", callerMembership.getUser().getDisplayName());
        auditService.logActivity(group, callerMembership.getUser(), AuditAction.EXPENSE_DELETED, details);

        expenseRepository.delete(expense);

        groupBalanceCacheEvictor.evictGroupBalances(groupId);
    }

    private void verifyMembership(UUID groupId, UUID callerId) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, callerId)) {
            throw ApiException.forbidden("You must be a member of this group to view its expenses", "NOT_A_GROUP_MEMBER");
        }
    }

    private List<SplitParam> resolveSplitParams(
            SplitType splitType,
            List<ExpenseSplitDto> splitDtos,
            List<GroupMember> groupMembers,
            Set<UUID> memberUserIds
    ) {
        if (splitDtos != null && !splitDtos.isEmpty()) {
            // Validate all participants are members of the group
            for (ExpenseSplitDto dto : splitDtos) {
                if (!memberUserIds.contains(dto.userId())) {
                    throw ApiException.badRequest("All split participants must be members of the group", "SPLIT_USER_NOT_IN_GROUP");
                }
            }
            return splitDtos.stream()
                    .map(dto -> new SplitParam(dto.userId(), dto.amount(), dto.percentage(), dto.shares()))
                    .toList();
        }

        // Auto-split equal among all group members if splits list is omitted
        if (splitType == SplitType.EQUAL) {
            return groupMembers.stream()
                    .map(gm -> SplitParam.equal(gm.getUser().getId()))
                    .toList();
        }

        throw ApiException.badRequest("Split participants and allocations must be specified for split type: " + splitType, "SPLIT_PARAMS_REQUIRED");
    }

    private ExpenseResponse mapToExpenseResponse(Expense expense) {
        return mapToExpenseResponse(expense, expense.getShares());
    }

    private ExpenseResponse mapToExpenseResponse(Expense expense, List<ExpenseShare> expenseShares) {
        List<ExpenseShareDto> shares = expenseShares.stream()
                .map(share -> new ExpenseShareDto(
                        share.getUser().getId(),
                        share.getUser().getDisplayName(),
                        share.getUser().getEmail(),
                        share.getAmountOwed()
                ))
                .toList();

        return new ExpenseResponse(
                expense.getId(),
                expense.getGroup() != null ? expense.getGroup().getId() : null,
                expense.getGroup() != null ? expense.getGroup().getName() : null,
                expense.getPaidBy().getId(),
                expense.getPaidBy().getDisplayName(),
                expense.getPaidBy().getEmail(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getCurrency(),
                expense.getCategory(),
                expense.getCategory().getDisplayName(),
                expense.getSplitType(),
                expense.getReceiptUrl(),
                shares,
                expense.getCreatedAt()
        );
    }
}
