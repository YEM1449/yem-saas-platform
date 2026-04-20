# Module 09: Caching

## Why This Matters

Caching improves responsiveness, but it must not undermine correctness.

## Learning Goals

- understand what is cached today
- understand when Redis replaces Caffeine
- understand why revocation caches need careful invalidation

## Current Cache Use

Examples:

- user security metadata for token checks
- dashboard summaries
- project and societe lookups

## Files To Study

- [../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/config/CacheConfig.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/config/CacheConfig.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/config/RedisCacheConfig.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/config/RedisCacheConfig.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/UserSecurityCacheService.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/UserSecurityCacheService.java)

## Design Trade-Off

- Caffeine is simple and fast for single-node or local dev
- Redis is better when multiple app instances must share revocation and cache state

## Exercise

Explain why user security caching is a safer candidate than caching raw mutable business objects without strong invalidation rules.
