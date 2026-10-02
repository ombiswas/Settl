package com.settl.backend.expense;

import com.settl.backend.group.Group;
import com.settl.backend.group.GroupMember;
import com.settl.backend.group.GroupMemberRepository;
import com.settl.backend.group.GroupRepository;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ExpenseShareRepositoryTest {

    static {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private ExpenseShareRepository expenseShareRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private JavaMailSender javaMailSender;

    private User userWithGroupShares;
    private User userWithOnlyPersonalExpenses;
    private User userWithNoExpenses;
    private Group group;

    @BeforeEach
    void setUp() {
        expenseShareRepository.deleteAll();
        expenseRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        userRepository.deleteAll();

        // 1. User with group shares
        userWithGroupShares = userRepository.save(new User("group_member@example.com", "hash", "Group Member"));

        // 2. User with only personal expenses
        userWithOnlyPersonalExpenses = userRepository.save(new User("personal_only@example.com", "hash", "Personal User"));

        // 3. User with no expenses or shares
        userWithNoExpenses = userRepository.save(new User("no_expenses@example.com", "hash", "Inactive User"));

        // Set up group and membership
        group = groupRepository.save(new Group("Trip Group", "USD", userWithGroupShares));
        groupMemberRepository.save(new GroupMember(group, userWithGroupShares, true));
    }

    @Test
    @DisplayName("countGroupExpenseSharesByUserId correctly counts shares belonging to group expenses")
    void shouldCountGroupExpenseShares() {
        // Create 2 group expenses with shares for userWithGroupShares
        Expense groupExpense1 = new Expense(
                group,
                userWithGroupShares,
                "Dinner",
                new BigDecimal("60.00"),
                "USD",
                ExpenseCategory.FOOD_AND_DINING,
                SplitType.EQUAL,
                null
        );
        groupExpense1 = expenseRepository.save(groupExpense1);
        expenseShareRepository.save(new ExpenseShare(groupExpense1, userWithGroupShares, new BigDecimal("60.00")));

        Expense groupExpense2 = new Expense(
                group,
                userWithGroupShares,
                "Taxi",
                new BigDecimal("30.00"),
                "USD",
                ExpenseCategory.TRANSPORTATION,
                SplitType.EQUAL,
                null
        );
        groupExpense2 = expenseRepository.save(groupExpense2);
        expenseShareRepository.save(new ExpenseShare(groupExpense2, userWithGroupShares, new BigDecimal("30.00")));

        long count = expenseShareRepository.countGroupExpenseSharesByUserId(userWithGroupShares.getId());
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("countGroupExpenseSharesByUserId returns 0 when user has only personal expenses (even if shares exist on personal expense)")
    void shouldReturnZeroForUserWithOnlyPersonalExpenses() {
        // Create a personal expense (group is null)
        Expense personalExpense = new Expense(
                null,
                userWithOnlyPersonalExpenses,
                "Personal Coffee",
                new BigDecimal("5.50"),
                "USD",
                ExpenseCategory.FOOD_AND_DINING,
                SplitType.EQUAL,
                null
        );
        personalExpense = expenseRepository.save(personalExpense);

        // Even if an ExpenseShare is associated with a personal expense, group is NULL so it must not be counted
        expenseShareRepository.save(new ExpenseShare(personalExpense, userWithOnlyPersonalExpenses, new BigDecimal("5.50")));

        long count = expenseShareRepository.countGroupExpenseSharesByUserId(userWithOnlyPersonalExpenses.getId());
        assertThat(count).isEqualTo(0L);
    }

    @Test
    @DisplayName("countGroupExpenseSharesByUserId returns 0 for user with no expenses or shares")
    void shouldReturnZeroForUserWithNoExpenses() {
        long count = expenseShareRepository.countGroupExpenseSharesByUserId(userWithNoExpenses.getId());
        assertThat(count).isEqualTo(0L);
    }

    @Test
    @DisplayName("countGroupExpenseSharesByUserId returns 0 for a non-existent user ID")
    void shouldReturnZeroForNonExistentUser() {
        long count = expenseShareRepository.countGroupExpenseSharesByUserId(UUID.randomUUID());
        assertThat(count).isEqualTo(0L);
    }

    @Test
    @DisplayName("countGroupExpenseSharesByUserId ignores group expenses where user is not in shares")
    void shouldNotCountSharesOfOtherUsers() {
        User otherUser = userRepository.save(new User("other@example.com", "hash", "Other User"));
        groupMemberRepository.save(new GroupMember(group, otherUser, false));

        Expense groupExpense = new Expense(
                group,
                userWithGroupShares,
                "Groceries",
                new BigDecimal("100.00"),
                "USD",
                ExpenseCategory.FOOD_AND_DINING,
                SplitType.EQUAL,
                null
        );
        groupExpense = expenseRepository.save(groupExpense);

        // Share only assigned to otherUser
        expenseShareRepository.save(new ExpenseShare(groupExpense, otherUser, new BigDecimal("100.00")));

        // userWithGroupShares has 0 shares for this expense
        long countForGroupUser = expenseShareRepository.countGroupExpenseSharesByUserId(userWithGroupShares.getId());
        long countForOtherUser = expenseShareRepository.countGroupExpenseSharesByUserId(otherUser.getId());

        assertThat(countForGroupUser).isEqualTo(0L);
        assertThat(countForOtherUser).isEqualTo(1L);
    }
}
