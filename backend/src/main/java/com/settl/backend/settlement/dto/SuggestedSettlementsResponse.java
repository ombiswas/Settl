package com.settl.backend.settlement.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record SuggestedSettlementsResponse(
        UUID groupId,
        String groupName,
        String currency,
        int transactionCount,
        BigDecimal totalSettledAmount,
        List<SuggestedSettlementDto> suggestedTransactions
) {
}
