package com.yem.hlm.backend.project.api.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregated KPIs for a single Project.
 *
 * <ul>
 *   <li>{@code totalProperties}   – non-deleted property count</li>
 *   <li>{@code propertiesByType}  – type code → count (e.g. "VILLA" → 3)</li>
 *   <li>{@code statusBreakdown}   – status code → count (e.g. "ACTIVE" → 5)</li>
 *   <li>{@code depositsCount}     – all deposits linked to project properties</li>
 *   <li>{@code depositsTotalAmount} – sum of property prices for those deposits</li>
 *   <li>{@code salesCount}        – confirmed deposits (= sales)</li>
 *   <li>{@code salesTotalAmount}  – sum of property prices for confirmed deposits</li>
 * </ul>
 */
public record ProjectKpiDTO(
        UUID projectId,
        String projectName,
        long totalProperties,
        Map<String, Long> propertiesByType,
        Map<String, Long> statusBreakdown,
        long depositsCount,
        BigDecimal depositsTotalAmount,
        long salesCount,
        BigDecimal salesTotalAmount
) {
}
