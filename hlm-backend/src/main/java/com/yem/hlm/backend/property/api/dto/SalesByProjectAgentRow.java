package com.yem.hlm.backend.property.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row in the "sales per project per agent" KPI.
 * <p>
 * {@code projectName} is null when a sold property has no project assigned.
 */
public record SalesByProjectAgentRow(
        String projectName,
        UUID agentId,
        String agentEmail,
        long count,
        BigDecimal totalValue
) {}
