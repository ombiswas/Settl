package com.settl.backend.expense;

import com.settl.backend.common.ApiException;
import com.settl.backend.expense.dto.CategoryInfoDto;
import com.settl.backend.expense.dto.CreatePersonalExpenseRequest;
import com.settl.backend.expense.dto.CurrencyAnalyticsDto;
import com.settl.backend.expense.dto.CurrencyCategorySpendingDto;
import com.settl.backend.expense.dto.CurrencySummaryDto;
import com.settl.backend.expense.dto.ExpenseDateAmountDto;
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
        // Setup mock aggregate results for USD
        when(expenseRepository.findPersonalTotalsByUserId(userId))
                .thenReturn(List.of(new CurrencySummaryDto("USD", new BigDecimal("200.00"), 3L)));

        when(expenseRepository.findPersonalCategoriesByUserId(userId))
                .thenReturn(List.of(
                        new CurrencyCategorySpendingDto("USD", ExpenseCategory.FOOD_AND_DINING, new BigDecimal("150.00"), 2L),
                        new CurrencyCategorySpendingDto("USD", ExpenseCategory.TRANSPORTATION, new BigDecimal("50.00"), 1L)
                ));

        when(expenseRepository.findPersonalExpenseDatesByUserId(userId))
                .thenReturn(List.of(
                        new ExpenseDateAmountDto("USD", Instant.parse("2026-08-01T10:00:00Z"), new BigDecimal("100.00")),
                        new ExpenseDateAmountDto("USD", Instant.parse("2026-08-05T10:00:00Z"), new BigDecimal("50.00")),
                        new ExpenseDateAmountDto("USD", Instant.parse("2026-08-10T10:00:00Z"), new BigDecimal("50.00"))
                ));

        PersonalExpenseAnalyticsResponse analytics = personalExpenseService.getPersonalAnalytics(userId, null, null);

        assertThat(analytics.totalExpenseCount()).isEqualTo(3);
        assertThat(analytics.currencies()).hasSize(1);

        CurrencyAnalyticsDto usd = analytics.currencies().get(0);
        assertThat(usd.currency()).isEqualTo("USD");
        assertThat(usd.totalSpent()).isEqualTo(new BigDecimal("200.00"));
        assertThat(usd.totalExpenseCount()).isEqualTo(3);
        assertThat(usd.categoryBreakdown()).hasSize(2);

        // Food total is 150 (75%), Transportation total is 50 (25%)
        assertThat(usd.categoryBreakdown().get(0).category()).isEqualTo(ExpenseCategory.FOOD_AND_DINING);
        assertThat(usd.categoryBreakdown().get(0).totalAmount()).isEqualTo(new BigDecimal("150.00"));
        assertThat(usd.categoryBreakdown().get(0).percentage()).isEqualTo(new BigDecimal("75.00"));

        assertThat(usd.categoryBreakdown().get(1).category()).isEqualTo(ExpenseCategory.TRANSPORTATION);
        assertThat(usd.categoryBreakdown().get(1).totalAmount()).isEqualTo(new BigDecimal("50.00"));
        assertThat(usd.categoryBreakdown().get(1).percentage()).isEqualTo(new BigDecimal("25.00"));

        assertThat(usd.monthlyBreakdown()).hasSize(1);
        assertThat(usd.monthlyBreakdown().get(0).month()).isEqualTo("2026-08");
        assertThat(usd.monthlyBreakdown().get(0).totalAmount()).isEqualTo(new BigDecimal("200.00"));
    }

    @Test
    void getPersonalAnalyticsWithMixedCurrenciesNeverMixesAmounts() {
        // Setup mock aggregate results for INR and USD
        when(expenseRepository.findPersonalTotalsByUserId(userId))
                .thenReturn(List.of(
                        new CurrencySummaryDto("INR", new BigDecimal("5000.00"), 2L),
                        new CurrencySummaryDto("USD", new BigDecimal("150.00"), 2L)
                ));

        when(expenseRepository.findPersonalCategoriesByUserId(userId))
                .thenReturn(List.of(
                        new CurrencyCategorySpendingDto("INR", ExpenseCategory.HOUSING_AND_UTILITIES, new BigDecimal("3000.00"), 1L),
                        new CurrencyCategorySpendingDto("INR", ExpenseCategory.FOOD_AND_DINING, new BigDecimal("2000.00"), 1L),
                        new CurrencyCategorySpendingDto("USD", ExpenseCategory.FOOD_AND_DINING, new BigDecimal("100.00"), 1L),
                        new CurrencyCategorySpendingDto("USD", ExpenseCategory.TRANSPORTATION, new BigDecimal("50.00"), 1L)
                ));

        when(expenseRepository.findPersonalExpenseDatesByUserId(userId))
                .thenReturn(List.of(
                        new ExpenseDateAmountDto("INR", Instant.parse("2026-08-01T10:00:00Z"), new BigDecimal("2000.00")),
                        new ExpenseDateAmountDto("INR", Instant.parse("2026-09-01T10:00:00Z"), new BigDecimal("3000.00")),
                        new ExpenseDateAmountDto("USD", Instant.parse("2026-08-02T10:00:00Z"), new BigDecimal("100.00")),
                        new ExpenseDateAmountDto("USD", Instant.parse("2026-08-15T10:00:00Z"), new BigDecimal("50.00"))
                ));

        PersonalExpenseAnalyticsResponse analytics = personalExpenseService.getPersonalAnalytics(userId, null, null);

        assertThat(analytics.totalExpenseCount()).isEqualTo(4);
        assertThat(analytics.currencies()).hasSize(2);

        // INR section
        CurrencyAnalyticsDto inr = analytics.currencies().get(0);
        assertThat(inr.currency()).isEqualTo("INR");
        assertThat(inr.totalSpent()).isEqualTo(new BigDecimal("5000.00"));
        assertThat(inr.totalExpenseCount()).isEqualTo(2);
        assertThat(inr.categoryBreakdown()).hasSize(2);
        assertThat(inr.categoryBreakdown().get(0).category()).isEqualTo(ExpenseCategory.HOUSING_AND_UTILITIES);
        assertThat(inr.categoryBreakdown().get(0).totalAmount()).isEqualTo(new BigDecimal("3000.00"));
        assertThat(inr.categoryBreakdown().get(0).percentage()).isEqualTo(new BigDecimal("60.00"));
        assertThat(inr.categoryBreakdown().get(1).category()).isEqualTo(ExpenseCategory.FOOD_AND_DINING);
        assertThat(inr.categoryBreakdown().get(1).totalAmount()).isEqualTo(new BigDecimal("2000.00"));
        assertThat(inr.categoryBreakdown().get(1).percentage()).isEqualTo(new BigDecimal("40.00"));
        assertThat(inr.monthlyBreakdown()).hasSize(2);
        assertThat(inr.monthlyBreakdown().get(0).month()).isEqualTo("2026-08");
        assertThat(inr.monthlyBreakdown().get(0).totalAmount()).isEqualTo(new BigDecimal("2000.00"));
        assertThat(inr.monthlyBreakdown().get(1).month()).isEqualTo("2026-09");
        assertThat(inr.monthlyBreakdown().get(1).totalAmount()).isEqualTo(new BigDecimal("3000.00"));

        // USD section
        CurrencyAnalyticsDto usd = analytics.currencies().get(1);
        assertThat(usd.currency()).isEqualTo("USD");
        assertThat(usd.totalSpent()).isEqualTo(new BigDecimal("150.00"));
        assertThat(usd.totalExpenseCount()).isEqualTo(2);
        assertThat(usd.categoryBreakdown()).hasSize(2);
        assertThat(usd.categoryBreakdown().get(0).category()).isEqualTo(ExpenseCategory.FOOD_AND_DINING);
        assertThat(usd.categoryBreakdown().get(0).totalAmount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(usd.categoryBreakdown().get(0).percentage()).isEqualTo(new BigDecimal("66.67"));
        assertThat(usd.categoryBreakdown().get(1).category()).isEqualTo(ExpenseCategory.TRANSPORTATION);
        assertThat(usd.categoryBreakdown().get(1).totalAmount()).isEqualTo(new BigDecimal("50.00"));
        assertThat(usd.categoryBreakdown().get(1).percentage()).isEqualTo(new BigDecimal("33.33"));
        assertThat(usd.monthlyBreakdown()).hasSize(1);
        assertThat(usd.monthlyBreakdown().get(0).month()).isEqualTo("2026-08");
        assertThat(usd.monthlyBreakdown().get(0).totalAmount()).isEqualTo(new BigDecimal("150.00"));
    }

    @Test
    void getPersonalAnalyticsWithEmptyDataReturnsEmptyCurrencies() {
        when(expenseRepository.findPersonalTotalsByUserId(userId)).thenReturn(List.of());

        PersonalExpenseAnalyticsResponse analytics = personalExpenseService.getPersonalAnalytics(userId, null, null);

        assertThat(analytics.currencies()).isEmpty();
        assertThat(analytics.totalExpenseCount()).isEqualTo(0);
    }

    @Test
    void getPersonalAnalyticsWithCurrencyFilterReturnsOnlyRequestedCurrency() {
        when(expenseRepository.findPersonalTotalsByUserIdAndCurrency(userId, "USD"))
                .thenReturn(List.of(new CurrencySummaryDto("USD", new BigDecimal("100.00"), 1L)));
        when(expenseRepository.findPersonalCategoriesByUserIdAndCurrency(userId, "USD"))
                .thenReturn(List.of(new CurrencyCategorySpendingDto("USD", ExpenseCategory.FOOD_AND_DINING, new BigDecimal("100.00"), 1L)));
        when(expenseRepository.findPersonalExpenseDatesByUserIdAndCurrency(userId, "USD"))
                .thenReturn(List.of(new ExpenseDateAmountDto("USD", Instant.parse("2026-08-01T10:00:00Z"), new BigDecimal("100.00"))));

        PersonalExpenseAnalyticsResponse analytics = personalExpenseService.getPersonalAnalytics(userId, "USD", null, null);

        assertThat(analytics.currencies()).hasSize(1);
        assertThat(analytics.currencies().get(0).currency()).isEqualTo("USD");
        assertThat(analytics.currencies().get(0).totalSpent()).isEqualTo(new BigDecimal("100.00"));
        assertThat(analytics.totalExpenseCount()).isEqualTo(1);
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
