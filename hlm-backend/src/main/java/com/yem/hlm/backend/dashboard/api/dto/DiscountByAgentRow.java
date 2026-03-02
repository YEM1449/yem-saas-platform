package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One row in the discountByAgent breakdown — top agents by average discount %. */
public record DiscountByAgentRow(
        UUID agentId,
        String agentEmail,
        BigDecimal avgDiscountPercent,
        long salesCount
) {}
