package com.settl.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class TokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupScheduler.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public TokenCleanupScheduler(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Purges expired refresh tokens from the database.
     * Only tokens whose expiresAt is in the past are removed.
     * Revoked-but-unexpired tokens are deliberately retained to allow
     * refresh-token reuse and breach detection in AuthService.
     *
     * Default schedule: every day at 03:00 UTC ("0 0 3 * * *").
     */
    @Scheduled(cron = "${app.token-cleanup.cron:0 0 3 * * *}")
    @Transactional
    public int cleanupExpiredTokens() {
        log.debug("Executing scheduled expired refresh token cleanup...");
        try {
            int deletedCount = refreshTokenRepository.deleteExpiredBefore(Instant.now());
            if (deletedCount > 0) {
                log.info("Expired refresh token cleanup completed: removed {} tokens", deletedCount);
            } else {
                log.debug("Expired refresh token cleanup completed: no expired tokens to remove");
            }
            return deletedCount;
        } catch (Exception e) {
            log.error("Error occurred while executing scheduled expired refresh token cleanup", e);
            throw e;
        }
    }
}
