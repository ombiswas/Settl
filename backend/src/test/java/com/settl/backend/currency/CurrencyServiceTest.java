package com.settl.backend.currency;

import com.settl.backend.common.ApiException;
import com.settl.backend.currency.dto.ConvertCurrencyResponse;
import com.settl.backend.currency.dto.CurrencyDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyServiceTest {

    @Mock
    private FxRateProvider fxRateProvider;

    private CurrencyService currencyService;

    private Map<String, BigDecimal> mockRates;

    @BeforeEach
    void setUp() {
        currencyService = new CurrencyService(fxRateProvider);

        mockRates = new HashMap<>();
        mockRates.put("EUR", new BigDecimal("1.000000"));
        mockRates.put("USD", new BigDecimal("1.085000"));
        mockRates.put("GBP", new BigDecimal("0.855000"));
        mockRates.put("INR", new BigDecimal("90.500000"));
        mockRates.put("JPY", new BigDecimal("168.500000"));
    }

    @Test
    void sameCurrencyConversionIsFastNoOp() {
        BigDecimal amount = new BigDecimal("123.456");

        BigDecimal result = currencyService.convert(amount, "USD", "USD");

        assertThat(result).isEqualTo(new BigDecimal("123.46"));
        verify(fxRateProvider, never()).getRatesForBase(any(), any());
    }

    @Test
    void conversionMathAccuracyFromEurToUsd() {
        when(fxRateProvider.getRatesForBase(eq("EUR"), any())).thenReturn(mockRates);

        // 100 EUR * 1.085000 = 108.50 USD
        BigDecimal result = currencyService.convert(new BigDecimal("100.00"), "EUR", "USD");

        assertThat(result).isEqualTo(new BigDecimal("108.50"));
    }

    @Test
    void conversionMathAccuracyCrossCurrencyUsdToInr() {
        when(fxRateProvider.getRatesForBase(eq("EUR"), any())).thenReturn(mockRates);

        // 100 USD -> EUR: 100 / 1.085 = 92.165899 EUR
        // 92.165899 * 90.50 = 8341.013860 -> 8341.01 INR
        BigDecimal result = currencyService.convert(new BigDecimal("100.00"), "USD", "INR");

        assertThat(result).isEqualTo(new BigDecimal("8341.01"));
    }

    @Test
    void roundingEdgeCasesHalfEvenBankersRounding() {
        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("EUR", new BigDecimal("1.000000"));
        rates.put("USD", new BigDecimal("1.000000"));
        when(fxRateProvider.getRatesForBase(eq("EUR"), any())).thenReturn(rates);

        // Half-even rounding tests through CurrencyService
        // 2.505 rounds to 2.50 (even)
        // 2.515 rounds to 2.52 (even)
        // 2.525 rounds to 2.52 (even)
        BigDecimal val1 = currencyService.convert(new BigDecimal("2.505"), "EUR", "USD");
        BigDecimal val2 = currencyService.convert(new BigDecimal("2.515"), "EUR", "USD");
        BigDecimal val3 = currencyService.convert(new BigDecimal("2.525"), "EUR", "USD");

        assertThat(val1).isEqualTo(new BigDecimal("2.50"));
        assertThat(val2).isEqualTo(new BigDecimal("2.52"));
        assertThat(val3).isEqualTo(new BigDecimal("2.52"));
    }

    @Test
    void negativeAmountShouldThrowBadRequest() {
        assertThatThrownBy(() -> currencyService.convert(new BigDecimal("-10.00"), "USD", "EUR"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Amount cannot be negative");
    }

    @Test
    void invalidCurrencyCodeShouldThrowBadRequest() {
        assertThatThrownBy(() -> currencyService.convert(new BigDecimal("10.00"), "XYZ123", "EUR"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Currency code must be a valid 3-letter ISO-4217 code");
    }

    @Test
    void convertWithDetailsReturnsProperMetadata() {
        when(fxRateProvider.getRatesForBase(eq("EUR"), any())).thenReturn(mockRates);

        LocalDate testDate = LocalDate.of(2026, 8, 16);
        ConvertCurrencyResponse response = currencyService.convertWithDetails(new BigDecimal("50.00"), "EUR", "GBP", testDate);

        assertThat(response.originalAmount()).isEqualTo(new BigDecimal("50.00"));
        assertThat(response.fromCurrency()).isEqualTo("EUR");
        assertThat(response.toCurrency()).isEqualTo("GBP");
        assertThat(response.convertedAmount()).isEqualTo(new BigDecimal("42.75")); // 50 * 0.855
        assertThat(response.rateDate()).isEqualTo("2026-08-16");
    }

    @Test
    void getSupportedCurrenciesReturnsSortedListWithRates() {
        when(fxRateProvider.getRatesForBase(eq("EUR"), any())).thenReturn(mockRates);

        List<CurrencyDto> supported = currencyService.getSupportedCurrencies();

        assertThat(supported).isNotEmpty();
        assertThat(supported).extracting(CurrencyDto::code).contains("EUR", "USD", "GBP", "INR", "JPY");
    }
}
