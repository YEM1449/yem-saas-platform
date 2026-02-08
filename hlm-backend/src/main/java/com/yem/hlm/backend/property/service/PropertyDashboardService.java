package com.yem.hlm.backend.property.service;

import com.yem.hlm.backend.property.api.dto.PropertySummaryDTO;
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
import java.util.HashMap;
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
}
