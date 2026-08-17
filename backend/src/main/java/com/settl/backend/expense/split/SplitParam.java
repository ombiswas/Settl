package com.settl.backend.expense.split;

import java.math.BigDecimal;
import java.util.UUID;

public record SplitParam(
        UUID userId,
        BigDecimal amount,
        BigDecimal percentage,
        Integer shares
) {
    public static SplitParam equal(UUID userId) {
        return new SplitParam(userId, null, null, null);
    }

    public static SplitParam exact(UUID userId, BigDecimal amount) {
        return new SplitParam(userId, amount, null, null);
    }

    public static SplitParam percentage(UUID userId, BigDecimal percentage) {
        return new SplitParam(userId, null, percentage, null);
    }

    public static SplitParam shares(UUID userId, Integer shares) {
        return new SplitParam(userId, null, null, shares);
    }
}
