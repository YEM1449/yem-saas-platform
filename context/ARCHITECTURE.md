# ARCHITECTURE.md — Compact Architecture Reference

_Updated: 2026-03-05_

## Layer Stack
```text
Browser
  → HTTPS (TLS 1.2/1.3)
  → Nginx:443 [production] OR Tomcat:8443 [dev/staging]
  → Angular SPA (hlm-frontend:4200 in dev)
  → Spring Boot API (hlm-backend:8080 or :8443)
  → PostgreSQL (Liquibase-managed schema)
  → Email/SMS provider interfaces (outbox + direct portal email use case)
```

In dev: Angular dev proxy forwards `/auth`, `/api`, `/dashboard`, `/actuator` to `:8080`.

## TLS Architecture

**Dev:**   Embedded Tomcat TLS — `SSL_ENABLED=true` — `scripts/generate-dev-cert.sh`
**Prod:**  Nginx terminates TLS — Spring Boot listens plain HTTP behind proxy
           `FORWARD_HEADERS_STRATEGY=FRAMEWORK` (trusts `X-Forwarded-Proto` from Nginx)
**Nginx config:** `nginx/nginx.conf` (security headers, HTTP→HTTPS redirect, API proxy)

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
- `contact/`, `property/`, `project/`, `deposit/`, `reservation/`, `contract/`: core CRM commercial workflow.
- `dashboard/`: commercial, cash, receivables aggregate read models.
- `outbox/` and `notification/`: async outbound + in-app notifications.
- `payments/`: schedule/workflow/reminders/cash dashboard. Sole payment implementation — `payment/` (v1) was deleted in Epic/sec-improvement (2026-03-06).
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
Email: SmtpEmailSender wired via spring.mail.* bridge (activates when app.email.host is set).
SMS:   TwilioSmsSender (activates when app.sms.account-sid is set).

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

## Reservations
```text
POST /api/reservations                         → create (ADMIN/MANAGER)
GET  /api/reservations/{id}                    → get
GET  /api/reservations                         → list (all tenant, newest first)
POST /api/reservations/{id}/cancel             → cancel ACTIVE → CANCELLED (ADMIN/MANAGER)
POST /api/reservations/{id}/convert-to-deposit → ACTIVE → CONVERTED_TO_DEPOSIT + creates Deposit (ADMIN/MANAGER)
```
- Statuses: `ACTIVE`, `EXPIRED`, `CANCELLED`, `CONVERTED_TO_DEPOSIT`
- Scheduler: `ReservationExpiryScheduler` (cron hourly, `app.reservation.expiry-cron`)
- DB table: `property_reservation` (changeset 026)

## Payments (v2 Only)
`payment/` (v1) package was deleted in Epic/sec-improvement (2026-03-06). Only `payments/` (v2) exists.

| Key Paths |
|-----------|
| `GET /api/contracts/{id}/schedule` |
| `POST /api/contracts/{id}/schedule` |
| `PUT/DELETE /api/schedule-items/{itemId}` |
| `POST /api/schedule-items/{itemId}/issue|send|cancel` |
| `GET /api/schedule-items/{itemId}/pdf` |
| `GET|POST /api/schedule-items/{itemId}/payments` |
| `GET /api/dashboard/commercial/cash` |

Migration history: `docs/v2/payment-v1-retirement-plan.v2.md`.

## Storage + PDF Notes
- `MediaStorageService` abstraction: `LocalFileMediaStorage` default (`MEDIA_OBJECT_STORAGE_ENABLED=false`)
  | `ObjectStorageMediaStorage` (`MEDIA_OBJECT_STORAGE_ENABLED=true`).
- S3 protocol with mandatory path-style addressing (`pathStyleAccessEnabled=true`) — compatible with:
  OVH Object Storage, Scaleway, Hetzner, Cloudflare R2, MinIO (self-hosted), and AWS S3.
  Set `MEDIA_OBJECT_STORAGE_ENDPOINT` to the provider URL. Leave blank only for AWS S3 (SDK auto-resolves).
- PDF generation is synchronous in-memory (`DocumentGenerationService`, OpenHtmlToPDF fast mode); tune heap for production.

## Scheduled Tasks
- `ReservationExpiryScheduler` — hourly cron, marks ACTIVE reservations past expiry as EXPIRED
- `OutboxDispatcherScheduler` — polls outbox every `OUTBOX_POLL_INTERVAL_MS` ms
- `ReminderScheduler` — cron `REMINDER_CRON` (default 08:00), sends overdue payment reminders
- Portal token cleanup: `PortalTokenCleanupScheduler` — daily 03:00
  Deletes expired or used portal_token rows via `deleteExpiredAndUsed()`.

## Dependency Direction
```text
Controller(api) -> Service -> Repository -> Domain
Service may compose other services
Domain should not depend on controller/repository layers
common/ should remain low-coupling shared primitives
```
