package com.settl.backend.recurring.dto;

import com.settl.backend.expense.ExpenseCategory;
import com.settl.backend.expense.SplitType;
import com.settl.backend.recurring.RecurringFrequency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateRecurringExpenseRequest(
        @NotBlank(message = "Template description is required")
        @Size(max = 255, message = "Description cannot exceed 255 characters")
        String templateDescription,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        BigDecimal amount,

        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO-4217 code")
        String currency,

        @NotNull(message = "Category is required")
        ExpenseCategory category,

        @NotNull(message = "Split type is required")
        SplitType splitType,

        @NotNull(message = "Frequency is required")
        RecurringFrequency frequency,

        @NotNull(message = "Next run date/time is required")
        Instant nextRunAt
) {
}
