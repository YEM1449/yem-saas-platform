# Task 09 — New Document Attachment Module

## Priority: NEW FEATURE
## Risk: N/A (additive)
## Effort: 3 hours
## Depends on: Task 02 (uses SocieteContextHelper)

## Problem

The system can generate PDFs (contracts, reservations, call-for-funds) but has no way to store uploaded documents (ID copies, signed contracts, proof of payment, etc.) linked to contacts, contracts, or deposits.

## Module Design

Reuse the existing `MediaStorageService` interface (local + S3) from the `media` module. This module adds a generic `Document` entity that can attach files to any entity type.

```
com.yem.hlm.backend.document/
├── api/
│   ├── DocumentController.java
│   └── dto/
│       ├── DocumentResponse.java
│       └── DocumentUploadResponse.java
├── domain/
│   ├── Document.java
│   └── DocumentEntityType.java
├── repo/
│   └── DocumentRepository.java
└── service/
    ├── DocumentService.java
    └── DocumentNotFoundException.java
```

## Files to Create

### 1. Domain: `document/domain/DocumentEntityType.java`

```java
package com.yem.hlm.backend.document.domain;

public enum DocumentEntityType {
    CONTACT,
    CONTRACT,
    DEPOSIT,
    PROPERTY,
    RESERVATION
}
```

### 2. Domain: `document/domain/Document.java`

```java
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
        @Index(name = "idx_doc_societe_entity", columnList = "societe_id,entity_type,entity_id"),
        @Index(name = "idx_doc_societe_created", columnList = "societe_id,created_at DESC")
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
        this.societeId = societeId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.fileName = fileName;
        this.fileKey = fileKey;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.uploadedBy = uploadedBy;
    }
}
```

### 3. Repository, Service, Controller

Follow existing `PropertyMediaController` / `PropertyMediaService` pattern:
- Upload: `POST /api/documents?entityType=CONTACT&entityId={uuid}` (multipart)
- List: `GET /api/documents?entityType=CONTACT&entityId={uuid}`
- Download: `GET /api/documents/{id}/download`
- Delete: `DELETE /api/documents/{id}`
- Inject `SocieteContextHelper` and `MediaStorageService`
- Validate entity exists and belongs to current société before attaching

### 4. Liquibase Migration: `040-create-document-table.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: "040-create-document-table"
      author: "claude-audit"
      changes:
        - createTable:
            tableName: document
            columns:
              - column: { name: id, type: uuid, defaultValueComputed: gen_random_uuid(), constraints: { primaryKey: true } }
              - column: { name: societe_id, type: uuid, constraints: { nullable: false } }
              - column: { name: entity_type, type: varchar(20), constraints: { nullable: false } }
              - column: { name: entity_id, type: uuid, constraints: { nullable: false } }
              - column: { name: file_name, type: varchar(300), constraints: { nullable: false } }
              - column: { name: file_key, type: varchar(500), constraints: { nullable: false } }
              - column: { name: mime_type, type: varchar(100), constraints: { nullable: false } }
              - column: { name: file_size, type: bigint }
              - column: { name: description, type: varchar(500) }
              - column: { name: uploaded_by, type: uuid, constraints: { nullable: false } }
              - column: { name: created_at, type: timestamp, constraints: { nullable: false }, defaultValueComputed: now() }
        - addForeignKeyConstraint:
            baseTableName: document
            baseColumnNames: societe_id
            referencedTableName: societe
            referencedColumnNames: id
            constraintName: fk_document_societe
        - createIndex: { indexName: idx_doc_societe_entity, tableName: document, columns: [{ column: societe_id }, { column: entity_type }, { column: entity_id }] }
        - createIndex: { indexName: idx_doc_societe_created, tableName: document, columns: [{ column: societe_id }, { column: created_at, descending: true }] }
```

## Tests to Run

```bash
cd hlm-backend && ./mvnw test -Dtest=DocumentControllerIT
cd hlm-backend && ./mvnw test
```

## Acceptance Criteria

- [ ] Document CRUD + upload/download endpoints work
- [ ] Documents scoped by societeId
- [ ] Documents can be linked to CONTACT, CONTRACT, DEPOSIT, PROPERTY, RESERVATION
- [ ] Reuses existing MediaStorageService for file storage
- [ ] Cross-société isolation verified
- [ ] Liquibase migration runs cleanly
