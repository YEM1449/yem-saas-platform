package com.yem.hlm.backend.dashboard.api;

import com.yem.hlm.backend.dashboard.api.dto.ReceivablesDashboardDTO;
import com.yem.hlm.backend.dashboard.api.dto.VenteReceivablesSummary;
import com.yem.hlm.backend.dashboard.service.ReceivablesDashboardService;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 *   <li>ADMIN / MANAGER — full société data, optional agentId filter.</li>
 *   <li>AGENT — server-enforced scope to own contracts.</li>
 * </ul>
 */
@Tag(name = "Dashboard \u2013 Receivables", description = "Receivables aging and collection analytics")
@RestController
@RequestMapping("/api/dashboard/receivables")
public class ReceivablesDashboardController {

    private final ReceivablesDashboardService service;
    private final SocieteContextHelper societeContextHelper;

    public ReceivablesDashboardController(ReceivablesDashboardService service,
                                          SocieteContextHelper societeContextHelper) {
        this.service = service;
        this.societeContextHelper = societeContextHelper;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
    public ReceivablesDashboardDTO summary(@RequestParam(required = false) UUID agentId) {
        UUID societeId        = societeContextHelper.requireSocieteId();
        UUID effectiveAgentId = service.resolveEffectiveAgentId(agentId);
        return service.getSummary(societeId, effectiveAgentId);
    }

    @GetMapping("/vente")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
    public VenteReceivablesSummary venteReceivables() {
        return service.getVenteReceivablesSummary(societeContextHelper.requireSocieteId());
    }
}
