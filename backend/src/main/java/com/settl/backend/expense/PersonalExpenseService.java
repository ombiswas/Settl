package com.settl.backend.expense;

import com.settl.backend.common.ApiException;
import com.settl.backend.common.CurrencyValidator;
import com.settl.backend.expense.dto.CategoryInfoDto;
import com.settl.backend.expense.dto.CategorySpendingDto;
import com.settl.backend.expense.dto.CreatePersonalExpenseRequest;
import com.settl.backend.expense.dto.CurrencyAnalyticsDto;
import com.settl.backend.expense.dto.CurrencyCategorySpendingDto;
import com.settl.backend.expense.dto.CurrencySummaryDto;
import com.settl.backend.expense.dto.ExpenseDateAmountDto;
import com.settl.backend.expense.dto.MonthlySpendingDto;
import com.settl.backend.expense.dto.PersonalExpenseAnalyticsResponse;
import com.settl.backend.expense.dto.PersonalExpenseResponse;
import com.settl.backend.expense.dto.UpdatePersonalExpenseRequest;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PersonalExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    public PersonalExpenseService(ExpenseRepository expenseRepository, UserRepository userRepository) {
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public PersonalExpenseResponse createPersonalExpense(UUID userId, CreatePersonalExpenseRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found", "USER_NOT_FOUND"));

        String currency = request.currency() != null && !request.currency().isBlank()
                ? request.currency().trim().toUpperCase()
                : "USD";
        CurrencyValidator.validate(currency);

        Expense expense = new Expense(
                null, // group_id is NULL for personal expenses
                user,
                request.description(),
                request.amount(),
                currency,
                request.category(),
                SplitType.PERSONAL,
                request.receiptUrl()
        );

        ExpenseShare share = new ExpenseShare(expense, user, request.amount().setScale(2, RoundingMode.HALF_UP));
        expense.addShare(share);

        Expense saved = expenseRepository.save(expense);
        return mapToPersonalResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PersonalExpenseResponse> getPersonalExpenses(
            UUID userId,
            ExpenseCategory category,
            LocalDate startDate,
            LocalDate endDate
    ) {
        List<Expense> expenses;

        if (startDate != null && endDate != null) {
            Instant start = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant end = endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

            if (category != null) {
                expenses = expenseRepository.findPersonalExpensesByUserIdAndCategoryAndDateRange(userId, category, start, end);
            } else {
                expenses = expenseRepository.findPersonalExpensesByUserIdAndDateRange(userId, start, end);
            }
        } else if (category != null) {
            expenses = expenseRepository.findPersonalExpensesByUserIdAndCategory(userId, category);
        } else {
            expenses = expenseRepository.findPersonalExpensesByUserId(userId);
        }

        return expenses.stream().map(this::mapToPersonalResponse).toList();
    }

    @Transactional(readOnly = true)
    public PersonalExpenseResponse getPersonalExpenseById(UUID userId, UUID expenseId) {
        Expense expense = expenseRepository.findPersonalExpenseByIdAndUserId(expenseId, userId)
                .orElseThrow(() -> ApiException.notFound("Personal expense not found", "EXPENSE_NOT_FOUND"));
        return mapToPersonalResponse(expense);
    }

    @Transactional
    public PersonalExpenseResponse updatePersonalExpense(UUID userId, UUID expenseId, UpdatePersonalExpenseRequest request) {
        Expense expense = expenseRepository.findPersonalExpenseByIdAndUserId(expenseId, userId)
                .orElseThrow(() -> ApiException.notFound("Personal expense not found", "EXPENSE_NOT_FOUND"));

        String currency = request.currency() != null && !request.currency().isBlank()
                ? request.currency().trim().toUpperCase()
                : expense.getCurrency();
        CurrencyValidator.validate(currency);

        expense.setDescription(request.description());
        expense.setAmount(request.amount());
        expense.setCurrency(currency);
        expense.setCategory(request.category());
        expense.setReceiptUrl(request.receiptUrl());

        if (!expense.getShares().isEmpty()) {
            expense.getShares().get(0).setAmountOwed(request.amount().setScale(2, RoundingMode.HALF_UP));
        }

        Expense updated = expenseRepository.save(expense);
        return mapToPersonalResponse(updated);
    }

    @Transactional
    public void deletePersonalExpense(UUID userId, UUID expenseId) {
        Expense expense = expenseRepository.findPersonalExpenseByIdAndUserId(expenseId, userId)
                .orElseThrow(() -> ApiException.notFound("Personal expense not found", "EXPENSE_NOT_FOUND"));
        expenseRepository.delete(expense);
    }

    @Transactional(readOnly = true)
    public PersonalExpenseAnalyticsResponse getPersonalAnalytics(
            UUID userId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return getPersonalAnalytics(userId, null, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public PersonalExpenseAnalyticsResponse getPersonalAnalytics(
            UUID userId,
            String currencyFilter,
            LocalDate startDate,
            LocalDate endDate
    ) {
        String currency = (currencyFilter != null && !currencyFilter.isBlank())
                ? currencyFilter.trim().toUpperCase()
                : null;
        if (currency != null) {
            CurrencyValidator.validate(currency);
        }

        boolean hasDateRange = (startDate != null && endDate != null);
        Instant start = hasDateRange ? startDate.atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        Instant end = hasDateRange ? endDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : null;

        List<CurrencySummaryDto> totals;
        List<CurrencyCategorySpendingDto> categories;
        List<ExpenseDateAmountDto> dates;

        if (currency != null) {
            if (hasDateRange) {
                totals = expenseRepository.findPersonalTotalsByUserIdAndCurrencyAndDateRange(userId, currency, start, end);
                categories = expenseRepository.findPersonalCategoriesByUserIdAndCurrencyAndDateRange(userId, currency, start, end);
                dates = expenseRepository.findPersonalExpenseDatesByUserIdAndCurrencyAndDateRange(userId, currency, start, end);
            } else {
                totals = expenseRepository.findPersonalTotalsByUserIdAndCurrency(userId, currency);
                categories = expenseRepository.findPersonalCategoriesByUserIdAndCurrency(userId, currency);
                dates = expenseRepository.findPersonalExpenseDatesByUserIdAndCurrency(userId, currency);
            }
        } else {
            if (hasDateRange) {
                totals = expenseRepository.findPersonalTotalsByUserIdAndDateRange(userId, start, end);
                categories = expenseRepository.findPersonalCategoriesByUserIdAndDateRange(userId, start, end);
                dates = expenseRepository.findPersonalExpenseDatesByUserIdAndDateRange(userId, start, end);
            } else {
                totals = expenseRepository.findPersonalTotalsByUserId(userId);
                categories = expenseRepository.findPersonalCategoriesByUserId(userId);
                dates = expenseRepository.findPersonalExpenseDatesByUserId(userId);
            }
        }

        if (totals.isEmpty()) {
            return new PersonalExpenseAnalyticsResponse(List.of(), 0);
        }

        Map<String, List<CurrencyCategorySpendingDto>> categoriesByCurrency = categories.stream()
                .collect(Collectors.groupingBy(CurrencyCategorySpendingDto::currency));

        Map<String, List<ExpenseDateAmountDto>> datesByCurrency = dates.stream()
                .collect(Collectors.groupingBy(ExpenseDateAmountDto::currency));

        List<CurrencyAnalyticsDto> sections = new ArrayList<>();
        int totalCountAcrossCurrencies = 0;

        for (CurrencySummaryDto totalDto : totals) {
            String curr = totalDto.currency();
            BigDecimal currTotal = totalDto.totalSpent().setScale(2, RoundingMode.HALF_UP);
            int currCount = (int) totalDto.count();
            totalCountAcrossCurrencies += currCount;

            // Category breakdown per currency
            List<CategorySpendingDto> categoryBreakdown = new ArrayList<>();
            List<CurrencyCategorySpendingDto> currCategories = categoriesByCurrency.getOrDefault(curr, List.of());
            for (CurrencyCategorySpendingDto catDto : currCategories) {
                BigDecimal catTotal = catDto.totalAmount().setScale(2, RoundingMode.HALF_UP);
                BigDecimal percentage = BigDecimal.ZERO;
                if (currTotal.compareTo(BigDecimal.ZERO) > 0) {
                    percentage = catTotal.multiply(new BigDecimal("100.00"))
                            .divide(currTotal, 2, RoundingMode.HALF_UP);
                }
                categoryBreakdown.add(new CategorySpendingDto(
                        catDto.category(),
                        catDto.category().getDisplayName(),
                        catTotal,
                        percentage,
                        (int) catDto.count()
                ));
            }
            categoryBreakdown.sort((a, b) -> b.totalAmount().compareTo(a.totalAmount()));

            // Monthly breakdown per currency
            Map<String, MonthBucket> monthlyMap = new TreeMap<>();
            List<ExpenseDateAmountDto> currDates = datesByCurrency.getOrDefault(curr, List.of());
            for (ExpenseDateAmountDto dateDto : currDates) {
                String monthStr = MONTH_FORMATTER.format(dateDto.createdAt());
                monthlyMap.computeIfAbsent(monthStr, k -> new MonthBucket()).add(dateDto.amount());
            }

            List<MonthlySpendingDto> monthlyBreakdown = monthlyMap.entrySet().stream()
                    .map(entry -> new MonthlySpendingDto(
                            entry.getKey(),
                            entry.getValue().total.setScale(2, RoundingMode.HALF_UP),
                            entry.getValue().count
                    ))
                    .toList();

            sections.add(new CurrencyAnalyticsDto(
                    curr,
                    currTotal,
                    currCount,
                    categoryBreakdown,
                    monthlyBreakdown
            ));
        }

        return new PersonalExpenseAnalyticsResponse(sections, totalCountAcrossCurrencies);
    }

    private static class MonthBucket {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;

        void add(BigDecimal amount) {
            total = total.add(amount);
            count++;
        }
    }

    public List<CategoryInfoDto> getAllCategories() {
        return Arrays.stream(ExpenseCategory.values())
                .map(cat -> new CategoryInfoDto(cat.name(), cat.getDisplayName()))
                .toList();
    }

    private PersonalExpenseResponse mapToPersonalResponse(Expense expense) {
        return new PersonalExpenseResponse(
                expense.getId(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getCurrency(),
                expense.getCategory(),
                expense.getCategory().getDisplayName(),
                expense.getReceiptUrl(),
                expense.getCreatedAt()
        );
    }
}
