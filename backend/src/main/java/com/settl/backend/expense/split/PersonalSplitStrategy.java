package com.settl.backend.expense.split;

import com.settl.backend.common.ApiException;
import com.settl.backend.expense.SplitType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PersonalSplitStrategy implements SplitStrategy {

    @Override
    public SplitType getSplitType() {
        return SplitType.PERSONAL;
    }

    @Override
    public Map<UUID, BigDecimal> calculateShares(BigDecimal totalAmount, List<SplitParam> params, UUID payerId) {
        if (payerId == null) {
            throw ApiException.badRequest("Payer ID is required for a personal expense", "INVALID_SPLIT");
        }

        BigDecimal normalizedTotal = totalAmount.setScale(2, RoundingMode.HALF_UP);
        Map<UUID, BigDecimal> shares = new LinkedHashMap<>();
        shares.put(payerId, normalizedTotal);
        return shares;
    }
}
