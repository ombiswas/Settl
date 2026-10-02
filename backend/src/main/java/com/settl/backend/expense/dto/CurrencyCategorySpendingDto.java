package com.settl.backend.expense.dto;

import com.settl.backend.expense.ExpenseCategory;

import java.math.BigDecimal;

public record CurrencyCategorySpendingDto(
        String currency,
        ExpenseCategory category,
        BigDecimal totalAmount,
        long count
) {
}
