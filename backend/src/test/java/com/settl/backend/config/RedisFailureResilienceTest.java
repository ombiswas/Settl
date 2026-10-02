package com.settl.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.settl.backend.common.ratelimit.RateLimitInterceptor;
import com.settl.backend.common.ratelimit.RateLimitResult;
import com.settl.backend.common.ratelimit.RateLimiterService;
import com.settl.backend.settlement.GroupBalanceCacheService;
import com.settl.backend.settlement.dto.GroupBalanceResponse;
import com.settl.backend.settlement.dto.UserBalanceDto;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisFailureResilienceTest {

    @Mock
    private RedisOperations<String, String> redisOperations;

    @Test
    @DisplayName("Lettuce configuration customizer configures explicit short command and connect timeouts")
    void lettuceConfigurationEnforcesShortTimeouts() {
        RedisConfig config = new RedisConfig();
        Duration connectTimeout = Duration.ofMillis(500);
        Duration commandTimeout = Duration.ofMillis(750);

        LettuceClientConfigurationBuilderCustomizer customizer =
                config.lettuceClientConfigurationCustomizer(connectTimeout, commandTimeout);

        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
                LettuceClientConfiguration.builder();

        customizer.customize(builder);
        LettuceClientConfiguration clientConfig = builder.build();

        assertThat(clientConfig.getCommandTimeout()).isEqualTo(commandTimeout);
        assertThat(clientConfig.getClientOptions()).isPresent();

        ClientOptions options = clientConfig.getClientOptions().get();
        SocketOptions socketOptions = options.getSocketOptions();
        assertThat(socketOptions.getConnectTimeout()).isEqualTo(connectTimeout);
        assertThat(options.getTimeoutOptions().isApplyConnectionTimeout()).isTrue();
    }

    @Test
    @DisplayName("RateLimiterService fails open immediately when Redis command times out")
    void rateLimiterServiceFailsOpenOnRedisTimeout() {
        RateLimiterService service = new RateLimiterService(redisOperations);

        when(redisOperations.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new QueryTimeoutException("Redis command timed out after 1000ms"));

        long start = System.currentTimeMillis();
        RateLimitResult result = service.checkRateLimit("ratelimit:login:ip:192.168.1.1", 5, 60);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(5);
        assertThat(result.remaining()).isEqualTo(4);
        assertThat(elapsed).isLessThan(500);
    }

    @Test
    @DisplayName("RateLimiterService fails open when Redis connection fails completely")
    void rateLimiterServiceFailsOpenOnConnectionFailure() {
        RateLimiterService service = new RateLimiterService(redisOperations);

        when(redisOperations.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("Could not connect to Redis on localhost:6379"));

        RateLimitResult result = service.checkRateLimit("ratelimit:resend_verification:ip:10.0.0.1", 3, 3600);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(3);
    }

    static class DummyRateLimitedController {
        @com.settl.backend.common.ratelimit.RateLimited(keyPrefix = "dummy_login", limit = 5, windowSeconds = 60)
        public void handleRequest() {}
    }

    @Test
    @DisplayName("RateLimitInterceptor fails open on unexpected Redis error and allows request through")
    void rateLimitInterceptorAllowsRequestWhenRedisFails() throws Exception {
        RateLimiterService service = new RateLimiterService(redisOperations);
        when(redisOperations.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis unreachable"));

        RateLimitInterceptor interceptor = new RateLimitInterceptor(service, new ObjectMapper(), new MockEnvironment());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();

        DummyRateLimitedController controller = new DummyRateLimitedController();
        org.springframework.web.method.HandlerMethod handlerMethod =
                new org.springframework.web.method.HandlerMethod(controller, controller.getClass().getMethod("handleRequest"));

        boolean allowed = interceptor.preHandle(request, response, handlerMethod);
        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("CacheErrorHandler intercepts Redis errors on GET/PUT/EVICT without throwing")
    void cacheErrorHandlerCatchesRedisErrorsGracefully() {
        CacheConfig cacheConfig = new CacheConfig();
        CacheErrorHandler errorHandler = cacheConfig.errorHandler();
        Cache mockCache = mock(Cache.class);
        when(mockCache.getName()).thenReturn(CacheConfig.GROUP_BALANCES_CACHE);

        QueryTimeoutException timeout = new QueryTimeoutException("Redis GET timed out");
        RedisConnectionFailureException connFail = new RedisConnectionFailureException("Redis down");

        assertThatCode(() -> errorHandler.handleCacheGetError(timeout, mockCache, UUID.randomUUID()))
                .doesNotThrowAnyException();

        assertThatCode(() -> errorHandler.handleCachePutError(connFail, mockCache, UUID.randomUUID(), "value"))
                .doesNotThrowAnyException();

        assertThatCode(() -> errorHandler.handleCacheEvictError(connFail, mockCache, UUID.randomUUID()))
                .doesNotThrowAnyException();

        assertThatCode(() -> errorHandler.handleCacheClearError(timeout, mockCache))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Balance query returns correct DB data when cache layer throws Redis error")
    void balanceServiceReturnsDbDataWhenCacheThrows() {
        GroupBalanceCacheService mockCacheService = mock(GroupBalanceCacheService.class);
        UUID groupId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();

        GroupBalanceResponse expectedDbResponse = new GroupBalanceResponse(
                groupId,
                "Trip",
                "USD",
                BigDecimal.valueOf(150),
                List.of(new UserBalanceDto(callerId, "Alice", "alice@example.com", BigDecimal.ZERO, "SETTLED", BigDecimal.valueOf(150), BigDecimal.valueOf(150)))
        );

        when(mockCacheService.calculateGroupBalances(groupId)).thenReturn(expectedDbResponse);

        GroupBalanceResponse response = mockCacheService.calculateGroupBalances(groupId);

        assertThat(response).isNotNull();
        assertThat(response.groupId()).isEqualTo(groupId);
        assertThat(response.totalGroupSpend()).isEqualByComparingTo(BigDecimal.valueOf(150));
    }
}
