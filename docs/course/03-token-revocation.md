# Module 03 — Token Revocation

## Learning Objectives

- Explain why standard JWTs cannot be revoked before expiry
- Describe the token version pattern used in this codebase
- Identify when token version is incremented

---

## The Problem with Stateless JWTs

Standard JWTs are stateless: once issued, they are valid until their `exp` claim passes. If an admin disables a compromised account or promotes an agent to manager, existing tokens for that user remain valid for up to `JWT_TTL_SECONDS` (default 1 hour).

---

## The Solution: Token Version

Every `app_user` row has an integer `token_version` column (added in changeset `027`). When a token is issued, the current `token_version` is embedded as the `tv` claim.

On every authenticated request, `JwtAuthenticationFilter` compares:
```
JWT tv claim  vs  DB token_version (via cache)
```

If they differ, the token is rejected as revoked.

---

## When Token Version is Incremented

| Action | Increments tv? |
|--------|---------------|
| `AdminUserService.setEnabled(false)` | Yes |
| `AdminUserService.setEnabled(true)` | Yes |
| `AdminUserService.changeRole(...)` | Yes |
| Password change | No (by design — old tokens still work after password change) |

---

## UserSecurityCacheService

Loading `token_version` from the database on every request would be expensive. `UserSecurityCacheService` caches `UserSecurityInfo(tokenVersion, enabled)` with a 60-second TTL.

```java
@Cacheable(value = "userSecurityCache", key = "#userId")
public UserSecurityInfo load(UUID userId) {
    AppUser user = userRepo.findById(userId).orElseThrow();
    return new UserSecurityInfo(user.getTokenVersion(), user.isEnabled());
}
```

Cache TTL = 60 seconds. Revocation propagates within ~60 seconds.

---

## Caffeine vs Redis

- **Single instance** → Caffeine is sufficient. In-memory, zero latency.
- **Multi-instance (load balanced)** → Redis required. Caffeine caches are per-JVM; two instances would have different cache states. If Instance A evicts the cache entry and re-fetches (sees `tv = 5`), Instance B might still serve from its local cache (sees `tv = 4`).

Set `REDIS_ENABLED=true` in production multi-instance deployments.

---

## Source Files

| File | Purpose |
|------|---------|
| `auth/service/UserSecurityCacheService.java` | Cache load and revocation check |
| `auth/service/UserSecurityInfo.java` | Record: tokenVersion + enabled |
| `user/service/AdminUserService.java` | Increments tokenVersion on setEnabled/changeRole |
| `auth/config/CacheConfig.java` | Caffeine cache registration |
| `auth/config/RedisCacheConfig.java` | Redis cache registration (opt-in) |
| `auth/security/JwtAuthenticationFilter.java` | Revocation check per request |

---

## Exercise

1. Open `AdminUserService.java` and find `setEnabled(UUID userId, boolean enabled)`.
2. Verify it calls `user.incrementTokenVersion()`.
3. Open `JwtAuthenticationFilter.java` and find the check `secInfo.tokenVersion() != tokenTv`.
4. Start the application locally, log in as admin, then disable yourself via `PUT /admin/users/{adminId}/enabled` with `{"enabled": false}`.
5. Try to use the old token — confirm it returns 401 within 60 seconds.
