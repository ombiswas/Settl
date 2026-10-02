package com.settl.backend.common.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.settl.backend.auth.CustomUserPrincipal;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
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
    private final Environment env;

    // Default global rate limit for unannotated endpoints: 100 requests per minute
    private static final int DEFAULT_LIMIT = 100;
    private static final int DEFAULT_WINDOW_SECONDS = 60;
    private static final String DEFAULT_PREFIX = "global";

    public RateLimitInterceptor(
            RateLimiterService rateLimiterService,
            ObjectMapper objectMapper,
            Environment env
    ) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
        this.env = env;
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

        String prefix = annotation != null && !annotation.keyPrefix().isBlank()
                ? annotation.keyPrefix()
                : (annotation != null ? handlerMethod.getMethod().getName() : DEFAULT_PREFIX);
        RateLimitType type = annotation != null ? annotation.type() : RateLimitType.USER_OR_IP;

        if (type == RateLimitType.IP_AND_EMAIL) {
            int ipLimit = resolveLimit(prefix, annotation != null ? annotation.limit() : DEFAULT_LIMIT);
            int ipWindow = resolveWindowSeconds(prefix, annotation != null ? annotation.windowSeconds() : DEFAULT_WINDOW_SECONDS);
            String ipKey = "ratelimit:" + prefix + ":ip:" + extractClientIp(request);
            RateLimitResult ipResult = rateLimiterService.checkRateLimit(ipKey, ipLimit, ipWindow);

            String email = extractEmailFromBody(request);
            RateLimitResult emailResult = null;
            if (email != null) {
                String emailPrefix = prefix + "-email";
                int emailLimit = resolveLimit(emailPrefix, ipLimit);
                int emailWindow = resolveWindowSeconds(emailPrefix, ipWindow);
                String emailKey = "ratelimit:" + prefix + ":email:" + email;
                emailResult = rateLimiterService.checkRateLimit(emailKey, emailLimit, emailWindow);
            }

            RateLimitResult effectiveResult;
            if (!ipResult.allowed()) {
                effectiveResult = ipResult;
            } else if (emailResult != null && !emailResult.allowed()) {
                effectiveResult = emailResult;
            } else if (emailResult != null) {
                long minRemaining = Math.min(ipResult.remaining(), emailResult.remaining());
                long maxReset = Math.max(ipResult.resetTimestampEpochSeconds(), emailResult.resetTimestampEpochSeconds());
                effectiveResult = RateLimitResult.allowed(ipResult.limit(), minRemaining, maxReset);
            } else {
                effectiveResult = ipResult;
            }

            response.setHeader("X-RateLimit-Limit", String.valueOf(effectiveResult.limit()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(effectiveResult.remaining()));
            response.setHeader("X-RateLimit-Reset", String.valueOf(effectiveResult.resetTimestampEpochSeconds()));

            if (!effectiveResult.allowed()) {
                log.warn("Rate limit exceeded for endpoint '{}' (limit: {}, retryAfter: {}s)",
                        prefix, effectiveResult.limit(), effectiveResult.retryAfterSeconds());
                throw new RateLimitExceededException(
                        "Too many requests. Please try again in " + effectiveResult.retryAfterSeconds() + " seconds.",
                        effectiveResult.retryAfterSeconds()
                );
            }

            return true;
        }

        int limit = resolveLimit(prefix, annotation != null ? annotation.limit() : DEFAULT_LIMIT);
        int windowSeconds = resolveWindowSeconds(prefix, annotation != null ? annotation.windowSeconds() : DEFAULT_WINDOW_SECONDS);

        String identifier = resolveClientIdentifier(request, type);
        String rateLimitKey = "ratelimit:" + prefix + ":" + identifier;

        RateLimitResult result = rateLimiterService.checkRateLimit(rateLimitKey, limit, windowSeconds);

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetTimestampEpochSeconds()));

        if (!result.allowed()) {
            log.warn("Rate limit exceeded for key '{}' (limit: {}, window: {}s, retryAfter: {}s)",
                    rateLimitKey, limit, windowSeconds, result.retryAfterSeconds());
            throw new RateLimitExceededException(
                    "Too many requests. Please try again in " + result.retryAfterSeconds() + " seconds.",
                    result.retryAfterSeconds()
            );
        }

        return true;
    }

    private int resolveLimit(String prefix, int fallbackLimit) {
        String propKeyHyphen = "app.rate-limit." + prefix.replace('_', '-') + ".limit";
        Integer val = env.getProperty(propKeyHyphen, Integer.class);
        if (val != null) {
            return val;
        }
        String propKeyUnderscore = "app.rate-limit." + prefix + ".limit";
        val = env.getProperty(propKeyUnderscore, Integer.class);
        return val != null ? val : fallbackLimit;
    }

    private int resolveWindowSeconds(String prefix, int fallbackWindow) {
        String propKeyHyphen = "app.rate-limit." + prefix.replace('_', '-') + ".window-seconds";
        Integer val = env.getProperty(propKeyHyphen, Integer.class);
        if (val != null) {
            return val;
        }
        String propKeyUnderscore = "app.rate-limit." + prefix + ".window-seconds";
        val = env.getProperty(propKeyUnderscore, Integer.class);
        return val != null ? val : fallbackWindow;
    }

    private String extractEmailFromBody(HttpServletRequest request) {
        try {
            ServletInputStream is = request.getInputStream();
            if (is == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(is);
            if (root != null && root.hasNonNull("email")) {
                String email = root.get("email").asText().trim();
                return email.isBlank() ? null : email.toLowerCase();
            }
        } catch (Exception e) {
            log.debug("Could not extract email from request body for rate limiting: {}", e.getMessage());
        }
        return null;
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

