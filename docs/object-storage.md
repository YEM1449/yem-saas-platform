# Object Storage Guide

This guide covers every aspect of configuring media storage for property files (photos, PDFs).
All configuration, provider-specific setup, and migration steps are documented here.

---

## 1. Overview

The platform stores property media files using the **S3 object storage protocol** — an open standard
for object storage, not an AWS-specific product. Any provider that implements this protocol
(OVH, Scaleway, Hetzner, Cloudflare R2, MinIO, AWS S3) works with zero code changes.

Two implementations are available:

| Mode | Class | When Active |
|---|---|---|
| Local filesystem | `LocalFileMediaStorage` | `MEDIA_OBJECT_STORAGE_ENABLED=false` (default) |
| S3-compatible object storage | `ObjectStorageMediaStorage` | `MEDIA_OBJECT_STORAGE_ENABLED=true` |

**Use local filesystem for development.** Use object storage for any deployment where:
- multiple backend instances share the same media (horizontal scaling)
- media must persist across container restarts without a mounted volume
- you need a managed, durable storage solution

---

## 2. How It Works

`ObjectStorageMediaStorage` implements the `MediaStorageService` interface with three operations:

| Method | Operation | S3 API |
|---|---|---|
| `store(bytes, filename, contentType)` | Upload file, return storage key | `PutObject` |
| `load(key)` | Download file as `InputStream` | `GetObject` |
| `delete(key)` | Remove file (idempotent) | `DeleteObject` |

**Storage key format:** `{UUID}.{extension}` — a random UUID ensures no collisions across tenants.

**Path-style addressing (`pathStyleAccessEnabled=true`)** is always enabled in the `S3Client`. This is
**required by every non-AWS provider** and harmless on AWS. Without it, the SDK constructs URLs like
`https://<bucket>.s3.<region>.amazonaws.com/<key>`, which breaks on all non-AWS endpoints (OVH,
Scaleway, MinIO, etc.). Path-style constructs `https://<endpoint>/<bucket>/<key>`, which all providers
support.

**Conditional activation:** `@ConditionalOnProperty(name = "app.media.object-storage.enabled", havingValue = "true") @Primary`
means `ObjectStorageMediaStorage` is instantiated only when explicitly enabled, and takes priority
over `LocalFileMediaStorage` when active.

**Fail-fast startup:** the `S3Client` is built in the constructor (not lazily). If credentials or
the endpoint are wrong, the application fails to start immediately instead of failing silently at
the first upload.

**Bucket auto-creation:** on startup, the service calls `HeadBucket`. If the bucket does not exist
(`NoSuchBucketException`), it calls `CreateBucket`. No manual bucket creation is needed when using MinIO.
For cloud providers, create the bucket via their control panel before deploying.

---

## 3. Supported Providers

| Provider | Endpoint format | Region format | Notes |
|---|---|---|---|
| **OVH Object Storage** (Gravelines) | `https://s3.gra.perf.cloud.ovh.net` | `gra` | EU-West data sovereignty |
| **OVH Object Storage** (Strasbourg) | `https://s3.sbg.perf.cloud.ovh.net` | `sbg` | EU-West data sovereignty |
| **OVH Object Storage** (BHS) | `https://s3.bhs.io.cloud.ovh.net` | `bhs` | Canada |
| **Scaleway Object Storage** (Paris) | `https://s3.fr-par.scw.cloud` | `fr-par` | EU data sovereignty |
| **Scaleway Object Storage** (Amsterdam) | `https://s3.nl-ams.scw.cloud` | `nl-ams` | EU |
| **Hetzner Object Storage** (Falkenstein) | `https://fsn1.your-objectstorage.com` | `fsn1` | EU |
| **Hetzner Object Storage** (Nuremberg) | `https://nbg1.your-objectstorage.com` | `nbg1` | EU |
| **Cloudflare R2** | `https://<accountid>.r2.cloudflarestorage.com` | `auto` | Global, no egress fees |
| **MinIO** (self-hosted / Docker) | `http://minio:9000` or `http://localhost:9000` | `us-east-1` (any value works) | Local dev / self-hosted |
| **AWS S3** | *(leave blank — SDK auto-resolves)* | `eu-west-1`, `eu-west-3`, etc. | Set blank endpoint |

---

## 4. Configuration Reference

All configuration is under `app.media.object-storage.*` in `application.yml`, driven by these env vars:

| Env Variable | YAML Key | Default | Required | Description |
|---|---|---|---|---|
| `MEDIA_OBJECT_STORAGE_ENABLED` | `enabled` | `false` | — | `true` activates object storage; `false` uses local filesystem |
| `MEDIA_OBJECT_STORAGE_ENDPOINT` | `endpoint` | *(blank)* | No | Provider URL. **Leave blank for AWS S3** (SDK auto-resolves). Set for all other providers. |
| `MEDIA_OBJECT_STORAGE_REGION` | `region` | `eu-west-1` | Yes | Region code accepted by your provider (e.g. `gra`, `fr-par`, `eu-west-1`) |
| `MEDIA_OBJECT_STORAGE_BUCKET` | `bucket` | `hlm-media` | Yes | Bucket or container name to use for media uploads |
| `MEDIA_OBJECT_STORAGE_ACCESS_KEY` | `access-key` | *(blank)* | Yes | S3 access key ID from your provider |
| `MEDIA_OBJECT_STORAGE_SECRET_KEY` | `secret-key` | *(blank)* | Yes | S3 secret access key from your provider |

---

## 5. Local Development with MinIO

MinIO is included in `docker-compose.yml` as a local S3-compatible server with no external dependencies.

```bash
# Start only the MinIO service
docker compose up -d minio

# MinIO console (create buckets, browse files, manage credentials)
# http://localhost:9001
# Default credentials: minioadmin / minioadmin (MINIO_ROOT_USER / MINIO_ROOT_PASSWORD in .env)
```

The `docker-compose.yml` automatically wires `MEDIA_OBJECT_STORAGE_ENDPOINT=http://minio:9000` for the
backend container — no extra configuration needed when running the full stack.

To test object storage locally without Docker Compose, start the backend with:

```env
MEDIA_OBJECT_STORAGE_ENABLED=true
MEDIA_OBJECT_STORAGE_ENDPOINT=http://localhost:9000
MEDIA_OBJECT_STORAGE_REGION=us-east-1
MEDIA_OBJECT_STORAGE_BUCKET=hlm-media
MEDIA_OBJECT_STORAGE_ACCESS_KEY=minioadmin
MEDIA_OBJECT_STORAGE_SECRET_KEY=minioadmin
```

The bucket `hlm-media` is created automatically on backend startup if it does not exist.

---

## 6. OVH Object Storage (step-by-step)

OVH Object Storage is recommended for Moroccan/French deployments — data stays in EU, competitive pricing,
and Moroccan CNDP data sovereignty requirements are met.

### 6.1 Create a container

1. Log in to [OVH Control Panel](https://www.ovh.com/manager/) → Cloud → Object Storage → Create a container.
2. Choose region: **Gravelines (GRA)** for France/Morocco (lowest latency), or **Strasbourg (SBG)**.
3. Set container access: **Private** (the backend authenticates with S3 keys).
4. Name the container: `hlm-media` (or your preferred name).

### 6.2 Generate S3 credentials

1. OVH Control Panel → Identity/IAM → Service Accounts → Create service account.
2. Assign role: **Object Storage Operator** (read + write + delete on object storage).
3. Generate S3 credentials: go to the service account → S3 Credentials → Generate.
4. Copy the **Access Key** and **Secret Key** — the secret is shown only once.

### 6.3 Configure the backend

**Gravelines (GRA) region:**
```env
MEDIA_OBJECT_STORAGE_ENABLED=true
MEDIA_OBJECT_STORAGE_ENDPOINT=https://s3.gra.perf.cloud.ovh.net
MEDIA_OBJECT_STORAGE_REGION=gra
MEDIA_OBJECT_STORAGE_BUCKET=hlm-media
MEDIA_OBJECT_STORAGE_ACCESS_KEY=<your-ovh-access-key>
MEDIA_OBJECT_STORAGE_SECRET_KEY=<your-ovh-secret-key>
```

**Strasbourg (SBG) region:**
```env
MEDIA_OBJECT_STORAGE_ENABLED=true
MEDIA_OBJECT_STORAGE_ENDPOINT=https://s3.sbg.perf.cloud.ovh.net
MEDIA_OBJECT_STORAGE_REGION=sbg
MEDIA_OBJECT_STORAGE_BUCKET=hlm-media
MEDIA_OBJECT_STORAGE_ACCESS_KEY=<your-ovh-access-key>
MEDIA_OBJECT_STORAGE_SECRET_KEY=<your-ovh-secret-key>
```

> Note: OVH requires path-style addressing — already always enabled in our implementation.

---

## 7. Scaleway Object Storage (step-by-step)

### 7.1 Create a bucket

1. Log in to [Scaleway Console](https://console.scaleway.com/) → Object Storage → Create a bucket.
2. Choose region: **Paris (fr-par)** for EU data residency.
3. Set visibility: **Private**.
4. Name: `hlm-media`.

### 7.2 Generate API keys

1. Scaleway Console → Organization → API Keys → Generate new API key.
2. Scope: select **Object Storage** project access.
3. Copy the **Access Key ID** and **Secret Key**.

### 7.3 Configure the backend

```env
MEDIA_OBJECT_STORAGE_ENABLED=true
MEDIA_OBJECT_STORAGE_ENDPOINT=https://s3.fr-par.scw.cloud
MEDIA_OBJECT_STORAGE_REGION=fr-par
MEDIA_OBJECT_STORAGE_BUCKET=hlm-media
MEDIA_OBJECT_STORAGE_ACCESS_KEY=<your-scaleway-access-key>
MEDIA_OBJECT_STORAGE_SECRET_KEY=<your-scaleway-secret-key>
```

---

## 8. Hetzner Object Storage (step-by-step)

### 8.1 Enable Object Storage

1. Log in to [Hetzner Cloud Console](https://console.hetzner.cloud/) → your project → Object Storage.
2. Create a bucket in region **Falkenstein (fsn1)** or **Nuremberg (nbg1)**.
3. Name: `hlm-media`.

### 8.2 Generate access keys

1. Project → Security → S3 Credentials → Generate credentials.
2. Copy the **Access Key** and **Secret Key**.

### 8.3 Configure the backend

**Falkenstein (fsn1):**
```env
MEDIA_OBJECT_STORAGE_ENABLED=true
MEDIA_OBJECT_STORAGE_ENDPOINT=https://fsn1.your-objectstorage.com
MEDIA_OBJECT_STORAGE_REGION=fsn1
MEDIA_OBJECT_STORAGE_BUCKET=hlm-media
MEDIA_OBJECT_STORAGE_ACCESS_KEY=<your-hetzner-access-key>
MEDIA_OBJECT_STORAGE_SECRET_KEY=<your-hetzner-secret-key>
```

---

## 9. AWS S3 (for completeness)

AWS S3 is supported but not recommended for Moroccan/French deployments due to data sovereignty
considerations (OVH Paris/Gravelines is preferred).

For AWS S3, leave `MEDIA_OBJECT_STORAGE_ENDPOINT` blank — the SDK auto-resolves the AWS endpoint:

```env
MEDIA_OBJECT_STORAGE_ENABLED=true
MEDIA_OBJECT_STORAGE_ENDPOINT=
MEDIA_OBJECT_STORAGE_REGION=eu-west-3
MEDIA_OBJECT_STORAGE_BUCKET=hlm-media
MEDIA_OBJECT_STORAGE_ACCESS_KEY=<aws-access-key-id>
MEDIA_OBJECT_STORAGE_SECRET_KEY=<aws-secret-access-key>
```

Create the S3 bucket in the AWS console before deploying. Bucket auto-creation requires
`s3:CreateBucket` permission. Alternatively, create the bucket manually and ensure the IAM
policy grants `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`, `s3:HeadBucket`.

---

## 10. Migrating from LocalFileMediaStorage to ObjectStorageMediaStorage

Use the AWS CLI (also compatible with OVH, Scaleway, Hetzner, and MinIO via `--endpoint-url`):

```bash
# Upload existing local files to OVH Gravelines
aws s3 sync ./uploads s3://hlm-media \
  --endpoint-url https://s3.gra.perf.cloud.ovh.net \
  --region gra

# Or to MinIO (local or self-hosted)
aws s3 sync ./uploads s3://hlm-media \
  --endpoint-url http://localhost:9000 \
  --region us-east-1
```

After syncing:
1. Set `MEDIA_OBJECT_STORAGE_ENABLED=true` and the provider env vars in your production `.env`.
2. Restart the backend — new uploads go to object storage immediately.
3. Verify a few files load correctly from the frontend.
4. Archive the `./uploads` directory — do not delete it until you have confirmed all files are accessible.

---

## 11. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `SdkClientException: Unable to execute HTTP request` | Endpoint URL is wrong or unreachable | Check `MEDIA_OBJECT_STORAGE_ENDPOINT` — use `curl <endpoint>` to test connectivity |
| `NoSuchBucketException` | Bucket name wrong or not created yet | Verify bucket name in `MEDIA_OBJECT_STORAGE_BUCKET`. For cloud providers, create it in the control panel first |
| `SignatureDoesNotMatch` | Access key / secret key wrong | Re-generate S3 credentials on your provider's console |
| `403 Forbidden` | Credentials lack write permission on the bucket | Check IAM / service account permissions — the key needs read + write + delete |
| Files upload but download returns 404 | Path-style addressing was disabled | This is fixed in our implementation — `pathStyleAccessEnabled=true` is always set in `ObjectStorageMediaStorage` |
| Application fails to start with credential error | Credentials rejected at startup | `ObjectStorageMediaStorage` builds the client eagerly — check access key, secret key, and endpoint. Look for `[OBJ-STORE]` log lines |
| `NoSuchKeyException` on download | File was deleted or key is wrong | The key returned by `store()` must be persisted in the database via `PropertyMedia` entity |
