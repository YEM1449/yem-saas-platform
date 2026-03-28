package com.yem.hlm.backend.media.service;

import com.yem.hlm.backend.media.config.ObjectStorageProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

/**
 * S3-compatible object storage implementation of {@link MediaStorageService}.
 *
 * <p>Works with any provider that implements the S3 object storage protocol,
 * including OVH Object Storage, Scaleway Object Storage, Hetzner Object Storage,
 * Cloudflare R2, MinIO (self-hosted), and AWS S3.
 *
 * <p>Activated when {@code app.media.object-storage.enabled=true}.
 * Falls back to {@link LocalFileMediaStorage} when disabled.
 *
 * <h3>Configuration keys</h3>
 * <pre>
 * app.media.object-storage.enabled      (env: MEDIA_OBJECT_STORAGE_ENABLED)
 * app.media.object-storage.endpoint     (env: MEDIA_OBJECT_STORAGE_ENDPOINT)
 * app.media.object-storage.region       (env: MEDIA_OBJECT_STORAGE_REGION)
 * app.media.object-storage.bucket       (env: MEDIA_OBJECT_STORAGE_BUCKET)
 * app.media.object-storage.access-key   (env: MEDIA_OBJECT_STORAGE_ACCESS_KEY)
 * app.media.object-storage.secret-key   (env: MEDIA_OBJECT_STORAGE_SECRET_KEY)
 * </pre>
 *
 * <h3>Provider endpoint examples</h3>
 * <pre>
 * OVH (Gravelines):              https://s3.gra.perf.cloud.ovh.net
 * OVH (Strasbourg):              https://s3.sbg.perf.cloud.ovh.net
 * Scaleway (Paris):              https://s3.fr-par.scw.cloud
 * Hetzner (Falkenstein):         https://fsn1.your-objectstorage.com
 * Cloudflare R2:                 https://&lt;accountid&gt;.r2.cloudflarestorage.com
 * MinIO (local / docker):        http://minio:9000  or  http://localhost:9000
 * AWS S3:                        leave endpoint blank — SDK auto-resolves
 * </pre>
 */
@Service
@Primary
@ConditionalOnProperty(name = "app.media.object-storage.enabled", havingValue = "true")
public class ObjectStorageMediaStorage implements MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageMediaStorage.class);

    private final String bucket;
    private final S3Client s3Client;

    /**
     * Builds the S3Client eagerly at construction time so the application fails fast
     * on startup if credentials or endpoint are misconfigured.
     */
    public ObjectStorageMediaStorage(ObjectStorageProperties props) {
        this.bucket = props.bucket();

        // ── THE CRITICAL FIX: path-style addressing ───────────────────────────
        // pathStyleAccessEnabled(true) is REQUIRED by every non-AWS provider
        // (OVH, Scaleway, Hetzner, MinIO, Cloudflare R2, ...) and is harmless on AWS.
        // Virtual-hosted-style (the SDK default) constructs URLs like:
        //   https://<bucket>.s3.<region>.amazonaws.com/<key>
        // which breaks on all non-AWS endpoints.
        // Path-style constructs:
        //   https://<endpoint>/<bucket>/<key>
        // which all S3-compatible providers support.
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)   // ← NEVER REMOVE — required by all non-AWS providers
                .build();

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .serviceConfiguration(s3Config);

        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.endpoint()));
        }

        this.s3Client = builder.build();

        ensureBucketExists();
        log.info("[OBJ-STORE] Ready — endpoint={} bucket={}",
                (props.endpoint() == null || props.endpoint().isBlank()) ? "AWS S3" : props.endpoint(),
                bucket);
    }

    // ── store ─────────────────────────────────────────────────────────────────

    @Override
    public String store(byte[] data, String originalFilename, String contentType) throws IOException {
        String ext = extractExtension(originalFilename);
        String key = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) data.length)
                            .build(),
                    RequestBody.fromBytes(data));
        } catch (S3Exception e) {
            throw new IOException("Object storage upload failed: " + e.getMessage(), e);
        }

        log.debug("[OBJ-STORE] Stored key={} size={} bytes", key, data.length);
        return key;
    }

    // ── load ──────────────────────────────────────────────────────────────────

    @Override
    public InputStream load(String fileKey) throws IOException {
        try {
            // ResponseInputStream<GetObjectResponse> IS an InputStream — returned directly
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileKey)
                    .build());
        } catch (software.amazon.awssdk.services.s3.model.NoSuchKeyException e) {
            throw new MediaNotFoundException(fileKey);
        } catch (S3Exception e) {
            throw new IOException("Object storage download failed: " + e.getMessage(), e);
        }
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Override
    public void delete(String fileKey) throws IOException {
        try {
            // DeleteObject is idempotent — succeeds even if the key does not exist
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileKey)
                    .build());
        } catch (S3Exception e) {
            throw new IOException("Object storage delete failed: " + e.getMessage(), e);
        }

        log.debug("[OBJ-STORE] Deleted key={}", fileKey);
    }

    // ── shutdown ──────────────────────────────────────────────────────────────

    @PreDestroy
    void shutdown() {
        if (s3Client != null) {
            s3Client.close();
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("[OBJ-STORE] Created bucket '{}'", bucket);
        } catch (S3Exception e) {
            if (e.statusCode() == 403) {
                // Cloudflare R2 (and some other providers) return 403 on headBucket
                // when the API token has object-level permissions only (no ListBuckets/
                // HeadBucket right). The bucket is assumed to already exist — if the
                // credentials are genuinely wrong, the first store() call will fail.
                log.warn("[OBJ-STORE] headBucket returned 403 for bucket '{}' — "
                        + "assuming it exists (R2 object-scoped token?). "
                        + "Auth errors will surface on first upload.", bucket);
            } else {
                throw e;
            }
        }
    }

    private static String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1)
                ? filename.substring(dot + 1).toLowerCase()
                : "";
    }
}
