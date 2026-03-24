package com.yem.hlm.backend.media.api;

import com.yem.hlm.backend.media.api.dto.PropertyMediaResponse;
import com.yem.hlm.backend.media.service.PropertyMediaService;
import com.yem.hlm.backend.media.service.PropertyMediaService.MediaDownload;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for property media (images and PDFs).
 *
 * <ul>
 *   <li>POST   /api/properties/{id}/media   — upload (ADMIN/MANAGER)</li>
 *   <li>GET    /api/properties/{id}/media   — list metadata (all authenticated)</li>
 *   <li>GET    /api/media/{mediaId}/download — serve file bytes (all authenticated)</li>
 *   <li>DELETE /api/media/{mediaId}          — delete (ADMIN)</li>
 * </ul>
 */
@Tag(name = "Property Media", description = "Property image and document media management")
@RestController
public class PropertyMediaController {

    private final PropertyMediaService mediaService;

    public PropertyMediaController(PropertyMediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/api/properties/{id}/media")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public PropertyMediaResponse upload(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) throws IOException {
        return mediaService.upload(id, file);
    }

    @GetMapping("/api/properties/{id}/media")
    public List<PropertyMediaResponse> list(@PathVariable UUID id) {
        return mediaService.list(id);
    }

    @GetMapping("/api/media/{mediaId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID mediaId) throws IOException {
        MediaDownload dl = mediaService.download(mediaId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(dl.contentType()));
        String filename = Objects.requireNonNullElse(dl.filename(), "download");
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(Objects.requireNonNull(dl.stream())));
    }

    @DeleteMapping("/api/media/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID mediaId) throws IOException {
        mediaService.delete(mediaId);
    }
}
