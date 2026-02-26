package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One row in the "top sales by agent" breakdown (top 10). */
public record SalesByAgentRow(
        UUID agentId,
        String agentEmail,
        long salesCount,
        BigDecimal salesAmount
) {}
