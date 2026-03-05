package com.yem.hlm.backend.payment.api;

import com.yem.hlm.backend.payment.api.dto.*;
import com.yem.hlm.backend.payment.service.PaymentScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
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
 *
 * <p><strong>Deprecated:</strong> use the v2 payments API under
 * {@code /api/contracts/{contractId}/schedule} and {@code /api/schedule-items/*}.
 */
@Deprecated(since = "2026-03", forRemoval = false)
@RestController
@RequestMapping("/api/contracts/{contractId}/payment-schedule")
public class PaymentScheduleController {

    private static final String DEPRECATION_WARNING =
            "299 - \"Deprecated endpoint: migrate to /api/contracts/{contractId}/schedule and /api/schedule-items/*\"";

    private final PaymentScheduleService service;

    public PaymentScheduleController(PaymentScheduleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaymentScheduleResponse> get(@PathVariable UUID contractId) {
        return ResponseEntity.ok()
                .headers(deprecationHeaders())
                .body(service.getByContractId(contractId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<PaymentScheduleResponse> create(
            @PathVariable UUID contractId,
            @Valid @RequestBody CreatePaymentScheduleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .headers(deprecationHeaders())
                .body(service.createSchedule(contractId, req));
    }

    @PatchMapping("/tranches/{trancheId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<TrancheResponse> updateTranche(
            @PathVariable UUID contractId,
            @PathVariable UUID trancheId,
            @Valid @RequestBody UpdateTrancheRequest req) {
        return ResponseEntity.ok()
                .headers(deprecationHeaders())
                .body(service.updateTranche(trancheId, req));
    }

    private static HttpHeaders deprecationHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Deprecation", "true");
        headers.set("Sunset", "Wed, 31 Dec 2026 23:59:59 GMT");
        headers.set("Warning", DEPRECATION_WARNING);
        headers.set("Link", "</api/contracts/{contractId}/schedule>; rel=\"successor-version\"");
        return headers;
    }
}
