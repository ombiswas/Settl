package com.settl.backend.expense.split;

import com.settl.backend.expense.SplitType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SplitStrategy {

    SplitType getSplitType();

    Map<UUID, BigDecimal> calculateShares(BigDecimal totalAmount, List<SplitParam> params, UUID payerId);
}
