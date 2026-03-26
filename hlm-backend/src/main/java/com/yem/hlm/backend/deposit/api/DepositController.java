package com.yem.hlm.backend.deposit.api;

import com.yem.hlm.backend.deposit.api.dto.*;
import com.yem.hlm.backend.deposit.domain.DepositStatus;
import com.yem.hlm.backend.deposit.service.DepositService;
import com.yem.hlm.backend.deposit.service.pdf.ReservationDocumentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Tag(name = "Deposits", description = "Deposit (réservation notariale) lifecycle management")
@RestController
@RequestMapping("/api/deposits")
public class DepositController {

    private final DepositService depositService;
    private final ReservationDocumentService reservationDocumentService;

    public DepositController(DepositService depositService,
                             ReservationDocumentService reservationDocumentService) {
        this.depositService            = depositService;
        this.reservationDocumentService = reservationDocumentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public DepositResponse create(@Valid @RequestBody CreateDepositRequest request) {
        return depositService.create(request);
    }

    @GetMapping("/{id}")
    public DepositResponse get(@PathVariable UUID id) {
        return depositService.get(id);
    }

    /**
     * Downloads the PDF reservation certificate for a deposit.
     *
     * <p>RBAC:
     * <ul>
     *   <li>ADMIN / MANAGER — any deposit in the tenant.</li>
     *   <li>AGENT — own deposits only (enforced in {@link ReservationDocumentService}).</li>
     * </ul>
     */
    @GetMapping("/{id}/documents/reservation.pdf")
    public ResponseEntity<byte[]> downloadReservationPdf(@PathVariable UUID id) {
        byte[] pdf = reservationDocumentService.generate(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reservation_" + id + ".pdf\"")
                .body(pdf);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public DepositResponse confirm(@PathVariable UUID id) {
        return depositService.confirm(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public DepositResponse cancel(@PathVariable UUID id) {
        return depositService.cancel(id);
    }

    @GetMapping("/report")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public DepositReportResponse report(
            @RequestParam(value = "status", required = false) DepositStatus status,
            @RequestParam(value = "agentId", required = false) UUID agentId,
            @RequestParam(value = "contactId", required = false) UUID contactId,
            @RequestParam(value = "propertyId", required = false) UUID propertyId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return depositService.report(status, agentId, contactId, propertyId, from, to);
    }
}
