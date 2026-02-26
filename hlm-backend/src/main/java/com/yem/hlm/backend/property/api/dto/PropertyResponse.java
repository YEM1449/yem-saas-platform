package com.yem.hlm.backend.property.api.dto;

import com.yem.hlm.backend.property.domain.Property;
import com.yem.hlm.backend.property.domain.PropertyCategory;
import com.yem.hlm.backend.property.domain.PropertyStatus;
import com.yem.hlm.backend.property.domain.PropertyType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PropertyResponse(
        UUID id,
        PropertyType type,
        PropertyCategory category,
        PropertyStatus status,
        String referenceCode,
        String title,
        String description,
        String notes,
        BigDecimal price,
        String currency,
        BigDecimal commissionRate,
        BigDecimal estimatedValue,
        String address,
        String city,
        String region,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude,
        String titleDeedNumber,
        String cadastralReference,
        String ownerName,
        String legalStatus,
        BigDecimal surfaceAreaSqm,
        BigDecimal landAreaSqm,
        Integer bedrooms,
        Integer bathrooms,
        Integer floors,
        Integer parkingSpaces,
        Boolean hasGarden,
        Boolean hasPool,
        Integer buildingYear,
        Integer floorNumber,
        String zoning,
        Boolean isServiced,
        boolean listedForSale,
        UUID projectId,
        String projectName,
        String buildingName,
        UUID createdBy,
        UUID updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt,
        LocalDateTime publishedAt,
        LocalDateTime soldAt
) {
    public static PropertyResponse from(Property p) {
        return new PropertyResponse(
                p.getId(), p.getType(), p.getType().category(), p.getStatus(),
                p.getReferenceCode(), p.getTitle(),
                p.getDescription(), p.getNotes(), p.getPrice(), p.getCurrency(),
                p.getCommissionRate(), p.getEstimatedValue(), p.getAddress(), p.getCity(),
                p.getRegion(), p.getPostalCode(), p.getLatitude(), p.getLongitude(),
                p.getTitleDeedNumber(), p.getCadastralReference(), p.getOwnerName(),
                p.getLegalStatus(), p.getSurfaceAreaSqm(), p.getLandAreaSqm(),
                p.getBedrooms(), p.getBathrooms(), p.getFloors(), p.getParkingSpaces(),
                p.getHasGarden(), p.getHasPool(), p.getBuildingYear(), p.getFloorNumber(),
                p.getZoning(), p.getIsServiced(), p.isListedForSale(),
                p.getProject() != null ? p.getProject().getId() : null,
                p.getProjectName(), p.getBuildingName(),
                p.getCreatedBy(), p.getUpdatedBy(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getDeletedAt(), p.getPublishedAt(),
                p.getSoldAt()
        );
    }
}
