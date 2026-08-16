package com.settl.backend.currency.dto;

import java.math.BigDecimal;

public record CurrencyDto(
        String code,
        String name,
        BigDecimal rateAgainstEur
) {
}
