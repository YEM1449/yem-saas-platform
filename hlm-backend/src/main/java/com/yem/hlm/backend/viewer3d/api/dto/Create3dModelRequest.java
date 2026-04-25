package com.yem.hlm.backend.viewer3d.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for POST /api/projects/{projetId}/3d-model.
 * The GLB file must already be uploaded to R2; only the object key is sent here.
 */
public record Create3dModelRequest(
        @NotBlank @Size(max = 1024) String glbFileKey,
        boolean dracoCompressed
) {}
