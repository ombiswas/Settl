package com.settl.backend.expense.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ExpenseSplitDto(
        UUID userId,
        BigDecimal amount,
        BigDecimal percentage,
        Integer shares
) {
}
