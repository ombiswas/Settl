package com.settl.backend.expense.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ExpenseShareDto(
        UUID userId,
        String userDisplayName,
        String userEmail,
        BigDecimal amountOwed
) {
}
