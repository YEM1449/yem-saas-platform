package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Drill-down response for GET /api/dashboard/commercial/sales.
 * Returns a paged table of signed contracts plus aggregate totals for the same period.
 */
public record CommercialDashboardSalesDTO(
        long totalCount,
        BigDecimal totalAmount,
        int page,
        int pageSize,
        int totalPages,
        List<SalesTableRow> sales
) {}
