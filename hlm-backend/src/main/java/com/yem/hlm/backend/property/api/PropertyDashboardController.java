package com.yem.hlm.backend.property.api;

import com.yem.hlm.backend.property.api.dto.DashboardPeriod;
import com.yem.hlm.backend.property.api.dto.PropertySalesKpiDTO;
import com.yem.hlm.backend.property.api.dto.PropertySummaryDTO;
import com.yem.hlm.backend.property.service.InvalidPeriodException;
import com.yem.hlm.backend.property.service.PeriodCalculator;
import com.yem.hlm.backend.property.service.PropertyDashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * REST controller for property dashboard and KPIs.
 * <p>
 * Requires ADMIN or MANAGER role for access.
 */
@RestController
@RequestMapping("/dashboard/properties")
public class PropertyDashboardController {

    private final PropertyDashboardService dashboardService;

    public PropertyDashboardController(PropertyDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Get property dashboard summary with KPIs for a given period.
     * <p>
     * Period can be specified either:
     * - Using preset (LAST_7_DAYS, LAST_30_DAYS, THIS_MONTH, etc.)
     * - Using explicit from/to dates
     * <p>
     * Defaults to LAST_30_DAYS if no parameters provided.
     *
     * @param from start date (optional)
     * @param to end date (optional)
     * @param preset period preset (optional)
     * @return PropertySummaryDTO with aggregated statistics
     * @throws InvalidPeriodException if period validation fails
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public PropertySummaryDTO getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) DashboardPeriod preset
    ) {
        var range = resolvePeriod(from, to, preset);
        return dashboardService.getSummary(range.from(), range.to());
    }

    /**
     * Get sales KPIs broken down by project+agent and by building for a given period.
     * <p>
     * A "sale" is a deposit with status CONFIRMED during the period (by confirmedAt).
     * <p>
     * Period defaults to LAST_30_DAYS if no parameters provided.
     *
     * @param from start date (optional)
     * @param to end date (optional)
     * @param preset period preset (optional)
     * @return PropertySalesKpiDTO with breakdown rows
     * @throws InvalidPeriodException if period validation fails
     */
    @GetMapping("/sales-kpi")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public PropertySalesKpiDTO getSalesKpi(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) DashboardPeriod preset
    ) {
        var range = resolvePeriod(from, to, preset);
        return dashboardService.getSalesKpi(range.from(), range.to());
    }

    private PeriodCalculator.DateRange resolvePeriod(LocalDate from, LocalDate to, DashboardPeriod preset) {
        if (preset != null) {
            return PeriodCalculator.calculate(preset);
        }
        if (from == null || to == null) {
            return PeriodCalculator.calculate(DashboardPeriod.LAST_30_DAYS);
        }
        if (from.isAfter(to)) {
            throw new InvalidPeriodException("'from' date must be before or equal to 'to' date");
        }
        long daysBetween = ChronoUnit.DAYS.between(from, to);
        if (daysBetween > 366) {
            throw new InvalidPeriodException("Period cannot exceed 366 days (from: " + from + ", to: " + to + ")");
        }
        return new PeriodCalculator.DateRange(from, to);
    }
}
