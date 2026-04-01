package com.yem.hlm.backend.portal.api;

import com.yem.hlm.backend.auth.security.PortalCookieHelper;
import com.yem.hlm.backend.portal.api.dto.MagicLinkRequest;
import com.yem.hlm.backend.portal.api.dto.MagicLinkResponse;
import com.yem.hlm.backend.portal.api.dto.PortalTokenVerifyResponse;
import com.yem.hlm.backend.portal.service.PortalAuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Public portal authentication endpoints.
 *
 * <p>Both endpoints are explicitly permitted in {@code SecurityConfig}
 * (no JWT required).
 */
@Tag(name = "Portal Auth", description = "Magic-link portal authentication (public)")
@RestController
@RequestMapping("/api/portal/auth")
@Validated
public class PortalAuthController {

    private final PortalAuthService portalAuthService;
    private final PortalCookieHelper portalCookieHelper;

    public PortalAuthController(PortalAuthService portalAuthService,
                                PortalCookieHelper portalCookieHelper) {
        this.portalAuthService = portalAuthService;
        this.portalCookieHelper = portalCookieHelper;
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
            @Valid @RequestBody MagicLinkRequest req,
            HttpServletResponse servletResponse) {
        applyNoStoreHeaders(servletResponse);
        MagicLinkResponse magicLinkResponse = portalAuthService.requestLink(req.email(), req.societeKey());
        return ResponseEntity.ok(magicLinkResponse);
    }

    /**
     * Step 2 — Verify magic link token and obtain portal JWT.
     *
     * <p>GET /api/portal/auth/verify?token=xxx
     * <p>Public endpoint. Validates the raw token, marks it used, and returns a 2-h portal JWT.
     */
    @GetMapping("/verify")
    public ResponseEntity<PortalTokenVerifyResponse> verify(
            @RequestParam("token") @NotBlank @Size(max = 128) String token,
            HttpServletResponse response) {
        PortalTokenVerifyResponse verifyResponse = portalAuthService.verifyToken(token);
        applyNoStoreHeaders(response);
        response.addHeader(HttpHeaders.SET_COOKIE,
                portalCookieHelper.buildAuthCookie(
                        verifyResponse.accessToken(),
                        portalAuthService.portalSessionTtlSeconds()).toString());
        return ResponseEntity.ok(withoutToken(verifyResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        applyNoStoreHeaders(response);
        response.addHeader(HttpHeaders.SET_COOKIE, portalCookieHelper.buildClearCookie().toString());
        return ResponseEntity.noContent().build();
    }

    private PortalTokenVerifyResponse withoutToken(PortalTokenVerifyResponse res) {
        return new PortalTokenVerifyResponse("");
    }

    private void applyNoStoreHeaders(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }
}
