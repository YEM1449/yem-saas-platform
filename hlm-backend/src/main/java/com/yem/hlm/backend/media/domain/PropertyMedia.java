package com.yem.hlm.backend.media.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores metadata for a file attached to a property (image or PDF).
 *
 * <p>The actual file bytes are stored via {@code MediaStorageService}; this entity
 * only tracks the {@code fileKey} needed to retrieve or delete the file.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "property_media",
        indexes = {
                @Index(name = "idx_property_media_tenant_property",
                        columnList = "societe_id,property_id")
        }
)
public class PropertyMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    /** FK to the property this media belongs to. Stored as raw UUID to avoid circular dependency. */
    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    /** Storage provider key (relative path for local storage, S3 object key for S3). */
    @Column(name = "file_key", nullable = false, length = 500)
    private String fileKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Display order within the property gallery (0-based). */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    void onCreate() {
        if (this.uploadedAt == null) {
            this.uploadedAt = LocalDateTime.now();
        }
    }

    public PropertyMedia(UUID societeId, UUID propertyId, String fileKey,
                         String originalFilename, String contentType,
                         long sizeBytes, int sortOrder) {
        this.societeId        = societeId;
        this.propertyId       = propertyId;
        this.fileKey          = fileKey;
        this.originalFilename = originalFilename;
        this.contentType      = contentType;
        this.sizeBytes        = sizeBytes;
        this.sortOrder        = sortOrder;
    }
}
