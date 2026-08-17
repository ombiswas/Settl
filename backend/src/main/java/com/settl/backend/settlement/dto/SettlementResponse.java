package com.settl.backend.settlement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SettlementResponse(
        UUID id,
        UUID groupId,
        UUID fromUserId,
        String fromUserName,
        String fromUserEmail,
        UUID toUserId,
        String toUserName,
        String toUserEmail,
        BigDecimal amount,
        String currency,
        boolean simplified,
        Instant settledAt
) {
}
