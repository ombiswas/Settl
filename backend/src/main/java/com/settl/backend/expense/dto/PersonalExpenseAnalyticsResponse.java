package com.settl.backend.expense.dto;

import java.util.List;

public record PersonalExpenseAnalyticsResponse(
        List<CurrencyAnalyticsDto> currencies,
        int totalExpenseCount
) {
}
