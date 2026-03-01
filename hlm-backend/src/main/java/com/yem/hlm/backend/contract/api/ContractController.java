package com.yem.hlm.backend.contract.api;

import com.yem.hlm.backend.contract.api.dto.ContractResponse;
import com.yem.hlm.backend.contract.api.dto.CreateContractRequest;
import com.yem.hlm.backend.contract.domain.SaleContractStatus;
import com.yem.hlm.backend.contract.service.SaleContractService;
import com.yem.hlm.backend.contract.service.pdf.ContractDocumentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for the Sales Contract lifecycle.
 * <p>
 * RBAC rules:
 * <ul>
 *   <li>POST   /api/contracts                       — all authenticated roles</li>
 *   <li>POST   /api/contracts/{id}/sign             — ADMIN or MANAGER only</li>
 *   <li>POST   /api/contracts/{id}/cancel           — ADMIN or MANAGER only</li>
 *   <li>GET    /api/contracts                       — all roles (AGENT sees own only)</li>
 *   <li>GET    /api/contracts/{id}/documents/contract.pdf — all roles (AGENT sees own only)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final SaleContractService    contractService;
    private final ContractDocumentService contractDocumentService;

    public ContractController(SaleContractService contractService,
                               ContractDocumentService contractDocumentService) {
        this.contractService         = contractService;
        this.contractDocumentService = contractDocumentService;
    }

    /**
     * Create a DRAFT sales contract.
     * All authenticated roles may create; AGENT callers are automatically set as agent.
     */
    @PostMapping
    public ResponseEntity<ContractResponse> create(
            @Valid @RequestBody CreateContractRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contractService.create(request));
    }

    /**
     * Sign a DRAFT contract → SIGNED.
     * Side effect: property is moved to SOLD.
     * Requires ADMIN or MANAGER role.
     */
    @PostMapping("/{id}/sign")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ContractResponse sign(@PathVariable UUID id) {
        return contractService.sign(id);
    }

    /**
     * Cancel a DRAFT or SIGNED contract → CANCELED.
     * Side effect (if was SIGNED): property reverts to RESERVED or ACTIVE.
     * Requires ADMIN or MANAGER role.
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ContractResponse cancel(@PathVariable UUID id) {
        return contractService.cancel(id);
    }

    /**
     * List contracts for the current tenant.
     * Optional filters: status, projectId, agentId, from, to (signedAt range).
     * AGENT callers only receive their own contracts regardless of agentId parameter.
     */
    @GetMapping
    public List<ContractResponse> list(
            @RequestParam(required = false) SaleContractStatus status,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID agentId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return contractService.list(status, projectId, agentId, from, to);
    }

    /**
     * Get a single contract by ID (tenant-scoped).
     * AGENT callers may only access their own contracts.
     */
    @GetMapping("/{id}")
    public ContractResponse getById(@PathVariable UUID id) {
        return contractService.getById(id);
    }

    /**
     * Download a contract PDF.
     * RBAC: all authenticated roles; AGENT callers may only download their own contracts
     * (cross-ownership → 404 to avoid information leakage).
     */
    @GetMapping("/{id}/documents/contract.pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        byte[] pdf = contractDocumentService.generate(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"contract_" + id + ".pdf\"")
                .body(pdf);
    }
}
