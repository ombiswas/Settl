package com.settl.backend.recurring;

import com.settl.backend.audit.AuditAction;
import com.settl.backend.audit.AuditService;
import com.settl.backend.common.ApiException;
import com.settl.backend.expense.Expense;
import com.settl.backend.expense.ExpenseRepository;
import com.settl.backend.expense.ExpenseShare;
import com.settl.backend.expense.SplitType;
import com.settl.backend.expense.split.SplitCalculator;
import com.settl.backend.expense.split.SplitParam;
import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMember;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.recurring.dto.CreateRecurringExpenseRequest;
import com.settl.backend.recurring.dto.RecurringExpenseResponse;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecurringExpenseService {

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseService.class);

    private final RecurringExpenseRepository recurringExpenseRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final SplitCalculator splitCalculator;
    private final AuditService auditService;

    public RecurringExpenseService(
            RecurringExpenseRepository recurringExpenseRepository,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository,
            ExpenseRepository expenseRepository,
            SplitCalculator splitCalculator,
            AuditService auditService
    ) {
        this.recurringExpenseRepository = recurringExpenseRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.splitCalculator = splitCalculator;
        this.auditService = auditService;
    }

    @Transactional
    public RecurringExpenseResponse createRecurringExpense(UUID groupId, UUID callerId, CreateRecurringExpenseRequest request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found", "GROUP_NOT_FOUND"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, callerId)) {
            throw ApiException.forbidden("You must be a member of this group to create recurring expenses", "NOT_A_GROUP_MEMBER");
        }

        User paidBy = userRepository.findById(callerId)
                .orElseThrow(() -> ApiException.notFound("User not found", "USER_NOT_FOUND"));

        String currency = request.currency() != null && !request.currency().isBlank()
                ? request.currency().trim().toUpperCase()
                : group.getDefaultCurrency();
        validateCurrency(currency);

        RecurringExpense recurring = new RecurringExpense(
                group,
                request.templateDescription(),
                request.amount(),
                currency,
                request.category(),
                request.splitType(),
                paidBy,
                request.frequency(),
                request.nextRunAt()
        );

        RecurringExpense saved = recurringExpenseRepository.save(recurring);

        Map<String, Object> details = new HashMap<>();
        details.put("templateDescription", saved.getTemplateDescription());
        details.put("amount", saved.getAmount().toString());
        details.put("currency", saved.getCurrency());
        details.put("frequency", saved.getFrequency().name());
        details.put("nextRunAt", saved.getNextRunAt().toString());
        auditService.logActivity(group, paidBy, AuditAction.RECURRING_EXPENSE_CREATED, details);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RecurringExpenseResponse> getGroupRecurringExpenses(UUID groupId, UUID callerId) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, callerId)) {
            throw ApiException.forbidden("You must be a member of this group to view recurring expenses", "NOT_A_GROUP_MEMBER");
        }

        List<RecurringExpense> list = recurringExpenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
        return list.stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void deactivateRecurringExpense(UUID groupId, UUID recurringId, UUID callerId) {
        GroupMember callerMembership = groupMemberRepository.findByGroupIdAndUserId(groupId, callerId)
                .orElseThrow(() -> ApiException.forbidden("You must be a member of this group", "NOT_A_GROUP_MEMBER"));

        RecurringExpense recurring = recurringExpenseRepository.findByIdAndGroupId(recurringId, groupId)
                .orElseThrow(() -> ApiException.notFound("Recurring expense not found", "RECURRING_EXPENSE_NOT_FOUND"));

        boolean isCreator = recurring.getPaidBy().getId().equals(callerId);
        boolean isAdmin = callerMembership.isAdmin();
        if (!isCreator && !isAdmin) {
            throw ApiException.forbidden("Only the creator or a group admin can deactivate recurring expenses", "INSUFFICIENT_PERMISSIONS");
        }

        recurring.setActive(false);
        recurringExpenseRepository.save(recurring);
    }

    @Transactional
    public int processDueRecurringExpenses() {
        Instant now = Instant.now();
        List<RecurringExpense> dueExpenses = recurringExpenseRepository.findDueRecurringExpenses(now);

        int processedCount = 0;
        for (RecurringExpense recurring : dueExpenses) {
            try {
                processSingleRecurringExpense(recurring, now);
                processedCount++;
            } catch (Exception e) {
                log.error("Failed to execute recurring expense id={}", recurring.getId(), e);
            }
        }
        return processedCount;
    }

    private void processSingleRecurringExpense(RecurringExpense recurring, Instant now) {
        Group group = recurring.getGroup();
        User paidBy = recurring.getPaidBy();

        List<GroupMember> groupMembers = groupMemberRepository.findByGroupIdWithUser(group.getId());
        if (groupMembers.isEmpty()) {
            return;
        }

        Map<UUID, User> userMap = groupMembers.stream().collect(Collectors.toMap(gm -> gm.getUser().getId(), GroupMember::getUser));
        List<SplitParam> splitParams = groupMembers.stream()
                .map(gm -> SplitParam.equal(gm.getUser().getId()))
                .toList();

        Map<UUID, BigDecimal> computedShares = splitCalculator.calculate(
                SplitType.EQUAL,
                recurring.getAmount(),
                splitParams,
                paidBy.getId()
        );

        Expense generatedExpense = new Expense(
                group,
                paidBy,
                "[Recurring] " + recurring.getTemplateDescription(),
                recurring.getAmount(),
                recurring.getCurrency(),
                recurring.getCategory(),
                SplitType.EQUAL,
                null
        );

        for (Map.Entry<UUID, BigDecimal> entry : computedShares.entrySet()) {
            User shareUser = userMap.get(entry.getKey());
            if (shareUser != null) {
                generatedExpense.addShare(new ExpenseShare(generatedExpense, shareUser, entry.getValue()));
            }
        }

        Expense savedExpense = expenseRepository.save(generatedExpense);

        // Advance next_run_at according to frequency (idempotency step)
        Instant nextRun = calculateNextRun(recurring.getNextRunAt(), recurring.getFrequency());
        recurring.setNextRunAt(nextRun);
        recurringExpenseRepository.save(recurring);

        // Audit Logging
        Map<String, Object> details = new HashMap<>();
        details.put("recurringExpenseId", recurring.getId() != null ? recurring.getId().toString() : "");
        details.put("templateDescription", recurring.getTemplateDescription());
        details.put("generatedExpenseId", savedExpense.getId() != null ? savedExpense.getId().toString() : "");
        details.put("amount", savedExpense.getAmount().toString());
        details.put("currency", savedExpense.getCurrency());
        auditService.logActivity(group, paidBy, AuditAction.RECURRING_EXPENSE_TRIGGERED, details);
    }

    private Instant calculateNextRun(Instant currentNextRun, RecurringFrequency frequency) {
        if (frequency == RecurringFrequency.WEEKLY) {
            return currentNextRun.plus(7, ChronoUnit.DAYS);
        } else if (frequency == RecurringFrequency.MONTHLY) {
            return ZonedDateTime.ofInstant(currentNextRun, ZoneOffset.UTC)
                    .plusMonths(1)
                    .toInstant();
        }
        return currentNextRun.plus(30, ChronoUnit.DAYS);
    }

    private RecurringExpenseResponse mapToResponse(RecurringExpense entity) {
        return new RecurringExpenseResponse(
                entity.getId(),
                entity.getGroup().getId(),
                entity.getTemplateDescription(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getCategory(),
                entity.getSplitType(),
                entity.getPaidBy().getId(),
                entity.getPaidBy().getDisplayName(),
                entity.getPaidBy().getEmail(),
                entity.getFrequency(),
                entity.getNextRunAt(),
                entity.isActive(),
                entity.getCreatedAt()
        );
    }

    private void validateCurrency(String currencyCode) {
        try {
            Currency.getInstance(currencyCode);
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid ISO-4217 currency code: " + currencyCode, "INVALID_CURRENCY");
        }
    }
}
