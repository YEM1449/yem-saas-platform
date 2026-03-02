package com.yem.hlm.backend.portal.api;

import com.yem.hlm.backend.payment.api.dto.PaymentScheduleResponse;
import com.yem.hlm.backend.portal.service.PortalContractService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Portal payment-schedule endpoints — ROLE_PORTAL only.
 */
@RestController
@RequestMapping("/api/portal/contracts")
@PreAuthorize("hasRole('PORTAL')")
public class PortalPaymentsController {

    private final PortalContractService portalContractService;

    public PortalPaymentsController(PortalContractService portalContractService) {
        this.portalContractService = portalContractService;
    }

    /**
     * GET /api/portal/contracts/{contractId}/payment-schedule
     * Returns the full payment schedule (tranches) for the buyer's contract.
     */
    @GetMapping("/{contractId}/payment-schedule")
    public ResponseEntity<PaymentScheduleResponse> getPaymentSchedule(
            @PathVariable UUID contractId) {
        return ResponseEntity.ok(portalContractService.getPaymentSchedule(contractId));
    }
}
