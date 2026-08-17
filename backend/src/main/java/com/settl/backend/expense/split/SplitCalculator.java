package com.settl.backend.expense.split;

import com.settl.backend.common.ApiException;
import com.settl.backend.expense.SplitType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SplitCalculator {

    private final Map<SplitType, SplitStrategy> strategyMap = new EnumMap<>(SplitType.class);

    public SplitCalculator(List<SplitStrategy> strategies) {
        for (SplitStrategy strategy : strategies) {
            strategyMap.put(strategy.getSplitType(), strategy);
        }
    }

    public Map<UUID, BigDecimal> calculate(SplitType splitType, BigDecimal totalAmount, List<SplitParam> params, UUID payerId) {
        if (splitType == null) {
            throw ApiException.badRequest("Split type is required", "INVALID_SPLIT_TYPE");
        }

        SplitStrategy strategy = strategyMap.get(splitType);
        if (strategy == null) {
            throw ApiException.badRequest("No split strategy implemented for split type: " + splitType, "UNSUPPORTED_SPLIT_TYPE");
        }

        return strategy.calculateShares(totalAmount, params, payerId);
    }
}
