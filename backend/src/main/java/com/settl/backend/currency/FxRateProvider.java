package com.settl.backend.currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public interface FxRateProvider {
    Map<String, BigDecimal> getRatesForBase(String baseCurrency, LocalDate date);
}
