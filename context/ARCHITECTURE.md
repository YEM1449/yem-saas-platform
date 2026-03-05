# ARCHITECTURE.md — Compact Architecture Reference

_Updated: 2026-03-05_

## Layer Stack
```text
Browser -> Angular SPA (hlm-frontend:4200)
           -> dev proxy (/auth,/api,/dashboard,/actuator -> :8080)
Spring Boot API (hlm-backend:8080)
           -> PostgreSQL (Liquibase-managed schema)
           -> Email/SMS provider interfaces (outbox + direct portal email use case)
```

## Request Pipeline
```text
HTTP Request
  -> JwtAuthenticationFilter
     -> decode JWT, extract sub/tid/roles
     -> ROLE_PORTAL: principal=contactId, skip user security cache
     -> CRM roles: validate tokenVersion via UserSecurityCacheService
     -> set TenantContext (tenantId + userId/contactId)
  -> SecurityConfig route authorization
  -> @PreAuthorize checks (method-level)
  -> Controller (DTO contract)
  -> Service (tenant-scoped business rules)
  -> Repository (tenant-filtered data access)
  -> TenantContext.clear() in filter finally block
```

## Security Matcher Order (First Match Wins)
Reflects `auth/security/SecurityConfig.java`:
```text
OPTIONS /**                                  -> permitAll
/auth/login                                  -> permitAll
/actuator/health, /actuator/info             -> permitAll
/v3/api-docs/**, /swagger-ui/**              -> permitAll
POST /tenants                                -> permitAll
POST /api/portal/auth/request-link           -> permitAll
GET  /api/portal/auth/verify                 -> permitAll
/api/portal/**                               -> hasRole("PORTAL")
/api/**                                      -> hasAnyRole("ADMIN","MANAGER","AGENT")
anyRequest                                   -> authenticated
```

## JWT Model
| Claim | CRM JWT | Portal JWT |
|------|---------|------------|
| `sub` | userId | contactId |
| `tid` | tenantId | tenantId |
| `roles` | CRM roles | `ROLE_PORTAL` |
| `tv` | required (token revocation) | absent |
| TTL | configurable (default 3600s) | fixed 2h |

## Module Boundaries (Backend)
- `auth/`: security config, JWT providers, filter, user security cache.
- `tenant/`: tenant entity/context + bootstrap.
- `contact/`, `property/`, `project/`, `deposit/`, `contract/`: core CRM commercial workflow.
- `dashboard/`: commercial, cash, receivables aggregate read models.
- `outbox/` and `notification/`: async outbound + in-app notifications.
- `payment/`: v1 payments API, deprecated but still served.
- `payments/`: v2 schedule/workflow/reminders/cash dashboard (preferred).
- `portal/`: magic-link auth and buyer read-only endpoints.
- `media/`: storage abstraction + local implementation (cloud-swappable).
- `common/`: error contracts/shared infra primitives.

## Multi-Tenancy Enforcement Points
1. JWT `tid` claim is mandatory for authenticated access.
2. `TenantContext` is populated in auth filter, cleared after request.
3. Services use `TenantContext.getTenantId()` for all scoped operations.
4. Repositories include tenant filtering in queries.
5. Cross-tenant resources return 404/forbidden by design.

## Caching
- Provider: Caffeine (node-local, non-distributed).
- Key caches: `userSecurityCache`, `commercialDashboard*`, `receivablesDashboard`, `cashDashboard`.
- Strategy: short TTL + bounded size. `userSecurityCache` evicted on role/enablement changes.

## Outbox Pattern
```text
API transaction writes OutboundMessage(PENDING)
-> scheduler polls batch with FOR UPDATE SKIP LOCKED
-> dispatcher invokes EmailSender/SmsSender
-> success: SENT
-> failure: retry backoff (1m, 5m, 30m) until max retries
-> exhausted: FAILED
```

## Portal Magic-Link Flow
```text
POST /api/portal/auth/request-link
  -> generate random raw token
  -> persist SHA-256(rawToken) + expiry + tenant/contact mapping
  -> send link by email

GET /api/portal/auth/verify?token=raw
  -> hash raw token and validate usable token row
  -> mark token used
  -> issue ROLE_PORTAL JWT (sub=contactId, tid=tenantId, ttl=2h)
```

## Payments v1 vs v2
| Package | Status | Key Paths |
|---------|--------|-----------|
| `payment/` | deprecated | `/api/contracts/{id}/payment-schedule`, `/api/payment-calls` |
| `payments/` | preferred | `/api/contracts/{id}/schedule`, `/api/schedule-items/**`, `/api/dashboard/commercial/cash` |

v1 endpoints now emit deprecation headers (`Deprecation`, `Sunset`, `Warning`, `Link`) to drive migration.

## Storage + PDF Notes
- `MediaStorageService` abstraction with `LocalFileMediaStorage` default (`MEDIA_STORAGE_DIR`, `MEDIA_MAX_FILE_SIZE`).
- PDF generation is synchronous in-memory (`DocumentGenerationService`, OpenHtmlToPDF fast mode); tune heap for production.

## Dependency Direction
```text
Controller(api) -> Service -> Repository -> Domain
Service may compose other services
Domain should not depend on controller/repository layers
common/ should remain low-coupling shared primitives
```
