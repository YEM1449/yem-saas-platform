package com.yem.hlm.backend.dashboard.api.dto;

import java.util.UUID;

/**
 * Per-project inventory KPI snapshot.
 *
 * <ul>
 *   <li>{@code saleRate}        = sold / total (0.0 when total = 0)</li>
 *   <li>{@code reservationRate} = reserved / total (0.0 when total = 0)</li>
 * </ul>
 */
public record ProjectKpiRow(
        UUID   projectId,
        String projectName,
        long   totalProperties,
        long   available,
        long   reserved,
        long   sold,
        double saleRate,
        double reservationRate
) {}
