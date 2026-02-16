package com.yem.hlm.backend.auth.api;

import com.yem.hlm.backend.tenant.context.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Endpoint simple pour valider que :
 * - JWT est lu
 * - TenantContext est rempli par le filtre
 */
@RestController
public class AuthMeController {

    @GetMapping("/auth/me")
    public Map<String, Object> me() {
        Map<String, Object> result = new HashMap<>();
        result.put("userId", TenantContext.getUserId());
        result.put("tenantId", TenantContext.getTenantId());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .findFirst()
                    .ifPresent(role -> result.put("role", role));
        }
        return result;
    }
}
