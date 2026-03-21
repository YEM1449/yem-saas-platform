package com.yem.hlm.backend.dashboard.api;

import com.yem.hlm.backend.dashboard.api.dto.ReceivablesDashboardDTO;
import com.yem.hlm.backend.dashboard.service.ReceivablesDashboardService;
import com.yem.hlm.backend.societe.SocieteContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Receivables dashboard endpoint.
 *
 * <pre>
 * GET /api/dashboard/receivables
 *   ?agentId= (UUID, optional — ignored for ROLE_AGENT, forced to self)
 * </pre>
 *
 * RBAC:
 * <ul>
 *   <li>ADMIN / MANAGER — full tenant data, optional agentId filter.</li>
 *   <li>AGENT — server-enforced scope to own contracts.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/dashboard/receivables")
public class ReceivablesDashboardController {

    private final ReceivablesDashboardService service;

    public ReceivablesDashboardController(ReceivablesDashboardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
    public ReceivablesDashboardDTO summary(@RequestParam(required = false) UUID agentId) {
        UUID societeId        = SocieteContext.getSocieteId();
        UUID effectiveAgentId = service.resolveEffectiveAgentId(agentId);
        return service.getSummary(societeId, effectiveAgentId);
    }
}
