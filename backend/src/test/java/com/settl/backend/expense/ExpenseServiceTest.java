package com.settl.backend.expense;

import com.settl.backend.common.ApiException;
import com.settl.backend.expense.dto.CreateExpenseRequest;
import com.settl.backend.expense.dto.ExpenseResponse;
import com.settl.backend.expense.dto.ExpenseSplitDto;
import com.settl.backend.expense.dto.UpdateExpenseRequest;
import com.settl.backend.expense.split.EqualSplitStrategy;
import com.settl.backend.expense.split.ExactSplitStrategy;
import com.settl.backend.expense.split.PercentageSplitStrategy;
import com.settl.backend.expense.split.PersonalSplitStrategy;
import com.settl.backend.expense.split.SharesSplitStrategy;
import com.settl.backend.expense.split.SplitCalculator;
import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMember;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    private ExpenseService expenseService;

    private Group testGroup;
    private User creatorUser;
    private User memberUser;
    private User outsiderUser;

    private UUID groupId;
    private UUID creatorId;
    private UUID memberId;
    private UUID outsiderId;

    @BeforeEach
    void setUp() {
        SplitCalculator splitCalculator = new SplitCalculator(List.of(
                new EqualSplitStrategy(),
                new ExactSplitStrategy(),
                new PercentageSplitStrategy(),
                new SharesSplitStrategy(),
                new PersonalSplitStrategy()
        ));

        expenseService = new ExpenseService(
                expenseRepository,
                groupRepository,
                groupMemberRepository,
                userRepository,
                splitCalculator
        );

        creatorId = UUID.randomUUID();
        creatorUser = new User("admin@example.com", "hash", "Admin User");
        creatorUser.setId(creatorId);

        memberId = UUID.randomUUID();
        memberUser = new User("member@example.com", "hash", "Member User");
        memberUser.setId(memberId);

        outsiderId = UUID.randomUUID();
        outsiderUser = new User("outsider@example.com", "hash", "Outsider User");
        outsiderUser.setId(outsiderId);

        groupId = UUID.randomUUID();
        testGroup = new Group("Trip to Alps", "EUR", creatorUser);
        testGroup.setId(groupId);
    }

    @Test
    void createGroupExpenseEqualSplitAutoPopulatesAllMembers() {
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));
        when(groupMemberRepository.findByGroupIdAndUserId(groupId, creatorId))
                .thenReturn(Optional.of(new GroupMember(testGroup, creatorUser, true)));
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creatorUser));
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, creatorId)).thenReturn(true);

        GroupMember gm1 = new GroupMember(testGroup, creatorUser, true);
        GroupMember gm2 = new GroupMember(testGroup, memberUser, false);
        when(groupMemberRepository.findByGroupIdWithUser(groupId)).thenReturn(List.of(gm1, gm2));

        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense exp = inv.getArgument(0);
            exp.setId(UUID.randomUUID());
            return exp;
        });

        CreateExpenseRequest request = new CreateExpenseRequest(
                "Hotel Booking",
                new BigDecimal("200.00"),
                "EUR",
                ExpenseCategory.TRAVEL,
                SplitType.EQUAL,
                null,
                null,
                null // Auto-split equally
        );

        ExpenseResponse response = expenseService.createGroupExpense(groupId, creatorId, request);

        assertThat(response).isNotNull();
        assertThat(response.description()).isEqualTo("Hotel Booking");
        assertThat(response.amount()).isEqualTo(new BigDecimal("200.00"));
        assertThat(response.shares()).hasSize(2);
        assertThat(response.shares().get(0).amountOwed()).isEqualTo(new BigDecimal("100.00"));
        assertThat(response.shares().get(1).amountOwed()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void createExpenseByNonMemberShouldThrowForbidden() {
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));
        when(groupMemberRepository.findByGroupIdAndUserId(groupId, outsiderId)).thenReturn(Optional.empty());

        CreateExpenseRequest request = new CreateExpenseRequest(
                "Ski Pass",
                new BigDecimal("50.00"),
                "EUR",
                ExpenseCategory.ENTERTAINMENT,
                SplitType.EQUAL,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> expenseService.createGroupExpense(groupId, outsiderId, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("You must be a member of this group");
    }

    @Test
    void nonAdminOrNonCreatorCannotDeleteExpense() {
        GroupMember memberMembership = new GroupMember(testGroup, memberUser, false); // Not admin
        when(groupMemberRepository.findByGroupIdAndUserId(groupId, memberId))
                .thenReturn(Optional.of(memberMembership));

        Expense expense = new Expense(testGroup, creatorUser, "Dinner", new BigDecimal("80.00"), "EUR", ExpenseCategory.FOOD_AND_DINING, SplitType.EQUAL, null);
        expense.setId(UUID.randomUUID());
        when(expenseRepository.findByIdAndGroupId(expense.getId(), groupId)).thenReturn(Optional.of(expense));

        assertThatThrownBy(() -> expenseService.deleteGroupExpense(groupId, expense.getId(), memberId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only the creator of this expense or a group admin can delete it");
    }

    @Test
    void adminCanDeleteAnyGroupExpense() {
        GroupMember adminMembership = new GroupMember(testGroup, creatorUser, true); // Admin
        when(groupMemberRepository.findByGroupIdAndUserId(groupId, creatorId))
                .thenReturn(Optional.of(adminMembership));

        Expense expense = new Expense(testGroup, memberUser, "Drinks", new BigDecimal("30.00"), "EUR", ExpenseCategory.FOOD_AND_DINING, SplitType.EQUAL, null);
        expense.setId(UUID.randomUUID());
        when(expenseRepository.findByIdAndGroupId(expense.getId(), groupId)).thenReturn(Optional.of(expense));

        expenseService.deleteGroupExpense(groupId, expense.getId(), creatorId);
        verify(expenseRepository).delete(expense);
    }
}
