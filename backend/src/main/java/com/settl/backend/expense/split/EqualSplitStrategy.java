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
public class EqualSplitStrategy implements SplitStrategy {

    @Override
    public SplitType getSplitType() {
        return SplitType.EQUAL;
    }

    @Override
    public Map<UUID, BigDecimal> calculateShares(BigDecimal totalAmount, List<SplitParam> params, UUID payerId) {
        if (params == null || params.isEmpty()) {
            throw ApiException.badRequest("At least one participant is required for an equal split", "INVALID_SPLIT");
        }

        int count = params.size();
        BigDecimal normalizedTotal = totalAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal baseShare = normalizedTotal.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);

        Map<UUID, BigDecimal> shares = new LinkedHashMap<>();
        for (SplitParam param : params) {
            shares.put(param.userId(), baseShare);
        }

        // Remainder calculation (e.g. 10.00 split 3 ways: 3.33 * 3 = 9.99, remainder = 0.01)
        BigDecimal currentSum = baseShare.multiply(BigDecimal.valueOf(count));
        BigDecimal remainder = normalizedTotal.subtract(currentSum);

        if (remainder.compareTo(BigDecimal.ZERO) > 0) {
            // Assign remainder cents deterministically: prefer the payer, otherwise first participant in list
            UUID primaryUser = (payerId != null && shares.containsKey(payerId)) ? payerId : params.get(0).userId();
            shares.put(primaryUser, shares.get(primaryUser).add(remainder));
        }

        return shares;
    }
}
