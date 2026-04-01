package com.yem.hlm.backend.media.service;

import com.yem.hlm.backend.property.service.PropertyNotFoundException;
import com.yem.hlm.backend.media.api.dto.PropertyMediaResponse;
import com.yem.hlm.backend.media.domain.PropertyMedia;
import com.yem.hlm.backend.media.repo.PropertyMediaRepository;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.societe.SocieteContext;
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

/**
 * Handles upload, listing, download, and deletion of property media files.
 * All operations are société-scoped via {@link SocieteContext}.
 */
@Service
@Transactional(readOnly = true)
public class PropertyMediaService {

    private final PropertyMediaRepository mediaRepository;
    private final PropertyRepository propertyRepository;
    private final MediaStorageService storageService;
    private final long maxFileSize;
    private final Set<String> allowedTypes;

    public PropertyMediaService(
            PropertyMediaRepository mediaRepository,
            PropertyRepository propertyRepository,
            MediaStorageService storageService,
            @Value("${app.media.max-file-size:10485760}") long maxFileSize,
            @Value("${app.media.allowed-types:image/jpeg,image/png,image/webp,application/pdf}")
            String allowedTypesRaw) {
        this.mediaRepository    = mediaRepository;
        this.propertyRepository = propertyRepository;
        this.storageService     = storageService;
        this.maxFileSize        = maxFileSize;
        this.allowedTypes       = Arrays.stream(allowedTypesRaw.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    // =========================================================================
    // Upload
    // =========================================================================

    @Transactional
    public PropertyMediaResponse upload(UUID propertyId, MultipartFile file) throws IOException {
        UUID societeId = SocieteContext.getSocieteId();

        // Guard: property exists in this société
        propertyRepository.findBySocieteIdAndIdAndDeletedAtIsNull(societeId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));

        // Validate size
        if (file.getSize() > maxFileSize) {
            throw new MediaTooLargeException(file.getSize(), maxFileSize);
        }

        // Validate content type
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        if (!allowedTypes.contains(contentType)) {
            throw new MediaTypeNotAllowedException(contentType);
        }

        int sortOrder = mediaRepository.nextSortOrder(societeId, propertyId);
        // Use the byte-array overload so the SDK can compute the real SHA-256 payload hash.
        // RequestBody.fromInputStream() sends UNSIGNED-PAYLOAD which many S3-compatible
        // providers (MinIO, Cloudflare R2, OVH, ...) reject with 403 Access Denied.
        // At the configured 10 MB max, loading the bytes into heap is fine.
        String fileKey = storageService.store(file.getBytes(), file.getOriginalFilename(), contentType);

        PropertyMedia media = new PropertyMedia(
                societeId, propertyId, fileKey,
                file.getOriginalFilename() != null ? file.getOriginalFilename() : fileKey,
                contentType, file.getSize(), sortOrder);
        PropertyMedia saved = mediaRepository.save(media);
        return PropertyMediaResponse.from(saved);
    }

    // =========================================================================
    // List
    // =========================================================================

    public List<PropertyMediaResponse> list(UUID propertyId) {
        UUID societeId = SocieteContext.getSocieteId();
        propertyRepository.findBySocieteIdAndIdAndDeletedAtIsNull(societeId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));
        return mediaRepository.findBySocieteIdAndPropertyIdOrderBySortOrderAsc(societeId, propertyId)
                .stream()
                .map(PropertyMediaResponse::from)
                .toList();
    }

    // =========================================================================
    // Download
    // =========================================================================

    public record MediaDownload(InputStream stream, String contentType, String filename) {}

    public MediaDownload download(UUID mediaId) throws IOException {
        UUID societeId = SocieteContext.getSocieteId();
        PropertyMedia media = mediaRepository.findBySocieteIdAndId(societeId, mediaId)
                .orElseThrow(() -> new MediaNotFoundException(mediaId));
        InputStream stream = storageService.load(media.getFileKey());
        return new MediaDownload(stream, media.getContentType(), media.getOriginalFilename());
    }

    // =========================================================================
    // Delete
    // =========================================================================

    @Transactional
    public void delete(UUID mediaId) throws IOException {
        UUID societeId = SocieteContext.getSocieteId();
        PropertyMedia media = mediaRepository.findBySocieteIdAndId(societeId, mediaId)
                .orElseThrow(() -> new MediaNotFoundException(mediaId));
        storageService.delete(media.getFileKey());
        mediaRepository.delete(media);
    }

    // =========================================================================
    // Count (used by PropertyResponse enrichment)
    // =========================================================================

    public int countForProperty(UUID societeId, UUID propertyId) {
        return mediaRepository.countByTenantAndProperty(societeId, propertyId);
    }
}
