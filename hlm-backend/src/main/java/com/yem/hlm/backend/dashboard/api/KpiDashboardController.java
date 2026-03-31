package com.yem.hlm.backend.dashboard.api;

import com.yem.hlm.backend.dashboard.api.dto.ImmeubleKpiRow;
import com.yem.hlm.backend.dashboard.api.dto.ProjectKpiRow;
import com.yem.hlm.backend.dashboard.service.KpiDashboardService;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Inventory KPI endpoints grouped by project and by building (immeuble).
 *
 * <pre>
 * GET /api/dashboard/kpi/by-project   — one row per project
 * GET /api/dashboard/kpi/by-immeuble  — one row per building
 * </pre>
 *
 * <p>RBAC: ADMIN and MANAGER see all data; AGENT scoping is not applied here
 * because inventory KPIs are property-level metrics, not agent-level.
 */
@Tag(name = "Dashboard \u2013 KPI", description = "Inventory KPIs by project and building")
@RestController
@RequestMapping("/api/dashboard/kpi")
public class KpiDashboardController {

    private final KpiDashboardService kpiService;
    private final SocieteContextHelper societeContextHelper;

    public KpiDashboardController(KpiDashboardService kpiService,
                                  SocieteContextHelper societeContextHelper) {
        this.kpiService           = kpiService;
        this.societeContextHelper = societeContextHelper;
    }

    @Operation(summary = "Inventory KPIs per project",
               description = "Returns total, available, reserved, and sold property counts "
                           + "plus sale-rate and reservation-rate for each project.")
    @GetMapping("/by-project")
    public List<ProjectKpiRow> byProject() {
        UUID societeId = societeContextHelper.requireSocieteId();
        return kpiService.kpiByProject(societeId);
    }

    @Operation(summary = "Inventory KPIs per building",
               description = "Returns total, available, reserved, and sold property counts "
                           + "plus sale-rate and reservation-rate for each immeuble. "
                           + "Properties without an assigned building are excluded.")
    @GetMapping("/by-immeuble")
    public List<ImmeubleKpiRow> byImmeuble() {
        UUID societeId = societeContextHelper.requireSocieteId();
        return kpiService.kpiByImmeuble(societeId);
    }
}
