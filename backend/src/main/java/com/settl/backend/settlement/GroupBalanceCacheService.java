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
import com.settl.backend.settlement.dto.UserAmountDto;
import com.settl.backend.settlement.dto.UserBalanceDto;
import com.settl.backend.user.User;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.settl.backend.config.CacheConfig.GROUP_BALANCES_CACHE;

@Service
public class GroupBalanceCacheService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final SettlementRepository settlementRepository;

    public GroupBalanceCacheService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            ExpenseRepository expenseRepository,
            ExpenseShareRepository expenseShareRepository,
            SettlementRepository settlementRepository
    ) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.expenseRepository = expenseRepository;
        this.expenseShareRepository = expenseShareRepository;
        this.settlementRepository = settlementRepository;
    }

    /**
     * Calculates caller-independent net balances for all members in a group.
     * Result is cached in Redis under "group_balances" with key = #groupId (30s TTL).
     */
    @Cacheable(value = GROUP_BALANCES_CACHE, key = "#groupId")
    @Transactional(readOnly = true)
    public GroupBalanceResponse calculateGroupBalances(UUID groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Group not found", "GROUP_NOT_FOUND"));

        List<GroupMember> members = groupMemberRepository.findByGroupIdWithUser(groupId);

        BigDecimal totalGroupSpend = BigDecimal.ZERO;
        List<Expense> groupExpenses = expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
        for (Expense expense : groupExpenses) {
            totalGroupSpend = totalGroupSpend.add(expense.getAmount());
        }
        totalGroupSpend = totalGroupSpend.setScale(2, RoundingMode.HALF_EVEN);

        Map<UUID, BigDecimal> paidMap = expenseRepository.findTotalPaidPerUserInGroup(groupId).stream()
                .collect(Collectors.toMap(UserAmountDto::userId, UserAmountDto::amount));
        Map<UUID, BigDecimal> owedMap = expenseShareRepository.findTotalOwedPerUserInGroup(groupId).stream()
                .collect(Collectors.toMap(UserAmountDto::userId, UserAmountDto::amount));
        Map<UUID, BigDecimal> settlementsPaidMap = settlementRepository.findTotalSettlementsPaidPerUserInGroup(groupId).stream()
                .collect(Collectors.toMap(UserAmountDto::userId, UserAmountDto::amount));
        Map<UUID, BigDecimal> settlementsReceivedMap = settlementRepository.findTotalSettlementsReceivedPerUserInGroup(groupId).stream()
                .collect(Collectors.toMap(UserAmountDto::userId, UserAmountDto::amount));

        List<UserBalanceDto> balanceDtos = new ArrayList<>();
        for (GroupMember gm : members) {
            User user = gm.getUser();
            BigDecimal sumPaid = paidMap.getOrDefault(user.getId(), BigDecimal.ZERO);
            BigDecimal sumOwed = owedMap.getOrDefault(user.getId(), BigDecimal.ZERO);
            BigDecimal sumSettledPaid = settlementsPaidMap.getOrDefault(user.getId(), BigDecimal.ZERO);
            BigDecimal sumSettledReceived = settlementsReceivedMap.getOrDefault(user.getId(), BigDecimal.ZERO);

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
}
