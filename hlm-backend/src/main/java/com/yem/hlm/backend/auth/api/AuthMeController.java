package com.yem.hlm.backend.auth.api;

import com.yem.hlm.backend.tenant.context.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return Map.of(
                "userId", TenantContext.getUserId(),
                "tenantId", TenantContext.getTenantId()
        );
    }
}
