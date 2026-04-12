package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record InventoryIntelligenceDTO(
        LocalDateTime asOf,
        StockSummary overall,
        List<ProjectStock> byProject
) {

    public record StockSummary(
            long total,
            long available,
            long reserved,
            long sold,
            long withdrawn,
            BigDecimal absorptionRate
    ) {}

    public record ProjectStock(
            UUID projectId,
            String projectName,
            long total,
            long available,
            long reserved,
            long sold,
            BigDecimal absorptionRate,
            BigDecimal totalValue,
            BigDecimal soldValue
    ) {}
}
