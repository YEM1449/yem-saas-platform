package com.yem.hlm.backend.dashboard.api;

import com.yem.hlm.backend.dashboard.service.KpiComputationService;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * GET /api/kpis/tranche/{id} — latest computed KPI snapshot for a tranche.
 */
@Tag(name = "KPIs", description = "Tranche-level commercial KPI snapshots")
@RestController
@RequestMapping("/api/kpis")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
public class TrancheKpiController {

    private final KpiComputationService kpiService;
    private final SocieteContextHelper societeCtx;

    public TrancheKpiController(KpiComputationService kpiService,
                                SocieteContextHelper societeCtx) {
        this.kpiService  = kpiService;
        this.societeCtx  = societeCtx;
    }

    @GetMapping("/tranche/{trancheId}")
    public KpiSnapshotResponse getForTranche(@PathVariable UUID trancheId) {
        UUID societeId = societeCtx.requireSocieteId();
        return KpiSnapshotResponse.from(kpiService.getLatest(societeId, trancheId));
    }
}
