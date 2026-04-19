package com.yem.hlm.backend.portal.api;

import com.yem.hlm.backend.portal.api.dto.PortalContractResponse;
import com.yem.hlm.backend.portal.api.dto.PortalTenantInfoResponse;
import com.yem.hlm.backend.portal.service.PortalContractService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Portal contract endpoints — ROLE_PORTAL only.
 */
@Tag(name = "Portal \u2013 Contracts", description = "Buyer portal contract endpoints (ROLE_PORTAL)")
@RestController
@RequestMapping("/api/portal")
@PreAuthorize("hasRole('PORTAL')")
public class PortalContractsController {

    private final PortalContractService portalContractService;

    public PortalContractsController(PortalContractService portalContractService) {
        this.portalContractService = portalContractService;
    }

    /** GET /api/portal/contracts — list buyer's own contracts. */
    @GetMapping("/contracts")
    public ResponseEntity<List<PortalContractResponse>> listContracts() {
        return ResponseEntity.ok(portalContractService.listContracts());
    }

    /** GET /api/portal/tenant-info — tenant name + logo for portal shell. */
    @GetMapping("/tenant-info")
    public ResponseEntity<PortalTenantInfoResponse> getTenantInfo() {
        return ResponseEntity.ok(portalContractService.getTenantInfo());
    }
}
