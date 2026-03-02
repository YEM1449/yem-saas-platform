package com.yem.hlm.backend.media.service;

import com.yem.hlm.backend.property.service.PropertyNotFoundException;
import com.yem.hlm.backend.media.api.dto.PropertyMediaResponse;
import com.yem.hlm.backend.media.domain.PropertyMedia;
import com.yem.hlm.backend.media.repo.PropertyMediaRepository;
import com.yem.hlm.backend.property.repo.PropertyRepository;
import com.yem.hlm.backend.tenant.context.TenantContext;
import com.yem.hlm.backend.tenant.repo.TenantRepository;
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
 * All operations are tenant-scoped via {@link TenantContext}.
 */
@Service
@Transactional(readOnly = true)
public class PropertyMediaService {

    private final PropertyMediaRepository mediaRepository;
    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final MediaStorageService storageService;
    private final long maxFileSize;
    private final Set<String> allowedTypes;

    public PropertyMediaService(
            PropertyMediaRepository mediaRepository,
            PropertyRepository propertyRepository,
            TenantRepository tenantRepository,
            MediaStorageService storageService,
            @Value("${app.media.max-file-size:10485760}") long maxFileSize,
            @Value("${app.media.allowed-types:image/jpeg,image/png,image/webp,application/pdf}")
            String allowedTypesRaw) {
        this.mediaRepository    = mediaRepository;
        this.propertyRepository = propertyRepository;
        this.tenantRepository   = tenantRepository;
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
        UUID tenantId = TenantContext.getTenantId();

        // Guard: property exists in this tenant
        propertyRepository.findByTenant_IdAndIdAndDeletedAtIsNull(tenantId, propertyId)
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

        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));

        int sortOrder = mediaRepository.nextSortOrder(tenantId, propertyId);
        String fileKey = storageService.store(file.getBytes(), file.getOriginalFilename(), contentType);

        PropertyMedia media = new PropertyMedia(
                tenant, propertyId, fileKey,
                file.getOriginalFilename() != null ? file.getOriginalFilename() : fileKey,
                contentType, file.getSize(), sortOrder);
        PropertyMedia saved = mediaRepository.save(media);
        return PropertyMediaResponse.from(saved);
    }

    // =========================================================================
    // List
    // =========================================================================

    public List<PropertyMediaResponse> list(UUID propertyId) {
        UUID tenantId = TenantContext.getTenantId();
        propertyRepository.findByTenant_IdAndIdAndDeletedAtIsNull(tenantId, propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));
        return mediaRepository.findByTenant_IdAndPropertyIdOrderBySortOrderAsc(tenantId, propertyId)
                .stream()
                .map(PropertyMediaResponse::from)
                .toList();
    }

    // =========================================================================
    // Download
    // =========================================================================

    public record MediaDownload(InputStream stream, String contentType, String filename) {}

    public MediaDownload download(UUID mediaId) throws IOException {
        UUID tenantId = TenantContext.getTenantId();
        PropertyMedia media = mediaRepository.findByTenant_IdAndId(tenantId, mediaId)
                .orElseThrow(() -> new MediaNotFoundException(mediaId));
        InputStream stream = storageService.load(media.getFileKey());
        return new MediaDownload(stream, media.getContentType(), media.getOriginalFilename());
    }

    // =========================================================================
    // Delete
    // =========================================================================

    @Transactional
    public void delete(UUID mediaId) throws IOException {
        UUID tenantId = TenantContext.getTenantId();
        PropertyMedia media = mediaRepository.findByTenant_IdAndId(tenantId, mediaId)
                .orElseThrow(() -> new MediaNotFoundException(mediaId));
        storageService.delete(media.getFileKey());
        mediaRepository.delete(media);
    }

    // =========================================================================
    // Count (used by PropertyResponse enrichment)
    // =========================================================================

    public int countForProperty(UUID tenantId, UUID propertyId) {
        return mediaRepository.countByTenantAndProperty(tenantId, propertyId);
    }
}
