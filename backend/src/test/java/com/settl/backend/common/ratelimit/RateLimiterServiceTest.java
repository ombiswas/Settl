package com.settl.backend.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private RedisOperations<String, String> redisOperations;

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService(redisOperations);
    }

    @Test
    void shouldAllowRequestWhenUnderLimit() {
        long resetTime = System.currentTimeMillis() / 1000 + 60;
        when(redisOperations.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(1L, 4L, resetTime, 0L));

        RateLimitResult result = rateLimiterService.checkRateLimit("test:key", 5, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(5);
        assertThat(result.remaining()).isEqualTo(4);
        assertThat(result.resetTimestampEpochSeconds()).isEqualTo(resetTime);
        assertThat(result.retryAfterSeconds()).isEqualTo(0);
    }

    @Test
    void shouldRejectRequestWhenLimitExceeded() {
        long resetTime = System.currentTimeMillis() / 1000 + 45;
        when(redisOperations.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(0L, 0L, resetTime, 45L));

        RateLimitResult result = rateLimiterService.checkRateLimit("test:key", 5, 60);

        assertThat(result.allowed()).isFalse();
        assertThat(result.limit()).isEqualTo(5);
        assertThat(result.remaining()).isEqualTo(0);
        assertThat(result.retryAfterSeconds()).isEqualTo(45);
    }

    @Test
    void shouldFallbackGracefullyWhenRedisFails() {
        when(redisOperations.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        RateLimitResult result = rateLimiterService.checkRateLimit("test:key", 10, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(10);
    }
}
