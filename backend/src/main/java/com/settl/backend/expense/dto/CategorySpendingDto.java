package com.settl.backend.expense.dto;

import com.settl.backend.expense.ExpenseCategory;

import java.math.BigDecimal;

public record CategorySpendingDto(
        ExpenseCategory category,
        String categoryDisplayName,
        BigDecimal totalAmount,
        BigDecimal percentage,
        int count
) {
}
