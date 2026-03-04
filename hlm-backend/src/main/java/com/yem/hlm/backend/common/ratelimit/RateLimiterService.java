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

    /** Login: 5 attempts per 15 minutes per email. */
    private static final int LOGIN_CAPACITY = 5;
    private static final Duration LOGIN_REFILL_PERIOD = Duration.ofMinutes(15);

    /** Portal magic-link request: 3 attempts per hour per email. */
    private static final int PORTAL_CAPACITY = 3;
    private static final Duration PORTAL_REFILL_PERIOD = Duration.ofHours(1);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Checks and consumes one login token for the given email.
     *
     * @throws RateLimitExceededException if the rate limit is exceeded
     */
    public void checkLogin(String email) {
        Bucket bucket = buckets.computeIfAbsent(
                "login:" + email,
                k -> buildBucket(LOGIN_CAPACITY, LOGIN_REFILL_PERIOD));
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException(
                    "Too many login attempts. Please try again in 15 minutes.");
        }
    }

    /**
     * Checks and consumes one portal magic-link token for the given email.
     *
     * @throws RateLimitExceededException if the rate limit is exceeded
     */
    public void checkPortalLink(String email) {
        Bucket bucket = buckets.computeIfAbsent(
                "portal:" + email,
                k -> buildBucket(PORTAL_CAPACITY, PORTAL_REFILL_PERIOD));
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException(
                    "Too many magic link requests. Please try again in 1 hour.");
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
