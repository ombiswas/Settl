package com.settl.backend.currency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FxRateClientTest {

    @Mock
    private RedisOperations<String, String> redisOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private FxRateClient fxRateClient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Target an unreachable URL to test fallback resilience
        fxRateClient = new FxRateClient(redisOperations, objectMapper, "http://localhost:59999");
    }

    @Test
    void shouldReturnCachedRatesWhenAvailableInRedis() {
        when(redisOperations.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("fx:rates:latest:EUR"))
                .thenReturn("{\"EUR\":1.0,\"USD\":1.10,\"GBP\":0.86}");

        Map<String, BigDecimal> rates = fxRateClient.getRatesForBase("EUR", null);

        assertThat(rates).isNotNull();
        assertThat(rates.get("USD")).isEqualByComparingTo("1.10");
        assertThat(rates.get("GBP")).isEqualByComparingTo("0.86");
    }

    @Test
    void shouldFallbackToLastKnownCacheWhenApiFails() {
        when(redisOperations.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("fx:rates:latest:EUR")).thenReturn(null);
        when(valueOperations.get("fx:rates:last_known:EUR"))
                .thenReturn("{\"EUR\":1.0,\"USD\":1.09,\"INR\":91.2}");

        Map<String, BigDecimal> rates = fxRateClient.getRatesForBase("EUR", null);

        assertThat(rates).isNotNull();
        assertThat(rates.get("USD")).isEqualByComparingTo("1.09");
        assertThat(rates.get("INR")).isEqualByComparingTo("91.2");
    }

    @Test
    void shouldFallbackToHardcodedBaselineTableWhenApiAndCacheAreUnavailable() {
        when(redisOperations.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        Map<String, BigDecimal> rates = fxRateClient.getRatesForBase("EUR", null);

        assertThat(rates).isNotNull();
        assertThat(rates).containsKey("EUR");
        assertThat(rates).containsKey("USD");
        assertThat(rates).containsKey("GBP");
        assertThat(rates).containsKey("INR");
        assertThat(rates).containsKey("CAD");
        assertThat(rates).containsKey("AUD");
        assertThat(rates).containsKey("JPY");
        assertThat(rates).containsKey("CHF");

        assertThat(rates.get("EUR")).isEqualByComparingTo("1.000000");
        assertThat(rates.get("USD")).isEqualByComparingTo("1.085000");
        assertThat(rates.get("INR")).isEqualByComparingTo("90.500000");
    }
}
