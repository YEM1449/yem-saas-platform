package com.yem.hlm.backend.dashboard.api;

import com.yem.hlm.backend.dashboard.api.dto.HomeDashboardDTO;
import com.yem.hlm.backend.dashboard.api.dto.ProjectDirectorKpiDTO;
import com.yem.hlm.backend.dashboard.api.dto.ShareholderKpiDTO;
import com.yem.hlm.backend.dashboard.service.HomeDashboardService;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Home dashboard snapshot — role-aware, single call.
 *
 * <pre>
 * GET /api/dashboard/home
 * </pre>
 *
 * Returns a unified snapshot scoped to the caller's role:
 * ADMIN/MANAGER — full société data; AGENT — personal data only.
 * Response is cached 30 s per (societeId, actorId, role).
 */
@RestController
@RequestMapping("/api/dashboard/home")
@Tag(name = "Home Dashboard", description = "Role-aware home page snapshot")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
public class HomeDashboardController {

    private final HomeDashboardService svc;
    private final SocieteContextHelper ctx;

    public HomeDashboardController(HomeDashboardService svc, SocieteContextHelper ctx) {
        this.svc = svc;
        this.ctx = ctx;
    }

    @GetMapping
    public ResponseEntity<HomeDashboardDTO> getSnapshot() {
        UUID societeId = ctx.requireSocieteId();
        UUID actorId   = ctx.requireUserId();
        String role    = ctx.getRole();
        return ResponseEntity.ok(svc.getSnapshot(societeId, actorId, role));
    }

    @GetMapping("/shareholder")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ShareholderKpiDTO getShareholderKpis() {
        return svc.getShareholderKpis(ctx.requireSocieteId());
    }

    @GetMapping("/project-director")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ProjectDirectorKpiDTO getProjectDirectorKpis() {
        return svc.getProjectDirectorKpis(ctx.requireSocieteId());
    }
}
