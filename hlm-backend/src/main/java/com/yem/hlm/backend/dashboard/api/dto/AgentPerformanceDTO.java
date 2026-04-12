package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Agent performance analytics — ranking by total sales, conversion rate,
 * average deal size and average time to close.
 */
public record AgentPerformanceDTO(

        LocalDateTime asOf,

        List<AgentRow> agents

) {

    /**
     * @param agentId          UUID
     * @param agentName        display name
     * @param totalSales       count of LIVRE ventes (all-time)
     * @param totalCA          sum of prixVente for LIVRE
     * @param conversionRate   LIVRE / (LIVRE + ANNULE) * 100; null when no terminal ventes
     * @param avgDealSize      avg prixVente of LIVRE ventes; null when none
     * @param avgDaysToClose   avg days from creation to LIVRE (null when no delivered)
     * @param activePipeline   count of non-terminal ventes currently open
     */
    public record AgentRow(
            UUID agentId,
            String agentName,
            long totalSales,
            BigDecimal totalCA,
            BigDecimal conversionRate,
            BigDecimal avgDealSize,
            BigDecimal avgDaysToClose,
            long activePipeline
    ) {}
}
