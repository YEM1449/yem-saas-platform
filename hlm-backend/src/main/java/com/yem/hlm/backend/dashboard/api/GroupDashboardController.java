package com.yem.hlm.backend.dashboard.api;

import com.yem.hlm.backend.dashboard.api.dto.GroupDashboardDTO;
import com.yem.hlm.backend.dashboard.service.GroupDashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vue Groupe — consolidated cross-société dashboard for group owners.
 *
 * <p>Aggregates revenue, stock, cash and VEFA alerts over every société where the caller
 * holds an ADMIN membership. Returns 403 when the caller has no ADMIN membership at all.
 */
@Tag(name = "Vue Groupe", description = "Consolidated multi-société dashboard for group owners")
@RestController
@RequestMapping("/api/groupe")
public class GroupDashboardController {

    private final GroupDashboardService service;

    public GroupDashboardController(GroupDashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public GroupDashboardDTO getDashboard() {
        return service.getGroupDashboard();
    }
}
