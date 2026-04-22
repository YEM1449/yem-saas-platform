package com.yem.hlm.backend.dashboard.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Project Director view — per-project commercial progress and delivery tracking.
 * Returned by GET /api/dashboard/home/project-director.
 */
public record ProjectDirectorKpiDTO(
        List<ProjectProgressRow> projects,
        LocalDateTime generatedAt
) {
    public record ProjectProgressRow(
            UUID projectId,
            String projectName,
            long totalUnits,
            long soldUnits,
            long reservedUnits,
            long availableUnits,
            double soldPct,
            double reservedPct,
            /** Earliest planned delivery date across all tranches; null if no tranches configured. */
            LocalDate deliveryPlanned,
            /** True if deliveryPlanned is null (not configured) or is today or in the future. */
            boolean deliveryOnTrack
    ) {}
}
