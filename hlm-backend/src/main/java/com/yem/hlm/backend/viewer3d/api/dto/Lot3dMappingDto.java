package com.yem.hlm.backend.viewer3d.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Full mapping entry — returned as part of GET /api/projects/{id}/3d-model.
 * Enriched with property display data (ref, type, surface, price) so the viewer
 * never has to make an extra call to /api/properties.
 */
public record Lot3dMappingDto(
        String    meshId,
        String    immeubleMeshId,
        String    trancheMeshId,
        UUID      lotId,
        String    lotRef,
        String    typology,
        BigDecimal surface,
        BigDecimal prix
) {}
