package com.yem.hlm.backend.dashboard.api;

import com.yem.hlm.backend.dashboard.api.dto.AgentPerformanceDTO;
import com.yem.hlm.backend.dashboard.api.dto.AlertDTO;
import com.yem.hlm.backend.dashboard.api.dto.DiscountAnalyticsDTO;
import com.yem.hlm.backend.dashboard.api.dto.ForecastDTO;
import com.yem.hlm.backend.dashboard.api.dto.FunnelDTO;
import com.yem.hlm.backend.dashboard.api.dto.InventoryIntelligenceDTO;
import com.yem.hlm.backend.dashboard.api.dto.KpiComparisonDTO;
import com.yem.hlm.backend.dashboard.api.dto.PipelineAnalysisDTO;
import com.yem.hlm.backend.dashboard.service.DashboardCockpitService;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Executive cockpit endpoints — KPI comparison, sales funnel and rule-based alerts.
 *
 * <p>All three are société-scoped, ADMIN/MANAGER only, and cached for 60 s. They do
 * not replace the existing {@code /api/dashboard/home} snapshot — they enrich it with
 * decision-grade signals (deltas, drop-off ratios, alerts).
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard Cockpit", description = "Executive KPI comparison, funnel and alerts")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class DashboardCockpitController {

    private final DashboardCockpitService svc;
    private final SocieteContextHelper    ctx;

    public DashboardCockpitController(DashboardCockpitService svc, SocieteContextHelper ctx) {
        this.svc = svc;
        this.ctx = ctx;
    }

    @GetMapping("/kpi-comparison")
    public ResponseEntity<KpiComparisonDTO> kpiComparison() {
        UUID societeId = ctx.requireSocieteId();
        return ResponseEntity.ok(svc.getKpiComparison(societeId));
    }

    @GetMapping("/funnel")
    public ResponseEntity<FunnelDTO> funnel() {
        UUID societeId = ctx.requireSocieteId();
        return ResponseEntity.ok(svc.getFunnel(societeId));
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<AlertDTO>> alerts() {
        UUID societeId = ctx.requireSocieteId();
        return ResponseEntity.ok(svc.getAlerts(societeId));
    }

    @GetMapping("/pipeline-analysis")
    public ResponseEntity<PipelineAnalysisDTO> pipelineAnalysis() {
        UUID societeId = ctx.requireSocieteId();
        return ResponseEntity.ok(svc.getPipelineAnalysis(societeId));
    }

    @GetMapping("/forecast")
    public ResponseEntity<ForecastDTO> forecast() {
        UUID societeId = ctx.requireSocieteId();
        return ResponseEntity.ok(svc.getForecast(societeId));
    }

    @GetMapping("/agents-performance")
    public ResponseEntity<AgentPerformanceDTO> agentsPerformance() {
        UUID societeId = ctx.requireSocieteId();
        return ResponseEntity.ok(svc.getAgentPerformance(societeId));
    }

    @GetMapping("/inventory-intelligence")
    public ResponseEntity<InventoryIntelligenceDTO> inventoryIntelligence() {
        UUID societeId = ctx.requireSocieteId();
        return ResponseEntity.ok(svc.getInventoryIntelligence(societeId));
    }

    @GetMapping("/discount-analytics")
    public ResponseEntity<DiscountAnalyticsDTO> discountAnalytics() {
        UUID societeId = ctx.requireSocieteId();
        return ResponseEntity.ok(svc.getDiscountAnalytics(societeId));
    }
}
