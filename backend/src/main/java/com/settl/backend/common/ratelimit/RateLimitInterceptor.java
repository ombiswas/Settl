package com.settl.backend.common.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settl.backend.auth.CustomUserPrincipal;
import com.settl.backend.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    // Default global rate limit for unannotated endpoints: 100 requests per minute
    private static final int DEFAULT_LIMIT = 100;
    private static final int DEFAULT_WINDOW_SECONDS = 60;
    private static final String DEFAULT_PREFIX = "global";

    public RateLimitInterceptor(RateLimiterService rateLimiterService, ObjectMapper objectMapper) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) throws Exception {

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimited annotation = handlerMethod.getMethodAnnotation(RateLimited.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RateLimited.class);
        }

        int limit = annotation != null ? annotation.limit() : DEFAULT_LIMIT;
        int windowSeconds = annotation != null ? annotation.windowSeconds() : DEFAULT_WINDOW_SECONDS;
        String prefix = annotation != null && !annotation.keyPrefix().isBlank()
                ? annotation.keyPrefix()
                : (annotation != null ? handlerMethod.getMethod().getName() : DEFAULT_PREFIX);
        RateLimitType type = annotation != null ? annotation.type() : RateLimitType.USER_OR_IP;

        String identifier = resolveClientIdentifier(request, type);
        String rateLimitKey = "ratelimit:" + prefix + ":" + identifier;

        RateLimitResult result = rateLimiterService.checkRateLimit(rateLimitKey, limit, windowSeconds);

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetTimestampEpochSeconds()));

        if (!result.allowed()) {
            response.setStatus(429); // HTTP 429 Too Many Requests
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            log.warn("Rate limit exceeded for key '{}' (limit: {}, window: {}s, retryAfter: {}s)",
                    rateLimitKey, limit, windowSeconds, result.retryAfterSeconds());

            ApiResponse<Void> errorResponse = ApiResponse.error(
                    "Too many requests. Please try again in " + result.retryAfterSeconds() + " seconds.",
                    "RATE_LIMIT_EXCEEDED"
            );

            objectMapper.writeValue(response.getOutputStream(), errorResponse);
            return false;
        }

        return true;
    }

    private String resolveClientIdentifier(HttpServletRequest request, RateLimitType type) {
        if (type == RateLimitType.USER_OR_IP) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
                return "user:" + principal.id();
            }
        }
        return "ip:" + extractClientIp(request);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }
}
