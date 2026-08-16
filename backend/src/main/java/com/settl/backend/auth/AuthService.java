package com.settl.backend.auth;

import com.settl.backend.auth.dto.RegisterRequest;
import com.settl.backend.auth.dto.RegisterResponse;
import com.settl.backend.auth.dto.ResendVerificationRequest;
import com.settl.backend.auth.dto.VerifyEmailResponse;
import com.settl.backend.common.ApiException;
import com.settl.backend.user.User;
import com.settl.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String appBaseUrl;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

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
        // Always return generic response to prevent user enumeration
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[24];
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
