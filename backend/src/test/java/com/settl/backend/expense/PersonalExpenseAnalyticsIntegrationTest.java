package com.settl.backend.expense;

import com.settl.backend.expense.dto.CurrencyAnalyticsDto;
import com.settl.backend.expense.dto.PersonalExpenseAnalyticsResponse;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.password=${TEST_DB_PASSWORD:2603}"
})
class PersonalExpenseAnalyticsIntegrationTest {

    static {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private PersonalExpenseService personalExpenseService;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private JavaMailSender javaMailSender;

    private User alice;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        userRepository.deleteAll();

        alice = userRepository.save(new User("alice@analytics.test", "hash", "Alice"));
    }

    @Test
    @DisplayName("Single-currency USD analytics aggregated in PostgreSQL matches exact numbers")
    void testSingleCurrencyAnalyticsMatchesExpectedNumbers() {
        createPersonalExpense("Groceries", new BigDecimal("100.00"), "USD", ExpenseCategory.FOOD_AND_DINING, "2026-08-01T10:00:00Z");
        createPersonalExpense("Subway Pass", new BigDecimal("50.00"), "USD", ExpenseCategory.TRANSPORTATION, "2026-08-05T10:00:00Z");
        createPersonalExpense("Dinner Out", new BigDecimal("50.00"), "USD", ExpenseCategory.FOOD_AND_DINING, "2026-08-10T10:00:00Z");

        PersonalExpenseAnalyticsResponse response = personalExpenseService.getPersonalAnalytics(alice.getId(), null, null);

        assertThat(response.totalExpenseCount()).isEqualTo(3);
        assertThat(response.currencies()).hasSize(1);

        CurrencyAnalyticsDto usd = response.currencies().get(0);
        assertThat(usd.currency()).isEqualTo("USD");
        assertThat(usd.totalSpent()).isEqualByComparingTo("200.00");
        assertThat(usd.totalExpenseCount()).isEqualTo(3);

        assertThat(usd.categoryBreakdown()).hasSize(2);
        assertThat(usd.categoryBreakdown().get(0).category()).isEqualTo(ExpenseCategory.FOOD_AND_DINING);
        assertThat(usd.categoryBreakdown().get(0).totalAmount()).isEqualByComparingTo("150.00");
        assertThat(usd.categoryBreakdown().get(0).percentage()).isEqualByComparingTo("75.00");
        assertThat(usd.categoryBreakdown().get(0).count()).isEqualTo(2);

        assertThat(usd.categoryBreakdown().get(1).category()).isEqualTo(ExpenseCategory.TRANSPORTATION);
        assertThat(usd.categoryBreakdown().get(1).totalAmount()).isEqualByComparingTo("50.00");
        assertThat(usd.categoryBreakdown().get(1).percentage()).isEqualByComparingTo("25.00");
        assertThat(usd.categoryBreakdown().get(1).count()).isEqualTo(1);

        assertThat(usd.monthlyBreakdown()).hasSize(1);
        assertThat(usd.monthlyBreakdown().get(0).month()).isEqualTo("2026-08");
        assertThat(usd.monthlyBreakdown().get(0).totalAmount()).isEqualByComparingTo("200.00");
        assertThat(usd.monthlyBreakdown().get(0).count()).isEqualTo(3);
    }

    @Test
    @DisplayName("Mixed USD + INR expenses are segregated into per-currency sections and never mixed")
    void testMixedCurrenciesNeverMixesAmounts() {
        createPersonalExpense("Groceries", new BigDecimal("100.00"), "USD", ExpenseCategory.FOOD_AND_DINING, "2026-08-01T10:00:00Z");
        createPersonalExpense("Taxi", new BigDecimal("50.00"), "USD", ExpenseCategory.TRANSPORTATION, "2026-08-05T10:00:00Z");

        createPersonalExpense("Thali", new BigDecimal("2000.00"), "INR", ExpenseCategory.FOOD_AND_DINING, "2026-08-10T10:00:00Z");
        createPersonalExpense("Rent", new BigDecimal("3000.00"), "INR", ExpenseCategory.HOUSING_AND_UTILITIES, "2026-09-01T10:00:00Z");

        PersonalExpenseAnalyticsResponse response = personalExpenseService.getPersonalAnalytics(alice.getId(), null, null);

        assertThat(response.totalExpenseCount()).isEqualTo(4);
        assertThat(response.currencies()).hasSize(2);

        CurrencyAnalyticsDto inr = response.currencies().stream().filter(c -> c.currency().equals("INR")).findFirst().orElseThrow();
        assertThat(inr.totalSpent()).isEqualByComparingTo("5000.00");
        assertThat(inr.totalExpenseCount()).isEqualTo(2);
        assertThat(inr.categoryBreakdown().get(0).category()).isEqualTo(ExpenseCategory.HOUSING_AND_UTILITIES);
        assertThat(inr.categoryBreakdown().get(0).totalAmount()).isEqualByComparingTo("3000.00");
        assertThat(inr.categoryBreakdown().get(0).percentage()).isEqualByComparingTo("60.00");
        assertThat(inr.monthlyBreakdown()).hasSize(2);
        assertThat(inr.monthlyBreakdown().get(0).month()).isEqualTo("2026-08");
        assertThat(inr.monthlyBreakdown().get(0).totalAmount()).isEqualByComparingTo("2000.00");
        assertThat(inr.monthlyBreakdown().get(1).month()).isEqualTo("2026-09");
        assertThat(inr.monthlyBreakdown().get(1).totalAmount()).isEqualByComparingTo("3000.00");

        CurrencyAnalyticsDto usd = response.currencies().stream().filter(c -> c.currency().equals("USD")).findFirst().orElseThrow();
        assertThat(usd.totalSpent()).isEqualByComparingTo("150.00");
        assertThat(usd.totalExpenseCount()).isEqualTo(2);
        assertThat(usd.monthlyBreakdown()).hasSize(1);
        assertThat(usd.monthlyBreakdown().get(0).month()).isEqualTo("2026-08");
        assertThat(usd.monthlyBreakdown().get(0).totalAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("Currency filter returns only matching currency section")
    void testCurrencyFilter() {
        createPersonalExpense("Coffee", new BigDecimal("10.00"), "USD", ExpenseCategory.FOOD_AND_DINING, "2026-08-01T10:00:00Z");
        createPersonalExpense("Metro", new BigDecimal("500.00"), "INR", ExpenseCategory.TRANSPORTATION, "2026-08-01T10:00:00Z");

        PersonalExpenseAnalyticsResponse usdOnly = personalExpenseService.getPersonalAnalytics(alice.getId(), "USD", null, null);
        assertThat(usdOnly.currencies()).hasSize(1);
        assertThat(usdOnly.currencies().get(0).currency()).isEqualTo("USD");
        assertThat(usdOnly.currencies().get(0).totalSpent()).isEqualByComparingTo("10.00");
        assertThat(usdOnly.totalExpenseCount()).isEqualTo(1);

        PersonalExpenseAnalyticsResponse inrOnly = personalExpenseService.getPersonalAnalytics(alice.getId(), "inr", null, null);
        assertThat(inrOnly.currencies()).hasSize(1);
        assertThat(inrOnly.currencies().get(0).currency()).isEqualTo("INR");
        assertThat(inrOnly.currencies().get(0).totalSpent()).isEqualByComparingTo("500.00");
        assertThat(inrOnly.totalExpenseCount()).isEqualTo(1);

        PersonalExpenseAnalyticsResponse eurEmpty = personalExpenseService.getPersonalAnalytics(alice.getId(), "EUR", null, null);
        assertThat(eurEmpty.currencies()).isEmpty();
        assertThat(eurEmpty.totalExpenseCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("User with zero expenses returns empty currencies array and zero count")
    void testEmptyData() {
        PersonalExpenseAnalyticsResponse response = personalExpenseService.getPersonalAnalytics(alice.getId(), null, null);
        assertThat(response.currencies()).isEmpty();
        assertThat(response.totalExpenseCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Date range filter accurately constrains PostgreSQL aggregations")
    void testDateRangeFilter() {
        createPersonalExpense("Early", new BigDecimal("100.00"), "USD", ExpenseCategory.OTHER, "2026-07-15T10:00:00Z");
        createPersonalExpense("Target 1", new BigDecimal("25.00"), "USD", ExpenseCategory.OTHER, "2026-08-01T10:00:00Z");
        createPersonalExpense("Target 2", new BigDecimal("75.00"), "USD", ExpenseCategory.OTHER, "2026-08-05T10:00:00Z");
        createPersonalExpense("Late", new BigDecimal("200.00"), "USD", ExpenseCategory.OTHER, "2026-08-20T10:00:00Z");

        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 10);

        PersonalExpenseAnalyticsResponse response = personalExpenseService.getPersonalAnalytics(alice.getId(), null, startDate, endDate);
        assertThat(response.currencies()).hasSize(1);
        assertThat(response.currencies().get(0).totalSpent()).isEqualByComparingTo("100.00");
        assertThat(response.currencies().get(0).totalExpenseCount()).isEqualTo(2);
    }

    private void createPersonalExpense(String description, BigDecimal amount, String currency, ExpenseCategory category, String timestampIso) {
        Expense expense = new Expense(
                null,
                alice,
                description,
                amount,
                currency,
                category,
                SplitType.PERSONAL,
                null
        );
        expense.setCreatedAt(Instant.parse(timestampIso));
        Expense saved = expenseRepository.save(expense);
        saved.setCreatedAt(Instant.parse(timestampIso));
        expenseRepository.save(saved);
    }
}
