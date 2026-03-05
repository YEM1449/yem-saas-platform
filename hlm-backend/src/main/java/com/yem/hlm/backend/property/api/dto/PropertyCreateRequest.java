package com.yem.hlm.backend.property.api.dto;

import com.yem.hlm.backend.property.domain.PropertyType;
import jakarta.validation.constraints.*;

import java.util.UUID;

import java.math.BigDecimal;

public record PropertyCreateRequest(
        @NotNull PropertyType type,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 50) String referenceCode,
        @NotNull @DecimalMin("0.0") BigDecimal price,
        @Size(min = 3, max = 3) String currency,
        BigDecimal commissionRate,
        BigDecimal estimatedValue,
        @Size(max = 255) String address,
        @Size(max = 100) String city,
        @Size(max = 100) String region,
        @Size(max = 20) String postalCode,
        BigDecimal latitude,
        BigDecimal longitude,
        @Size(max = 100) String titleDeedNumber,
        @Size(max = 100) String cadastralReference,
        @Size(max = 200) String ownerName,
        @Size(max = 100) String legalStatus,
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
        @Size(max = 5000) String description,
        @Size(max = 5000) String notes,
        Boolean listedForSale,
        @NotNull UUID projectId,
        @Size(max = 100) String buildingName
) {
    public PropertyCreateRequest {
        if (currency == null || currency.isBlank()) currency = "MAD";
        if (title != null) title = title.trim();
        if (referenceCode != null) referenceCode = referenceCode.trim().toUpperCase();
    }
}
