package com.settl.backend.currency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.settl.backend.currency.dto.FxRatesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class FxRateClient implements FxRateProvider {

    private static final Logger log = LoggerFactory.getLogger(FxRateClient.class);

    private final RestClient restClient;
    private final RedisOperations<String, String> redisOperations;
    private final ObjectMapper objectMapper;

    @Value("${app.fx.base-url:https://api.frankfurter.dev}")
    private String fxBaseUrl;

    public static final Map<String, BigDecimal> BASELINE_EUR_RATES;

    static {
        Map<String, BigDecimal> baseline = new HashMap<>();
        baseline.put("EUR", new BigDecimal("1.000000"));
        baseline.put("USD", new BigDecimal("1.085000"));
        baseline.put("GBP", new BigDecimal("0.855000"));
        baseline.put("INR", new BigDecimal("90.500000"));
        baseline.put("CAD", new BigDecimal("1.470000"));
        baseline.put("AUD", new BigDecimal("1.650000"));
        baseline.put("JPY", new BigDecimal("168.500000"));
        baseline.put("CHF", new BigDecimal("0.960000"));
        baseline.put("SGD", new BigDecimal("1.450000"));
        baseline.put("CNY", new BigDecimal("7.850000"));
        baseline.put("NZD", new BigDecimal("1.780000"));
        BASELINE_EUR_RATES = Collections.unmodifiableMap(baseline);
    }

    public FxRateClient(
            RedisOperations<String, String> redisOperations,
            ObjectMapper objectMapper,
            @Value("${app.fx.base-url:https://api.frankfurter.dev}") String fxBaseUrl
    ) {
        this.redisOperations = redisOperations;
        this.objectMapper = objectMapper;
        this.fxBaseUrl = fxBaseUrl;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(4));

        this.restClient = RestClient.builder()
                .baseUrl(fxBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public Map<String, BigDecimal> getRatesForBase(String baseCurrency, LocalDate date) {
        String base = baseCurrency != null ? baseCurrency.trim().toUpperCase() : "EUR";
        String dateStr = date != null ? date.toString() : "latest";
        String cacheKey = "fx:rates:" + dateStr + ":" + base;
        String backupKey = "fx:rates:last_known:" + base;

        // 1. Check Redis Cache
        try {
            String cachedJson = redisOperations.opsForValue().get(cacheKey);
            if (cachedJson != null && !cachedJson.isBlank()) {
                log.debug("FX cache hit for key: {}", cacheKey);
                return objectMapper.readValue(cachedJson, new TypeReference<Map<String, BigDecimal>>() {});
            }
        } catch (Exception e) {
            log.warn("Redis read failed for FX cache key {}: {}", cacheKey, e.getMessage());
        }

        // 2. Fetch from External Frankfurter API
        try {
            String path = (date != null ? "/" + date : "/v1/latest") + "?base=" + base;
            FxRatesResponse response = restClient.get()
                    .uri(path)
                    .retrieve()
                    .body(FxRatesResponse.class);

            if (response != null && response.rates() != null && !response.rates().isEmpty()) {
                Map<String, BigDecimal> allRates = new HashMap<>(response.rates());
                allRates.put(base, BigDecimal.ONE.setScale(6));

                // Save to Redis with 24-hour TTL and backup key
                try {
                    String json = objectMapper.writeValueAsString(allRates);
                    redisOperations.opsForValue().set(cacheKey, json, Duration.ofHours(24));
                    redisOperations.opsForValue().set(backupKey, json, Duration.ofDays(30));
                    log.info("Fetched and cached FX rates from API for base={} date={}", base, dateStr);
                } catch (Exception e) {
                    log.warn("Failed to cache FX rates in Redis: {}", e.getMessage());
                }

                return allRates;
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch live FX rates from Frankfurter API ({}: {}). Attempting backup cache fallback.",
                    fxBaseUrl, ex.getMessage());
        }

        // 3. Fallback to Last Known Cache in Redis
        try {
            String lastKnownJson = redisOperations.opsForValue().get(backupKey);
            if (lastKnownJson != null && !lastKnownJson.isBlank()) {
                log.info("Using last known cached FX rates for base: {}", base);
                return objectMapper.readValue(lastKnownJson, new TypeReference<Map<String, BigDecimal>>() {});
            }
        } catch (Exception e) {
            log.warn("Redis read failed for last-known FX key {}: {}", backupKey, e.getMessage());
        }

        // 4. Fallback to Hardcoded Baseline Rates Table
        log.warn("Using hardcoded baseline FX rates for base: {}", base);
        return calculateBaselineForBase(base);
    }

    private Map<String, BigDecimal> calculateBaselineForBase(String base) {
        if ("EUR".equalsIgnoreCase(base)) {
            return new HashMap<>(BASELINE_EUR_RATES);
        }

        BigDecimal eurToBaseRate = BASELINE_EUR_RATES.get(base);
        if (eurToBaseRate == null || eurToBaseRate.compareTo(BigDecimal.ZERO) == 0) {
            return new HashMap<>(BASELINE_EUR_RATES);
        }

        Map<String, BigDecimal> rates = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : BASELINE_EUR_RATES.entrySet()) {
            BigDecimal eurToTargetRate = entry.getValue();
            BigDecimal baseToTargetRate = eurToTargetRate.divide(eurToBaseRate, 6, java.math.RoundingMode.HALF_EVEN);
            rates.put(entry.getKey(), baseToTargetRate);
        }
        rates.put(base, BigDecimal.ONE.setScale(6));
        return rates;
    }
}
