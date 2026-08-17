package com.settl.backend.settlement.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record UserBalanceDto(
        UUID userId,
        String displayName,
        String email,
        BigDecimal netBalance,
        String status,
        BigDecimal totalPaid,
        BigDecimal totalShare
) {
}
