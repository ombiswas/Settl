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
public class PercentageSplitStrategy implements SplitStrategy {

    @Override
    public SplitType getSplitType() {
        return SplitType.PERCENTAGE;
    }

    @Override
    public Map<UUID, BigDecimal> calculateShares(BigDecimal totalAmount, List<SplitParam> params, UUID payerId) {
        if (params == null || params.isEmpty()) {
            throw ApiException.badRequest("Participants and their percentage allocations are required", "INVALID_SPLIT");
        }

        BigDecimal normalizedTotal = totalAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalPercentage = BigDecimal.ZERO;

        for (SplitParam param : params) {
            if (param.percentage() == null || param.percentage().compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest("Each participant must have a positive split percentage", "INVALID_SPLIT_PERCENTAGE");
            }
            totalPercentage = totalPercentage.add(param.percentage());
        }

        if (totalPercentage.compareTo(new BigDecimal("100.00")) != 0) {
            throw ApiException.badRequest(
                    "Split percentages must sum to exactly 100.00% (currently " + totalPercentage + "%)",
                    "PERCENTAGE_SUM_MISMATCH"
            );
        }

        Map<UUID, BigDecimal> shares = new LinkedHashMap<>();
        BigDecimal calculatedSum = BigDecimal.ZERO;

        for (SplitParam param : params) {
            BigDecimal share = normalizedTotal.multiply(param.percentage())
                    .divide(new BigDecimal("100.00"), 2, RoundingMode.HALF_UP);
            shares.put(param.userId(), share);
            calculatedSum = calculatedSum.add(share);
        }

        BigDecimal diff = normalizedTotal.subtract(calculatedSum);
        if (diff.compareTo(BigDecimal.ZERO) != 0) {
            UUID primaryUser = (payerId != null && shares.containsKey(payerId)) ? payerId : params.get(0).userId();
            shares.put(primaryUser, shares.get(primaryUser).add(diff));
        }

        return shares;
    }
}
