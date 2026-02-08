package com.yem.hlm.backend.property.api.dto;

import com.yem.hlm.backend.property.domain.PropertyStatus;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

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
        String legalStatus
) {
}
