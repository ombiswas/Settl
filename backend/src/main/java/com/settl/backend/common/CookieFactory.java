package com.settl.backend.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieFactory {

    public static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/auth";
    private static final String SAME_SITE = "Strict";

    private final boolean secureCookies;

    public CookieFactory(@Value("${app.secure-cookies:false}") boolean secureCookies) {
        this.secureCookies = secureCookies;
    }

    public ResponseCookie createRefreshTokenCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureCookies)
                .path(COOKIE_PATH)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .sameSite(SAME_SITE)
                .build();
    }

    public ResponseCookie createClearRefreshTokenCookie() {
        return createRefreshTokenCookie("", 0);
    }

    public boolean isSecure() {
        return secureCookies;
    }
}
