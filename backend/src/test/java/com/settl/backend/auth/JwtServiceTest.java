package com.settl.backend.auth;

import com.settl.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private final String testSecret = Base64.getEncoder().encodeToString("very-secure-256-bit-secret-key-for-jwt-testing-12345678".getBytes());

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(testSecret, 900000); // 15 min
    }

    @Test
    void shouldGenerateAndValidateTokenSuccessfully() {
        UUID userId = UUID.randomUUID();
        User user = new User("sarah@example.com", "hash", "Sarah Connor");
        user.setId(userId);

        String token = jwtService.generateAccessToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtService.extractEmail(token)).isEqualTo("sarah@example.com");
        assertThat(jwtService.extractDisplayName(token)).isEqualTo("Sarah Connor");
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtService shortLivedJwtService = new JwtService(testSecret, -1000); // Already expired
        User user = new User("sarah@example.com", "hash", "Sarah Connor");
        user.setId(UUID.randomUUID());

        String expiredToken = shortLivedJwtService.generateAccessToken(user);

        assertThat(jwtService.isTokenValid(expiredToken)).isFalse();
    }

    @Test
    void shouldRejectTamperedToken() {
        User user = new User("sarah@example.com", "hash", "Sarah Connor");
        user.setId(UUID.randomUUID());

        String token = jwtService.generateAccessToken(user);
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

        assertThat(jwtService.isTokenValid(tamperedToken)).isFalse();
    }
}
