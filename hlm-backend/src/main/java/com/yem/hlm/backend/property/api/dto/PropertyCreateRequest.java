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
        String description,
        String notes,
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
