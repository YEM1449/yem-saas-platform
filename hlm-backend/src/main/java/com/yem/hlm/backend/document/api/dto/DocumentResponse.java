package com.yem.hlm.backend.document.api.dto;

import com.yem.hlm.backend.document.domain.Document;
import com.yem.hlm.backend.document.domain.DocumentEntityType;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID societeId,
        DocumentEntityType entityType,
        UUID entityId,
        String fileName,
        String mimeType,
        Long fileSize,
        String description,
        UUID uploadedBy,
        LocalDateTime createdAt
) {
    public static DocumentResponse from(Document d) {
        return new DocumentResponse(
                d.getId(), d.getSocieteId(), d.getEntityType(), d.getEntityId(),
                d.getFileName(), d.getMimeType(), d.getFileSize(),
                d.getDescription(), d.getUploadedBy(), d.getCreatedAt()
        );
    }
}
