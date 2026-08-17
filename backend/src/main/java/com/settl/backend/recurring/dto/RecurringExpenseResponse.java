package com.settl.backend.recurring.dto;

import com.settl.backend.expense.ExpenseCategory;
import com.settl.backend.expense.SplitType;
import com.settl.backend.recurring.RecurringFrequency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RecurringExpenseResponse(
        UUID id,
        UUID groupId,
        String templateDescription,
        BigDecimal amount,
        String currency,
        ExpenseCategory category,
        SplitType splitType,
        UUID paidById,
        String paidByName,
        String paidByEmail,
        RecurringFrequency frequency,
        Instant nextRunAt,
        boolean active,
        Instant createdAt
) {
}
