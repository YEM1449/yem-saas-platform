package com.yem.hlm.backend.portal.api.dto;

import com.yem.hlm.backend.property.domain.Property;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-only property view for the buyer portal.
 */
public record PortalPropertyResponse(
        UUID id,
        String reference,
        String type,
        String title,
        BigDecimal surfaceAreaSqm,
        String city,
        String address,
        String description,
        String projectName
) {
    public static PortalPropertyResponse from(Property p, String projectName) {
        return new PortalPropertyResponse(
                p.getId(),
                p.getReferenceCode(),
                p.getType().name(),
                p.getTitle(),
                p.getSurfaceAreaSqm(),
                p.getCity(),
                p.getAddress(),
                p.getDescription(),
                projectName
        );
    }
}
