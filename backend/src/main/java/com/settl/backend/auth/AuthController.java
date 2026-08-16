package com.settl.backend.auth;

import com.settl.backend.auth.dto.AuthResponse;
import com.settl.backend.auth.dto.LoginRequest;
import com.settl.backend.auth.dto.RegisterRequest;
import com.settl.backend.auth.dto.RegisterResponse;
import com.settl.backend.auth.dto.ResendVerificationRequest;
import com.settl.backend.auth.dto.UserDto;
import com.settl.backend.auth.dto.VerifyEmailResponse;
import com.settl.backend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "User registration, email verification, login, JWT refresh rotation, and session management")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register new user", description = "Creates a new user account and dispatches an email verification link")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, response.message()));
    }

    @GetMapping("/verify")
    @Operation(summary = "Verify user email", description = "Validates the one-time verification token and activates the account")
    public ResponseEntity<ApiResponse<VerifyEmailResponse>> verifyEmail(@RequestParam("token") String token) {
        VerifyEmailResponse response = authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success(response, response.message()));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email", description = "Dispatches a new verification token to the user email if unverified")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
        return ResponseEntity.ok(ApiResponse.success(
                null,
                "If an account with that email exists and is unverified, a verification link has been sent."
        ));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in user", description = "Authenticates credentials and returns a short-lived access token + httpOnly refresh token cookie")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.refreshCookie().toString())
                .body(ApiResponse.success(result.authResponse(), "Login successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token & issue new access token", description = "Validates httpOnly refresh cookie, issues new access token, and rotates refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = AuthService.REFRESH_COOKIE_NAME, required = false) String rawRefreshToken
    ) {
        AuthService.LoginResult result = authService.refreshToken(rawRefreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.refreshCookie().toString())
                .body(ApiResponse.success(result.authResponse(), "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out user", description = "Revokes server-side refresh token and clears httpOnly cookie")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = AuthService.REFRESH_COOKIE_NAME, required = false) String rawRefreshToken
    ) {
        ResponseCookie clearCookie = authService.logout(rawRefreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(ApiResponse.success(null, "Logged out successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile", security = @SecurityRequirement(name = "BearerAuth"))
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser(@AuthenticationPrincipal CustomUserPrincipal principal) {
        UserDto userDto = new UserDto(principal.id(), principal.email(), principal.displayName());
        return ResponseEntity.ok(ApiResponse.success(userDto));
    }
}
