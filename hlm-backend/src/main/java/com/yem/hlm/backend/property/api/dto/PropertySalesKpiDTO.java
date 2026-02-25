package com.yem.hlm.backend.property.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Dashboard KPI response: sales broken down by project+agent and by building.
 * <p>
 * A "sale" is a deposit with status CONFIRMED during the given period (by confirmedAt).
 */
public record PropertySalesKpiDTO(
        LocalDate from,
        LocalDate to,
        List<SalesByProjectAgentRow> salesByProjectAgent,
        List<SalesByBuildingRow> salesByBuilding
) {}
