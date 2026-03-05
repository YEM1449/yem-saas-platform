package com.yem.hlm.backend.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiter using Bucket4j token buckets.
 *
 * <p>Keyed by operation + email so each account gets its own bucket.
 * Single-instance only — sufficient for MVP; replace with distributed
 * storage (Redis) when horizontal scaling is needed.
 */
@Service
public class RateLimiterService {

    private final RateLimitProperties properties;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiterService(RateLimitProperties properties) {
        this.properties = properties;
    }

    /**
     * Checks and consumes one login token for the given email.
     *
     * @throws RateLimitExceededException if the rate limit is exceeded
     */
    public void checkLogin(String email) {
        RateLimitProperties.Limit config = properties.getLogin();
        Bucket bucket = buckets.computeIfAbsent(
                "login:" + email,
                k -> buildBucket(config.getCapacity(), config.getRefillPeriod()));
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException(config.getExceededMessage());
        }
    }

    /**
     * Checks and consumes one portal magic-link token for the given email.
     *
     * @throws RateLimitExceededException if the rate limit is exceeded
     */
    public void checkPortalLink(String email) {
        RateLimitProperties.Limit config = properties.getPortalLink();
        Bucket bucket = buckets.computeIfAbsent(
                "portal:" + email,
                k -> buildBucket(config.getCapacity(), config.getRefillPeriod()));
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException(config.getExceededMessage());
        }
    }

    private static Bucket buildBucket(int capacity, Duration refillPeriod) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, refillPeriod)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
