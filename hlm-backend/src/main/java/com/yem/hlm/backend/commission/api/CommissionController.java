package com.yem.hlm.backend.commission.api;

import com.yem.hlm.backend.commission.api.dto.CommissionDTO;
import com.yem.hlm.backend.commission.api.dto.CommissionRuleRequest;
import com.yem.hlm.backend.commission.api.dto.CommissionRuleResponse;
import com.yem.hlm.backend.commission.service.CommissionService;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Commission endpoints.
 *
 * <pre>
 * GET  /api/commissions/my                    ← AGENT: own commissions
 * GET  /api/commissions?agentId=&from=&to=    ← ADMIN/MANAGER: all or by agent
 * GET  /api/commission-rules                  ← ADMIN: list rules
 * POST /api/commission-rules                  ← ADMIN: create rule
 * PUT  /api/commission-rules/{id}             ← ADMIN: update rule
 * DELETE /api/commission-rules/{id}           ← ADMIN: delete rule
 * </pre>
 */
@RestController
public class CommissionController {

    private final CommissionService service;
    private final SocieteContextHelper societeContextHelper;

    public CommissionController(CommissionService service, SocieteContextHelper societeContextHelper) {
        this.service = service;
        this.societeContextHelper = societeContextHelper;
    }

    // =========================================================================
    // Commission queries
    // =========================================================================

    /** AGENT: returns own earned commissions. */
    @GetMapping("/api/commissions/my")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
    public List<CommissionDTO> myCommissions(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        UUID societeId = societeContextHelper.requireSocieteId();
        UUID agentId  = societeContextHelper.requireUserId();
        return service.getAgentCommissions(societeId, agentId, from, to);
    }

    /** ADMIN/MANAGER: all commissions, optional agentId filter. */
    @GetMapping("/api/commissions")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<CommissionDTO> commissions(
            @RequestParam(required = false) UUID agentId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        UUID societeId = societeContextHelper.requireSocieteId();
        return service.getAgentCommissions(societeId, agentId, from, to);
    }

    // =========================================================================
    // Rule management (ADMIN only)
    // =========================================================================

    @GetMapping("/api/commission-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CommissionRuleResponse> listRules() {
        return service.listRules(societeContextHelper.requireSocieteId());
    }

    @PostMapping("/api/commission-rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CommissionRuleResponse createRule(@Valid @RequestBody CommissionRuleRequest req) {
        return service.createRule(societeContextHelper.requireSocieteId(), req);
    }

    @PutMapping("/api/commission-rules/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CommissionRuleResponse updateRule(@PathVariable UUID id,
                                             @Valid @RequestBody CommissionRuleRequest req) {
        return service.updateRule(societeContextHelper.requireSocieteId(), id, req);
    }

    @DeleteMapping("/api/commission-rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteRule(@PathVariable UUID id) {
        service.deleteRule(societeContextHelper.requireSocieteId(), id);
    }
}
