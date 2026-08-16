package com.settl.backend.auth;

import com.settl.backend.auth.dto.AuthResponse;
import com.settl.backend.auth.dto.LoginRequest;
import com.settl.backend.auth.dto.RegisterRequest;
import com.settl.backend.auth.dto.RegisterResponse;
import com.settl.backend.auth.dto.ResendVerificationRequest;
import com.settl.backend.auth.dto.UserDto;
import com.settl.backend.auth.dto.VerifyEmailResponse;
import com.settl.backend.common.ApiException;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    public static final String REFRESH_COOKIE_NAME = "refresh_token";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtService jwtService;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String appBaseUrl;

    @Value("${app.jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.jwtService = jwtService;
    }

    public record LoginResult(AuthResponse authResponse, ResponseCookie refreshCookie) {}

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw ApiException.conflict("An account with this email already exists", "EMAIL_ALREADY_EXISTS");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        String rawVerificationToken = generateSecureToken();
        String hashedVerificationToken = hashToken(rawVerificationToken);

        User user = new User(normalizedEmail, passwordHash, request.displayName().trim());
        user.setEmailVerified(false);
        user.setVerificationToken(hashedVerificationToken);
        user.setVerificationTokenExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));

        User savedUser = userRepository.save(user);

        String verificationUrl = appBaseUrl.split(",")[0].trim() + "/verify?token=" + rawVerificationToken;
        emailService.sendVerificationEmail(savedUser.getEmail(), savedUser.getDisplayName(), verificationUrl);

        log.info("User registered successfully: id={}, email={}", savedUser.getId(), savedUser.getEmail());

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getDisplayName(),
                savedUser.isEmailVerified(),
                "Registration successful. Please check your email to verify your account."
        );
    }

    @Transactional
    public VerifyEmailResponse verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.badRequest("Verification token is required", "INVALID_TOKEN");
        }

        String tokenHash = hashToken(rawToken.trim());
        Optional<User> userOpt = userRepository.findByVerificationToken(tokenHash);

        if (userOpt.isEmpty()) {
            throw ApiException.badRequest("Invalid or already used verification token", "INVALID_OR_EXPIRED_TOKEN");
        }

        User user = userOpt.get();

        if (user.getVerificationTokenExpiresAt() != null && user.getVerificationTokenExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Verification token has expired. Please request a new verification link.", "TOKEN_EXPIRED");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiresAt(null);
        userRepository.save(user);

        log.info("Email verified successfully for user id={}", user.getId());

        return new VerifyEmailResponse(true, "Email successfully verified! You can now log in.");
    }

    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (!user.isEmailVerified()) {
                String rawToken = generateSecureToken();
                String hashedToken = hashToken(rawToken);

                user.setVerificationToken(hashedToken);
                user.setVerificationTokenExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
                userRepository.save(user);

                String verificationUrl = appBaseUrl.split(",")[0].trim() + "/verify?token=" + rawToken;
                emailService.sendVerificationEmail(user.getEmail(), user.getDisplayName(), verificationUrl);

                log.info("Resent verification email for user id={}", user.getId());
            }
        }
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password", "INVALID_CREDENTIALS"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password", "INVALID_CREDENTIALS");
        }

        if (!user.isEmailVerified()) {
            throw ApiException.forbidden("Email address has not been verified. Please verify your email before logging in.", "EMAIL_NOT_VERIFIED");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = generateSecureToken();
        String hashedRefreshToken = hashToken(rawRefreshToken);

        Instant expiresAt = Instant.now().plusMillis(refreshTokenExpirationMs);
        RefreshToken refreshToken = new RefreshToken(user, hashedRefreshToken, expiresAt);
        refreshTokenRepository.save(refreshToken);

        ResponseCookie cookie = createRefreshTokenCookie(rawRefreshToken, refreshTokenExpirationMs / 1000);

        AuthResponse authResponse = AuthResponse.of(
                accessToken,
                jwtService.getAccessTokenExpirationSeconds(),
                new UserDto(user.getId(), user.getEmail(), user.getDisplayName())
        );

        log.info("User logged in successfully: id={}, email={}", user.getId(), user.getEmail());
        return new LoginResult(authResponse, cookie);
    }

    @Transactional
    public LoginResult refreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("Refresh token is missing", "REFRESH_TOKEN_MISSING");
        }

        String tokenHash = hashToken(rawRefreshToken.trim());
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            throw ApiException.unauthorized("Invalid refresh token. Please log in again.", "INVALID_REFRESH_TOKEN");
        }

        RefreshToken oldToken = tokenOpt.get();
        User user = oldToken.getUser();

        // Breach / Reuse Detection: if already revoked token is used, revoke all tokens in family
        if (oldToken.isRevoked()) {
            log.warn("Breach alert: Revoked refresh token reuse detected for user id={}. Invalidating all tokens.", user.getId());
            refreshTokenRepository.revokeAllByUser(user);
            throw ApiException.unauthorized("Revoked token reuse detected. All active sessions have been invalidated for security. Please log in again.", "TOKEN_REUSE_DETECTED");
        }

        if (oldToken.isExpired()) {
            oldToken.setRevoked(true);
            refreshTokenRepository.save(oldToken);
            throw ApiException.unauthorized("Refresh token has expired. Please log in again.", "REFRESH_TOKEN_EXPIRED");
        }

        // Token Rotation: revoke current token and issue new one
        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);

        String newRawRefreshToken = generateSecureToken();
        String newHashedRefreshToken = hashToken(newRawRefreshToken);
        Instant newExpiresAt = Instant.now().plusMillis(refreshTokenExpirationMs);

        RefreshToken newRefreshToken = new RefreshToken(user, newHashedRefreshToken, newExpiresAt);
        refreshTokenRepository.save(newRefreshToken);

        String newAccessToken = jwtService.generateAccessToken(user);
        ResponseCookie cookie = createRefreshTokenCookie(newRawRefreshToken, refreshTokenExpirationMs / 1000);

        AuthResponse authResponse = AuthResponse.of(
                newAccessToken,
                jwtService.getAccessTokenExpirationSeconds(),
                new UserDto(user.getId(), user.getEmail(), user.getDisplayName())
        );

        log.info("Rotated refresh token and issued new access token for user id={}", user.getId());
        return new LoginResult(authResponse, cookie);
    }

    @Transactional
    public ResponseCookie logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = hashToken(rawRefreshToken.trim());
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
                log.info("Revoked refresh token for user id={}", token.getUser().getId());
            });
        }
        return createRefreshTokenCookie("", 0);
    }

    public ResponseCookie createRefreshTokenCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(false) // Can be true in HTTPS/production
                .path("/api/auth")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .sameSite("Strict")
                .build();
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return UUID.randomUUID().toString().replace("-", "") + HexFormat.of().formatHex(randomBytes);
    }

    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
