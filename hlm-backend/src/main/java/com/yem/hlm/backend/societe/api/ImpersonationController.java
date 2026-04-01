package com.yem.hlm.backend.societe.api;

import com.yem.hlm.backend.auth.security.CookieTokenHelper;
import com.yem.hlm.backend.societe.SocieteContext;
import com.yem.hlm.backend.societe.SocieteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles ending an active SUPER_ADMIN impersonation session.
 *
 * <p>This endpoint lives under {@code /api/**} (not {@code /api/admin/**}) so that it is
 * reachable with an impersonation token, which carries the target user's role
 * (ADMIN/MANAGER/AGENT) rather than ROLE_SUPER_ADMIN.</p>
 *
 * <p>The {@code imp} claim in the current httpOnly cookie identifies the original
 * SUPER_ADMIN. The service re-issues a full SUPER_ADMIN JWT and atomically replaces
 * the impersonation cookie.</p>
 */
@Tag(name = "Auth", description = "Authentication and société switching")
@RestController
@RequestMapping("/api")
public class ImpersonationController {

    private final SocieteService societeService;
    private final CookieTokenHelper cookieHelper;

    public ImpersonationController(SocieteService societeService, CookieTokenHelper cookieHelper) {
        this.societeService = societeService;
        this.cookieHelper   = cookieHelper;
    }

    @PostMapping("/end-impersonation")
    @Operation(summary = "End impersonation — restore the original SUPER_ADMIN session")
    public ResponseEntity<Void> endImpersonation(HttpServletResponse response) {
        String newToken = societeService.endImpersonation(SocieteContext.getImpersonatedBy());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieHelper.buildAuthCookie(newToken, 3600L).toString());
        return ResponseEntity.noContent().build();
    }
}
