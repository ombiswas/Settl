package com.settl.backend.settlement;

import com.settl.backend.common.ApiException;
import com.settl.backend.expense.Expense;
import com.settl.backend.expense.ExpenseRepository;
import com.settl.backend.expense.ExpenseShareRepository;
import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMember;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.settlement.dto.GroupBalanceResponse;
import com.settl.backend.settlement.dto.SuggestedSettlementDto;
import com.settl.backend.settlement.dto.SuggestedSettlementsResponse;
import com.settl.backend.settlement.dto.UserBalanceDto;
import com.settl.backend.settlement.simplifier.DebtSimplifier;
import com.settl.backend.settlement.simplifier.SimplifiedTransaction;
import com.settl.backend.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BalanceService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final SettlementRepository settlementRepository;
    private final DebtSimplifier debtSimplifier;

    public BalanceService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            ExpenseRepository expenseRepository,
            ExpenseShareRepository expenseShareRepository,
            SettlementRepository settlementRepository,
            DebtSimplifier debtSimplifier
    ) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.expenseRepository = expenseRepository;
        this.expenseShareRepository = expenseShareRepository;
        this.settlementRepository = settlementRepository;
        this.debtSimplifier = debtSimplifier;
    }

    @Transactional(readOnly = true)
    public GroupBalanceResponse getGroupBalances(UUID groupId, UUID callerId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found", "GROUP_NOT_FOUND"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, callerId)) {
            throw ApiException.forbidden("You must be a member of this group to view balances", "NOT_A_GROUP_MEMBER");
        }

        List<GroupMember> members = groupMemberRepository.findByGroupIdWithUser(groupId);
        List<UserBalanceDto> balanceDtos = new ArrayList<>();

        BigDecimal totalGroupSpend = BigDecimal.ZERO;
        List<Expense> groupExpenses = expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
        for (Expense expense : groupExpenses) {
            totalGroupSpend = totalGroupSpend.add(expense.getAmount());
        }
        totalGroupSpend = totalGroupSpend.setScale(2, RoundingMode.HALF_EVEN);

        for (GroupMember gm : members) {
            User user = gm.getUser();
            BigDecimal sumPaid = expenseRepository.sumPaidByUserIdInGroup(groupId, user.getId());
            BigDecimal sumOwed = expenseShareRepository.sumOwedByUserIdInGroup(groupId, user.getId());
            BigDecimal sumSettledPaid = settlementRepository.sumSettlementsPaidByUserIdInGroup(groupId, user.getId());
            BigDecimal sumSettledReceived = settlementRepository.sumSettlementsReceivedByUserIdInGroup(groupId, user.getId());

            BigDecimal netBalance = sumPaid.subtract(sumOwed)
                    .add(sumSettledPaid)
                    .subtract(sumSettledReceived)
                    .setScale(2, RoundingMode.HALF_EVEN);

            String status;
            if (netBalance.compareTo(new BigDecimal("0.005")) > 0) {
                status = "IS_OWED";
            } else if (netBalance.compareTo(new BigDecimal("-0.005")) < 0) {
                status = "OWES";
            } else {
                status = "SETTLED";
            }

            balanceDtos.add(new UserBalanceDto(
                    user.getId(),
                    user.getDisplayName(),
                    user.getEmail(),
                    netBalance,
                    status,
                    sumPaid.setScale(2, RoundingMode.HALF_EVEN),
                    sumOwed.setScale(2, RoundingMode.HALF_EVEN)
            ));
        }

        return new GroupBalanceResponse(
                group.getId(),
                group.getName(),
                group.getDefaultCurrency(),
                totalGroupSpend,
                balanceDtos
        );
    }

    @Transactional(readOnly = true)
    public SuggestedSettlementsResponse getSuggestedSettlements(UUID groupId, UUID callerId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found", "GROUP_NOT_FOUND"));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, callerId)) {
            throw ApiException.forbidden("You must be a member of this group to view settlement suggestions", "NOT_A_GROUP_MEMBER");
        }

        List<GroupMember> members = groupMemberRepository.findByGroupIdWithUser(groupId);
        Map<UUID, User> userMap = members.stream().collect(Collectors.toMap(gm -> gm.getUser().getId(), GroupMember::getUser));

        Map<UUID, BigDecimal> netBalances = new HashMap<>();
        for (GroupMember gm : members) {
            UUID userId = gm.getUser().getId();
            BigDecimal sumPaid = expenseRepository.sumPaidByUserIdInGroup(groupId, userId);
            BigDecimal sumOwed = expenseShareRepository.sumOwedByUserIdInGroup(groupId, userId);
            BigDecimal sumSettledPaid = settlementRepository.sumSettlementsPaidByUserIdInGroup(groupId, userId);
            BigDecimal sumSettledReceived = settlementRepository.sumSettlementsReceivedByUserIdInGroup(groupId, userId);

            BigDecimal netBalance = sumPaid.subtract(sumOwed)
                    .add(sumSettledPaid)
                    .subtract(sumSettledReceived)
                    .setScale(2, RoundingMode.HALF_EVEN);

            netBalances.put(userId, netBalance);
        }

        List<SimplifiedTransaction> simplified = debtSimplifier.simplify(netBalances);

        List<SuggestedSettlementDto> suggestedDtos = new ArrayList<>();
        BigDecimal totalSettled = BigDecimal.ZERO;

        for (SimplifiedTransaction tx : simplified) {
            User fromUser = userMap.get(tx.fromUserId());
            User toUser = userMap.get(tx.toUserId());

            suggestedDtos.add(new SuggestedSettlementDto(
                    tx.fromUserId(),
                    fromUser != null ? fromUser.getDisplayName() : "Unknown",
                    fromUser != null ? fromUser.getEmail() : "",
                    tx.toUserId(),
                    toUser != null ? toUser.getDisplayName() : "Unknown",
                    toUser != null ? toUser.getEmail() : "",
                    tx.amount(),
                    group.getDefaultCurrency()
            ));

            totalSettled = totalSettled.add(tx.amount());
        }

        return new SuggestedSettlementsResponse(
                group.getId(),
                group.getName(),
                group.getDefaultCurrency(),
                suggestedDtos.size(),
                totalSettled.setScale(2, RoundingMode.HALF_EVEN),
                suggestedDtos
        );
    }
}
