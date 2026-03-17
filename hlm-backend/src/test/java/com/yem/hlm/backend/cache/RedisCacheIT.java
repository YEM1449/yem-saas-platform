package com.yem.hlm.backend.cache;

import com.yem.hlm.backend.auth.config.CacheConfig;
import com.yem.hlm.backend.auth.config.RedisCacheConfig;
import com.yem.hlm.backend.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the {@link RedisCacheConfig} produces a working {@link RedisCacheManager}
 * when {@code app.redis.enabled=true} and that it suppresses the Caffeine {@link CacheConfig}.
 *
 * <p>Uses a real Redis container via Testcontainers. The Postgres container from
 * {@link IntegrationTestBase} is reused for the full application context.
 */
@SpringBootTest
@Testcontainers
class RedisCacheIT extends IntegrationTestBase {

    private static final int REDIS_PORT = 6379;

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("app.redis.enabled",        () -> "true");
        registry.add("spring.data.redis.host",   REDIS::getHost);
        registry.add("spring.data.redis.port",   () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired CacheManager cacheManager;
    @Autowired RedisConnectionFactory connectionFactory;

    // =========================================================================
    // 1. CacheManager is RedisCacheManager (not Caffeine) when Redis is enabled
    // =========================================================================

    @Test
    void cacheManager_isRedisCacheManager() {
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
    }

    // =========================================================================
    // 2. All expected caches are registered in the Redis cache manager
    // =========================================================================

    @Test
    void allExpectedCaches_areRegistered() {
        var names = cacheManager.getCacheNames();
        assertThat(names).contains(
                CacheConfig.USER_SECURITY_CACHE,
                CacheConfig.COMMERCIAL_DASHBOARD_CACHE,
                CacheConfig.CASH_DASHBOARD_CACHE,
                CacheConfig.RECEIVABLES_DASHBOARD_CACHE
        );
    }

    // =========================================================================
    // 3. Redis connection is healthy — PING returns PONG
    // =========================================================================

    @Test
    void redisConnection_isHealthy() {
        try (var conn = connectionFactory.getConnection()) {
            String pong = conn.ping();
            assertThat(pong).isEqualToIgnoringCase("PONG");
        }
    }

    // =========================================================================
    // 4. Cache put/get round-trip via the userSecurity cache
    // =========================================================================

    @Test
    void userSecurityCache_putAndGet_roundTrips() {
        var cache = cacheManager.getCache(CacheConfig.USER_SECURITY_CACHE);
        assertThat(cache).isNotNull();

        cache.put("test-key", "test-value");
        var result = cache.get("test-key", String.class);
        assertThat(result).isEqualTo("test-value");

        cache.evict("test-key");
        assertThat(cache.get("test-key")).isNull();
    }
}
