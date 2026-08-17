package com.settl.backend.settlement;

import com.settl.backend.common.ApiException;
import com.settl.backend.expense.Expense;
import com.settl.backend.expense.ExpenseCategory;
import com.settl.backend.expense.ExpenseRepository;
import com.settl.backend.expense.ExpenseShareRepository;
import com.settl.backend.expense.SplitType;
import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMember;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.settlement.dto.GroupBalanceResponse;
import com.settl.backend.settlement.dto.SuggestedSettlementsResponse;
import com.settl.backend.settlement.simplifier.DebtSimplifier;
import com.settl.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseShareRepository expenseShareRepository;

    @Mock
    private SettlementRepository settlementRepository;

    private BalanceService balanceService;

    private Group testGroup;
    private User alice;
    private User bob;
    private UUID groupId;
    private UUID aliceId;
    private UUID bobId;

    @BeforeEach
    void setUp() {
        DebtSimplifier debtSimplifier = new DebtSimplifier();
        balanceService = new BalanceService(
                groupRepository,
                groupMemberRepository,
                expenseRepository,
                expenseShareRepository,
                settlementRepository,
                debtSimplifier
        );

        aliceId = UUID.randomUUID();
        alice = new User("alice@example.com", "hash", "Alice");
        alice.setId(aliceId);

        bobId = UUID.randomUUID();
        bob = new User("bob@example.com", "hash", "Bob");
        bob.setId(bobId);

        groupId = UUID.randomUUID();
        testGroup = new Group("Dinner Club", "USD", alice);
        testGroup.setId(groupId);
    }

    @Test
    void getGroupBalancesShouldComputeNetAmountsAndStatuses() {
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, aliceId)).thenReturn(true);

        GroupMember gm1 = new GroupMember(testGroup, alice, true);
        GroupMember gm2 = new GroupMember(testGroup, bob, false);
        when(groupMemberRepository.findByGroupIdWithUser(groupId)).thenReturn(List.of(gm1, gm2));

        Expense exp = new Expense(testGroup, alice, "Dinner", new BigDecimal("100.00"), "USD", ExpenseCategory.FOOD_AND_DINING, SplitType.EQUAL, null);
        when(expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId)).thenReturn(List.of(exp));

        // Alice paid 100, owes 50 -> net +50 (IS_OWED)
        when(expenseRepository.sumPaidByUserIdInGroup(groupId, aliceId)).thenReturn(new BigDecimal("100.00"));
        when(expenseShareRepository.sumOwedByUserIdInGroup(groupId, aliceId)).thenReturn(new BigDecimal("50.00"));
        when(settlementRepository.sumSettlementsPaidByUserIdInGroup(groupId, aliceId)).thenReturn(BigDecimal.ZERO);
        when(settlementRepository.sumSettlementsReceivedByUserIdInGroup(groupId, aliceId)).thenReturn(BigDecimal.ZERO);

        // Bob paid 0, owes 50 -> net -50 (OWES)
        when(expenseRepository.sumPaidByUserIdInGroup(groupId, bobId)).thenReturn(BigDecimal.ZERO);
        when(expenseShareRepository.sumOwedByUserIdInGroup(groupId, bobId)).thenReturn(new BigDecimal("50.00"));
        when(settlementRepository.sumSettlementsPaidByUserIdInGroup(groupId, bobId)).thenReturn(BigDecimal.ZERO);
        when(settlementRepository.sumSettlementsReceivedByUserIdInGroup(groupId, bobId)).thenReturn(BigDecimal.ZERO);

        GroupBalanceResponse response = balanceService.getGroupBalances(groupId, aliceId);

        assertThat(response.totalGroupSpend()).isEqualTo(new BigDecimal("100.00"));
        assertThat(response.balances()).hasSize(2);

        assertThat(response.balances().get(0).userId()).isEqualTo(aliceId);
        assertThat(response.balances().get(0).netBalance()).isEqualTo(new BigDecimal("50.00"));
        assertThat(response.balances().get(0).status()).isEqualTo("IS_OWED");

        assertThat(response.balances().get(1).userId()).isEqualTo(bobId);
        assertThat(response.balances().get(1).netBalance()).isEqualTo(new BigDecimal("-50.00"));
        assertThat(response.balances().get(1).status()).isEqualTo("OWES");
    }

    @Test
    void getSuggestedSettlementsRecommendsDirectSettlement() {
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, aliceId)).thenReturn(true);

        GroupMember gm1 = new GroupMember(testGroup, alice, true);
        GroupMember gm2 = new GroupMember(testGroup, bob, false);
        when(groupMemberRepository.findByGroupIdWithUser(groupId)).thenReturn(List.of(gm1, gm2));

        when(expenseRepository.sumPaidByUserIdInGroup(groupId, aliceId)).thenReturn(new BigDecimal("100.00"));
        when(expenseShareRepository.sumOwedByUserIdInGroup(groupId, aliceId)).thenReturn(new BigDecimal("50.00"));
        when(settlementRepository.sumSettlementsPaidByUserIdInGroup(groupId, aliceId)).thenReturn(BigDecimal.ZERO);
        when(settlementRepository.sumSettlementsReceivedByUserIdInGroup(groupId, aliceId)).thenReturn(BigDecimal.ZERO);

        when(expenseRepository.sumPaidByUserIdInGroup(groupId, bobId)).thenReturn(BigDecimal.ZERO);
        when(expenseShareRepository.sumOwedByUserIdInGroup(groupId, bobId)).thenReturn(new BigDecimal("50.00"));
        when(settlementRepository.sumSettlementsPaidByUserIdInGroup(groupId, bobId)).thenReturn(BigDecimal.ZERO);
        when(settlementRepository.sumSettlementsReceivedByUserIdInGroup(groupId, bobId)).thenReturn(BigDecimal.ZERO);

        SuggestedSettlementsResponse response = balanceService.getSuggestedSettlements(groupId, aliceId);

        assertThat(response.transactionCount()).isEqualTo(1);
        assertThat(response.totalSettledAmount()).isEqualTo(new BigDecimal("50.00"));
        assertThat(response.suggestedTransactions().get(0).fromUserId()).isEqualTo(bobId);
        assertThat(response.suggestedTransactions().get(0).toUserId()).isEqualTo(aliceId);
        assertThat(response.suggestedTransactions().get(0).amount()).isEqualTo(new BigDecimal("50.00"));
    }

    @Test
    void nonMemberCannotViewBalances() {
        UUID outsiderId = UUID.randomUUID();
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, outsiderId)).thenReturn(false);

        assertThatThrownBy(() -> balanceService.getGroupBalances(groupId, outsiderId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("You must be a member of this group");
    }
}
