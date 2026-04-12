package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pipeline intelligence snapshot — weighted values, aging and risk flags.
 */
public record PipelineAnalysisDTO(

        LocalDateTime asOf,

        /** Total weighted pipeline = sum(prixVente * probability / 100). */
        BigDecimal totalWeightedValue,

        /** Total raw (unweighted) pipeline CA. */
        BigDecimal totalRawValue,

        /** Pipeline breakdown per statut. */
        List<PipelineStage> stages,

        /** Deals flagged as at-risk (> 30 days in current stage, non-terminal). */
        List<AtRiskDeal> atRiskDeals

) {

    public record PipelineStage(
            String statut,
            long count,
            BigDecimal rawValue,
            BigDecimal weightedValue,
            int defaultProbability,
            long avgAgingDays
    ) {}

    public record AtRiskDeal(
            java.util.UUID venteId,
            String venteRef,
            String contactFullName,
            String statut,
            BigDecimal prixVente,
            BigDecimal weightedValue,
            long agingDays
    ) {}
}
