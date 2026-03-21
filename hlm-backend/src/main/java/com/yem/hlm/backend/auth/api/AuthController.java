package com.yem.hlm.backend.auth.api;

import com.yem.hlm.backend.auth.api.dto.LoginRequest;
import com.yem.hlm.backend.auth.api.dto.LoginResponse;
import com.yem.hlm.backend.auth.api.dto.SwitchSocieteRequest;
import com.yem.hlm.backend.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    /**
     * Switch the active société for a multi-société user.
     *
     * <p>Requires a valid JWT (any CRM role). Returns a new JWT scoped to the
     * requested société if the authenticated user has an active membership there.
     *
     * <p>POST /auth/switch-societe
     */
    @PostMapping("/switch-societe")
    public LoginResponse switchSociete(
            @Valid @RequestBody SwitchSocieteRequest req,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return authService.switchSociete(userId, req);
    }
}
