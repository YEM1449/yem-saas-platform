package com.yem.hlm.backend.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Creates and clears the buyer-portal auth cookie that carries the portal JWT.
 *
 * <p>The cookie path is restricted to {@code /api/portal} so a buyer session does
 * not bleed into CRM routes when the same browser also holds an operator session.</p>
 */
@Component
public class PortalCookieHelper {

    public static final String COOKIE_NAME = "hlm_portal_auth";
    private static final String COOKIE_PATH = "/api/portal";

    @Value("${app.cookie.secure:false}")
    private boolean secure;

    @Value("${app.cookie.same-site:Lax}")
    private String sameSite;

    public ResponseCookie buildAuthCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }

    public ResponseCookie buildClearCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
    }

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
