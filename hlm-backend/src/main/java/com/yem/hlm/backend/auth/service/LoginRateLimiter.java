package com.yem.hlm.backend.auth.service;

import com.yem.hlm.backend.auth.config.LoginRateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Login-specific rate limiter with two independent token-bucket maps:
 * - IP bucket: limits total attempts per client IP address
 * - Identity bucket: limits attempts per tenantKey:email combination
 *
 * <p>A @Scheduled cleanup removes idle buckets every 5 minutes to prevent unbounded growth.</p>
 */
@Service
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private final LoginRateLimitProperties props;
    private final ConcurrentHashMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> keyBuckets = new ConcurrentHashMap<>();

    public LoginRateLimiter(LoginRateLimitProperties props) {
        this.props = props;
    }

    /**
     * Result of a rate-limit check.
     */
    public record RateLimitResult(boolean allowed, long remainingTokens, long waitSeconds) {}

    /**
     * Tries to consume one token from both the IP bucket and the identity bucket.
     *
     * @param ip        client IP (from X-Forwarded-For or RemoteAddr)
     * @param tenantKey the tenant key from the login request
     * @param email     the email from the login request
     * @return result indicating whether the request is allowed
     */
    public RateLimitResult tryConsume(String ip, String tenantKey, String email) {
        String ipKey  = "ip:" + ip;
        String idKey  = "id:" + tenantKey + ":" + email;

        Bucket ipBucket  = ipBuckets.computeIfAbsent(ipKey, k -> buildBucket(props.getIpMax(), props.getWindowSeconds()));
        Bucket idBucket  = keyBuckets.computeIfAbsent(idKey, k -> buildBucket(props.getKeyMax(), props.getWindowSeconds()));

        // Check IP bucket first
        ConsumptionProbe ipProbe = ipBucket.tryConsumeAndReturnRemaining(1);
        if (!ipProbe.isConsumed()) {
            long waitSecs = TimeUnit.NANOSECONDS.toSeconds(ipProbe.getNanosToWaitForRefill()) + 1;
            return new RateLimitResult(false, 0, waitSecs);
        }

        // Check identity bucket
        ConsumptionProbe idProbe = idBucket.tryConsumeAndReturnRemaining(1);
        if (!idProbe.isConsumed()) {
            long waitSecs = TimeUnit.NANOSECONDS.toSeconds(idProbe.getNanosToWaitForRefill()) + 1;
            return new RateLimitResult(false, 0, waitSecs);
        }

        long remaining = Math.min(ipProbe.getRemainingTokens(), idProbe.getRemainingTokens());
        return new RateLimitResult(true, remaining, 0);
    }

    /**
     * Cleanup idle buckets every 5 minutes to prevent unbounded memory growth.
     * Only runs when scheduling is enabled (disabled in test profile).
     */
    @Scheduled(fixedDelayString = "PT5M")
    public void cleanupIdleBuckets() {
        int ipRemoved  = cleanupMap(ipBuckets);
        int idRemoved  = cleanupMap(keyBuckets);
        if (ipRemoved + idRemoved > 0) {
            log.debug("LoginRateLimiter cleanup: removed {} IP buckets, {} identity buckets", ipRemoved, idRemoved);
        }
    }

    private int cleanupMap(ConcurrentHashMap<String, Bucket> map) {
        int removed = 0;
        for (var entry : map.entrySet()) {
            // Remove buckets that are effectively full (no recent activity)
            if (entry.getValue().getAvailableTokens() == entry.getValue().getAvailableTokens()) {
                // Prune entries where the bucket is at full capacity — means it hasn't been used recently
                long available = entry.getValue().getAvailableTokens();
                long capacity = isIpBucket(entry.getKey()) ? props.getIpMax() : props.getKeyMax();
                if (available >= capacity) {
                    map.remove(entry.getKey());
                    removed++;
                }
            }
        }
        return removed;
    }

    private boolean isIpBucket(String key) {
        return key.startsWith("ip:");
    }

    private static Bucket buildBucket(int capacity, int windowSeconds) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, Duration.ofSeconds(windowSeconds))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
