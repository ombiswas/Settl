package com.settl.backend.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final RedisOperations<String, String> redisOperations;
    private final RedisScript<List> rateLimitScript;

    private static final String LUA_SCRIPT = """
        local key = KEYS[1]
        local now = tonumber(ARGV[1])
        local windowMs = tonumber(ARGV[2])
        local limit = tonumber(ARGV[3])
        local member = ARGV[4]

        local clearBefore = now - windowMs
        redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore)

        local currentCount = redis.call('ZCARD', key)

        if currentCount < limit then
            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, windowMs)
            return {1, limit - currentCount - 1, math.floor((now + windowMs) / 1000), 0}
        else
            local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
            local oldestTime = now
            if oldest and #oldest >= 2 then
                oldestTime = tonumber(oldest[2])
            end
            local retryAfter = math.ceil(((oldestTime + windowMs) - now) / 1000)
            if retryAfter < 1 then retryAfter = 1 end
            return {0, 0, math.floor((oldestTime + windowMs) / 1000), retryAfter}
        end
        """;

    public RateLimiterService(RedisOperations<String, String> redisOperations) {
        this.redisOperations = redisOperations;
        this.rateLimitScript = new DefaultRedisScript<>(LUA_SCRIPT, List.class);
    }

    public RateLimitResult checkRateLimit(String key, int limit, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        String member = UUID.randomUUID().toString();

        try {
            List<?> result = redisOperations.execute(
                    rateLimitScript,
                    List.of(key),
                    String.valueOf(now),
                    String.valueOf(windowMs),
                    String.valueOf(limit),
                    member
            );

            if (result != null && result.size() >= 4) {
                long allowed = ((Number) result.get(0)).longValue();
                long remaining = ((Number) result.get(1)).longValue();
                long resetEpochSeconds = ((Number) result.get(2)).longValue();
                long retryAfterSeconds = ((Number) result.get(3)).longValue();

                if (allowed == 1) {
                    return RateLimitResult.allowed(limit, remaining, resetEpochSeconds);
                } else {
                    return RateLimitResult.rejected(limit, resetEpochSeconds, retryAfterSeconds);
                }
            }
        } catch (Exception e) {
            log.warn("Redis rate limiter check failed for key: {}. Falling back to allowing request. Reason: {}", key, e.getMessage());
            long fallbackReset = (now + windowMs) / 1000;
            return RateLimitResult.allowed(limit, limit - 1, fallbackReset);
        }

        long fallbackReset = (now + windowMs) / 1000;
        return RateLimitResult.allowed(limit, limit - 1, fallbackReset);
    }
}
