package com.yem.hlm.backend.payment.api;

import com.yem.hlm.backend.payment.api.dto.PaymentCallResponse;
import com.yem.hlm.backend.payment.api.dto.PaymentResponse;
import com.yem.hlm.backend.payment.api.dto.RecordPaymentRequest;
import com.yem.hlm.backend.payment.service.PaymentCallDocumentService;
import com.yem.hlm.backend.payment.service.PaymentCallService;
import com.yem.hlm.backend.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Payment Call (Appel de Fonds) REST API.
 *
 * <pre>
 * GET  /api/payment-calls                                          → all roles (paged)
 * GET  /api/payment-calls/{id}                                     → all roles
 * GET  /api/payment-calls/{id}/documents/appel-de-fonds.pdf        → all roles
 * POST /api/contracts/{cId}/payment-schedule/tranches/{tId}/issue-call → ADMIN/MANAGER
 * GET  /api/payment-calls/{id}/payments                            → all roles
 * POST /api/payment-calls/{id}/payments                            → ADMIN/MANAGER
 * </pre>
 */
@RestController
public class PaymentCallController {

    private final PaymentCallService        callService;
    private final PaymentService            paymentService;
    private final PaymentCallDocumentService documentService;

    public PaymentCallController(PaymentCallService callService,
                                 PaymentService paymentService,
                                 PaymentCallDocumentService documentService) {
        this.callService     = callService;
        this.paymentService  = paymentService;
        this.documentService = documentService;
    }

    // ---- List all calls ----

    @GetMapping("/api/payment-calls")
    @PreAuthorize("isAuthenticated()")
    public Page<PaymentCallResponse> listCalls(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return callService.listCalls(pageable);
    }

    // ---- Get single call ----

    @GetMapping("/api/payment-calls/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaymentCallResponse> getCall(@PathVariable UUID id) {
        return ResponseEntity.ok(callService.getCall(id));
    }

    // ---- PDF ----

    @GetMapping("/api/payment-calls/{id}/documents/appel-de-fonds.pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        byte[] pdf = documentService.generate(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("appel-de-fonds_" + id + ".pdf")
                        .build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    // ---- Issue call (tranche PLANNED → ISSUED, call ISSUED) ----

    @PostMapping("/api/contracts/{contractId}/payment-schedule/tranches/{trancheId}/issue-call")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<PaymentCallResponse> issueCall(
            @PathVariable UUID contractId,
            @PathVariable UUID trancheId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(callService.issueCall(trancheId));
    }

    // ---- Payments on a call ----

    @GetMapping("/api/payment-calls/{id}/payments")
    @PreAuthorize("isAuthenticated()")
    public List<PaymentResponse> listPayments(@PathVariable UUID id) {
        return paymentService.listPayments(id);
    }

    @PostMapping("/api/payment-calls/{id}/payments")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public ResponseEntity<PaymentResponse> recordPayment(
            @PathVariable UUID id,
            @Valid @RequestBody RecordPaymentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.recordPayment(id, req));
    }
}
