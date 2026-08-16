package com.settl.backend.auth.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserDto user
) {
    public static AuthResponse of(String accessToken, long expiresInSeconds, UserDto user) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds, user);
    }
}
