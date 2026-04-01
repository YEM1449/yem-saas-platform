package com.yem.hlm.backend.document.service;

import com.yem.hlm.backend.document.domain.Document;
import com.yem.hlm.backend.document.domain.DocumentEntityType;
import com.yem.hlm.backend.document.repo.DocumentRepository;
import com.yem.hlm.backend.media.service.MediaStorageService;
import com.yem.hlm.backend.media.service.MediaTooLargeException;
import com.yem.hlm.backend.media.service.MediaTypeNotAllowedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final MediaStorageService storageService;
    private final long maxFileSize;
    private final Set<String> allowedTypes;

    public DocumentService(
            DocumentRepository documentRepository,
            MediaStorageService storageService,
            @Value("${app.documents.max-file-size:10485760}") long maxFileSize,
            @Value("${app.documents.allowed-types:application/pdf,image/jpeg,image/png,image/webp,text/plain,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-excel,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet}")
            String allowedTypesRaw) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.maxFileSize = maxFileSize;
        this.allowedTypes = Arrays.stream(allowedTypesRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    @Transactional
    public Document upload(UUID societeId, UUID uploadedByUserId,
                           DocumentEntityType entityType, UUID entityId,
                           MultipartFile file) throws IOException {
        if (file.getSize() > maxFileSize) {
            throw new MediaTooLargeException(file.getSize(), maxFileSize);
        }
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        if (!allowedTypes.contains(contentType)) {
            throw new MediaTypeNotAllowedException(contentType);
        }
        String fileKey = storageService.store(file.getBytes(), file.getOriginalFilename(), contentType);
        String fileName = sanitizeFilename(file.getOriginalFilename(), fileKey);

        Document doc = new Document(societeId, entityType, entityId,
                fileName, fileKey, contentType, file.getSize(), uploadedByUserId);
        return documentRepository.save(doc);
    }

    public List<Document> list(UUID societeId, DocumentEntityType entityType, UUID entityId) {
        return documentRepository
                .findBySocieteIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(societeId, entityType, entityId);
    }

    public record DocumentDownload(InputStream stream, String contentType, String fileName) {}

    public DocumentDownload download(UUID societeId, UUID documentId) throws IOException {
        Document doc = requireDocument(societeId, documentId);
        InputStream stream = storageService.load(doc.getFileKey());
        return new DocumentDownload(stream, doc.getMimeType(), doc.getFileName());
    }

    @Transactional
    public void delete(UUID societeId, UUID documentId) throws IOException {
        Document doc = requireDocument(societeId, documentId);
        storageService.delete(doc.getFileKey());
        documentRepository.delete(doc);
    }

    private Document requireDocument(UUID societeId, UUID documentId) {
        return documentRepository.findBySocieteIdAndId(societeId, documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    /**
     * Strips path separators, null bytes, and control characters from an uploaded filename.
     * Prevents HTTP header injection in Content-Disposition and path traversal in logs.
     */
    private static String sanitizeFilename(String original, String fallback) {
        if (original == null || original.isBlank()) return fallback;
        // Remove path components (both separators)
        String name = original.replaceAll("[/\\\\]", "_");
        // Remove null bytes and ASCII control characters (0x00–0x1F, 0x7F)
        name = name.replaceAll("[\\x00-\\x1F\\x7F]", "");
        // Trim and fall back to generated key if result is blank
        name = name.trim();
        return name.isBlank() ? fallback : name;
    }
}
