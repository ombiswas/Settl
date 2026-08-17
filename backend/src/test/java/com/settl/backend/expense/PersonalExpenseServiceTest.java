package com.settl.backend.expense;

import com.settl.backend.common.ApiException;
import com.settl.backend.expense.dto.CategoryInfoDto;
import com.settl.backend.expense.dto.CreatePersonalExpenseRequest;
import com.settl.backend.expense.dto.PersonalExpenseAnalyticsResponse;
import com.settl.backend.expense.dto.PersonalExpenseResponse;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private UserRepository userRepository;

    private PersonalExpenseService personalExpenseService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        personalExpenseService = new PersonalExpenseService(expenseRepository, userRepository);

        userId = UUID.randomUUID();
        testUser = new User("user@example.com", "hash", "User Name");
        testUser.setId(userId);
    }

    @Test
    void createPersonalExpenseShouldSetGroupNullAndSplitTypePersonal() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(inv -> {
            Expense exp = inv.getArgument(0);
            exp.setId(UUID.randomUUID());
            return exp;
        });

        CreatePersonalExpenseRequest request = new CreatePersonalExpenseRequest(
                "Lunch with Coffee",
                new BigDecimal("15.50"),
                "USD",
                ExpenseCategory.FOOD_AND_DINING,
                null
        );

        PersonalExpenseResponse response = personalExpenseService.createPersonalExpense(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.description()).isEqualTo("Lunch with Coffee");
        assertThat(response.amount()).isEqualTo(new BigDecimal("15.50"));
        assertThat(response.category()).isEqualTo(ExpenseCategory.FOOD_AND_DINING);

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        Expense saved = captor.getValue();
        assertThat(saved.getGroup()).isNull();
        assertThat(saved.getSplitType()).isEqualTo(SplitType.PERSONAL);
        assertThat(saved.getShares()).hasSize(1);
        assertThat(saved.getShares().get(0).getAmountOwed()).isEqualTo(new BigDecimal("15.50"));
    }

    @Test
    void getPersonalAnalyticsCalculatesMetricsAccurately() {
        Expense exp1 = new Expense(null, testUser, "Groceries", new BigDecimal("100.00"), "USD", ExpenseCategory.FOOD_AND_DINING, SplitType.PERSONAL, null);
        exp1.setId(UUID.randomUUID());
        exp1.setCreatedAt(Instant.parse("2026-08-01T10:00:00Z"));

        Expense exp2 = new Expense(null, testUser, "Subway Pass", new BigDecimal("50.00"), "USD", ExpenseCategory.TRANSPORTATION, SplitType.PERSONAL, null);
        exp2.setId(UUID.randomUUID());
        exp2.setCreatedAt(Instant.parse("2026-08-05T10:00:00Z"));

        Expense exp3 = new Expense(null, testUser, "Dinner Out", new BigDecimal("50.00"), "USD", ExpenseCategory.FOOD_AND_DINING, SplitType.PERSONAL, null);
        exp3.setId(UUID.randomUUID());
        exp3.setCreatedAt(Instant.parse("2026-08-10T10:00:00Z"));

        when(expenseRepository.findPersonalExpensesByUserId(userId)).thenReturn(List.of(exp1, exp2, exp3));

        PersonalExpenseAnalyticsResponse analytics = personalExpenseService.getPersonalAnalytics(userId, null, null);

        assertThat(analytics.totalSpent()).isEqualTo(new BigDecimal("200.00"));
        assertThat(analytics.totalExpenseCount()).isEqualTo(3);
        assertThat(analytics.categoryBreakdown()).hasSize(2);

        // Food total is 150 (75%), Transportation total is 50 (25%)
        assertThat(analytics.categoryBreakdown().get(0).category()).isEqualTo(ExpenseCategory.FOOD_AND_DINING);
        assertThat(analytics.categoryBreakdown().get(0).totalAmount()).isEqualTo(new BigDecimal("150.00"));
        assertThat(analytics.categoryBreakdown().get(0).percentage()).isEqualTo(new BigDecimal("75.00"));

        assertThat(analytics.categoryBreakdown().get(1).category()).isEqualTo(ExpenseCategory.TRANSPORTATION);
        assertThat(analytics.categoryBreakdown().get(1).totalAmount()).isEqualTo(new BigDecimal("50.00"));
        assertThat(analytics.categoryBreakdown().get(1).percentage()).isEqualTo(new BigDecimal("25.00"));

        assertThat(analytics.monthlyBreakdown()).hasSize(1);
        assertThat(analytics.monthlyBreakdown().get(0).month()).isEqualTo("2026-08");
        assertThat(analytics.monthlyBreakdown().get(0).totalAmount()).isEqualTo(new BigDecimal("200.00"));
    }

    @Test
    void deletePersonalExpenseNotInUserOwnershipThrowsNotFound() {
        UUID otherExpenseId = UUID.randomUUID();
        when(expenseRepository.findPersonalExpenseByIdAndUserId(otherExpenseId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> personalExpenseService.deletePersonalExpense(userId, otherExpenseId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Personal expense not found");
    }

    @Test
    void getAllCategoriesReturnsAllEnumValues() {
        List<CategoryInfoDto> categories = personalExpenseService.getAllCategories();
        assertThat(categories).hasSize(ExpenseCategory.values().length);
    }
}
