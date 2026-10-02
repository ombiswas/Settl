package com.settl.backend.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrencyValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "GBP", "JPY", "INR", "CAD", "AUD"})
    @DisplayName("Should accept valid ISO-4217 currency codes")
    void validate_ValidCurrency_DoesNotThrow(String currencyCode) {
        assertThatCode(() -> CurrencyValidator.validate(currencyCode))
                .doesNotThrowAnyException();
        assertThatCode(() -> CurrencyValidator.validateCurrency(currencyCode))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID", "ZZZ", "123", "US", "USDD", "   ", ""})
    @DisplayName("Should reject invalid currency codes with BAD_REQUEST and INVALID_CURRENCY error code")
    void validate_InvalidCurrency_ThrowsApiException(String currencyCode) {
        assertThatThrownBy(() -> CurrencyValidator.validate(currencyCode))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid ISO-4217 currency code: " + currencyCode)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getErrorCode()).isEqualTo("INVALID_CURRENCY");
                });
    }

    @Test
    @DisplayName("Should reject null currency code with BAD_REQUEST and INVALID_CURRENCY error code")
    void validate_NullCurrency_ThrowsApiException() {
        assertThatThrownBy(() -> CurrencyValidator.validate(null))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid ISO-4217 currency code: null")
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getErrorCode()).isEqualTo("INVALID_CURRENCY");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"usd", "eur", "gbp", "jpy", "inr"})
    @DisplayName("Should reject lowercase currency codes because ISO-4217 codes are uppercase")
    void validate_LowercaseCurrency_ThrowsApiException(String currencyCode) {
        assertThatThrownBy(() -> CurrencyValidator.validate(currencyCode))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid ISO-4217 currency code: " + currencyCode)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getErrorCode()).isEqualTo("INVALID_CURRENCY");
                });
    }
}
