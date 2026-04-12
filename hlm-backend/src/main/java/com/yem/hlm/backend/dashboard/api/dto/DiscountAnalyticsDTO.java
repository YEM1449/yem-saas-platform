package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DiscountAnalyticsDTO(
        LocalDateTime asOf,
        long totalDealsWithDiscount,
        long totalDeals,
        BigDecimal avgDiscountPercent,
        BigDecimal maxDiscountPercent,
        BigDecimal totalDiscountVolume,
        List<AgentDiscount> byAgent
) {

    public record AgentDiscount(
            UUID agentId,
            String agentName,
            long dealsWithDiscount,
            long totalDeals,
            BigDecimal avgDiscountPercent,
            BigDecimal totalDiscountVolume
    ) {}
}
