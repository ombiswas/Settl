package com.settl.backend.expense.dto;

import com.settl.backend.expense.ExpenseCategory;
import com.settl.backend.expense.SplitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdateExpenseRequest(
        @NotBlank(message = "Description is required")
        @Size(max = 255, message = "Description cannot exceed 255 characters")
        String description,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        BigDecimal amount,

        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO-4217 code")
        String currency,

        @NotNull(message = "Category is required")
        ExpenseCategory category,

        @NotNull(message = "Split type is required")
        SplitType splitType,

        UUID paidByUserId,

        String receiptUrl,

        List<ExpenseSplitDto> splits
) {
}
