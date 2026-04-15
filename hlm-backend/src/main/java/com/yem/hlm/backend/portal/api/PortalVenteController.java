package com.yem.hlm.backend.portal.api;

import com.yem.hlm.backend.media.service.MediaStorageService;
import com.yem.hlm.backend.portal.api.dto.PortalVenteResponse;
import com.yem.hlm.backend.portal.service.PortalVenteService;
import com.yem.hlm.backend.vente.api.dto.VenteDocumentResponse;
import com.yem.hlm.backend.vente.domain.VenteDocumentType;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Buyer portal vente endpoints — ROLE_PORTAL only.
 *
 * <p>Buyers can view their own vente pipeline data, download documents,
 * and upload signed contracts or supplementary documents.
 */
@Tag(name = "Portal \u2013 Ventes", description = "Buyer portal sale pipeline endpoints (ROLE_PORTAL)")
@RestController
@RequestMapping("/api/portal/ventes")
@PreAuthorize("hasRole('PORTAL')")
public class PortalVenteController {

    private final PortalVenteService portalVenteService;
    private final MediaStorageService mediaStorage;

    public PortalVenteController(PortalVenteService portalVenteService,
                                 MediaStorageService mediaStorage) {
        this.portalVenteService = portalVenteService;
        this.mediaStorage       = mediaStorage;
    }

    /** Returns all ventes for the authenticated buyer. */
    @GetMapping
    public List<PortalVenteResponse> list() {
        return portalVenteService.listMyVentes();
    }

    /** Returns a specific vente for the authenticated buyer. */
    @GetMapping("/{id}")
    public PortalVenteResponse get(@PathVariable UUID id) {
        return portalVenteService.getMyVente(id);
    }

    /** Download a document attached to the buyer's vente. */
    @GetMapping("/{id}/documents/{docId}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(
            @PathVariable UUID id,
            @PathVariable UUID docId) {
        String key = portalVenteService.getDocumentKey(id, docId);
        try {
            InputStream stream = mediaStorage.load(key);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                    .body(new InputStreamResource(stream));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to load document: " + e.getMessage());
        }
    }

    /** Upload a document for the authenticated buyer's vente. */
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public VenteDocumentResponse uploadDocument(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "documentType", defaultValue = "AUTRE") VenteDocumentType documentType) {
        try {
            String storageKey = mediaStorage.store(
                    file.getBytes(), file.getOriginalFilename(), file.getContentType());
            return portalVenteService.uploadDocument(
                    id, file.getOriginalFilename(), storageKey,
                    file.getContentType(), file.getSize(), documentType);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store document: " + e.getMessage());
        }
    }
}
