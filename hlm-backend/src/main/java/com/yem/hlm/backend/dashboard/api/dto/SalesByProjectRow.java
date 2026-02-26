package com.yem.hlm.backend.dashboard.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One row in the "top sales by project" breakdown (top 10). */
public record SalesByProjectRow(
        UUID projectId,
        String projectName,
        long salesCount,
        BigDecimal salesAmount
) {}
