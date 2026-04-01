package com.yem.hlm.backend.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Creates and clears the httpOnly auth cookie that carries the JWT session token.
 *
 * <p>Security properties:
 * <ul>
 *   <li><b>HttpOnly</b> — invisible to JavaScript, prevents XSS token theft</li>
 *   <li><b>Secure</b>   — transmitted only over HTTPS (set {@code COOKIE_SECURE=true} in production)</li>
 *   <li><b>SameSite=Lax</b> — sent on same-site requests and top-level navigations;
 *       blocks cross-site form-post CSRF attacks while still working behind a
 *       same-domain reverse proxy (nginx / Cloudflare)</li>
 *   <li><b>Path=/</b>   — sent to all backend paths (auth + api)</li>
 * </ul>
 */
@Component
public class CookieTokenHelper {

    public static final String COOKIE_NAME = "hlm_auth";

    @Value("${app.cookie.secure:false}")
    private boolean secure;

    @Value("${app.cookie.same-site:Lax}")
    private String sameSite;

    /**
     * Build a Set-Cookie header value for a new auth session.
     *
     * @param token         JWT token string
     * @param maxAgeSeconds how long the cookie should live (should match JWT TTL)
     */
    public ResponseCookie buildAuthCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }

    /**
     * Build a Set-Cookie header value that immediately expires the auth cookie (logout).
     */
    public ResponseCookie buildClearCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(0)
                .build();
    }

    /**
     * Extract the JWT value from the incoming request's cookies.
     * Returns null if the cookie is absent or blank.
     */
    public String extractFromRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) {
                String val = c.getValue();
                return (val == null || val.isBlank()) ? null : val;
            }
        }
        return null;
    }
}
