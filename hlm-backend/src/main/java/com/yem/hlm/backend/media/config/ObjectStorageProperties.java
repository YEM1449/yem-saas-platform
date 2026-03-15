package com.yem.hlm.backend.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for S3-compatible object storage.
 *
 * <p>Bound from {@code app.media.object-storage.*} in {@code application.yml}.
 * The bean is only loaded when the property is present; validation annotations
 * are therefore only enforced when the feature is active.
 *
 * <h3>Provider endpoint examples</h3>
 * <pre>
 * OVH (Gravelines):          https://s3.gra.perf.cloud.ovh.net
 * OVH (Strasbourg):          https://s3.sbg.perf.cloud.ovh.net
 * Scaleway (Paris):          https://s3.fr-par.scw.cloud
 * Hetzner (Falkenstein):     https://fsn1.your-objectstorage.com
 * Cloudflare R2:             https://&lt;accountid&gt;.r2.cloudflarestorage.com
 * MinIO (self-hosted):       http://minio:9000
 * AWS S3:                    leave endpoint blank — SDK auto-resolves
 * </pre>
 */
@Validated
@ConfigurationProperties("app.media.object-storage")
public record ObjectStorageProperties(
        boolean enabled,
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey
) {
    public ObjectStorageProperties {
        if (endpoint == null) endpoint = "";
        if (region == null || region.isBlank()) region = "eu-west-1";
        if (bucket == null || bucket.isBlank()) bucket = "hlm-media";
        if (accessKey == null) accessKey = "";
        if (secretKey == null) secretKey = "";
    }
}
