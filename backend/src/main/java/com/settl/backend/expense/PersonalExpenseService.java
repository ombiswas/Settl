package com.settl.backend.expense;

import com.settl.backend.common.ApiException;
import com.settl.backend.expense.dto.CategoryInfoDto;
import com.settl.backend.expense.dto.CategorySpendingDto;
import com.settl.backend.expense.dto.CreatePersonalExpenseRequest;
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
import java.util.Currency;
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
        validateCurrency(currency);

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
        validateCurrency(currency);

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
        List<PersonalExpenseResponse> expenses = getPersonalExpenses(userId, null, startDate, endDate);

        BigDecimal totalSpent = BigDecimal.ZERO;
        Map<ExpenseCategory, List<PersonalExpenseResponse>> byCategory = new LinkedHashMap<>();
        Map<String, List<PersonalExpenseResponse>> byMonth = new TreeMap<>();

        for (PersonalExpenseResponse exp : expenses) {
            totalSpent = totalSpent.add(exp.amount());
            byCategory.computeIfAbsent(exp.category(), k -> new ArrayList<>()).add(exp);

            String monthStr = MONTH_FORMATTER.format(exp.createdAt());
            byMonth.computeIfAbsent(monthStr, k -> new ArrayList<>()).add(exp);
        }

        totalSpent = totalSpent.setScale(2, RoundingMode.HALF_UP);
        final BigDecimal finalTotal = totalSpent;

        List<CategorySpendingDto> categoryBreakdown = new ArrayList<>();
        for (Map.Entry<ExpenseCategory, List<PersonalExpenseResponse>> entry : byCategory.entrySet()) {
            ExpenseCategory cat = entry.getKey();
            BigDecimal catTotal = entry.getValue().stream()
                    .map(PersonalExpenseResponse::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal percentage = BigDecimal.ZERO;
            if (finalTotal.compareTo(BigDecimal.ZERO) > 0) {
                percentage = catTotal.multiply(new BigDecimal("100.00"))
                        .divide(finalTotal, 2, RoundingMode.HALF_UP);
            }

            categoryBreakdown.add(new CategorySpendingDto(
                    cat,
                    cat.getDisplayName(),
                    catTotal,
                    percentage,
                    entry.getValue().size()
            ));
        }

        categoryBreakdown.sort((a, b) -> b.totalAmount().compareTo(a.totalAmount()));

        List<MonthlySpendingDto> monthlyBreakdown = new ArrayList<>();
        for (Map.Entry<String, List<PersonalExpenseResponse>> entry : byMonth.entrySet()) {
            BigDecimal monthTotal = entry.getValue().stream()
                    .map(PersonalExpenseResponse::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            monthlyBreakdown.add(new MonthlySpendingDto(
                    entry.getKey(),
                    monthTotal,
                    entry.getValue().size()
            ));
        }

        return new PersonalExpenseAnalyticsResponse(
                totalSpent,
                expenses.size(),
                expenses.isEmpty() ? "USD" : expenses.get(0).currency(),
                categoryBreakdown,
                monthlyBreakdown
        );
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

    private void validateCurrency(String currencyCode) {
        try {
            Currency.getInstance(currencyCode);
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid ISO-4217 currency code: " + currencyCode, "INVALID_CURRENCY");
        }
    }
}
