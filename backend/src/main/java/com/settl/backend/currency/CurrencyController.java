package com.settl.backend.currency;

import com.settl.backend.common.ApiResponse;
import com.settl.backend.currency.dto.ConvertCurrencyResponse;
import com.settl.backend.currency.dto.CurrencyDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/currencies")
@Tag(name = "Currencies", description = "Multi-currency support, live FX rates against EUR, and precise currency conversion")
public class CurrencyController {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    @GetMapping
    @Operation(summary = "List supported currencies", description = "Retrieves all supported ISO-4217 currencies and their latest rates against EUR")
    public ResponseEntity<ApiResponse<List<CurrencyDto>>> getSupportedCurrencies() {
        List<CurrencyDto> currencies = currencyService.getSupportedCurrencies();
        return ResponseEntity.ok(ApiResponse.success(currencies, "Supported currencies retrieved"));
    }

    @GetMapping("/convert")
    @Operation(summary = "Convert currency amount", description = "Converts an amount between two currencies with Banker's rounding (HALF_EVEN)")
    public ResponseEntity<ApiResponse<ConvertCurrencyResponse>> convertCurrency(
            @RequestParam("amount") BigDecimal amount,
            @RequestParam("from") String from,
            @RequestParam("to") String to,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        ConvertCurrencyResponse response = currencyService.convertWithDetails(amount, from, to, date);
        return ResponseEntity.ok(ApiResponse.success(response, "Currency converted successfully"));
    }
}
