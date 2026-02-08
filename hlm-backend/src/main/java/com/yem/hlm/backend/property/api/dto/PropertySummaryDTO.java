package com.yem.hlm.backend.property.api.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Dashboard summary DTO with aggregated property statistics.
 */
public record PropertySummaryDTO(
        int totalActive,
        int totalReserved,
        int totalSold,
        int totalDraft,
        Map<String, Integer> countByType,
        Map<String, BigDecimal> avgPriceByType,
        long createdInPeriod,
        long soldInPeriod,
        Long totalValueSold
) {
}
