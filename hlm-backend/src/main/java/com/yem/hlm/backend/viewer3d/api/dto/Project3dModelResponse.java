package com.yem.hlm.backend.viewer3d.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response for GET /api/projects/{id}/3d-model.
 * Combines model metadata, a short-lived pre-signed R2 URL, and the full mesh→lot mapping list.
 */
public record Project3dModelResponse(
        UUID             id,
        UUID             projetId,
        String           glbPresignedUrl,
        Instant          expiresAt,
        boolean          dracoCompressed,
        List<Lot3dMappingDto> mappings
) {}
