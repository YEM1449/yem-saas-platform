package com.yem.hlm.backend.viewer3d.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for POST /api/projects/{projetId}/3d-model/upload-url.
 * The backend validates the request and returns a pre-signed PUT URL for direct R2 upload.
 */
public record UploadUrlRequest(
        @NotBlank @Size(max = 500) String fileName,
        @Min(1) @Max(52_428_800) long fileSizeBytes,
        boolean dracoCompressed
) {}
