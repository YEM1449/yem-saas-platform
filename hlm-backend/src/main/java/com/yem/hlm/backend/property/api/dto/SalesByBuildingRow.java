package com.yem.hlm.backend.property.api.dto;

import java.math.BigDecimal;

/**
 * One row in the "sales per building" KPI.
 * <p>
 * {@code buildingName} is null when a sold property has no building assigned.
 */
public record SalesByBuildingRow(
        String buildingName,
        long count,
        BigDecimal totalValue
) {}
