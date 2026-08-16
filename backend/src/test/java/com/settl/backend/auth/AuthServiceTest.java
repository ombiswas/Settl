package com.settl.backend.auth;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private EmailService emailService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, emailService);
        ReflectionTestUtils.setField(authService, "appBaseUrl", "http://localhost:5173");
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
    void verifyEmailWithInvalidOrReusedTokenShouldThrowException() {
        String rawToken = "unknown-or-already-used-token";
        String hashedToken = AuthService.hashToken(rawToken);

        when(userRepository.findByVerificationToken(hashedToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail(rawToken))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid or already used");
    }

    @Test
    void resendVerificationForUnverifiedUserShouldRotateTokenAndSendEmail() {
        User user = new User("alex@example.com", "hash", "Alex Smith");
        user.setId(UUID.randomUUID());
        user.setEmailVerified(false);
        user.setVerificationToken("old-token-hash");

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(user));

        authService.resendVerification(new ResendVerificationRequest("alex@example.com"));

        assertThat(user.getVerificationToken()).isNotEqualTo("old-token-hash");
        assertThat(user.getVerificationTokenExpiresAt()).isAfter(Instant.now());
        verify(userRepository).save(user);
        verify(emailService).sendVerificationEmail(eq("alex@example.com"), eq("Alex Smith"), anyString());
    }

    @Test
    void resendVerificationForAlreadyVerifiedUserShouldDoNothingSilently() {
        User user = new User("alex@example.com", "hash", "Alex Smith");
        user.setId(UUID.randomUUID());
        user.setEmailVerified(true);

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(user));

        authService.resendVerification(new ResendVerificationRequest("alex@example.com"));

        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(any(), any(), any());
    }
}
