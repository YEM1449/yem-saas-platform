# Module 17 — Object Storage

## Learning Objectives

- Describe the `MediaStorage` interface and its two implementations
- Explain the activation condition for each backend
- Configure MinIO for local S3 testing

---

## MediaStorage Interface

```java
public interface MediaStorage {
    String upload(String key, InputStream data, String contentType);
    InputStream download(String key);
    void delete(String key);
}
```

Two implementations:

| Class | Activation | Storage |
|-------|-----------|---------|
| `LocalFileMediaStorage` | `@Primary` (always) | Local filesystem |
| `ObjectStorageMediaStorage` | `@ConditionalOnProperty("app.media.object-storage.enabled")` | S3-compatible API |

---

## LocalFileMediaStorage

Writes files to `MEDIA_STORAGE_DIR` (default `./uploads`):

```java
public String upload(String key, InputStream data, String contentType) {
    Path target = Paths.get(storageDir).resolve(key);
    Files.createDirectories(target.getParent());
    Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
    return key;
}
```

File keys are UUID strings like `550e8400-e29b-41d4-a716.jpg`.

In Docker, the backend writes to `/tmp/hlm-uploads` (mounted volume, writable by uid 1001).

---

## ObjectStorageMediaStorage

Uses AWS SDK v2 `S3Client`:

```java
public String upload(String key, InputStream data, String contentType) {
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .build(),
        RequestBody.fromInputStream(data, data.available())
    );
    return key;
}
```

**Important:** File keys are `String` type, not `UUID`. This allows storage identifiers like `550e8400-e29b.jpg` which are semantically strings, not UUID objects.

The S3 client is configured with `forcePathStyle(true)` for MinIO / self-hosted compatibility:
```java
S3Client.builder()
    .endpointOverride(URI.create(endpoint))
    .forcePathStyle(true)
    .build()
```

---

## Switching Backends

```bash
# Local filesystem (default)
MEDIA_OBJECT_STORAGE_ENABLED=false

# S3-compatible
MEDIA_OBJECT_STORAGE_ENABLED=true
MEDIA_OBJECT_STORAGE_ENDPOINT=http://hlm-minio:9000
MEDIA_OBJECT_STORAGE_REGION=us-east-1
MEDIA_OBJECT_STORAGE_BUCKET=hlm-media
MEDIA_OBJECT_STORAGE_ACCESS_KEY=minioadmin
MEDIA_OBJECT_STORAGE_SECRET_KEY=minioadmin
```

---

## The @ConditionalOnProperty Gotcha

If you used `@ConditionalOnProperty` for the S3 backend:

```java
// WRONG — treats empty string "" as "present":
@ConditionalOnProperty("app.media.object-storage.enabled")
```

This would activate `ObjectStorageMediaStorage` even when the env var is blank (because `${MEDIA_OBJECT_STORAGE_ENABLED:}` defaults to `""`).

The correct pattern already used in this codebase:
```java
@ConditionalOnProperty(name = "app.media.object-storage.enabled", havingValue = "true")
```

Or for the SMTP example where the property defaults to empty string:
```java
@ConditionalOnExpression("!'${app.email.host:}'.isBlank()")
```

---

## Source Files

| File | Purpose |
|------|---------|
| `media/service/MediaStorage.java` | Interface |
| `media/service/LocalFileMediaStorage.java` | Filesystem implementation |
| `media/service/ObjectStorageMediaStorage.java` | S3 implementation |
| `media/config/MediaStorageConfig.java` | S3Client bean |

---

## Exercise

1. Start the Docker stack: `docker compose up -d`.
2. Access MinIO console at `http://localhost:9001` (minioadmin / minioadmin).
3. Create a bucket named `hlm-media`.
4. Add `MEDIA_OBJECT_STORAGE_ENABLED=true` and MinIO config to `.env`.
5. Upload a JPEG via `POST /api/properties/{id}/media`.
6. Verify the file appears in the MinIO console under `hlm-media`.
7. Download it via `GET /api/properties/{id}/media/{mediaId}` and confirm it opens.
