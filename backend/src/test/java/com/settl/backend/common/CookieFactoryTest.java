package com.settl.backend.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import static org.assertj.core.api.Assertions.assertThat;

class CookieFactoryTest {

    @Test
    void whenSecureIsTrue_createRefreshTokenCookieHasSecureAttribute() {
        CookieFactory factory = new CookieFactory(true);
        ResponseCookie cookie = factory.createRefreshTokenCookie("raw-token-123", 3600);

        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEqualTo("raw-token-123");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(3600);
        assertThat(cookie.toString()).contains("Secure");
    }

    @Test
    void whenSecureIsFalse_createRefreshTokenCookieDoesNotHaveSecureAttribute() {
        CookieFactory factory = new CookieFactory(false);
        ResponseCookie cookie = factory.createRefreshTokenCookie("raw-token-123", 3600);

        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEqualTo("raw-token-123");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(3600);
        assertThat(cookie.toString()).doesNotContain("Secure");
    }

    @Test
    void whenSecureIsTrue_createClearRefreshTokenCookieHasSecureAttribute() {
        CookieFactory factory = new CookieFactory(true);
        ResponseCookie cookie = factory.createClearRefreshTokenCookie();

        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge().getSeconds()).isZero();
        assertThat(cookie.toString()).contains("Secure");
    }

    @Test
    void whenSecureIsFalse_createClearRefreshTokenCookieDoesNotHaveSecureAttribute() {
        CookieFactory factory = new CookieFactory(false);
        ResponseCookie cookie = factory.createClearRefreshTokenCookie();

        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getName()).isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge().getSeconds()).isZero();
        assertThat(cookie.toString()).doesNotContain("Secure");
    }
}
