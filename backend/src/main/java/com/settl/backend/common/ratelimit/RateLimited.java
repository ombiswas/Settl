package com.settl.backend.common.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {

    /**
     * Maximum number of requests allowed in the given window.
     */
    int limit();

    /**
     * Duration of the sliding window in seconds.
     */
    int windowSeconds();

    /**
     * Optional custom key prefix for grouping (e.g., "login", "register").
     */
    String keyPrefix() default "";

    /**
     * Whether to rate-limit by IP or by authenticated User (falling back to IP).
     */
    RateLimitType type() default RateLimitType.IP;
}
