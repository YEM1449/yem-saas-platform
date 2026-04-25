package com.yem.hlm.backend.viewer3d.api.dto;

import java.time.Instant;

/**
 * Returned by POST /api/projects/{projetId}/3d-model/upload-url.
 * The client must PUT the GLB file directly to {@code uploadUrl}, then confirm
 * via POST /api/projects/{projetId}/3d-model with the returned {@code fileKey}.
 */
public record UploadUrlResponse(
        String uploadUrl,
        String fileKey,
        Instant expiresAt
) {}
