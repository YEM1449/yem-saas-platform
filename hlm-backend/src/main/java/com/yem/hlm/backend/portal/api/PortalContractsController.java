package com.yem.hlm.backend.portal.api;

import com.yem.hlm.backend.portal.api.dto.PortalContractResponse;
import com.yem.hlm.backend.portal.api.dto.PortalTenantInfoResponse;
import com.yem.hlm.backend.portal.service.PortalContractService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Portal contract endpoints — ROLE_PORTAL only.
 */
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

    /** GET /api/portal/contracts/{id}/documents/contract.pdf */
    @GetMapping("/contracts/{id}/documents/contract.pdf")
    public ResponseEntity<byte[]> getContractPdf(@PathVariable UUID id) {
        byte[] pdf = portalContractService.getContractPdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"contract_" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /** GET /api/portal/tenant-info — tenant name + logo for portal shell. */
    @GetMapping("/tenant-info")
    public ResponseEntity<PortalTenantInfoResponse> getTenantInfo() {
        return ResponseEntity.ok(portalContractService.getTenantInfo());
    }
}
