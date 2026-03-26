package com.yem.hlm.backend.document.api;

import com.yem.hlm.backend.document.api.dto.DocumentResponse;
import com.yem.hlm.backend.document.api.dto.DocumentUploadResponse;
import com.yem.hlm.backend.document.domain.DocumentEntityType;
import com.yem.hlm.backend.document.service.DocumentService;
import com.yem.hlm.backend.document.service.DocumentService.DocumentDownload;
import com.yem.hlm.backend.societe.SocieteContextHelper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Document attachment endpoints.
 *
 * <pre>
 * POST   /api/documents?entityType=CONTACT&entityId={uuid}  ← upload document
 * GET    /api/documents?entityType=CONTACT&entityId={uuid}  ← list documents for entity
 * GET    /api/documents/{id}/download                       ← download file
 * DELETE /api/documents/{id}                                ← delete (ADMIN/MANAGER)
 * </pre>
 */
@Tag(name = "Documents", description = "Cross-entity document attachments")
@RestController
@RequestMapping("/api/documents")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
public class DocumentController {

    private final DocumentService documentService;
    private final SocieteContextHelper societeContextHelper;

    public DocumentController(DocumentService documentService, SocieteContextHelper societeContextHelper) {
        this.documentService = documentService;
        this.societeContextHelper = societeContextHelper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentUploadResponse upload(
            @RequestParam DocumentEntityType entityType,
            @RequestParam UUID entityId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return DocumentUploadResponse.from(
                documentService.upload(
                        societeContextHelper.requireSocieteId(),
                        societeContextHelper.requireUserId(),
                        entityType, entityId, file));
    }

    @GetMapping
    public List<DocumentResponse> list(
            @RequestParam DocumentEntityType entityType,
            @RequestParam UUID entityId) {
        return documentService.list(societeContextHelper.requireSocieteId(), entityType, entityId)
                .stream().map(DocumentResponse::from).toList();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) throws IOException {
        DocumentDownload dl = documentService.download(societeContextHelper.requireSocieteId(), id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(dl.contentType()));
        String filename = Objects.requireNonNullElse(dl.fileName(), "download");
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(new InputStreamResource(dl.stream()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void delete(@PathVariable UUID id) throws IOException {
        documentService.delete(societeContextHelper.requireSocieteId(), id);
    }
}
