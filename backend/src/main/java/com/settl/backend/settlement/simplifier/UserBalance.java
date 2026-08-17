package com.settl.backend.settlement.simplifier;

import java.math.BigDecimal;
import java.util.UUID;

public record UserBalance(
        UUID userId,
        BigDecimal amount
) {
}
