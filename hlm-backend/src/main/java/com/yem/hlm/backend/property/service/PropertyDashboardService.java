package com.yem.hlm.backend.property.service;

import com.yem.hlm.backend.deposit.domain.DepositStatus;
import com.yem.hlm.backend.property.api.dto.PropertySalesKpiDTO;
import com.yem.hlm.backend.property.api.dto.PropertySummaryDTO;
import com.yem.hlm.backend.property.api.dto.SalesByBuildingRow;
import com.yem.hlm.backend.property.api.dto.SalesByProjectAgentRow;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.tenant.context.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for property dashboard aggregations and KPIs.
 */
@Service
@Transactional(readOnly = true)
public class PropertyDashboardService {

    private final PropertyRepository propertyRepository;

    public PropertyDashboardService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    /**
     * Get property summary with KPIs for a given period.
     *
     * @param from start date of period
     * @param to end date of period
     * @return PropertySummaryDTO with aggregated statistics
     */
    public PropertySummaryDTO getSummary(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.getTenantId();

        // Convert LocalDate to LocalDateTime for repository queries
        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(LocalTime.MAX);

        // Count by status
        Map<PropertyStatus, Integer> statusCounts = new HashMap<>();
        propertyRepository.countByStatus(tenantId).forEach(row -> {
            PropertyStatus status = (PropertyStatus) row[0];
            Long count = (Long) row[1];
            statusCounts.put(status, count.intValue());
        });

        // Count by type
        Map<String, Integer> typeCounts = new HashMap<>();
        propertyRepository.countByType(tenantId).forEach(row -> {
            PropertyType type = (PropertyType) row[0];
            Long count = (Long) row[1];
            typeCounts.put(type.name(), count.intValue());
        });

        // Average price by type
        Map<String, BigDecimal> avgPriceByType = new HashMap<>();
        propertyRepository.averagePriceByType(tenantId).forEach(row -> {
            PropertyType type = (PropertyType) row[0];
            Double avgPrice = (Double) row[1];
            avgPriceByType.put(type.name(), BigDecimal.valueOf(avgPrice));
        });

        // Period-specific metrics
        long createdInPeriod = propertyRepository.countCreatedInPeriod(tenantId, fromDateTime, toDateTime);
        long soldInPeriod = propertyRepository.countSoldInPeriod(tenantId, fromDateTime, toDateTime);
        Long totalValueSold = propertyRepository.totalValueSoldInPeriod(tenantId, fromDateTime, toDateTime);

        return new PropertySummaryDTO(
                statusCounts.getOrDefault(PropertyStatus.ACTIVE, 0),
                statusCounts.getOrDefault(PropertyStatus.RESERVED, 0),
                statusCounts.getOrDefault(PropertyStatus.SOLD, 0),
                statusCounts.getOrDefault(PropertyStatus.DRAFT, 0),
                typeCounts,
                avgPriceByType,
                createdInPeriod,
                soldInPeriod,
                totalValueSold
        );
    }

    /**
     * Get sales KPIs broken down by project+agent and by building for a given period.
     * <p>
     * A "sale" is a deposit with status CONFIRMED during the period (by confirmedAt).
     *
     * @param from start date of period
     * @param to end date of period
     * @return PropertySalesKpiDTO with breakdown rows
     */
    public PropertySalesKpiDTO getSalesKpi(LocalDate from, LocalDate to) {
        UUID tenantId = TenantContext.getTenantId();

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.atTime(LocalTime.MAX);

        List<SalesByProjectAgentRow> byProjectAgent = new ArrayList<>();
        propertyRepository.salesByProjectAndAgent(tenantId, DepositStatus.CONFIRMED, fromDateTime, toDateTime)
                .forEach(row -> byProjectAgent.add(new SalesByProjectAgentRow(
                        (String) row[2],                        // projectName
                        (UUID) row[0],                          // agentId
                        (String) row[1],                        // agentEmail
                        ((Long) row[3]),                        // count
                        toBigDecimal(row[4])                    // totalValue
                )));

        List<SalesByBuildingRow> byBuilding = new ArrayList<>();
        propertyRepository.salesByBuilding(tenantId, DepositStatus.CONFIRMED, fromDateTime, toDateTime)
                .forEach(row -> byBuilding.add(new SalesByBuildingRow(
                        (String) row[0],                        // buildingName
                        ((Long) row[1]),                        // count
                        toBigDecimal(row[2])                    // totalValue
                )));

        return new PropertySalesKpiDTO(from, to, byProjectAgent, byBuilding);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
