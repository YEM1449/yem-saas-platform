package com.yem.hlm.backend.document.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "document",
    indexes = {
        @Index(name = "idx_doc_societe_entity",  columnList = "societe_id,entity_type,entity_id"),
        @Index(name = "idx_doc_societe_created", columnList = "societe_id,created_at")
    }
)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private DocumentEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Setter
    @Column(name = "file_name", nullable = false, length = 300)
    private String fileName;

    @Column(name = "file_key", nullable = false, length = 500)
    private String fileKey;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @Setter
    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Document(UUID societeId, DocumentEntityType entityType, UUID entityId,
                    String fileName, String fileKey, String mimeType, Long fileSize, UUID uploadedBy) {
        this.societeId  = societeId;
        this.entityType = entityType;
        this.entityId   = entityId;
        this.fileName   = fileName;
        this.fileKey    = fileKey;
        this.mimeType   = mimeType;
        this.fileSize   = fileSize;
        this.uploadedBy = uploadedBy;
    }
}
