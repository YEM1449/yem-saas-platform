# Module 09 — Caching

## Learning Objectives

- Explain why the platform has two cache backends
- List the cache names, TTLs, and what each stores
- Describe when Redis is required vs when Caffeine is sufficient

---

## Why Two Backends?

**Caffeine** is an in-process cache (in the JVM heap). It has zero network overhead and is ideal for single-instance deployments.

**Redis** is an out-of-process distributed cache. It adds ~1–2 ms network latency per read but is shared across all JVM instances.

| Scenario | Caffeine | Redis |
|----------|---------|-------|
| Local dev / single instance | Yes | Not needed |
| Multi-instance (load balanced) | Inconsistent | Required |

The critical cache is `userSecurityCache` — it stores `tokenVersion` and `enabled` for JWT revocation. With Caffeine in a multi-instance setup, two instances might have different cache states, leading to a window where a revoked token is still accepted on one instance.

---

## Cache Names

| Cache Name | TTL | Max Size | Content |
|------------|-----|---------|---------|
| `userSecurityCache` | 60 s | 1000 | `UserSecurityInfo(tokenVersion, enabled)` keyed by `userId` |
| `commercialDashboard` | 30 s | 500 | `CommercialDashboardSummaryDTO` keyed by resolved params |
| `receivablesDashboard` | 30 s | 200 | `ReceivablesDashboardDTO` |

---

## Caffeine Configuration

`auth/config/CacheConfig.java` registers each cache name with its TTL and max size:

```java
@Bean
public CacheManager caffeineCacheManager() {
    SimpleCacheManager manager = new SimpleCacheManager();
    manager.setCaches(List.of(
        registerCustomCache("userSecurityCache", Duration.ofSeconds(60), 1000),
        registerCustomCache("commercialDashboard", Duration.ofSeconds(30), 500),
        registerCustomCache("receivablesDashboard", Duration.ofSeconds(30), 200)
    ));
    return manager;
}
```

---

## Redis Configuration

`auth/config/RedisCacheConfig.java` is activated by `@ConditionalOnProperty("app.redis.enabled")`:

```java
@Bean
@ConditionalOnProperty("app.redis.enabled")
public CacheManager redisCacheManager(RedisConnectionFactory factory) {
    // Registers same cache names with same TTLs but backed by Redis
    // Uses GenericJackson2JsonRedisSerializer for serialization
}
```

---

## Using @Cacheable

Services annotate methods with `@Cacheable`:

```java
// UserSecurityCacheService
@Cacheable(value = "userSecurityCache", key = "#userId")
public UserSecurityInfo load(UUID userId) {
    // Reads from DB; cached result is returned on subsequent calls
}

// CommercialDashboardService
@Cacheable(value = "commercialDashboard", key = "#tenantId + ':' + #agentId + ':' + #projectId")
public CommercialDashboardSummaryDTO getSummary(UUID tenantId, UUID agentId, UUID projectId, ...) {
    // ...
}
```

The `key` parameter uses Spring Expression Language (SpEL). Cache entries are stored and retrieved by this key.

---

## Enabling Redis

```bash
# .env
REDIS_ENABLED=true
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your-password
```

The Redis container is included in `docker-compose.yml` and is always started (even when `REDIS_ENABLED=false` — it just goes unused).

---

## Source Files

| File | Purpose |
|------|---------|
| `auth/config/CacheConfig.java` | Caffeine cache registration |
| `auth/config/RedisCacheConfig.java` | Redis cache registration (conditional) |
| `auth/service/UserSecurityCacheService.java` | Uses `userSecurityCache` |
| `dashboard/service/CommercialDashboardService.java` | Uses `commercialDashboard` |

---

## Exercise

1. Open `CacheConfig.java` and find the TTL for `userSecurityCache`.
2. Open `UserSecurityCacheService.java` and find the `@Cacheable` annotation.
3. Calculate: if a user is disabled at 10:00:00, what is the latest time a request with their old token could succeed? (Answer: 10:01:00 — the cache TTL is 60 s.)
4. Explain in one paragraph why you would NOT use Redis in a single-instance development environment.
