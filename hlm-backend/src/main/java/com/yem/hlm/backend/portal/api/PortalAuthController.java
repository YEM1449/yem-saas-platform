package com.yem.hlm.backend.portal.api;

import com.yem.hlm.backend.portal.api.dto.MagicLinkRequest;
import com.yem.hlm.backend.portal.api.dto.MagicLinkResponse;
import com.yem.hlm.backend.portal.api.dto.PortalTokenVerifyResponse;
import com.yem.hlm.backend.portal.service.PortalAuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public portal authentication endpoints.
 *
 * <p>Both endpoints are explicitly permitted in {@code SecurityConfig}
 * (no JWT required).
 */
@RestController
@RequestMapping("/api/portal/auth")
public class PortalAuthController {

    private final PortalAuthService portalAuthService;

    public PortalAuthController(PortalAuthService portalAuthService) {
        this.portalAuthService = portalAuthService;
    }

    /**
     * Step 1 — Request a magic link.
     *
     * <p>POST /api/portal/auth/request-link
     * <p>Public endpoint. Generates a one-time magic-link token, sends it by email,
     * and returns the URL in the response body for dev/test convenience.
     */
    @PostMapping("/request-link")
    public ResponseEntity<MagicLinkResponse> requestLink(
            @Valid @RequestBody MagicLinkRequest req) {
        MagicLinkResponse response = portalAuthService.requestLink(req.email(), req.tenantKey());
        return ResponseEntity.ok(response);
    }

    /**
     * Step 2 — Verify magic link token and obtain portal JWT.
     *
     * <p>GET /api/portal/auth/verify?token=xxx
     * <p>Public endpoint. Validates the raw token, marks it used, and returns a 2-h portal JWT.
     */
    @GetMapping("/verify")
    public ResponseEntity<PortalTokenVerifyResponse> verify(
            @RequestParam("token") String token) {
        PortalTokenVerifyResponse response = portalAuthService.verifyToken(token);
        return ResponseEntity.ok(response);
    }
}
