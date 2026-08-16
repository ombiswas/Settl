package com.settl.backend.currency;

import com.settl.backend.common.ApiException;
import com.settl.backend.currency.dto.ConvertCurrencyResponse;
import com.settl.backend.currency.dto.CurrencyDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;

@Service
public class CurrencyService {

    private final FxRateProvider fxRateProvider;

    public CurrencyService(FxRateProvider fxRateProvider) {
        this.fxRateProvider = fxRateProvider;
    }

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        return convert(amount, fromCurrency, toCurrency, null);
    }

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate date) {
        if (amount == null) {
            throw ApiException.badRequest("Amount is required", "INVALID_AMOUNT");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("Amount cannot be negative", "INVALID_AMOUNT");
        }

        String from = normalizeAndValidateCurrency(fromCurrency);
        String to = normalizeAndValidateCurrency(toCurrency);

        // Fast No-Op for same-currency conversions
        if (from.equalsIgnoreCase(to)) {
            return amount.setScale(2, RoundingMode.HALF_EVEN);
        }

        Map<String, BigDecimal> eurRates = fxRateProvider.getRatesForBase("EUR", date);

        BigDecimal fromRateAgainstEur = eurRates.get(from);
        BigDecimal toRateAgainstEur = eurRates.get(to);

        if (fromRateAgainstEur == null || fromRateAgainstEur.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Unsupported from currency: " + from, "UNSUPPORTED_CURRENCY");
        }
        if (toRateAgainstEur == null || toRateAgainstEur.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Unsupported to currency: " + to, "UNSUPPORTED_CURRENCY");
        }

        // Formula: (amount / fromRateAgainstEur) * toRateAgainstEur
        // All intermediate calculations with 6 decimal places, rounded to 2 decimal places with HALF_EVEN at the end
        BigDecimal amountInEur = amount.divide(fromRateAgainstEur, 6, RoundingMode.HALF_EVEN);
        BigDecimal targetAmount = amountInEur.multiply(toRateAgainstEur).setScale(6, RoundingMode.HALF_EVEN);

        return targetAmount.setScale(2, RoundingMode.HALF_EVEN);
    }

    public BigDecimal getExchangeRate(String fromCurrency, String toCurrency, LocalDate date) {
        String from = normalizeAndValidateCurrency(fromCurrency);
        String to = normalizeAndValidateCurrency(toCurrency);

        if (from.equalsIgnoreCase(to)) {
            return BigDecimal.ONE.setScale(6, RoundingMode.HALF_EVEN);
        }

        Map<String, BigDecimal> eurRates = fxRateProvider.getRatesForBase("EUR", date);
        BigDecimal fromRate = eurRates.get(from);
        BigDecimal toRate = eurRates.get(to);

        if (fromRate == null || toRate == null || fromRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Exchange rate not available for pair " + from + "/" + to, "FX_RATE_UNAVAILABLE");
        }

        return toRate.divide(fromRate, 6, RoundingMode.HALF_EVEN);
    }

    public ConvertCurrencyResponse convertWithDetails(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate date) {
        BigDecimal converted = convert(amount, fromCurrency, toCurrency, date);
        BigDecimal rate = getExchangeRate(fromCurrency, toCurrency, date);
        String rateDate = date != null ? date.toString() : LocalDate.now().toString();

        return new ConvertCurrencyResponse(
                amount.setScale(2, RoundingMode.HALF_EVEN),
                normalizeAndValidateCurrency(fromCurrency),
                converted,
                normalizeAndValidateCurrency(toCurrency),
                rate,
                rateDate
        );
    }

    public List<CurrencyDto> getSupportedCurrencies() {
        Map<String, BigDecimal> eurRates = fxRateProvider.getRatesForBase("EUR", null);
        List<CurrencyDto> currencies = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : eurRates.entrySet()) {
            String code = entry.getKey();
            String name = getCurrencyDisplayName(code);
            currencies.add(new CurrencyDto(code, name, entry.getValue().setScale(6, RoundingMode.HALF_EVEN)));
        }

        currencies.sort((a, b) -> a.code().compareTo(b.code()));
        return currencies;
    }

    private String normalizeAndValidateCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.trim().length() != 3) {
            throw ApiException.badRequest("Currency code must be a valid 3-letter ISO-4217 code", "INVALID_CURRENCY");
        }
        String normalized = currencyCode.trim().toUpperCase();
        try {
            Currency.getInstance(normalized);
            return normalized;
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unsupported currency code: " + currencyCode, "INVALID_CURRENCY");
        }
    }

    private String getCurrencyDisplayName(String code) {
        try {
            return Currency.getInstance(code).getDisplayName();
        } catch (Exception e) {
            return code;
        }
    }
}
