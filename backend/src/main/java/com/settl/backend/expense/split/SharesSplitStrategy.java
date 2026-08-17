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
public class SharesSplitStrategy implements SplitStrategy {

    @Override
    public SplitType getSplitType() {
        return SplitType.SHARES;
    }

    @Override
    public Map<UUID, BigDecimal> calculateShares(BigDecimal totalAmount, List<SplitParam> params, UUID payerId) {
        if (params == null || params.isEmpty()) {
            throw ApiException.badRequest("Participants and their share weights are required", "INVALID_SPLIT");
        }

        BigDecimal normalizedTotal = totalAmount.setScale(2, RoundingMode.HALF_UP);
        int totalShares = 0;

        for (SplitParam param : params) {
            if (param.shares() == null || param.shares() <= 0) {
                throw ApiException.badRequest("Each participant must have at least 1 share", "INVALID_SHARES");
            }
            totalShares += param.shares();
        }

        if (totalShares <= 0) {
            throw ApiException.badRequest("Total shares must be greater than zero", "INVALID_SHARES");
        }

        Map<UUID, BigDecimal> shares = new LinkedHashMap<>();
        BigDecimal calculatedSum = BigDecimal.ZERO;

        for (SplitParam param : params) {
            BigDecimal userWeight = BigDecimal.valueOf(param.shares());
            BigDecimal share = normalizedTotal.multiply(userWeight)
                    .divide(BigDecimal.valueOf(totalShares), 2, RoundingMode.HALF_UP);
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
