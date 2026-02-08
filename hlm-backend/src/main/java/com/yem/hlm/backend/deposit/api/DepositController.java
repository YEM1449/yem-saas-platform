package com.yem.hlm.backend.deposit.api;

import com.yem.hlm.backend.deposit.api.dto.*;
import com.yem.hlm.backend.deposit.domain.DepositStatus;
import com.yem.hlm.backend.deposit.service.DepositService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/deposits")
public class DepositController {

    private final DepositService depositService;

    public DepositController(DepositService depositService) {
        this.depositService = depositService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DepositResponse create(@Valid @RequestBody CreateDepositRequest request) {
        return depositService.create(request);
    }

    @GetMapping("/{id}")
    public DepositResponse get(@PathVariable UUID id) {
        return depositService.get(id);
    }

    @PostMapping("/{id}/confirm")
    public DepositResponse confirm(@PathVariable UUID id) {
        return depositService.confirm(id);
    }

    @PostMapping("/{id}/cancel")
    public DepositResponse cancel(@PathVariable UUID id) {
        return depositService.cancel(id);
    }

    @GetMapping("/report")
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
