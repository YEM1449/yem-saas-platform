package com.yem.hlm.backend.document.service;

import com.yem.hlm.backend.document.domain.Document;
import com.yem.hlm.backend.document.domain.DocumentEntityType;
import com.yem.hlm.backend.document.repo.DocumentRepository;
import com.yem.hlm.backend.media.service.MediaStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final MediaStorageService storageService;

    public DocumentService(DocumentRepository documentRepository, MediaStorageService storageService) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
    }

    @Transactional
    public Document upload(UUID societeId, UUID uploadedByUserId,
                           DocumentEntityType entityType, UUID entityId,
                           MultipartFile file) throws IOException {
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String fileKey = storageService.store(file.getBytes(), file.getOriginalFilename(), contentType);
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : fileKey;

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
}
