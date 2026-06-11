package com.yem.hlm.backend.dashboard.api;

import com.yem.hlm.backend.dashboard.service.TresorerieDashboardDTO;
import com.yem.hlm.backend.dashboard.service.TresorerieDashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** VEFA treasury dashboard — cash position + actionable alerts (Wave 12 P6). */
@Tag(name = "Dashboard trésorerie", description = "VEFA cash position and alerts")
@RestController
@RequestMapping("/api/dashboard/tresorerie")
public class TresorerieDashboardController {

    private final TresorerieDashboardService service;

    public TresorerieDashboardController(TresorerieDashboardService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public TresorerieDashboardDTO getTresorerie() {
        return service.getTresorerie();
    }
}
