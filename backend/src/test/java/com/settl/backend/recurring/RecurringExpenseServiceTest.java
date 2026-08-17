package com.settl.backend.recurring;

import com.settl.backend.audit.AuditService;
import com.settl.backend.expense.Expense;
import com.settl.backend.expense.ExpenseCategory;
import com.settl.backend.expense.ExpenseRepository;
import com.settl.backend.expense.SplitType;
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
import com.settl.backend.recurring.dto.CreateRecurringExpenseRequest;
import com.settl.backend.recurring.dto.RecurringExpenseResponse;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringExpenseServiceTest {

    @Mock
    private RecurringExpenseRepository recurringExpenseRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private AuditService auditService;

    private RecurringExpenseService recurringExpenseService;

    private Group testGroup;
    private User alice;
    private User bob;
    private UUID groupId;
    private UUID aliceId;
    private UUID bobId;

    @BeforeEach
    void setUp() {
        SplitCalculator splitCalculator = new SplitCalculator(List.of(
                new EqualSplitStrategy(),
                new ExactSplitStrategy(),
                new PercentageSplitStrategy(),
                new SharesSplitStrategy(),
                new PersonalSplitStrategy()
        ));

        recurringExpenseService = new RecurringExpenseService(
                recurringExpenseRepository,
                groupRepository,
                groupMemberRepository,
                userRepository,
                expenseRepository,
                splitCalculator,
                auditService
        );

        aliceId = UUID.randomUUID();
        alice = new User("alice@example.com", "hash", "Alice");
        alice.setId(aliceId);

        bobId = UUID.randomUUID();
        bob = new User("bob@example.com", "hash", "Bob");
        bob.setId(bobId);

        groupId = UUID.randomUUID();
        testGroup = new Group("Apartment 4B", "USD", alice);
        testGroup.setId(groupId);
    }

    @Test
    void createRecurringExpenseShouldPersistAndScheduleNextRun() {
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));
        when(groupMemberRepository.existsByGroupIdAndUserId(groupId, aliceId)).thenReturn(true);
        when(userRepository.findById(aliceId)).thenReturn(Optional.of(alice));

        Instant nextRun = Instant.now().plus(7, ChronoUnit.DAYS);
        CreateRecurringExpenseRequest request = new CreateRecurringExpenseRequest(
                "Wifi Internet",
                new BigDecimal("60.00"),
                "USD",
                ExpenseCategory.HOUSING_AND_UTILITIES,
                SplitType.EQUAL,
                RecurringFrequency.MONTHLY,
                nextRun
        );

        when(recurringExpenseRepository.save(any(RecurringExpense.class))).thenAnswer(inv -> {
            RecurringExpense r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        RecurringExpenseResponse response = recurringExpenseService.createRecurringExpense(groupId, aliceId, request);

        assertThat(response).isNotNull();
        assertThat(response.templateDescription()).isEqualTo("Wifi Internet");
        assertThat(response.amount()).isEqualTo(new BigDecimal("60.00"));
        assertThat(response.frequency()).isEqualTo(RecurringFrequency.MONTHLY);
        assertThat(response.active()).isTrue();
    }

    @Test
    void processDueRecurringExpensesIsIdempotentAndCreatesExpenses() {
        Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
        RecurringExpense recurring = new RecurringExpense(
                testGroup,
                "Netflix Subscription",
                new BigDecimal("20.00"),
                "USD",
                ExpenseCategory.ENTERTAINMENT,
                SplitType.EQUAL,
                alice,
                RecurringFrequency.MONTHLY,
                past
        );
        recurring.setId(UUID.randomUUID());

        // Setup mutable list simulating database queue
        List<RecurringExpense> mockDueQueue = new ArrayList<>(List.of(recurring));

        when(recurringExpenseRepository.findDueRecurringExpenses(any(Instant.class)))
                .thenAnswer(inv -> new ArrayList<>(mockDueQueue));

        GroupMember gm1 = new GroupMember(testGroup, alice, true);
        GroupMember gm2 = new GroupMember(testGroup, bob, false);
        when(groupMemberRepository.findByGroupIdWithUser(groupId)).thenReturn(List.of(gm1, gm2));

        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense exp = inv.getArgument(0);
            exp.setId(UUID.randomUUID());
            return exp;
        });

        // First run: processes due expense, advances nextRunAt, removes from due queue
        when(recurringExpenseRepository.save(any(RecurringExpense.class))).thenAnswer(inv -> {
            RecurringExpense r = inv.getArgument(0);
            mockDueQueue.remove(r); // Advanced past threshold
            return r;
        });

        int firstRunCount = recurringExpenseService.processDueRecurringExpenses();
        assertThat(firstRunCount).isEqualTo(1);

        verify(expenseRepository, times(1)).save(any(Expense.class));

        // Second run immediately following: queue is empty, 0 processed (Idempotent!)
        int secondRunCount = recurringExpenseService.processDueRecurringExpenses();
        assertThat(secondRunCount).isEqualTo(0);
        verify(expenseRepository, times(1)).save(any(Expense.class)); // Total save count remains 1
    }
}
