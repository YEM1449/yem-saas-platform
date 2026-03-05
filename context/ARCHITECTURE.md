# ARCHITECTURE.md — Compact Architecture Reference

_Updated: 2026-03-05_

## Layer Stack
```
Browser → Angular 19 SPA (hlm-frontend:4200)
              ↕ dev proxy (proxy.conf.json)
        Spring Boot API (hlm-backend:8080)
              ↕ JDBC
        PostgreSQL (schema via Liquibase)
              ↕ SMTP/SMS
        External providers (EmailSender / SmsSender)
```

## Request Pipeline
```
HTTP Request
  → JwtAuthenticationFilter (OncePerRequestFilter)
    → decode JWT → extract sub, tid, roles
    → if ROLE_PORTAL: principal = contactId (skip user cache)
    → else: validate tokenVersion via UserSecurityCacheService
    → set TenantContext (ThreadLocal: tenantId, userId)
    → set Spring Security Authentication
  → SecurityConfig route authorization
  → @PreAuthorize method security
  → Controller (DTO only)
  → Service (reads TenantContext for tenant scoping)
  → Repository (tenant_id in every query)
  → (finally) TenantContext.clear()
```

## Security Config Route Order (important — first match wins)
```
/auth/**                   → permitAll
/api/portal/auth/**        → permitAll
/api/portal/**             → hasRole("PORTAL")
/api/**                    → hasAnyRole("ADMIN","MANAGER","AGENT")
/dashboard/**              → hasAnyRole("ADMIN","MANAGER","AGENT")
/actuator/health           → permitAll
/actuator/**               → hasRole("ADMIN")
```

## JWT Claims
| Claim | CRM JWT | Portal JWT |
|-------|---------|-----------|
| `sub` | userId (UUID) | contactId (UUID) |
| `tid` | tenantId (UUID) | tenantId (UUID) |
| `roles` | ROLE_ADMIN etc. | ROLE_PORTAL |
| `tv` | tokenVersion (int) | **absent** |
| TTL | configurable (default 3600s) | 7200s (2h) |

## Multi-Tenancy Enforcement Points
1. JWT `tid` claim — extracted in `JwtAuthenticationFilter`
2. `TenantContext` ThreadLocal — set in filter, cleared in finally
3. Service methods — read `TenantContext.getTenantId()`
4. Repository queries — always include `AND tenant_id = :tenantId`
5. Entity — `@ManyToOne Tenant tenant` on every scoped entity

## Caching
- Framework: Caffeine (in-process, per-node, NOT distributed)
- Config: `CacheConfig` — `registerCustomCache(name, ttl, maxEntries)` per cache
- Cache names: `commercialDashboard`, `receivablesDashboard`, `userSecurityCache`
- Invalidation: TTL-based; `userSecurityCache` evicted on role change / disable

## Outbox Pattern
```
API → OutboundMessage saved (status=PENDING) in same transaction
     → OutboxScheduler polls batch (FOR UPDATE SKIP LOCKED)
     → OutboundDispatcherService calls EmailSender/SmsSender
     → Success → SENT; Failure → retry with exponential backoff
     → After maxRetries → FAILED (permanent)
Retry delays: {1, 5, 30} minutes (capped at array length)
```

## Portal Magic Link Flow
```
POST /api/portal/auth/request-link (email)
  → generate 32-byte SecureRandom token (URL-safe base64)
  → store SHA-256(token) in portal_token (not raw token)
  → send email directly via EmailSender.send()

GET /api/portal/auth/verify?token=X
  → SHA-256(X) → lookup portal_token (not expired, not used)
  → mark usedAt = now()
  → return Portal JWT (sub=contactId, roles=[ROLE_PORTAL], tid=tenantId)
```

## Liquibase Changelog
- Master: `hlm-backend/src/main/resources/db/changelog/db.changelog-master.yaml`
- Naming: `NNN_description.yaml` (e.g., `025_portal_token.yaml`)
- Range: 001–027+
- Rule: never edit applied changesets; always add new numbered files

## payment/ vs payments/ — Two Coexisting Packages

| Package | Purpose | Key API Paths |
|---------|---------|---------------|
| `payment/` | **v1 model**: PaymentSchedule (tranches), PaymentCall (Appel de Fonds PDF), payment recording | `/api/contracts/{id}/payment-schedule`, `/api/payment-calls` |
| `payments/` | **v2 model**: PaymentScheduleItem (richer workflow: issue→send→cancel), Call-for-Funds PDF+reminders, CashDashboard | `/api/contracts/{id}/schedule`, `/api/schedule-items/{id}`, `/api/dashboard/commercial/cash` |

Both serve active routes. `payments/` is the newer, more complete implementation.
`payment/` endpoints are now marked deprecated and emit deprecation headers with a migration link to the v2 API.

## media/ — Storage Architecture

- `MediaStorageService` interface + `LocalFileMediaStorage` default (writes to `MEDIA_STORAGE_DIR`, default `./uploads`)
- Cloud swap: provide `@Primary @Bean` implementing `MediaStorageService` (no other code changes needed)
- Env vars: `MEDIA_STORAGE_DIR`, `MEDIA_MAX_FILE_SIZE` (default 10 MB)
- **Not suitable for multi-node deployments** without shared storage or cloud swap

## PDF Generation

- `DocumentGenerationService`: Thymeleaf → HTML → `PdfRendererBuilder` (OpenHtmlToPDF, fast mode) → `ByteArrayOutputStream`
- Synchronous, in-memory — holds full PDF bytes in heap during render
- Recommended JVM: `-Xmx512m` minimum; increase if OOM observed on large documents
- Async option (future): queue PDF jobs in outbox, email result when ready

## Package Dependency Directions
```
Controller (api/) → depends on → Service
Service          → depends on → Repository + Domain + (other Services)
Repository       → depends on → Domain
Domain           → no dependencies on other packages
common/          → no dependencies (utility/error only)
```
