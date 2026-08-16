package com.settl.backend.common.ratelimit;

public record RateLimitResult(
        boolean allowed,
        long limit,
        long remaining,
        long resetTimestampEpochSeconds,
        long retryAfterSeconds
) {
    public static RateLimitResult allowed(long limit, long remaining, long resetTimestampEpochSeconds) {
        return new RateLimitResult(true, limit, remaining, resetTimestampEpochSeconds, 0);
    }

    public static RateLimitResult rejected(long limit, long resetTimestampEpochSeconds, long retryAfterSeconds) {
        return new RateLimitResult(false, limit, 0, resetTimestampEpochSeconds, retryAfterSeconds);
    }
}
