package com.settl.backend.settlement.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GroupBalanceResponse(
        UUID groupId,
        String groupName,
        String currency,
        BigDecimal totalGroupSpend,
        List<UserBalanceDto> balances
) {
}
