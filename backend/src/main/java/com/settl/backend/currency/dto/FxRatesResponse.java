package com.settl.backend.currency.dto;

import java.math.BigDecimal;
import java.util.Map;

public record FxRatesResponse(
        String base,
        String date,
        Map<String, BigDecimal> rates
) {
}
