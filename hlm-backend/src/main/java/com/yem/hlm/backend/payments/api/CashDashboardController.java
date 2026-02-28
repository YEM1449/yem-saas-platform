package com.yem.hlm.backend.payments.api;

import com.yem.hlm.backend.payments.api.dto.CashDashboardResponse;
import com.yem.hlm.backend.payments.service.CashDashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Cash-flow KPI dashboard.
 *
 * <pre>
 * GET /api/dashboard/commercial/cash?from=&to=
 * </pre>
 *
 * <p>Default window: current calendar month.
 */
@RestController
@RequestMapping("/api/dashboard/commercial/cash")
public class CashDashboardController {

    private final CashDashboardService cashDashboardService;

    public CashDashboardController(CashDashboardService cashDashboardService) {
        this.cashDashboardService = cashDashboardService;
    }

    @GetMapping
    public CashDashboardResponse getCashSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {
        LocalDate effectiveFrom = (from != null) ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate effectiveTo   = (to   != null) ? to   : LocalDate.now();
        return cashDashboardService.getSummary(effectiveFrom, effectiveTo);
    }
}
