package com.settl.backend.settlement.simplifier;

import java.math.BigDecimal;
import java.util.UUID;

public record SimplifiedTransaction(
        UUID fromUserId,
        UUID toUserId,
        BigDecimal amount
) {
}
