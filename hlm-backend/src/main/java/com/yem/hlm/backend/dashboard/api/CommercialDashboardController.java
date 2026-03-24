package com.yem.hlm.backend.dashboard.api;

import com.yem.hlm.backend.dashboard.api.dto.CommercialDashboardSalesDTO;
import com.yem.hlm.backend.dashboard.api.dto.CommercialDashboardSummaryDTO;
import com.yem.hlm.backend.dashboard.service.CommercialDashboardService;
import com.yem.hlm.backend.dashboard.service.DashboardEmitterRegistry;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Commercial dashboard endpoints — one call per screen.
 *
 * <pre>
 * GET /api/dashboard/commercial          ← alias, accepts LocalDate params (YYYY-MM-DD)
 * GET /api/dashboard/commercial/summary  ← canonical, accepts LocalDateTime params
 *   ?from=  (date or datetime, optional — default last 30 days)
 *   &to=    (date or datetime, optional — default now)
 *   &projectId= (UUID, optional)
 *   &agentId=   (UUID, optional — ignored for ROLE_AGENT, forced to self)
 *
 * GET /api/dashboard/commercial/sales    ← paginated drill-down
 *   same filters + ?page=0&size=20
 * </pre>
 *
 * RBAC:
 * <ul>
 *   <li>ADMIN / MANAGER — full société data, optional filters.</li>
 *   <li>AGENT — server-enforced scope to own agentId; no role guard needed on controller.</li>
 * </ul>
 *
 * Caching: summary responses are cached 30 s in {@code commercialDashboardSummaryCache}.
 */
@RestController
@RequestMapping("/api/dashboard/commercial")
public class CommercialDashboardController {

    private final CommercialDashboardService dashboardService;
    private final DashboardEmitterRegistry   emitterRegistry;
    private final Counter                    summaryRequestCounter;
    private final SocieteContextHelper       societeContextHelper;

    public CommercialDashboardController(CommercialDashboardService dashboardService,
                                         DashboardEmitterRegistry emitterRegistry,
                                         MeterRegistry meterRegistry,
                                         SocieteContextHelper societeContextHelper) {
        this.dashboardService = dashboardService;
        this.emitterRegistry  = emitterRegistry;
        this.societeContextHelper = societeContextHelper;
        this.summaryRequestCounter = Counter.builder("commercial_dashboard_summary_requests_total")
                .description("Total number of commercial dashboard summary requests (cache hits + misses)")
                .register(meterRegistry);
    }

    /**
     * Alias endpoint: accepts {@code from}/{@code to} as {@code YYYY-MM-DD} dates
     * (converts to start-of-day / end-of-day). Useful for simple date-picker UIs.
     * Delegates to the same service method as {@link #summary}.
     */
    @GetMapping
    public CommercialDashboardSummaryDTO summaryByDate(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,

            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID agentId
    ) {
        LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDt   = to   != null ? to.atTime(23, 59, 59) : null;
        return doSummary(fromDt, toDt, projectId, agentId);
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
        return doSummary(from, to, projectId, agentId);
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
        UUID societeId = societeContextHelper.requireSocieteId();
        LocalDateTime[] range = dashboardService.resolveDateRange(from, to);
        dashboardService.validateProject(societeId, projectId);
        UUID effectiveAgentId = dashboardService.resolveEffectiveAgentId(societeId, agentId);

        return dashboardService.getSales(societeId, range[0], range[1], projectId, effectiveAgentId, page, size);
    }

    /**
     * SSE endpoint — clients subscribe to receive real-time dashboard refresh signals.
     * Emits a {@code dashboard-refresh} event whenever a relevant domain event occurs
     * (e.g. CONTACT_CREATED). Timeout: 5 minutes; clients should reconnect on close.
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
    public SseEmitter subscribe(
            @RequestParam(defaultValue = "default") String sessionId) {
        UUID societeId = societeContextHelper.requireSocieteId();
        return emitterRegistry.register(societeId, sessionId);
    }

    // =========================================================================
    // Shared logic
    // =========================================================================

    private CommercialDashboardSummaryDTO doSummary(LocalDateTime from, LocalDateTime to,
                                                     UUID projectId, UUID agentId) {
        summaryRequestCounter.increment();
        UUID societeId = societeContextHelper.requireSocieteId();
        LocalDateTime[] range = dashboardService.resolveDateRange(from, to);
        dashboardService.validateProject(societeId, projectId);
        UUID effectiveAgentId = dashboardService.resolveEffectiveAgentId(societeId, agentId);

        return dashboardService.getSummary(societeId, range[0], range[1], projectId, effectiveAgentId);
    }
}
