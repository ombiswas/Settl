package com.settl.backend.settlement.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record UserAmountDto(
        UUID userId,
        BigDecimal amount
) {
}
