package com.settl.backend.auth;

import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TokenCleanupSchedulerIntegrationTest {

    static {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private TokenCleanupScheduler tokenCleanupScheduler;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private JavaMailSender javaMailSender;

    private User testUser;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User("cleanup-test@example.com", "hash", "Cleanup User");
        testUser.setEmailVerified(true);
        testUser = userRepository.save(testUser);
    }

    @Test
    void testCleanupExpiredTokensRemovesOnlyExpiredTokens() {
        Instant now = Instant.now();

        // 1. Valid, unexpired and unrevoked token (must NOT be deleted)
        RefreshToken validToken = new RefreshToken(testUser, "hash_valid", now.plus(7, ChronoUnit.DAYS));
        validToken = refreshTokenRepository.save(validToken);

        // 2. Revoked but UNEXPIRED token (must NOT be deleted; required for breach/reuse detection)
        RefreshToken revokedUnexpiredToken = new RefreshToken(testUser, "hash_revoked_unexpired", now.plus(3, ChronoUnit.DAYS));
        revokedUnexpiredToken.setRevoked(true);
        revokedUnexpiredToken = refreshTokenRepository.save(revokedUnexpiredToken);

        // 3. Expired token (unrevoked) (MUST be deleted)
        RefreshToken expiredToken = new RefreshToken(testUser, "hash_expired", now.minus(2, ChronoUnit.DAYS));
        expiredToken = refreshTokenRepository.save(expiredToken);

        // 4. Expired token (revoked) (MUST be deleted)
        RefreshToken expiredRevokedToken = new RefreshToken(testUser, "hash_expired_revoked", now.minus(5, ChronoUnit.DAYS));
        expiredRevokedToken.setRevoked(true);
        expiredRevokedToken = refreshTokenRepository.save(expiredRevokedToken);

        assertThat(refreshTokenRepository.count()).isEqualTo(4);

        // Run the cleanup task
        int deleted = tokenCleanupScheduler.cleanupExpiredTokens();

        assertThat(deleted).isEqualTo(2);
        assertThat(refreshTokenRepository.count()).isEqualTo(2);

        // Verify valid token still exists
        Optional<RefreshToken> remainingValid = refreshTokenRepository.findById(validToken.getId());
        assertThat(remainingValid).isPresent();
        assertThat(remainingValid.get().isRevoked()).isFalse();

        // Verify revoked-but-unexpired token still exists
        Optional<RefreshToken> remainingRevoked = refreshTokenRepository.findById(revokedUnexpiredToken.getId());
        assertThat(remainingRevoked).isPresent();
        assertThat(remainingRevoked.get().isRevoked()).isTrue();

        // Verify expired tokens were removed
        assertThat(refreshTokenRepository.findById(expiredToken.getId())).isEmpty();
        assertThat(refreshTokenRepository.findById(expiredRevokedToken.getId())).isEmpty();
    }
}
