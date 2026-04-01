package com.yem.hlm.backend.auth.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.yem.hlm.backend.auth.service.JwtProvider;
import com.yem.hlm.backend.auth.service.SecurityAuditLogger;
import com.yem.hlm.backend.auth.service.UserSecurityCacheService;
import com.yem.hlm.backend.auth.service.UserSecurityInfo;
import com.yem.hlm.backend.societe.SocieteContext;

/**
 * JWT Authentication Filter (OncePerRequestFilter).
 *
 * <p>Not a @Component: instantiated explicitly by {@link SecurityConfig} to avoid
 * double-registration (once as a servlet filter, once in the security chain).</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final UserSecurityCacheService userSecurityCacheService;
    private final SecurityAuditLogger securityAuditLogger;
    private final CookieTokenHelper cookieHelper;

    public JwtAuthenticationFilter(JwtProvider jwtProvider,
                                   UserSecurityCacheService userSecurityCacheService,
                                   SecurityAuditLogger securityAuditLogger,
                                   CookieTokenHelper cookieHelper) {
        this.jwtProvider              = jwtProvider;
        this.userSecurityCacheService = userSecurityCacheService;
        this.securityAuditLogger      = securityAuditLogger;
        this.cookieHelper             = cookieHelper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // 1) Resolve token: Bearer header first (partial tokens, API clients),
            //    then fall back to the httpOnly auth cookie (SPA sessions).
            String token = resolveBearerToken(request);
            if (token == null) {
                token = cookieHelper.extractFromRequest(request);
            }

            if (token != null && jwtProvider.isValid(token)) {
                // 2) Partial tokens (issued during multi-société selection) are only
                //    valid for /auth/switch-societe. Reject them for all other endpoints
                //    by skipping authentication — Spring Security will enforce access rules.
                if (jwtProvider.isPartialToken(token)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                UUID userId = safeExtractUserId(token);
                UUID societeId = safeExtractSocieteId(token);   // null for SUPER_ADMIN tokens
                List<GrantedAuthority> authorities = safeExtractAuthorities(token);
                boolean isSuperAdmin = authorities.stream()
                        .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));

                // Only proceed when userId is known AND the token has a société context
                // (normal CRM / portal) OR is a platform-level SUPER_ADMIN token.
                if (userId != null && (societeId != null || isSuperAdmin)) {

                    if (isPortalToken(authorities)) {
                        // Portal token: sub = contactId (not a CRM User row).
                        // Skip UserSecurityCacheService — portal sessions are
                        // stateless (invalidated by TTL / single-use magic-link logic).
                        SocieteContext.setSocieteId(societeId);
                        SocieteContext.setUserId(userId); // userId == contactId for portal
                        authorities.stream().map(GrantedAuthority::getAuthority)
                                .findFirst().ifPresent(SocieteContext::setRole);

                    } else {
                        // 3) Server-side revocation check: verify user is still enabled
                        //    and token version matches (role change / disable increments version)
                        int tokenTv = safeExtractTokenVersion(token);
                        UserSecurityInfo secInfo = userSecurityCacheService.getSecurityInfo(userId);
                        if (secInfo == null || !secInfo.enabled() || secInfo.tokenVersion() != tokenTv) {
                            // Token revoked: skip authentication, let Spring Security return 401
                            filterChain.doFilter(request, response);
                            return;
                        }

                        // 4) Store multi-société context in ThreadLocal
                        if (isSuperAdmin) {
                            // Platform-level SUPER_ADMIN: no société scope, system mode
                            SocieteContext.setSuperAdmin(true);
                        } else {
                            SocieteContext.setSocieteId(societeId);
                        }
                        SocieteContext.setUserId(userId);
                        authorities.stream().map(GrantedAuthority::getAuthority)
                                .findFirst().ifPresent(SocieteContext::setRole);

                        // 4b) Impersonation tokens carry an "imp" claim identifying
                        //     the SUPER_ADMIN who is acting as the target user.
                        UUID impersonatedBy = jwtProvider.extractImpersonatedBy(token);
                        if (impersonatedBy != null) {
                            SocieteContext.setImpersonatedBy(impersonatedBy);
                        }
                    }

                    // 5) Build an Authentication object.
                    //    principal = userId (or contactId for portal), authorities = roles from JWT
                    var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }

            // Continue the chain; access control is enforced later by Spring Security.
            filterChain.doFilter(request, response);

        } finally {
            // Critical: clear ThreadLocal to avoid leaking société/user between requests
            // when the container reuses threads.
            SocieteContext.clear();
        }
    }

    /**
     * Extracts the JWT from the Authorization header if it uses the Bearer scheme.
     */
    private String resolveBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }

        if (!authHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authHeader.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * Extract societeId from JWT. Returns null if missing/malformed.
     */
    private UUID safeExtractSocieteId(String token) {
        try {
            return jwtProvider.extractSocieteId(token);
        } catch (RuntimeException ex) {
            // Missing claim "sid" or invalid UUID format => token is not usable in SaaS context.
            return null;
        }
    }

    /**
     * Extract userId from JWT subject. Returns null if missing/malformed.
     */
    private UUID safeExtractUserId(String token) {
        try {
            return jwtProvider.extractUserId(token);
        } catch (RuntimeException ex) {
            // Missing subject or invalid UUID format => treat token as invalid.
            return null;
        }
    }

    /**
     * Extract tokenVersion from JWT. Returns 0 if missing (backward compat).
     */
    private int safeExtractTokenVersion(String token) {
        try {
            return jwtProvider.extractTokenVersion(token);
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    /**
     * Returns true when the authority list contains ROLE_PORTAL.
     * Portal tokens skip the UserSecurityCacheService check (no CRM User row exists).
     */
    private boolean isPortalToken(List<GrantedAuthority> authorities) {
        return authorities.stream()
                .anyMatch(a -> "ROLE_PORTAL".equals(a.getAuthority()));
    }

    /**
     * Extract roles from JWT and convert to Spring Security authorities.
     * Returns empty list if roles are missing (backward compatibility).
     */
    private List<GrantedAuthority> safeExtractAuthorities(String token) {
        try {
            List<String> roles = jwtProvider.extractRoles(token);
            return roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        } catch (RuntimeException ex) {
            // If role extraction fails, return empty authorities (least privilege)
            return java.util.List.of();
        }
    }
}
