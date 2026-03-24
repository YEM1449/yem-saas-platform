package com.yem.hlm.backend.portal.api;

import com.yem.hlm.backend.portal.api.dto.PortalPropertyResponse;
import com.yem.hlm.backend.portal.service.PortalContractService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Portal property endpoints — ROLE_PORTAL only.
 */
@Tag(name = "Portal \u2013 Properties", description = "Buyer portal property information endpoints (ROLE_PORTAL)")
@RestController
@RequestMapping("/api/portal/properties")
@PreAuthorize("hasRole('PORTAL')")
public class PortalPropertyController {

    private final PortalContractService portalContractService;

    public PortalPropertyController(PortalContractService portalContractService) {
        this.portalContractService = portalContractService;
    }

    /**
     * GET /api/portal/properties/{id}
     * Returns property details if the authenticated contact has a contract for it.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PortalPropertyResponse> getProperty(@PathVariable UUID id) {
        return ResponseEntity.ok(portalContractService.getProperty(id));
    }
}
