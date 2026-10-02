package com.settl.backend.user;

import com.settl.backend.auth.CustomUserPrincipal;
import com.settl.backend.common.ApiResponse;
import com.settl.backend.common.ratelimit.RateLimitType;
import com.settl.backend.common.ratelimit.RateLimited;
import com.settl.backend.user.dto.DeleteAccountRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User Management", description = "Endpoints for user profile and account lifecycle management")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @DeleteMapping("/me")
    @RateLimited(limit = 3, windowSeconds = 900, keyPrefix = "delete_account", type = RateLimitType.USER_OR_IP)
    @Operation(
            summary = "Delete user account",
            description = "Permanently deletes user account after verifying password and checking for zero balances and admin constraints",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody DeleteAccountRequest request
    ) {
        ResponseCookie clearCookie = userService.deleteAccount(principal.id(), request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(ApiResponse.success(null, "Account successfully deleted. All personal data and sessions have been purged."));
    }

    @PostMapping("/me/delete")
    @RateLimited(limit = 3, windowSeconds = 900, keyPrefix = "delete_account", type = RateLimitType.USER_OR_IP)
    @Operation(
            summary = "Delete user account (POST alternative)",
            description = "Alternative endpoint for clients/proxies that drop DELETE request payloads",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> deleteAccountPost(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody DeleteAccountRequest request
    ) {
        return deleteAccount(principal, request);
    }
}
