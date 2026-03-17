# Object Storage Guide — Engineer Guide

This guide covers how property media storage works, how to configure local file storage (default) and S3-compatible object storage (production), and how to connect MinIO for local S3 testing.

## Table of Contents

1. [Storage Architecture](#storage-architecture)
2. [Local File Storage (Default)](#local-file-storage-default)
3. [S3-Compatible Object Storage](#s3-compatible-object-storage)
4. [MinIO for Local S3 Testing](#minio-for-local-s3-testing)
5. [Switching Between Backends](#switching-between-backends)
6. [Supported Providers](#supported-providers)
7. [File Size and Type Limits](#file-size-and-type-limits)

---

## Storage Architecture

The `MediaStorage` interface abstracts the underlying storage backend:

```java
public interface MediaStorage {
    String upload(String key, InputStream data, String contentType);
    InputStream download(String key);
    void delete(String key);
}
```

Two implementations exist:

| Implementation | Bean | Activation |
|---------------|------|-----------|
| `LocalFileMediaStorage` | `@Primary` | Always active (default) |
| `ObjectStorageMediaStorage` | `@ConditionalOnProperty("app.media.object-storage.enabled")` | `MEDIA_OBJECT_STORAGE_ENABLED=true` |

When `MEDIA_OBJECT_STORAGE_ENABLED=true`, `ObjectStorageMediaStorage` is registered and Spring uses it over `LocalFileMediaStorage`.

---

## Local File Storage (Default)

All uploads go to the directory specified by `MEDIA_STORAGE_DIR` (default: `./uploads`).

### Configuration

```bash
# .env
MEDIA_STORAGE_DIR=./uploads
MEDIA_MAX_FILE_SIZE=10485760       # 10 MB
MEDIA_ALLOWED_TYPES=image/jpeg,image/png,image/webp,application/pdf
```

### Docker default

In Docker Compose, the backend mounts `/tmp/hlm-uploads` which is always writable by the non-root `hlm` user (uid 1001):

```yaml
volumes:
  - /tmp/hlm-uploads:/tmp/hlm-uploads
```

Set in your `.env`:
```bash
MEDIA_STORAGE_DIR=/tmp/hlm-uploads
```

### File key format

Local files are stored as `{UUID}.{extension}`, e.g., `550e8400-e29b-41d4-a716-446655440000.jpg`.

---

## S3-Compatible Object Storage

### Required Environment Variables

```bash
MEDIA_OBJECT_STORAGE_ENABLED=true
MEDIA_OBJECT_STORAGE_ENDPOINT=https://your-s3-endpoint   # blank for AWS S3
MEDIA_OBJECT_STORAGE_REGION=eu-west-1
MEDIA_OBJECT_STORAGE_BUCKET=hlm-media
MEDIA_OBJECT_STORAGE_ACCESS_KEY=your-access-key
MEDIA_OBJECT_STORAGE_SECRET_KEY=your-secret-key
```

### AWS S3

Leave `MEDIA_OBJECT_STORAGE_ENDPOINT` blank. The AWS SDK v2 auto-resolves the regional endpoint from `MEDIA_OBJECT_STORAGE_REGION`.

```bash
MEDIA_OBJECT_STORAGE_ENABLED=true
MEDIA_OBJECT_STORAGE_ENDPOINT=
MEDIA_OBJECT_STORAGE_REGION=eu-west-1
MEDIA_OBJECT_STORAGE_BUCKET=hlm-media
MEDIA_OBJECT_STORAGE_ACCESS_KEY=AKIAIOSFODNN7EXAMPLE
MEDIA_OBJECT_STORAGE_SECRET_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
```

### OVH Object Storage

```bash
MEDIA_OBJECT_STORAGE_ENDPOINT=https://s3.gra.cloud.ovh.net
MEDIA_OBJECT_STORAGE_REGION=gra
```

### Scaleway Object Storage

```bash
MEDIA_OBJECT_STORAGE_ENDPOINT=https://s3.fr-par.scw.cloud
MEDIA_OBJECT_STORAGE_REGION=fr-par
```

### Hetzner Object Storage

```bash
MEDIA_OBJECT_STORAGE_ENDPOINT=https://fsn1.your-objectstorage.com
MEDIA_OBJECT_STORAGE_REGION=fsn1
```

### Cloudflare R2

```bash
MEDIA_OBJECT_STORAGE_ENDPOINT=https://{account-id}.r2.cloudflarestorage.com
MEDIA_OBJECT_STORAGE_REGION=auto
```

### Implementation Notes

`ObjectStorageMediaStorage` uses AWS SDK v2 `S3Client` with `forcePathStyle(true)` for compatibility with MinIO and other self-hosted providers that do not support virtual-hosted-style bucket URLs.

File keys are plain string identifiers (not `UUID` objects) because keys like `550e8400-e29b.jpg` are semantically strings, not UUID instances.

---

## MinIO for Local S3 Testing

The Docker Compose stack includes MinIO as a local S3-compatible storage backend. Use this to test object storage without a cloud account.

### Access the MinIO console

Navigate to `http://localhost:9001` and log in with:
- Username: `minioadmin`
- Password: `minioadmin`

### Create a bucket

Via the MinIO console UI:
1. Click "Create Bucket".
2. Enter bucket name: `hlm-media`.
3. Click "Create Bucket".

Or via MinIO CLI (mc):
```bash
docker exec hlm-minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker exec hlm-minio mc mb local/hlm-media
```

### Configure the backend to use MinIO

```bash
# .env
MEDIA_OBJECT_STORAGE_ENABLED=true
MEDIA_OBJECT_STORAGE_ENDPOINT=http://hlm-minio:9000   # Docker service name
MEDIA_OBJECT_STORAGE_REGION=us-east-1                 # MinIO ignores region but SDK requires a value
MEDIA_OBJECT_STORAGE_BUCKET=hlm-media
MEDIA_OBJECT_STORAGE_ACCESS_KEY=minioadmin
MEDIA_OBJECT_STORAGE_SECRET_KEY=minioadmin
```

When running the backend outside Docker (local dev), use `http://localhost:9000` instead of `http://hlm-minio:9000`.

---

## Switching Between Backends

The switch is a single environment variable change:

```bash
# Local filesystem (default)
MEDIA_OBJECT_STORAGE_ENABLED=false

# S3-compatible
MEDIA_OBJECT_STORAGE_ENABLED=true
# + MEDIA_OBJECT_STORAGE_* vars
```

Restart the backend after changing this setting. Existing files on the old backend will not be migrated automatically — they remain at their original location.

---

## Supported Providers

All providers are compatible with the AWS S3 API via the AWS SDK v2 S3 client:

| Provider | Endpoint Pattern |
|----------|----------------|
| AWS S3 | (auto-resolved from region, leave endpoint blank) |
| MinIO (self-hosted) | `http://your-minio-host:9000` |
| OVH Object Storage | `https://s3.{region}.cloud.ovh.net` |
| Scaleway Object Storage | `https://s3.{region}.scw.cloud` |
| Hetzner Object Storage | `https://{location}.your-objectstorage.com` |
| Cloudflare R2 | `https://{account-id}.r2.cloudflarestorage.com` |

---

## File Size and Type Limits

| Setting | Env Var | Default |
|---------|---------|---------|
| Maximum file size | `MEDIA_MAX_FILE_SIZE` | `10485760` (10 MB) |
| Allowed MIME types | `MEDIA_ALLOWED_TYPES` | `image/jpeg,image/png,image/webp,application/pdf` |

Uploads exceeding these limits return HTTP 400. Adjust in `.env` if larger files or additional types are needed.

Also configure Spring's multipart size limits to match:
```yaml
spring:
  servlet:
    multipart:
      max-file-size: ${MEDIA_MAX_FILE_SIZE:10MB}
      max-request-size: ${MEDIA_MAX_FILE_SIZE:10MB}
```
