package com.settl.backend.expense.dto;

import com.settl.backend.expense.ExpenseCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PersonalExpenseResponse(
        UUID id,
        String description,
        BigDecimal amount,
        String currency,
        ExpenseCategory category,
        String categoryDisplayName,
        String receiptUrl,
        Instant createdAt
) {
}
