# Security Baseline

This document describes every security mechanism in the YEM SaaS Platform backend. All claims are derived from source code in `hlm-backend/src/main/java/com/yem/hlm/backend/`.

## Table of Contents

1. [JWT — Generation and Validation](#jwt--generation-and-validation)
2. [Token Revocation (UserSecurityCache)](#token-revocation-usersecuritycache)
3. [Filter Chain Order](#filter-chain-order)
4. [TenantContext Isolation](#tenantcontext-isolation)
5. [RBAC — Roles and @PreAuthorize Patterns](#rbac--roles-and-preauthorize-patterns)
6. [Rate Limiting](#rate-limiting)
7. [Account Lockout](#account-lockout)
8. [CORS Configuration](#cors-configuration)
9. [TLS / HTTPS](#tls--https)
10. [Security Headers](#security-headers)
11. [GDPR Erasure Protections](#gdpr-erasure-protections)
12. [Password Policy](#password-policy)

---

## JWT — Generation and Validation

**Class:** `JwtProvider` in `auth/service/JwtProvider.java`
**Config:** `JwtProperties` (`@ConfigurationProperties("security.jwt")`) backed by `@Validated @NotBlank` fields.

### Token claims

| Claim | Value | Example |
|-------|-------|---------|
| `sub` | User UUID (or contactId for portal) | `22222222-...` |
| `tid` | Tenant UUID | `11111111-...` |
| `roles` | JSON array of role names | `["ROLE_ADMIN"]` |
| `tv` | Token version (int) | `3` |
| `iat` | Issued-at epoch | `1710000000` |
| `exp` | Expiry epoch | `1710003600` |

**Algorithm:** `HS256` (HMAC-SHA256). The signing key is `JWT_SECRET` (minimum 32 characters, validated at startup by `@NotBlank`).

**TTL:** `JWT_TTL_SECONDS` (default: `3600` = 1 hour).

**Portal tokens** (`PortalJwtProvider`) share the same `JwtEncoder`/`JwtDecoder` beans. They use `roles: ["ROLE_PORTAL"]` and a 2-hour TTL. No `tv` claim.

### Validation in JwtAuthenticationFilter

1. Extract `Authorization: Bearer <token>` header.
2. Call `JwtProvider.isValid(token)` — Spring Security's `JwtDecoder` verifies signature and expiry.
3. Extract `tid` (UUID), `sub` (UUID), `roles`, `tv`.
4. For non-portal tokens: check `UserSecurityCacheService` for revocation.
5. Set `TenantContext` and `SecurityContextHolder`.
6. In `finally` block: call `TenantContext.clear()`.

---

## Token Revocation (UserSecurityCache)

**Problem:** JWTs are stateless — once issued they are valid until expiry. When an admin disables a user or changes their role, existing tokens must be invalidated immediately.

**Solution:** Each `app_user` row has a `token_version` integer column. When a user is disabled or their role changes, `token_version` is incremented (via `AdminUserService.setEnabled()` and `AdminUserService.changeRole()`). The JWT carries the `tv` claim capturing the version at issue time. On each request, `JwtAuthenticationFilter` loads `UserSecurityInfo` from the cache and compares `secInfo.tokenVersion() != tokenTv`. If they differ, the token is treated as revoked.

**Cache:** `userSecurityCache` (Caffeine or Redis), TTL 60 seconds. After expiry, the next request re-fetches from the database.

**Effect:** Token revocation propagates within ~60 seconds (cache TTL).

---

## Filter Chain Order

Defined in `SecurityConfig.securityFilterChain()`:

```
OPTIONS /**               → permitAll (CORS preflight)
POST /auth/login          → permitAll
GET  /actuator/health     → permitAll
GET  /actuator/info       → permitAll
GET  /v3/api-docs/**      → permitAll
GET  /swagger-ui/**       → permitAll
POST /tenants             → permitAll (bootstrap)
POST /api/portal/auth/request-link → permitAll
GET  /api/portal/auth/verify       → permitAll
/api/portal/**            → hasRole('PORTAL')
/api/**                   → hasAnyRole('ADMIN','MANAGER','AGENT')
anyRequest                → authenticated
```

Filters added before `UsernamePasswordAuthenticationFilter`:
1. `JwtAuthenticationFilter` — token parsing and TenantContext setup
2. `RequestCorrelationFilter` — correlation ID on MDC

---

## TenantContext Isolation

**Class:** `TenantContext` in `tenant/context/TenantContext.java`

`TenantContext` is a static utility class backed by two `ThreadLocal<UUID>` fields:
- `TENANT_ID` — the tenant UUID from the JWT `tid` claim
- `USER_ID` — the user UUID from the JWT `sub` claim

The filter populates them at request start; the `finally` block calls `TenantContext.clear()` to prevent thread pool leakage.

**Rule:** No service reads `tenantId` from the request body or URL parameters. All services call `TenantContext.getTenantId()` and include it in every JPA `WHERE` clause.

```java
// Correct pattern in every service:
UUID tenantId = TenantContext.getTenantId();
return contactRepo.findByTenantIdAndId(tenantId, contactId)
    .orElseThrow(ContactNotFoundException::new);
```

---

## RBAC — Roles and @PreAuthorize Patterns

### Roles

| Role | Defined in | Privileges |
|------|-----------|-----------|
| `ROLE_ADMIN` | `UserRole.ROLE_ADMIN` | Full CRUD on all resources including delete |
| `ROLE_MANAGER` | `UserRole.ROLE_MANAGER` | Create, Read, Update — no delete |
| `ROLE_AGENT` | `UserRole.ROLE_AGENT` | Read-only (list and get) |
| `ROLE_PORTAL` | JWT roles claim | Client portal read-only (own contracts and payments) |

### Convention

Spring Security adds the `ROLE_` prefix automatically in `hasRole()`. Code uses:
```java
@PreAuthorize("hasRole('ADMIN')")       // matches ROLE_ADMIN
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")  // matches either
```

### Permission Matrix

| Operation | ADMIN | MANAGER | AGENT | PORTAL |
|-----------|-------|---------|-------|--------|
| Login | Yes | Yes | Yes | Via magic link |
| Create user | Yes | No | No | No |
| Create property | Yes | Yes | No | No |
| Read property | Yes | Yes | Yes | Own (via portal) |
| Update property | Yes | Yes | No | No |
| Delete property | Yes | No | No | No |
| Create contact | Yes | Yes | No | No |
| Read contact | Yes | Yes | Yes | No |
| Create deposit | Yes | Yes | No | No |
| Confirm/Cancel deposit | Yes | Yes | No | No |
| Create reservation | Yes | Yes | No | No |
| Cancel reservation | Yes | Yes | No | No |
| Create contract | Yes | Yes | Yes (own agent) | No |
| Sign contract | Yes | Yes | No | No |
| Cancel contract | Yes | Yes | No | No |
| View dashboard | Yes (all) | Yes (all) | Yes (own agent) | No |
| Export GDPR data | Yes | Yes | No | No |
| Anonymize contact | Yes | No | No | No |
| Manage commission rules | Yes | No | No | No |
| View commissions | Yes (all) | Yes (all) | Yes (own) | No |
| Upload media | Yes | Yes | No | No |
| View audit log | Yes | Yes | No | No |
| Portal contracts | No | No | No | Yes (own) |
| Portal payments | No | No | No | Yes (own) |

---

## Rate Limiting

**Library:** Bucket4j 8.10.1 (`com.bucket4j:bucket4j-core`)
**Class:** `RateLimiterService` in `common/ratelimit/RateLimiterService.java`
**Login-specific:** `LoginRateLimiter` in `auth/service/LoginRateLimiter.java`

### Login rate limiting (two independent buckets)

Both buckets are checked before authenticating. If either is exhausted, a `LoginRateLimitedException` is thrown, which the global handler maps to HTTP 429 with a `Retry-After` header.

| Bucket | Key | Capacity | Refill | Config env vars |
|--------|-----|----------|--------|-----------------|
| IP bucket | Client IP address | `RATE_LIMIT_LOGIN_IP_MAX` (default: 20) | per `RATE_LIMIT_LOGIN_WINDOW_SECONDS` (default: 60s) | `RATE_LIMIT_LOGIN_IP_MAX`, `RATE_LIMIT_LOGIN_WINDOW_SECONDS` |
| Identity bucket | Email address | `RATE_LIMIT_LOGIN_KEY_MAX` (default: 10) | per `RATE_LIMIT_LOGIN_WINDOW_SECONDS` (default: 60s) | `RATE_LIMIT_LOGIN_KEY_MAX` |

### Portal magic link rate limiting

| Bucket | Key | Capacity | Refill |
|--------|-----|----------|--------|
| IP bucket | Client IP | `RATE_LIMIT_PORTAL_CAPACITY` (default: 3) | per `RATE_LIMIT_PORTAL_REFILL_PERIOD` (default: 1h) |

### General API rate limiting

`RateLimiterService` provides a general-purpose bucket used for specific operations (e.g., GDPR export). Keys are arbitrary strings. Capacity and refill period are configurable.

---

## Account Lockout

**Fields on `app_user`:** `failed_login_attempts int` and `locked_until timestamp` (added in changeset `027-add-login-lockout-fields`).

**Flow in `AuthService.login()`:**

1. If `user.locked_until` is set and is in the future → throw `AccountLockedException` (HTTP 401, `ErrorCode.ACCOUNT_LOCKED`).
2. If authentication succeeds → reset `failed_login_attempts = 0`, `locked_until = null`.
3. If authentication fails → increment `failed_login_attempts`.
4. If `failed_login_attempts >= LOCKOUT_MAX_ATTEMPTS` (default: 5) → set `locked_until = now + LOCKOUT_DURATION_MINUTES` (default: 15 minutes).

**Configuration:**

| Env var | Default | Purpose |
|---------|---------|---------|
| `LOCKOUT_MAX_ATTEMPTS` | `5` | Failed attempts before lockout |
| `LOCKOUT_DURATION_MINUTES` | `15` | How long the account is locked |

---

## CORS Configuration

**Class:** `CorsConfig` in `auth/security/CorsConfig.java`
**Config:** `app.cors.allowed-origins` property (env: `CORS_ALLOWED_ORIGINS`).

```yaml
app:
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:}   # empty = deny all
```

In `application-local.yml` (local dev profile):
```yaml
app:
  cors:
    allowed-origins: http://localhost:4200,http://127.0.0.1:4200,http://localhost:5173,http://localhost:3000
```

**Important:** Local dev must include both `http://localhost:4200` and `http://127.0.0.1:4200`. On Windows, browsers often send requests from `127.0.0.1` even when the URL shows `localhost`.

---

## TLS / HTTPS

Two modes are supported. See [guides/engineer/https-tls.md](../guides/engineer/https-tls.md) for full setup instructions.

### Mode A — Embedded Tomcat TLS

Set `SSL_ENABLED=true`. Spring Boot configures a PKCS12 keystore.

```yaml
server:
  ssl:
    enabled: ${SSL_ENABLED:false}
    key-store: ${SSL_KEYSTORE_PATH:classpath:ssl/hlm-dev.p12}
    key-store-type: PKCS12
    key-store-password: ${SSL_KEYSTORE_PASSWORD:changeit}
    key-alias: ${SSL_KEY_ALIAS:hlm-dev}
    enabled-protocols: TLSv1.2,TLSv1.3
```

When `SSL_ENABLED=true`, `SecurityConfig` enables HSTS:
```java
h.httpStrictTransportSecurity(hsts -> hsts
    .includeSubDomains(true)
    .maxAgeInSeconds(31536000)
    .preload(true));
```

`TlsRedirectConfig` adds a plain-HTTP connector on `HTTP_REDIRECT_PORT` that redirects to HTTPS.

### Mode B — Nginx TLS termination (production)

Keep `SSL_ENABLED=false`. Nginx handles TLS and proxies plain HTTP to Spring Boot. Set `FORWARD_HEADERS_STRATEGY=FRAMEWORK` so Spring trusts the `X-Forwarded-Proto: https` header.

---

## Security Headers

Set in `SecurityConfig` for every response:

```
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'
X-Frame-Options: DENY
```

HSTS is only emitted when `SSL_ENABLED=true` (see above).

CSRF protection is disabled (`csrf.disable()`) because the API is stateless JWT-based — no session cookies are used.

---

## GDPR Erasure Protections

When `DELETE /api/gdpr/contacts/{contactId}/anonymize` is called:

1. `GdprService.anonymizeContact()` checks if the contact has any `SIGNED` sale contracts.
2. If signed contracts exist → throw `GdprErasureBlockedException` (HTTP 409, `ErrorCode.GDPR_ERASURE_BLOCKED`).
3. If no signed contracts → `AnonymizationService` zeros all PII fields:
   - `full_name` → `"ANONYMIZED"`
   - `first_name`, `last_name` → null
   - `email` → `"anonymized-{id}@deleted.invalid"`
   - `phone` → null
   - Sets `anonymized_at = now()`
4. Prospect and client detail records are cleared.

This is anonymization (not hard delete) to preserve referential integrity for audit and financial records.

---

## Password Policy

**Annotation:** `@StrongPassword` backed by `StrongPasswordValidator`.

**Pattern:** `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z\d]).{12,}$`

Requirements:
- Minimum 12 characters
- At least one lowercase letter
- At least one uppercase letter
- At least one digit
- At least one special character

Applied to: `CreateUserRequest.password`, `ChangePasswordRequest.newPassword`.
