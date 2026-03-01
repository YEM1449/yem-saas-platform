package com.yem.hlm.backend.payment.api;

import com.yem.hlm.backend.payment.api.dto.*;
import com.yem.hlm.backend.payment.service.PaymentScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Payment Schedule REST API.
 *
 * <pre>
 * GET  /api/contracts/{contractId}/payment-schedule          → all roles
 * POST /api/contracts/{contractId}/payment-schedule          → ADMIN / MANAGER
 * PATCH /api/contracts/{contractId}/payment-schedule/tranches/{trancheId} → ADMIN / MANAGER
 * </pre>
 */
@RestController
@RequestMapping("/api/contracts/{contractId}/payment-schedule")
public class PaymentScheduleController {

    private final PaymentScheduleService service;

    public PaymentScheduleController(PaymentScheduleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaymentScheduleResponse> get(@PathVariable UUID contractId) {
        return ResponseEntity.ok(service.getByContractId(contractId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<PaymentScheduleResponse> create(
            @PathVariable UUID contractId,
            @Valid @RequestBody CreatePaymentScheduleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createSchedule(contractId, req));
    }

    @PatchMapping("/tranches/{trancheId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<TrancheResponse> updateTranche(
            @PathVariable UUID contractId,
            @PathVariable UUID trancheId,
            @Valid @RequestBody UpdateTrancheRequest req) {
        return ResponseEntity.ok(service.updateTranche(trancheId, req));
    }
}
