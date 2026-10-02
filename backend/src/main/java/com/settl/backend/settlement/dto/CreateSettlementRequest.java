package com.settl.backend.settlement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSettlementRequest(
        @NotNull(message = "Recipient user ID is required")
        UUID toUserId,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 12, fraction = 2, message = "Amount cannot have more than 2 decimal places")
        BigDecimal amount,

        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO-4217 code")
        String currency,

        Boolean isSimplified
) {
}
