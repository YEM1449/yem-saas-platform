package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Shareholder-facing portfolio KPIs.
 * Returned by GET /api/dashboard/home/shareholder.
 */
public record ShareholderKpiDTO(
        /** Sum of list price for all ACTIVE + RESERVED properties (unsold inventory value). */
        BigDecimal portfolioValueAtMarket,
        /** Sum of prix_vente for all LIVRE ventes (realized revenue). */
        BigDecimal soldValue,
        /** Sum of prix_vente for active pipeline (COMPROMIS, FINANCEMENT, ACTE_NOTARIE). */
        BigDecimal projectedCompletionExposure,
        /** Per-project concentration — sorted by pct descending. */
        List<ProjectConcentrationRow> concentrationByProject,
        LocalDateTime generatedAt
) {
    public record ProjectConcentrationRow(
            UUID projectId,
            String projectName,
            long unitCount,
            double pctOfPortfolio
    ) {}
}
