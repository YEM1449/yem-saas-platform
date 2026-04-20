# Object Storage Guide

The platform supports two storage modes for media and many document flows.

## 1. Storage Modes

### Local filesystem

- default for development
- simplest for local debugging
- data stored under the configured local media path

### S3-compatible object storage

- enabled through configuration
- used for production-style deployments
- supported providers include MinIO, AWS S3, Cloudflare R2, and other compatible vendors

## 2. What Uses Storage

- property media
- vente documents
- generic uploaded documents
- logos and template images where relevant
- generated PDFs once persisted through higher-level flows

## 3. Important Implementation Detail

The S3 adapter uses path-style addressing deliberately.

Why:

- many non-AWS providers require it
- removing it would break compatibility even if AWS still worked

## 4. Local MinIO Workflow

Compose exposes MinIO so engineers can test object-storage flows locally without using a cloud account.

Use this when validating:

- upload/download behavior
- object storage config wiring
- media and document flows

## 5. Operational Risks

- wrong endpoint or region
- bucket permissions that allow some operations and block others
- confusing local disk and object-storage environments during debugging
- provider-specific behaviors, especially for R2-style endpoints

## 6. Configuration Families

- `MEDIA_OBJECT_STORAGE_ENABLED`
- `MEDIA_OBJECT_STORAGE_ENDPOINT`
- `MEDIA_OBJECT_STORAGE_REGION`
- `MEDIA_OBJECT_STORAGE_BUCKET`
- `MEDIA_OBJECT_STORAGE_ACCESS_KEY`
- `MEDIA_OBJECT_STORAGE_SECRET_KEY`

## 7. When To Prefer Local Disk In Development

- fast frontend/backend iteration
- debugging a storage-independent workflow
- avoiding external or containerized dependencies during unit-level work

## 8. When To Validate Against Object Storage

- before shipping upload or download changes
- when changing storage adapters
- when modifying generated document persistence logic
