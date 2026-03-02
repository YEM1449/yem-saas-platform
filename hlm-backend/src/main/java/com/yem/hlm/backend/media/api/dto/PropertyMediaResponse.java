package com.yem.hlm.backend.media.api.dto;

import com.yem.hlm.backend.media.domain.PropertyMedia;

import java.time.LocalDateTime;
import java.util.UUID;

public record PropertyMediaResponse(
        UUID id,
        UUID propertyId,
        String fileKey,
        String originalFilename,
        String contentType,
        long sizeBytes,
        int sortOrder,
        LocalDateTime uploadedAt
) {
    public static PropertyMediaResponse from(PropertyMedia m) {
        return new PropertyMediaResponse(
                m.getId(), m.getPropertyId(), m.getFileKey(),
                m.getOriginalFilename(), m.getContentType(),
                m.getSizeBytes(), m.getSortOrder(), m.getUploadedAt()
        );
    }
}
