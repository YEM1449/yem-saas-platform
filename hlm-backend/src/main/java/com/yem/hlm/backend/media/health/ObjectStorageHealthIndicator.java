package com.yem.hlm.backend.media.health;

import com.yem.hlm.backend.media.service.ObjectStorageMediaStorage;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Health indicator for the object-storage (Cloudflare R2 / S3) backend (EX-016).
 *
 * <p>Without it, {@code /actuator/health} stays UP while R2 is unreachable — and since every
 * document, 3D-model and upload flow depends on R2, the first signal of an outage would be an angry
 * user call. This contributes to the aggregate {@code /actuator/health} (so monitoring/alerting can
 * see a DOWN), but it is <b>not</b> part of the liveness/readiness probe groups, so an R2 blip does
 * not restart or de-route the container — it only flags degraded state.
 *
 * <p>Only registered when object storage is actually configured
 * ({@code app.media.object-storage.enabled=true}); local-disk deployments have no external storage
 * dependency to probe. The result is cached for {@link #TTL_MS} so frequent health probes do not
 * translate into a HEAD request to R2 on every poll.
 */
@Component
@ConditionalOnProperty(name = "app.media.object-storage.enabled", havingValue = "true")
public class ObjectStorageHealthIndicator implements HealthIndicator {

    /** Cache window: avoid hammering R2 when the health endpoint is polled frequently. */
    private static final long TTL_MS = 20_000;

    private final ObjectStorageMediaStorage storage;

    private volatile long lastCheckAt = 0L;
    private volatile Health cached = Health.unknown().build();

    public ObjectStorageHealthIndicator(ObjectStorageMediaStorage storage) {
        this.storage = storage;
    }

    @Override
    public Health health() {
        long now = System.currentTimeMillis();
        if (lastCheckAt != 0L && now - lastCheckAt < TTL_MS) {
            return cached;
        }
        Health result;
        try {
            storage.verifyBucketReachable();
            result = Health.up()
                    .withDetail("backend", "object-storage")
                    .withDetail("bucket", storage.bucketName())
                    .build();
        } catch (Exception e) {
            result = Health.down()
                    .withDetail("backend", "object-storage")
                    .withDetail("bucket", storage.bucketName())
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
        cached = result;
        lastCheckAt = now;
        return result;
    }
}
