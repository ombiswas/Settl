package com.settl.backend.expense.dto;

import java.math.BigDecimal;

public record CurrencySummaryDto(
        String currency,
        BigDecimal totalSpent,
        long count
) {
}
