package com.settl.backend.expense.dto;

import java.math.BigDecimal;
import java.util.List;

public record PersonalExpenseAnalyticsResponse(
        BigDecimal totalSpent,
        int totalExpenseCount,
        String currency,
        List<CategorySpendingDto> categoryBreakdown,
        List<MonthlySpendingDto> monthlyBreakdown
) {
}
