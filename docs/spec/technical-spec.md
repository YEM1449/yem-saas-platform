# Technical Specification — YEM SaaS Platform

This document covers the complete technical stack, runtime configuration, infrastructure, data access patterns, caching, security implementation, async processing, build pipeline, and deployment model.

---

## Table of Contents

1. [Technology Stack](#1-technology-stack)
2. [Runtime Configuration Reference](#2-runtime-configuration-reference)
3. [Multi-Tenancy Implementation](#3-multi-tenancy-implementation)
4. [Security Implementation](#4-security-implementation)
5. [Data Access Layer](#5-data-access-layer)
6. [Caching Strategy](#6-caching-strategy)
7. [Async and Scheduled Processing](#7-async-and-scheduled-processing)
8. [Messaging and Outbox](#8-messaging-and-outbox)
9. [Media Storage](#9-media-storage)
10. [PDF Generation](#10-pdf-generation)
11. [Error Handling](#11-error-handling)
12. [Frontend Architecture](#12-frontend-architecture)
13. [Docker and Infrastructure](#13-docker-and-infrastructure)
14. [CI/CD Pipeline](#14-cicd-pipeline)
15. [Observability](#15-observability)
16. [Known Pitfalls and Patterns](#16-known-pitfalls-and-patterns)

---

## 1. Technology Stack

### Backend

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.5.11 |
| Build tool | Maven (wrapper) | 3.9+ |
| Database | PostgreSQL | 16 |
| ORM | Spring Data JPA / Hibernate | 6.x (via Boot) |
| Migrations | Liquibase | 5.0.0 |
| Security | Spring Security | 6.x |
| JWT | Spring Security OAuth2 Resource Server | 6.x |
| Rate limiting | Bucket4j | 8.10.1 |
| Cache | Caffeine (default) / Redis (opt-in) | — |
| Redis client | Lettuce via Spring Data Redis | — |
| Object storage | AWS SDK v2 | 2.27.21 |
| SMS | Twilio SDK | 10.4.1 |
| PDF | openhtmltopdf | 1.0.10 |
| HTML templates | Thymeleaf | — |
| API docs | springdoc-openapi | 2.8.10 |
| Observability | Micrometer OTLP | — |
| Test — unit | JUnit 5 + Mockito | — |
| Test — integration | Testcontainers + MockMvc | — |

### Frontend

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | TypeScript | 5.x |
| Framework | Angular | 19 |
| Build | Angular CLI | 19.x |
| HTTP client | Angular `HttpClient` | — |
| Routing | Angular Router (lazy-loaded) | — |
| Styles | SCSS | — |
| Dev server | Angular CLI dev server | — |
| Production serving | Nginx | latest (alpine) |

### Infrastructure

| Service | Image | Port |
|---------|-------|------|
| PostgreSQL | postgres:16-alpine | 5432 |
| Redis | redis:7-alpine | 6379 |
| MinIO | minio/minio:latest | 9000 (API), 9001 (console) |
| Backend | hlm-backend (custom) | 8080 |
| Frontend | hlm-frontend (Nginx) | 80 |

---

## 2. Runtime Configuration Reference

All configuration is environment-variable driven. The `application.yml` maps env vars with defaults using `${ENV_VAR:default}` syntax.

### Required (no default — application fails without these)

| Env Var | Description |
|---------|-------------|
| `JWT_SECRET` | HS256 signing key; minimum 32 characters |

### Database

| Env Var | Default | Description |
|---------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/hlm` | JDBC connection URL |
| `DB_USER` | `hlm_user` | Database username |
| `DB_PASSWORD` | `hlm_pwd` | Database password |

### JWT / Session

| Env Var | Default | Description |
|---------|---------|-------------|
| `JWT_TTL_SECONDS` | `3600` | Token lifetime in seconds |

### Server

| Env Var | Default | Description |
|---------|---------|-------------|
| `SERVER_PORT` | `8080` | HTTP listen port |
| `FORWARD_HEADERS_STRATEGY` | `NONE` | `NONE` for direct, `FRAMEWORK` behind Nginx/LB |
| `SSL_ENABLED` | `false` | Activate embedded Tomcat TLS |
| `SSL_KEYSTORE_PATH` | `classpath:ssl/hlm-dev.p12` | PKCS12 keystore path |
| `SSL_KEYSTORE_PASSWORD` | `changeit` | Keystore password |
| `SSL_KEY_ALIAS` | `hlm-dev` | Certificate alias |
| `HTTP_REDIRECT_PORT` | `8443` | HTTP→HTTPS redirect connector port |
| `CORS_ALLOWED_ORIGINS` | _(empty — deny all)_ | Comma-separated allowed origins |

### Caching

| Env Var | Default | Description |
|---------|---------|-------------|
| `REDIS_ENABLED` | `false` | `true` = Redis; `false` = Caffeine |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | _(empty)_ | Redis password |

### Rate Limiting & Lockout

| Env Var | Default | Description |
|---------|---------|-------------|
| `RATE_LIMIT_LOGIN_IP_MAX` | `20` | Max login attempts per IP per window |
| `RATE_LIMIT_LOGIN_KEY_MAX` | `10` | Max login attempts per email per window |
| `RATE_LIMIT_LOGIN_WINDOW_SECONDS` | `60` | Rate limit window in seconds |
| `RATE_LIMIT_PORTAL_CAPACITY` | `3` | Magic link requests per IP per hour |
| `LOCKOUT_MAX_ATTEMPTS` | `5` | Failed logins before account lockout |
| `LOCKOUT_DURATION_MINUTES` | `15` | Lockout duration |

### Email (SMTP)

| Env Var | Default | Description |
|---------|---------|-------------|
| `EMAIL_HOST` | _(empty)_ | SMTP host; blank = no-op provider |
| `EMAIL_PORT` | `587` | SMTP port |
| `EMAIL_USER` | _(empty)_ | SMTP username |
| `EMAIL_PASSWORD` | _(empty)_ | SMTP password |
| `EMAIL_FROM` | `noreply@example.com` | Sender address |

### SMS (Twilio)

| Env Var | Default | Description |
|---------|---------|-------------|
| `TWILIO_ACCOUNT_SID` | _(empty)_ | Account SID; blank = no-op |
| `TWILIO_AUTH_TOKEN` | _(empty)_ | Auth token |
| `TWILIO_FROM` | _(empty)_ | Twilio phone number |

### Outbox

| Env Var | Default | Description |
|---------|---------|-------------|
| `OUTBOX_BATCH_SIZE` | `20` | Messages claimed per poll tick |
| `OUTBOX_MAX_RETRIES` | `3` | Max attempts before `FAILED` |
| `OUTBOX_POLL_INTERVAL_MS` | `5000` | Polling interval in milliseconds |

### Media Storage

| Env Var | Default | Description |
|---------|---------|-------------|
| `MEDIA_STORAGE_DIR` | `./uploads` | Local filesystem upload directory |
| `MEDIA_MAX_FILE_SIZE` | `10485760` | Max file size in bytes (10 MB) |
| `MEDIA_ALLOWED_TYPES` | `image/jpeg,image/png,image/webp,application/pdf` | Allowed MIME types |
| `MEDIA_OBJECT_STORAGE_ENABLED` | `false` | Activate S3-compatible storage |
| `MEDIA_OBJECT_STORAGE_ENDPOINT` | _(empty)_ | S3 endpoint URL (blank = AWS) |
| `MEDIA_OBJECT_STORAGE_REGION` | `eu-west-1` | S3 region |
| `MEDIA_OBJECT_STORAGE_BUCKET` | `hlm-media` | S3 bucket name |
| `MEDIA_OBJECT_STORAGE_ACCESS_KEY` | _(empty)_ | S3 access key |
| `MEDIA_OBJECT_STORAGE_SECRET_KEY` | _(empty)_ | S3 secret key |

### GDPR

| Env Var | Default | Description |
|---------|---------|-------------|
| `GDPR_RETENTION_DAYS` | `1825` | Contact data retention period (5 years) |
| `DATA_RETENTION_CRON` | `0 0 2 * * *` | Cron for retention sweep (02:00 daily) |

### Scheduled Jobs

| Env Var | Default | Description |
|---------|---------|-------------|
| `PAYMENTS_OVERDUE_CRON` | `0 0 6 * * *` | Overdue payment scanner (06:00 daily) |
| `REMINDER_ENABLED` | `true` | Enable reminder scheduler |
| `REMINDER_CRON` | `0 0 8 * * *` | Reminder run (08:00 daily) |
| `REMINDER_DEPOSIT_WARN_DAYS` | `7,3,1` | Days before deposit due to warn |
| `REMINDER_PROSPECT_STALE_DAYS` | `14` | Days inactive before prospect marked stale |
| `PORTAL_CLEANUP_CRON` | `0 0 3 * * *` | Expired portal token cleanup (03:00 daily) |

### Observability

| Env Var | Default | Description |
|---------|---------|-------------|
| `OTEL_ENABLED` | `false` | Enable OTLP trace export |
| `OTEL_SAMPLE_RATE` | `1.0` | Sampling probability (0.0–1.0) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318/v1/traces` | OTLP collector endpoint |
| `MAIL_HEALTH_ENABLED` | `false` | Include mail in Actuator health check |

### HikariCP Connection Pool

| Setting | Value |
|---------|-------|
| `maximum-pool-size` | 20 |
| `minimum-idle` | 5 |
| `connection-timeout` | 30 000 ms |
| `idle-timeout` | 600 000 ms |
| `max-lifetime` | 1 800 000 ms |

---

## 3. Multi-Tenancy Implementation

### Tenant Isolation Model

All domain entities carry a `tenant_id` (UUID) foreign key column referencing the `tenant` table. Every service call begins by reading the tenant from `TenantContext.getTenantId()` and scoping all JPA queries with a `WHERE tenant_id = :tenantId` predicate.

### TenantContext

```
Class: com.yem.hlm.backend.tenant.context.TenantContext
```

- Static utility with two `ThreadLocal<UUID>` fields: `TENANT_ID` and `USER_ID`.
- `JwtAuthenticationFilter` populates both at request start by reading `tid` and `sub` JWT claims.
- `TenantContext.clear()` is called in the `finally` block of the filter to prevent thread pool leakage.
- No request-scoped Spring bean is used; ThreadLocal is zero-overhead in the synchronous servlet model.

### Security Invariant

No service reads `tenantId` from a request body or URL parameter. All services call `TenantContext.getTenantId()` and pass it to repository methods. Cross-tenant access is structurally impossible unless the filter is bypassed.

---

## 4. Security Implementation

### JWT Generation

- **Class:** `JwtProvider` (`auth/service/JwtProvider.java`)
- **Algorithm:** HS256 with key derived from `JWT_SECRET`
- **Claims:** `sub` (userId UUID), `tid` (tenantId UUID), `roles` (array of role strings), `tv` (token version int), `iat`, `exp`
- **Validation:** Spring Security's `JwtDecoder` verifies signature and expiry

### Token Revocation

- `app_user.token_version` int column.
- `AdminUserService.setEnabled(false)` and `AdminUserService.changeRole()` increment `token_version`.
- `JwtAuthenticationFilter` loads `UserSecurityInfo` from `userSecurityCache` and compares `secInfo.tokenVersion() != tv`.
- Cache TTL: 60 s. Revocation propagates within ~60 s.

### Portal JWT

- **Class:** `PortalJwtProvider`
- Same `JwtEncoder`/`JwtDecoder` beans as `JwtProvider`
- Claims: `sub` = contactId, `roles` = `["ROLE_PORTAL"]`, TTL 2 hours
- No `tv` claim — portal principal is a `contact` row, not an `app_user` row
- `JwtAuthenticationFilter.isPortalToken()` detects `ROLE_PORTAL` and skips `UserSecurityCacheService`

### Filter Chain

Filters registered before `UsernamePasswordAuthenticationFilter`:
1. `JwtAuthenticationFilter` — JWT parsing, TenantContext population
2. `RequestCorrelationFilter` — correlation ID injection into MDC

Security rules (in order):
```
OPTIONS /**                       → permitAll (CORS preflight)
POST /auth/login                  → permitAll
GET  /actuator/health             → permitAll
GET  /actuator/info               → permitAll
GET  /v3/api-docs/**              → permitAll
GET  /swagger-ui/**               → permitAll
POST /tenants                     → permitAll
POST /api/portal/auth/request-link → permitAll
GET  /api/portal/auth/verify       → permitAll
/api/portal/**                    → hasRole('PORTAL')
/api/**                           → hasAnyRole('ADMIN','MANAGER','AGENT')
anyRequest                        → authenticated
```

### RBAC Annotation Pattern

Controllers use method-level `@PreAuthorize`:
```java
@PreAuthorize("hasRole('ADMIN')")              // ROLE_ADMIN only
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')") // ADMIN or MANAGER
```
Spring Security auto-adds the `ROLE_` prefix; code uses bare role names in `hasRole()`.

### Password Policy

`@StrongPassword` annotation backed by `StrongPasswordValidator` with pattern:
```
^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z\d]).{12,}$
```
Requirements: min 12 chars, uppercase, lowercase, digit, special character.

### Security Headers

Every response carries:
```
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'
X-Frame-Options: DENY
```
HSTS is emitted only when `SSL_ENABLED=true`.
CSRF is disabled (stateless JWT API, no session cookies).

---

## 5. Data Access Layer

### JPA / Hibernate

- `ddl-auto: validate` — Hibernate validates schema against entities; all DDL is Liquibase-managed.
- Dialect: `PostgreSQLDialect`.
- Lazy loading for `@ManyToOne` relationships; `FetchType.LAZY` is explicit on FK joins.
- Audit timestamps (`createdAt`, `updatedAt`) are managed by `@PrePersist` / `@PreUpdate` lifecycle hooks.

### Repository Pattern

All repositories extend `JpaRepository<Entity, UUID>`. Custom queries use:
- JPQL `@Query` for complex joins and aggregations.
- Native SQL `@Query(nativeQuery = true)` for `FOR UPDATE SKIP LOCKED` and complex aggregation queries in the receivables dashboard.

### JPQL Nullable LocalDateTime Pattern

Nullable `LocalDateTime` parameters in JPQL queries require:
```java
// Correct:
CAST(:param AS LocalDateTime) IS NULL
// Wrong (PostgreSQL type inference error):
:param IS NULL
```

### Liquibase Migration Strategy

- **Additive-only:** Applied changesets are immutable. New changesets are numbered sequentially (001–030 as of current state).
- **Changelog:** `classpath:db/changelog/db.changelog-master.yaml`
- **Schema:** `public`
- Changes to existing data use new changesets (e.g., `030-update-seed-password.yaml`).

### Changeset Index

| Range | Domain |
|-------|--------|
| 001–005 | Core tables: tenant, app_user, project, property, contact |
| 006–010 | Deposit, contract, interests |
| 011–015 | Notifications, audit log, portal, commission |
| 016–020 | Payment v2 tables |
| 021–025 | Portal token, outbox |
| 026–030 | Reservations, lockout fields, v1 table drops, seed password update |

---

## 6. Caching Strategy

### Cache Names and Configuration

| Cache Name | TTL | Max Entries | Content |
|------------|-----|-------------|---------|
| `userSecurityCache` | 60 s | 1000 | `UserSecurityInfo` (tokenVersion, enabled) |
| `commercialDashboard` | 30 s | 500 | `CommercialDashboardSummaryDTO` keyed by resolved params |
| `receivablesDashboard` | 30 s | 200 | `ReceivablesDashboardDTO` |

### Caffeine (Default)

- Activated automatically when `REDIS_ENABLED=false` (default).
- `CacheConfig` registers each cache name with its TTL and max entries.
- In-process; no network overhead; suitable for single-instance deployments.

### Redis (Production / Multi-Instance)

- Activated by `@ConditionalOnProperty("app.redis.enabled")` in `RedisCacheConfig`.
- Uses `GenericJackson2JsonRedisSerializer` for all cache values.
- Required when running multiple application instances behind a load balancer (shared `userSecurityCache` ensures revocation consistency).
- Same cache names and `@Cacheable` annotations work with both backends.

### Cache Key Pattern

`CommercialDashboardService` uses `resolveEffectiveAgentId()` to compute the final `agentId` before caching, so cache keys are stable regardless of input combinations.

---

## 7. Async and Scheduled Processing

All schedulers use `@Scheduled` annotations and are disabled in tests via `spring.task.scheduling.enabled=false` (guarded by `@ConditionalOnProperty(matchIfMissing = true)`).

### Scheduler Inventory

| Class | Trigger | Action |
|-------|---------|--------|
| `OutboundDispatcherService` | `OUTBOX_POLL_INTERVAL_MS` (5 s) | Dispatch pending outbox messages |
| `DepositWorkflowScheduler` | Configurable | Expire stale PENDING deposits |
| `ReservationExpiryScheduler` | Hourly | Set ACTIVE reservations past `expiresAt` to EXPIRED |
| `DataRetentionScheduler` | `DATA_RETENTION_CRON` (02:00) | Flag contacts past retention window |
| `ReminderService` | `REMINDER_CRON` (08:00) | Send payment reminders via outbox |
| `payments/ReminderService` | `PAYMENTS_OVERDUE_CRON` (06:00) | Set ISSUED/SENT items with past due_date to OVERDUE |
| `PortalTokenCleanupScheduler` | `PORTAL_CLEANUP_CRON` (03:00) | Delete expired one-time portal tokens |

---

## 8. Messaging and Outbox

### Pattern

The transactional outbox pattern ensures email/SMS delivery is atomic with database operations:

1. Caller's `@Transactional` method calls `OutboxMessageService.queue(...)`.
2. `OutboxMessageService` persists a row to `outbound_message` within the same transaction.
3. `OutboundDispatcherService` polls `outbound_message` using `FOR UPDATE SKIP LOCKED`.
4. On success, row is marked `SENT`. On failure, `retryCount++` and `nextRetryAt` advanced with exponential backoff.
5. After `maxRetries` failures, row is marked `FAILED`.

### Provider Selection

| Provider | Activation Condition |
|----------|---------------------|
| `SmtpEmailSender` | `@ConditionalOnExpression("!'${app.email.host:}'.isBlank()")` |
| `NoopEmailSender` | `@ConditionalOnMissingBean` fallback |
| `TwilioSmsSender` | `@ConditionalOnExpression("!'${app.sms.account-sid:}'.isBlank()")` |
| `NoopSmsSender` | `@ConditionalOnMissingBean` fallback |

`@ConditionalOnExpression` is used instead of `@ConditionalOnProperty` because `@ConditionalOnProperty` treats an empty string as "present".

### Exponential Backoff Delays

`{1L, 5L, 30L}` minutes, indexed by `retryCount`. Capped at array length.

### Exception: Magic Link Email

Portal magic link emails are sent directly via `EmailSender.send()` (not the outbox) because the endpoint is public and has no active `@Transactional` context or user FK.

---

## 9. Media Storage

### Interface

`MediaStorage` with three methods: `upload(key, inputStream, contentType)`, `download(key)`, `delete(key)`.

### Local File Storage (Default)

- **Class:** `LocalFileMediaStorage` (`@Primary`)
- Writes to `MEDIA_STORAGE_DIR` (`./uploads` by default; `/tmp/hlm-uploads` in Docker).
- File keys are UUID strings (`{uuid}.{ext}`).

### S3-Compatible Object Storage (Opt-in)

- **Class:** `ObjectStorageMediaStorage`
- Activated by `@ConditionalOnProperty("app.media.object-storage.enabled")`
- Uses AWS SDK v2 `S3Client`.
- Compatible with MinIO, OVH, Scaleway, Hetzner, Cloudflare R2, AWS S3.
- Leave `MEDIA_OBJECT_STORAGE_ENDPOINT` blank for AWS S3 (SDK auto-resolves region endpoint).
- File keys are string identifiers (not `UUID` objects) to support arbitrary storage key formats.

---

## 10. PDF Generation

### Technology

`openhtmltopdf` 1.0.10 + Thymeleaf HTML templates.

### Documents

| Document | Service | Template |
|----------|---------|----------|
| Deposit reservation slip | `ReservationDocumentService` | `reservation.html` |
| Sale contract | `ContractDocumentService` | `contract.html` |

### Pitfall: HTML Entities

`openhtmltopdf` rejects HTML4 named entities (`&nbsp;`, `&mdash;`). Use numeric character references (`&#160;`, `&#8212;`) in all Thymeleaf templates.

---

## 11. Error Handling

### Error Envelope

All error responses use `ErrorResponse`:
```json
{
  "timestamp": "2026-03-17T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "code": "CONTACT_NOT_FOUND",
  "message": "Contact not found",
  "path": "/api/contacts/..."
}
```

### ErrorCode Enum

`ErrorCode` defines every machine-readable error code. Key codes:

| Code | HTTP Status | Scenario |
|------|------------|---------|
| `VALIDATION_ERROR` | 400 | Bean validation failure |
| `ACCOUNT_LOCKED` | 401 | Too many failed logins |
| `UNAUTHORIZED` | 401 | Missing or invalid JWT |
| `FORBIDDEN` | 403 | Insufficient role |
| `NOT_FOUND` | 404 | Generic resource not found |
| `USER_EMAIL_TAKEN` | 409 | Duplicate user email |
| `CONTACT_EMAIL_EXISTS` | 409 | Duplicate contact email |
| `PROPERTY_ALREADY_SOLD` | 409 | Contract on sold property |
| `PROPERTY_ALREADY_RESERVED` | 409 | Deposit on reserved property |
| `GDPR_ERASURE_BLOCKED` | 409 | Erasure blocked by signed contract |
| `RATE_LIMIT_EXCEEDED` | 429 | Rate limit hit |
| `INTERNAL_ERROR` | 500 | Unhandled exception |

### GlobalExceptionHandler

`@RestControllerAdvice` catches:
- `MethodArgumentNotValidException` → 400 with field-level validation errors
- Domain-specific exceptions (e.g., `ContactNotFoundException`) → mapped status + ErrorCode
- `LoginRateLimitedException` → 429 with `Retry-After` header
- Uncaught exceptions → 500 `INTERNAL_ERROR`

---

## 12. Frontend Architecture

### Application Structure

```
hlm-frontend/src/app/
├── app.config.ts          # Providers: router, httpClient, interceptors
├── app.routes.ts          # Top-level lazy routes
├── core/
│   ├── interceptors/      # authInterceptor, portalInterceptor
│   └── services/          # AuthService, TokenService
└── features/
    ├── auth/              # Login component
    ├── contacts/
    ├── contracts/
    │   └── payment-schedule.component  # v2 payment schedule
    ├── dashboard/
    ├── deposits/
    ├── portal/            # /portal route tree (magic link, contract view)
    ├── projects/
    ├── properties/
    └── reservations/
```

### Routing

Lazy-loaded feature modules under `/app/*`:
- `/app/dashboard` — Commercial dashboard KPIs
- `/app/projects` — Project list/detail
- `/app/properties` — Property catalogue
- `/app/contacts` — Contact CRM
- `/app/deposits` — Deposit workflow
- `/app/reservations` — Reservation management
- `/app/contracts` — Sale contracts
- `/app/contracts/:id/payments` — v2 payment schedule

Portal route tree (public entry + guarded views):
- `/portal/login` — Magic link request (public)
- `/portal/contracts` — Buyer's own contracts (guarded by portal JWT)

### HTTP Interceptors

Two interceptors registered in `app.config.ts`:
- `authInterceptor` — attaches `Authorization: Bearer {token}` to all `/api/**` calls using the CRM JWT from `localStorage`.
- `portalInterceptor` — attaches portal JWT (from `localStorage` key `hlm_portal_token`) only to `/api/portal/` calls.

### Dev Proxy

`proxy.conf.json` forwards all `/auth`, `/api`, `/dashboard`, and `/actuator` requests to `http://localhost:8080` during `npm start`.

---

## 13. Docker and Infrastructure

### Backend Dockerfile (Multi-Stage)

```
Stage 1 (build): eclipse-temurin:21-jdk-jammy
  - ./mvnw package -DskipTests
Stage 2 (runtime): eclipse-temurin:21-jre-jammy
  - Non-root user: uid 1001, group hlm
  - COPY target/*.jar app.jar
  - ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

SSL keystore generation is skipped in the Docker build stage; the keystore is injected at runtime via `SSL_KEYSTORE_PATH` if TLS is needed.

### Docker Compose Services

| Service | Image | Healthcheck |
|---------|-------|-------------|
| `hlm-postgres` | postgres:16-alpine | `pg_isready` |
| `hlm-redis` | redis:7-alpine | `redis-cli ping` |
| `hlm-minio` | minio/minio:latest | HTTP readiness check |
| `hlm-backend` | Built from `hlm-backend/Dockerfile` | `GET /actuator/health` |
| `hlm-frontend` | Built from `hlm-frontend/Dockerfile` | `curl -f http://localhost/` |

Backend depends on `hlm-postgres` and `hlm-redis` with `service_healthy` condition.

### Volumes

- `postgres_data` — Persistent PostgreSQL data.
- `redis_data` — Persistent Redis data.
- `minio_data` — Persistent MinIO object storage.

### Networking

All services are on a shared `hlm-network` Docker bridge network. Backend connects to PostgreSQL and Redis via Docker DNS (service name as hostname).

---

## 14. CI/CD Pipeline

### Workflows

**`backend-ci.yml`** — Triggered on push/PR to main and Epic/* branches:
1. Checkout
2. Set up Java 21 (Temurin)
3. Cache Maven dependencies
4. `./mvnw test` (unit tests)
5. `./mvnw failsafe:integration-test` (integration tests with Testcontainers)

**`frontend-ci.yml`** — Triggered on push/PR:
1. Checkout
2. Set up Node.js 20
3. `npm ci`
4. `npm run build`
5. `npm test -- --watch=false --browsers=ChromeHeadless`

**`docker-build.yml`** — Triggered on push to main:
1. Checkout
2. `docker compose build`
3. `docker compose up -d`
4. Smoke test: `GET /actuator/health` returns `{"status":"UP"}`
5. `docker compose down`

### Test Classification

| Test Type | Naming Convention | Plugin | Trigger |
|-----------|-----------------|--------|---------|
| Unit tests | `*Test.java` | Maven Surefire | `mvn test` |
| Integration tests | `*IT.java` | Maven Failsafe | `mvn failsafe:integration-test` |

Integration tests use `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` + Testcontainers PostgreSQL.

---

## 15. Observability

### Health

- `GET /actuator/health` — publicly accessible; returns `{"status":"UP"}` when DB, Redis (if enabled), and disk are healthy.
- `GET /actuator/info` — publicly accessible.

### Tracing (OpenTelemetry)

- Micrometer OTLP exporter.
- Activated by `OTEL_ENABLED=true`.
- Endpoint: `OTEL_EXPORTER_OTLP_ENDPOINT` (default `http://localhost:4318/v1/traces`).
- Sampling rate: `OTEL_SAMPLE_RATE` (default `1.0` = 100%).

### Correlation IDs

`RequestCorrelationFilter` (runs before `JwtAuthenticationFilter`):
- Reads `X-Correlation-ID` from request header; generates UUID v4 if absent.
- Stores in MDC as `correlationId`.
- Adds `tenantId` to MDC after JWT filter sets `TenantContext`.
- Writes `X-Correlation-ID` to response header.

---

## 16. Known Pitfalls and Patterns

### `@ConditionalOnProperty` vs. Empty Strings

`@ConditionalOnProperty` treats `""` (empty string) as "property present". Use `@ConditionalOnExpression("!'${prop:}'.isBlank()")` when the YAML maps `${ENV_VAR:}` (which defaults to empty string when the env var is not set).

**Affected:** `SmtpEmailSender`, `TwilioSmsSender`.

### JPQL Null `LocalDateTime` Parameters

PostgreSQL's type inference cannot resolve `NULL` in `:param IS NULL` in JPQL. Use:
```java
CAST(:param AS LocalDateTime) IS NULL
```
**Affected:** `ContractRepository`, `DepositRepository`, `CommercialAuditRepository`.

### openhtmltopdf HTML Entities

`openhtmltopdf` rejects HTML4 named entities. Use numeric character references:
- `&nbsp;` → `&#160;`
- `&mdash;` → `&#8212;`

### IT Test RBAC Setup

In integration tests, use `adminBearer` for all data-setup API calls (`POST /api/projects`, `POST /api/properties`, etc.). Use the role under test only for the actual operation being tested. For agent-ownership tests where agents cannot create resources via the API, save entities directly via repository injection.

### PropertyType Requirements

- `VILLA` requires: `surfaceAreaSqm`, `landAreaSqm`, `bedrooms`, `bathrooms` (all non-null).
- `APPARTEMENT` requires: `surfaceAreaSqm`, `bedrooms`, `bathrooms`, `floorNumber`.
- `APARTMENT` is NOT a valid enum value (use `APPARTEMENT`).

### Caffeine `build()` Return Type

`CaffeineCacheBuilder.build()` returns `Cache<Object, Object>`. IDE null-safety warnings when passed to `registerCustomCache()` are harmless.

### Seed Data

| Field | Value |
|-------|-------|
| Tenant key | `acme` |
| Tenant UUID | `11111111-1111-1111-1111-111111111111` |
| Admin email | `admin@acme.com` |
| Admin UUID | `22222222-2222-2222-2222-222222222222` |
| Admin password | `Admin123!Secure` |

The password was updated in changeset `030-update-seed-password.yaml` because the old `Admin123!` (9 chars) failed the `@StrongPassword` minimum-12-character requirement.
