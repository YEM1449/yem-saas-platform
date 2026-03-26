package com.yem.hlm.backend.portal.api;

import com.yem.hlm.backend.payments.api.dto.PaymentScheduleItemResponse;
import com.yem.hlm.backend.portal.service.PortalContractService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Portal payment-schedule endpoints — ROLE_PORTAL only.
 */
@Tag(name = "Portal \u2013 Payments", description = "Buyer portal payment schedule endpoints (ROLE_PORTAL)")
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
     * Returns the payment schedule items for the buyer's contract (v2 model).
     */
    @GetMapping("/{contractId}/payment-schedule")
    public ResponseEntity<List<PaymentScheduleItemResponse>> getPaymentSchedule(
            @PathVariable UUID contractId) {
        return ResponseEntity.ok(portalContractService.getPaymentSchedule(contractId));
    }
}
