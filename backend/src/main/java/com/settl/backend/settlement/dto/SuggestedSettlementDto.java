package com.settl.backend.settlement.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SuggestedSettlementDto(
        UUID fromUserId,
        String fromUserName,
        String fromUserEmail,
        UUID toUserId,
        String toUserName,
        String toUserEmail,
        BigDecimal amount,
        String currency
) {
}
