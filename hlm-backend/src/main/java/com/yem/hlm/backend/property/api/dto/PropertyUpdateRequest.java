package com.yem.hlm.backend.property.api.dto;

import com.yem.hlm.backend.property.domain.PropertyStatus;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record PropertyUpdateRequest(
        @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @Size(max = 5000) String notes,
        BigDecimal price,
        PropertyStatus status,
        @Size(max = 255) String address,
        @Size(max = 100) String city,
        @Size(max = 100) String region,
        @Size(max = 20) String postalCode,
        @Size(max = 100) String legalStatus,
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
        @Size(max = 100) String zoning,
        Boolean isServiced,
        // Listing + project/building fields
        Boolean listedForSale,
        UUID projectId,
        UUID immeubleId,
        @Size(max = 100) String buildingName
) {
}
