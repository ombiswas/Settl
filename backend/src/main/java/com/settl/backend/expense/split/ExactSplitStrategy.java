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
public class ExactSplitStrategy implements SplitStrategy {

    @Override
    public SplitType getSplitType() {
        return SplitType.EXACT;
    }

    @Override
    public Map<UUID, BigDecimal> calculateShares(BigDecimal totalAmount, List<SplitParam> params, UUID payerId) {
        if (params == null || params.isEmpty()) {
            throw ApiException.badRequest("Participants and their exact split amounts are required", "INVALID_SPLIT");
        }

        BigDecimal normalizedTotal = totalAmount.setScale(2, RoundingMode.HALF_UP);
        Map<UUID, BigDecimal> shares = new LinkedHashMap<>();
        BigDecimal sum = BigDecimal.ZERO;

        for (SplitParam param : params) {
            if (param.amount() == null || param.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest("Each participant must have a positive exact split amount", "INVALID_SPLIT_AMOUNT");
            }
            BigDecimal userAmount = param.amount().setScale(2, RoundingMode.HALF_UP);
            shares.put(param.userId(), userAmount);
            sum = sum.add(userAmount);
        }

        if (sum.compareTo(normalizedTotal) != 0) {
            throw ApiException.badRequest(
                    "Exact split amounts sum (" + sum + ") does not match total expense amount (" + normalizedTotal + ")",
                    "SPLIT_SUM_MISMATCH"
            );
        }

        return shares;
    }
}
