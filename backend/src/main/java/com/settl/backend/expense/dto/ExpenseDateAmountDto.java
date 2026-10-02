package com.settl.backend.expense.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ExpenseDateAmountDto(
        String currency,
        Instant createdAt,
        BigDecimal amount
) {
}
