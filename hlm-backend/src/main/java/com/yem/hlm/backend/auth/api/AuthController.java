package com.yem.hlm.backend.auth.api;

import com.yem.hlm.backend.auth.api.dto.LoginRequest;
import com.yem.hlm.backend.auth.api.dto.LoginResponse;
import com.yem.hlm.backend.auth.api.dto.SwitchSocieteRequest;
import com.yem.hlm.backend.auth.security.CookieTokenHelper;
import com.yem.hlm.backend.auth.service.AuthService;
import com.yem.hlm.backend.usermanagement.InvitationService;
import com.yem.hlm.backend.usermanagement.dto.ActivationRequest;
import com.yem.hlm.backend.usermanagement.dto.InvitationDetailsDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Authentication and société switching")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final InvitationService invitationService;
    private final CookieTokenHelper cookieHelper;

    public AuthController(AuthService authService,
                          InvitationService invitationService,
                          CookieTokenHelper cookieHelper) {
        this.authService      = authService;
        this.invitationService = invitationService;
        this.cookieHelper     = cookieHelper;
    }

    /**
     * Authenticate with email + password.
     *
     * <p>For single-société users: sets an httpOnly {@code hlm_auth} cookie containing
     * the JWT, and returns the response body with an empty {@code accessToken} (the token
     * is in the cookie, not in the body — invisible to JavaScript).
     *
     * <p>For multi-société users: no cookie is set; the partial token is returned in the
     * response body so the client can call {@code POST /auth/switch-societe}.
     */
    @Operation(summary = "Authenticate with email + password")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req,
                               HttpServletResponse response) {
        LoginResponse loginRes = authService.login(req);
        applyNoStoreHeaders(response);
        if (!loginRes.requiresSocieteSelection()) {
            // Full JWT — set as httpOnly cookie; suppress from response body
            setAuthCookie(response, loginRes.accessToken(), loginRes.expiresIn());
            return withoutToken(loginRes);
        }
        // Partial token for société selection — still returned in body (short-lived, limited-scope)
        return loginRes;
    }

    /**
     * Switch the active société for a multi-société user.
     *
     * <p>Accepts a partial token (issued during multi-société login selection) in the
     * {@code Authorization: Bearer} header. On success, sets the full scoped JWT in an
     * httpOnly cookie and returns the response body without the token.
     */
    @Operation(summary = "Switch active société; re-issues JWT scoped to the selected société")
    @PostMapping("/switch-societe")
    public LoginResponse switchSociete(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody SwitchSocieteRequest req,
            HttpServletResponse response) {
        LoginResponse loginRes = authService.switchSociete(authorizationHeader, req);
        applyNoStoreHeaders(response);
        setAuthCookie(response, loginRes.accessToken(), loginRes.expiresIn());
        return withoutToken(loginRes);
    }

    /**
     * Logout — clears the httpOnly auth cookie by issuing a Max-Age=0 Set-Cookie.
     * Accessible to authenticated users and (safely) to unauthenticated requests
     * (clearing a non-existent cookie is harmless).
     */
    @Operation(summary = "Logout — clears the auth cookie")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        applyNoStoreHeaders(response);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieHelper.buildClearCookie().toString());
        return ResponseEntity.noContent().build();
    }

    // ── Invitation endpoints (public — no JWT required) ─────────────────────────────

    @Operation(summary = "Vérifier un lien d'invitation et retourner les détails")
    @GetMapping("/invitation/{token}")
    public InvitationDetailsDto validateInvitation(@PathVariable String token) {
        return invitationService.validateToken(token);
    }

    /**
     * Account activation via invitation link.
     * On success, sets the full JWT in an httpOnly cookie.
     */
    @Operation(summary = "Activer son compte via un lien d'invitation")
    @PostMapping("/invitation/{token}/activer")
    public LoginResponse activerCompte(@PathVariable String token,
                                       @Valid @RequestBody ActivationRequest req,
                                       HttpServletResponse response) {
        LoginResponse loginRes = invitationService.activerCompte(token, req);
        applyNoStoreHeaders(response);
        setAuthCookie(response, loginRes.accessToken(), loginRes.expiresIn());
        return withoutToken(loginRes);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    /** Sets the httpOnly auth cookie on the response. */
    private void setAuthCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieHelper.buildAuthCookie(token, maxAgeSeconds).toString());
    }

    private void applyNoStoreHeaders(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
    }

    /**
     * Returns a copy of {@code res} with the {@code accessToken} cleared.
     * The JWT lives in the httpOnly cookie — there is no reason to echo it in the body.
     */
    private LoginResponse withoutToken(LoginResponse res) {
        return new LoginResponse("", res.tokenType(), res.expiresIn(),
                res.requiresSocieteSelection(), res.societes());
    }
}
