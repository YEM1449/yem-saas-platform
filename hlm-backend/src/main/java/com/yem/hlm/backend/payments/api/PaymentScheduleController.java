package com.yem.hlm.backend.payments.api;

import com.yem.hlm.backend.payments.api.dto.*;
import com.yem.hlm.backend.payments.service.CallForFundsPdfService;
import com.yem.hlm.backend.payments.service.CallForFundsWorkflowService;
import com.yem.hlm.backend.payments.service.PaymentScheduleService;
import com.yem.hlm.backend.payments.service.ReminderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for payment schedules (appels de fonds).
 *
 * <h3>URL structure</h3>
 * <pre>
 * GET    /api/contracts/{contractId}/schedule            — list items for a contract
 * POST   /api/contracts/{contractId}/schedule            — create item
 * PUT    /api/schedule-items/{itemId}                    — update item (DRAFT only)
 * DELETE /api/schedule-items/{itemId}                    — delete item (not PAID)
 * POST   /api/schedule-items/{itemId}/issue              — DRAFT → ISSUED
 * POST   /api/schedule-items/{itemId}/send               — ISSUED/SENT/OVERDUE → SENT + outbox
 * POST   /api/schedule-items/{itemId}/cancel             — any → CANCELED
 * GET    /api/schedule-items/{itemId}/pdf                — download call-for-funds PDF
 * GET    /api/schedule-items/{itemId}/payments           — list payments
 * POST   /api/schedule-items/{itemId}/payments           — add (partial) payment
 * POST   /api/schedule-items/{itemId}/remind             — trigger manual reminder run for item
 * </pre>
 *
 * <h3>RBAC</h3>
 * All write operations require ADMIN or MANAGER. AGENT may only read.
 */
@RestController
@RequestMapping("/api")
public class PaymentScheduleController {

    private final PaymentScheduleService      scheduleService;
    private final CallForFundsWorkflowService workflowService;
    private final CallForFundsPdfService      pdfService;
    private final ReminderService             reminderService;

    public PaymentScheduleController(PaymentScheduleService scheduleService,
                                     CallForFundsWorkflowService workflowService,
                                     CallForFundsPdfService pdfService,
                                     ReminderService reminderService) {
        this.scheduleService = scheduleService;
        this.workflowService = workflowService;
        this.pdfService      = pdfService;
        this.reminderService = reminderService;
    }

    // ── List ─────────────────────────────────────────────────────────────────

    @GetMapping("/contracts/{contractId}/schedule")
    public List<PaymentScheduleItemResponse> list(
            @PathVariable UUID contractId) {
        return scheduleService.listByContract(contractId);
    }

    // ── Create ───────────────────────────────────────────────────────────────

    @PostMapping("/contracts/{contractId}/schedule")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentScheduleItemResponse create(
            @PathVariable UUID contractId,
            @Valid @RequestBody CreateScheduleItemRequest req) {
        return scheduleService.create(contractId, req);
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @PutMapping("/schedule-items/{itemId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public PaymentScheduleItemResponse update(
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateScheduleItemRequest req) {
        return scheduleService.update(itemId, req);
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @DeleteMapping("/schedule-items/{itemId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID itemId) {
        scheduleService.delete(itemId);
    }

    // ── Issue ────────────────────────────────────────────────────────────────

    @PostMapping("/schedule-items/{itemId}/issue")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public PaymentScheduleItemResponse issue(@PathVariable UUID itemId) {
        return workflowService.issue(itemId);
    }

    // ── Send ─────────────────────────────────────────────────────────────────

    @PostMapping("/schedule-items/{itemId}/send")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public PaymentScheduleItemResponse send(
            @PathVariable UUID itemId,
            @Valid @RequestBody SendScheduleItemRequest req) {
        return workflowService.send(itemId, req);
    }

    // ── Cancel ───────────────────────────────────────────────────────────────

    @PostMapping("/schedule-items/{itemId}/cancel")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    public PaymentScheduleItemResponse cancel(@PathVariable UUID itemId) {
        return workflowService.cancel(itemId);
    }

    // ── PDF ──────────────────────────────────────────────────────────────────

    @GetMapping("/schedule-items/{itemId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID itemId) {
        byte[] pdf = pdfService.generate(itemId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment",
                "appel-de-fonds-" + itemId + ".pdf");
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    // ── Payments ─────────────────────────────────────────────────────────────

    @GetMapping("/schedule-items/{itemId}/payments")
    public List<PaymentResponse> listPayments(@PathVariable UUID itemId) {
        return workflowService.listPayments(itemId);
    }

    @PostMapping("/schedule-items/{itemId}/payments")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse addPayment(
            @PathVariable UUID itemId,
            @Valid @RequestBody AddPaymentRequest req) {
        return workflowService.addPayment(itemId, req);
    }

    // ── Manual reminder trigger ───────────────────────────────────────────────

    /**
     * Triggers the daily reminder run immediately (for manual/admin use).
     * This processes ALL tenants, identical to the scheduled run.
     */
    @PostMapping("/schedule-items/reminders/run")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void triggerReminders() {
        reminderService.processAll();
    }
}
