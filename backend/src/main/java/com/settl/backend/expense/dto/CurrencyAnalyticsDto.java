package com.settl.backend.expense.dto;

import java.math.BigDecimal;
import java.util.List;

public record CurrencyAnalyticsDto(
        String currency,
        BigDecimal totalSpent,
        int totalExpenseCount,
        List<CategorySpendingDto> categoryBreakdown,
        List<MonthlySpendingDto> monthlyBreakdown
) {
}
