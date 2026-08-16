package com.settl.backend.currency.dto;

import java.math.BigDecimal;

public record ConvertCurrencyResponse(
        BigDecimal originalAmount,
        String fromCurrency,
        BigDecimal convertedAmount,
        String toCurrency,
        BigDecimal exchangeRate,
        String rateDate
) {
}
