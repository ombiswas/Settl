package com.settl.backend.auth;

import com.settl.backend.auth.dto.LoginRequest;
import com.settl.backend.auth.dto.RegisterRequest;
import com.settl.backend.auth.dto.RegisterResponse;
import com.settl.backend.auth.dto.ResendVerificationRequest;
import com.settl.backend.auth.dto.VerifyEmailResponse;
import com.settl.backend.common.ApiException;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EmailService emailService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        String testSecret = Base64.getEncoder().encodeToString("very-secure-256-bit-secret-key-for-jwt-testing-12345678".getBytes());
        jwtService = new JwtService(testSecret, 900000);
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, emailService, jwtService);
        ReflectionTestUtils.setField(authService, "appBaseUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(authService, "refreshTokenExpirationMs", 604800000L);
    }

    @Test
    void registerShouldSucceedAndHashPasswordAndToken() {
        RegisterRequest request = new RegisterRequest("alex@example.com", "Password123!", "Alex Smith");
        when(userRepository.existsByEmail("alex@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        RegisterResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("alex@example.com");
        assertThat(response.displayName()).isEqualTo("Alex Smith");
        assertThat(response.emailVerified()).isFalse();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getPasswordHash()).isNotEqualTo("Password123!");
        assertThat(passwordEncoder.matches("Password123!", savedUser.getPasswordHash())).isTrue();
        assertThat(savedUser.getVerificationToken()).isNotNull();
        assertThat(savedUser.getVerificationTokenExpiresAt()).isAfter(Instant.now());

        verify(emailService).sendVerificationEmail(eq("alex@example.com"), eq("Alex Smith"), anyString());
    }

    @Test
    void registerDuplicateEmailShouldThrowConflict() {
        RegisterRequest request = new RegisterRequest("alex@example.com", "Password123!", "Alex Smith");
        when(userRepository.existsByEmail("alex@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(any(), any(), any());
    }

    @Test
    void verifyEmailWithValidTokenShouldActivateUser() {
        String rawToken = "sample-raw-token-12345";
        String hashedToken = AuthService.hashToken(rawToken);

        User user = new User("alex@example.com", "hash", "Alex Smith");
        user.setId(UUID.randomUUID());
        user.setEmailVerified(false);
        user.setVerificationToken(hashedToken);
        user.setVerificationTokenExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));

        when(userRepository.findByVerificationToken(hashedToken)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        VerifyEmailResponse response = authService.verifyEmail(rawToken);

        assertThat(response.verified()).isTrue();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getVerificationToken()).isNull();
        assertThat(user.getVerificationTokenExpiresAt()).isNull();

        verify(userRepository).save(user);
    }

    @Test
    void verifyEmailWithExpiredTokenShouldThrowException() {
        String rawToken = "expired-token-123";
        String hashedToken = AuthService.hashToken(rawToken);

        User user = new User("alex@example.com", "hash", "Alex Smith");
        user.setId(UUID.randomUUID());
        user.setEmailVerified(false);
        user.setVerificationToken(hashedToken);
        user.setVerificationTokenExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS));

        when(userRepository.findByVerificationToken(hashedToken)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyEmail(rawToken))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("expired");

        assertThat(user.isEmailVerified()).isFalse();
        verify(userRepository, never()).save(user);
    }

    @Test
    void loginWithVerifiedUserShouldReturnAccessTokenAndSetCookie() {
        User user = new User("alex@example.com", passwordEncoder.encode("Password123!"), "Alex Smith");
        user.setId(UUID.randomUUID());
        user.setEmailVerified(true);

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(user));

        AuthService.LoginResult result = authService.login(new LoginRequest("alex@example.com", "Password123!"));

        assertThat(result.authResponse()).isNotNull();
        assertThat(result.authResponse().accessToken()).isNotBlank();
        assertThat(result.authResponse().user().email()).isEqualTo("alex@example.com");
        assertThat(result.refreshCookie().getName()).isEqualTo("refresh_token");
        assertThat(result.refreshCookie().isHttpOnly()).isTrue();

        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void loginWithUnverifiedUserShouldThrowForbiddenEmailNotVerified() {
        User user = new User("alex@example.com", passwordEncoder.encode("Password123!"), "Alex Smith");
        user.setId(UUID.randomUUID());
        user.setEmailVerified(false);

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alex@example.com", "Password123!")))
                .isInstanceOf(ApiException.class)
                .matches(ex -> ((ApiException) ex).getErrorCode().equals("EMAIL_NOT_VERIFIED"));

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void loginWithWrongPasswordShouldThrowUnauthorized() {
        User user = new User("alex@example.com", passwordEncoder.encode("CorrectPassword!"), "Alex Smith");
        user.setId(UUID.randomUUID());
        user.setEmailVerified(true);

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alex@example.com", "WrongPassword!")))
                .isInstanceOf(ApiException.class)
                .matches(ex -> ((ApiException) ex).getErrorCode().equals("INVALID_CREDENTIALS"));
    }

    @Test
    void refreshTokenRotationShouldIssueNewAccessTokenAndRotateCookie() {
        User user = new User("alex@example.com", "hash", "Alex Smith");
        user.setId(UUID.randomUUID());
        user.setEmailVerified(true);

        String oldRawToken = "old-refresh-token-12345";
        String oldHashedToken = AuthService.hashToken(oldRawToken);
        RefreshToken oldToken = new RefreshToken(user, oldHashedToken, Instant.now().plus(7, ChronoUnit.DAYS));

        when(refreshTokenRepository.findByTokenHash(oldHashedToken)).thenReturn(Optional.of(oldToken));

        AuthService.LoginResult result = authService.refreshToken(oldRawToken);

        assertThat(oldToken.isRevoked()).isTrue();
        assertThat(result.authResponse().accessToken()).isNotBlank();
        assertThat(result.refreshCookie().getValue()).isNotEqualTo(oldRawToken);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
    }

    @Test
    void refreshTokenReuseShouldTriggerBreachDetectionAndRevokeAllTokens() {
        User user = new User("alex@example.com", "hash", "Alex Smith");
        user.setId(UUID.randomUUID());

        String leakedRawToken = "already-revoked-token";
        String leakedHashedToken = AuthService.hashToken(leakedRawToken);

        RefreshToken revokedToken = new RefreshToken(user, leakedHashedToken, Instant.now().plus(7, ChronoUnit.DAYS));
        revokedToken.setRevoked(true); // Already used / revoked

        when(refreshTokenRepository.findByTokenHash(leakedHashedToken)).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authService.refreshToken(leakedRawToken))
                .isInstanceOf(ApiException.class)
                .matches(ex -> ((ApiException) ex).getErrorCode().equals("TOKEN_REUSE_DETECTED"));

        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    void logoutShouldRevokeTokenAndClearCookie() {
        User user = new User("alex@example.com", "hash", "Alex");
        String rawToken = "sample-logout-token";
        String hashedToken = AuthService.hashToken(rawToken);
        RefreshToken token = new RefreshToken(user, hashedToken, Instant.now().plus(7, ChronoUnit.DAYS));

        when(refreshTokenRepository.findByTokenHash(hashedToken)).thenReturn(Optional.of(token));

        ResponseCookie cookie = authService.logout(rawToken);

        assertThat(token.isRevoked()).isTrue();
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(0);
        verify(refreshTokenRepository).save(token);
    }
}
