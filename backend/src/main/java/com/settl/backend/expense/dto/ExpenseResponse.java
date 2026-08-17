package com.settl.backend.expense.dto;

import com.settl.backend.expense.ExpenseCategory;
import com.settl.backend.expense.SplitType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        UUID groupId,
        String groupName,
        UUID paidById,
        String paidByName,
        String paidByEmail,
        String description,
        BigDecimal amount,
        String currency,
        ExpenseCategory category,
        String categoryDisplayName,
        SplitType splitType,
        String receiptUrl,
        List<ExpenseShareDto> shares,
        Instant createdAt
) {
}
