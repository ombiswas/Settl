package com.settl.backend.common;

import java.util.Currency;

/**
 * Utility for validating ISO-4217 currency codes.
 */
public final class CurrencyValidator {

    private CurrencyValidator() {
        // Utility class
    }

    public static void validate(String currencyCode) {
        try {
            Currency.getInstance(currencyCode);
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid ISO-4217 currency code: " + currencyCode, "INVALID_CURRENCY");
        }
    }

    public static void validateCurrency(String currencyCode) {
        validate(currencyCode);
    }
}
