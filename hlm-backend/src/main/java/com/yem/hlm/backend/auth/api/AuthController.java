package com.yem.hlm.backend.auth.api;

import com.yem.hlm.backend.auth.api.dto.LoginRequest;
import com.yem.hlm.backend.auth.api.dto.LoginResponse;
import com.yem.hlm.backend.auth.api.dto.SwitchSocieteRequest;
import com.yem.hlm.backend.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Authenticate with email + password; returns JWT access token")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    /**
     * Switch the active société for a multi-société user.
     *
     * <p>Accepts a partial token (issued during multi-société login selection) or
     * a full token (re-selecting a société). Token validation is performed by
     * AuthService; this endpoint is permitAll in SecurityConfig.
     *
     * <p>POST /auth/switch-societe
     */
    @Operation(summary = "Switch active société for a multi-société user; re-issues JWT scoped to the selected société")
    @PostMapping("/switch-societe")
    public LoginResponse switchSociete(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody SwitchSocieteRequest req) {
        return authService.switchSociete(authorizationHeader, req);
    }
}
