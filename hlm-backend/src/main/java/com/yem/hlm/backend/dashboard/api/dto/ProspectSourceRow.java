package com.yem.hlm.backend.dashboard.api.dto;

/**
 * One row in the prospectsBySource funnel breakdown.
 * conversionRate = convertedCount / count (0.0–1.0); null when count = 0.
 */
public record ProspectSourceRow(
        String source,
        long count,
        long convertedCount,
        Double conversionRate
) {}
