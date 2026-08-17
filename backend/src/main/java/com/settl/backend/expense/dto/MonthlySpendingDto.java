package com.settl.backend.expense.dto;

import java.math.BigDecimal;

public record MonthlySpendingDto(
        String month, // Format: YYYY-MM
        BigDecimal totalAmount,
        int count
) {
}
