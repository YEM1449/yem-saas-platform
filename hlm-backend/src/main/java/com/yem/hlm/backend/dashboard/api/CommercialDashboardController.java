package com.yem.hlm.backend.dashboard.api;

import com.yem.hlm.backend.dashboard.api.dto.CommercialDashboardSalesDTO;
import com.yem.hlm.backend.dashboard.api.dto.CommercialDashboardSummaryDTO;
import com.yem.hlm.backend.dashboard.service.CommercialDashboardService;
import com.yem.hlm.backend.tenant.context.TenantContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Commercial dashboard endpoints — one call per screen.
 *
 * <pre>
 * GET /api/dashboard/commercial/summary
 *   ?from=  (ISO datetime, optional — default last 30 days)
 *   &to=    (ISO datetime, optional — default now)
 *   &projectId= (UUID, optional)
 *   &agentId=   (UUID, optional — ignored for ROLE_AGENT, forced to self)
 *
 * GET /api/dashboard/commercial/sales
 *   same filters + ?page=0&size=20
 * </pre>
 *
 * RBAC:
 * <ul>
 *   <li>ADMIN / MANAGER — full tenant data, optional filters.</li>
 *   <li>AGENT — server-enforced scope to own agentId; no role guard needed on controller.</li>
 * </ul>
 *
 * Caching: summary responses are cached 30 s in {@code commercialDashboardSummaryCache}.
 */
@RestController
@RequestMapping("/api/dashboard/commercial")
public class CommercialDashboardController {

    private final CommercialDashboardService dashboardService;

    public CommercialDashboardController(CommercialDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Full commercial summary (KPI cards + breakdowns + trends + inventory).
     * One backend call, response cached 30 s.
     */
    @GetMapping("/summary")
    public CommercialDashboardSummaryDTO summary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,

            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID agentId
    ) {
        UUID tenantId = TenantContext.getTenantId();
        LocalDateTime[] range = dashboardService.resolveDateRange(from, to);
        dashboardService.validateProject(tenantId, projectId);
        UUID effectiveAgentId = dashboardService.resolveEffectiveAgentId(tenantId, agentId);

        return dashboardService.getSummary(tenantId, range[0], range[1], projectId, effectiveAgentId);
    }

    /**
     * Paginated sales drill-down table (signed contracts).
     * Not cached (relies on DB; paging changes per request).
     */
    @GetMapping("/sales")
    public CommercialDashboardSalesDTO sales(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,

            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID agentId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID tenantId = TenantContext.getTenantId();
        LocalDateTime[] range = dashboardService.resolveDateRange(from, to);
        dashboardService.validateProject(tenantId, projectId);
        UUID effectiveAgentId = dashboardService.resolveEffectiveAgentId(tenantId, agentId);

        return dashboardService.getSales(tenantId, range[0], range[1], projectId, effectiveAgentId, page, size);
    }
}
