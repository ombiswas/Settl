package com.settl.backend.auth;

import com.settl.backend.auth.dto.RegisterRequest;
import com.settl.backend.auth.dto.RegisterResponse;
import com.settl.backend.auth.dto.ResendVerificationRequest;
import com.settl.backend.auth.dto.VerifyEmailResponse;
import com.settl.backend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "User registration, verification, and authentication endpoints")
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
}
