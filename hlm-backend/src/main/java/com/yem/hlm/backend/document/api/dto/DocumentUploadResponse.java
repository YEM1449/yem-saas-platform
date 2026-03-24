package com.yem.hlm.backend.document.api.dto;

import com.yem.hlm.backend.document.domain.Document;

import java.util.UUID;

public record DocumentUploadResponse(UUID id, String fileName, String mimeType, Long fileSize) {
    public static DocumentUploadResponse from(Document d) {
        return new DocumentUploadResponse(d.getId(), d.getFileName(), d.getMimeType(), d.getFileSize());
    }
}
