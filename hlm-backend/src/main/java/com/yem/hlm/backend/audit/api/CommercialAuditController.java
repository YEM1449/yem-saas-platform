package com.yem.hlm.backend.audit.api;

import com.yem.hlm.backend.audit.api.dto.AuditEventResponse;
import com.yem.hlm.backend.audit.service.CommercialAuditService;
import com.yem.hlm.backend.societe.SocieteContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Read-only audit log endpoint.
 *
 * <pre>
 * GET /api/audit/commercial
 *   ?from=           (ISO datetime, optional)
 *   &to=             (ISO datetime, optional)
 *   &correlationType= e.g. DEPOSIT or CONTRACT (optional)
 *   &correlationId=  UUID (optional)
 *   &limit=          1–500, default 100
 * </pre>
 *
 * RBAC: ADMIN and MANAGER only.
 */
@Tag(name = "Audit", description = "Read-only commercial audit log")
@RestController
@RequestMapping("/api/audit/commercial")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class CommercialAuditController {

    private final CommercialAuditService auditService;

    public CommercialAuditController(CommercialAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditEventResponse> list(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,

            @RequestParam(required = false) String correlationType,
            @RequestParam(required = false) UUID correlationId,
            @RequestParam(defaultValue = "100") int limit
    ) {
        UUID societeId = SocieteContext.getSocieteId();
        return auditService.search(societeId, from, to, correlationType, correlationId, limit);
    }
}
