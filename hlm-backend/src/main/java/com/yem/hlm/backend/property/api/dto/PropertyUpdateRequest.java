package com.yem.hlm.backend.property.api.dto;

import com.yem.hlm.backend.property.domain.PropertyStatus;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record PropertyUpdateRequest(
        @Size(max = 200) String title,
        String description,
        String notes,
        BigDecimal price,
        PropertyStatus status,
        String address,
        String city,
        String region,
        String postalCode,
        String legalStatus,
        // Type-specific fields (partial update — null = no change)
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
        // Listing + project/building fields
        Boolean listedForSale,
        UUID projectId,
        @Size(max = 100) String buildingName
) {
}
