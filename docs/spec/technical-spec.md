# Technical Specification

This document summarizes how the current system is implemented and operated.

## 1. Technology Stack

### Backend

| Area | Technology |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.5.11 |
| Persistence | Spring Data JPA + Hibernate 6 |
| Database | PostgreSQL 16 |
| Migrations | Liquibase 5.0.0 |
| Security | Spring Security + OAuth2 Resource Server JWT support |
| Cache | Caffeine by default, Redis optional |
| Messaging | transactional outbox, SMTP email, Twilio SMS |
| Documents | Thymeleaf + OpenHTMLtoPDF |
| Object storage | local filesystem by default, AWS SDK v2 for S3-compatible backends |
| Observability | Spring Actuator + Micrometer OTLP tracing |

### Frontend

| Area | Technology |
| --- | --- |
| Framework | Angular 19 |
| Language | TypeScript 5 |
| Styles | CSS/SCSS |
| Tests | Karma/Jasmine, Playwright E2E |
| Serving | Angular dev server locally, Nginx in containerized production |

## 2. Repository Structure

```text
hlm-backend/   backend code and Liquibase
hlm-frontend/  Angular application
docs/          architecture, specs, guides
scripts/       smoke helpers and API support
.github/       CI workflows
```

## 3. Runtime Configuration

Configuration is environment-variable driven through [application.yml](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/resources/application.yml).

Important groups:

| Group | Examples |
| --- | --- |
| Core runtime | `SERVER_PORT`, `DB_URL`, `DB_USER`, `DB_PASSWORD` |
| Security | `JWT_SECRET`, `JWT_TTL_SECONDS`, `LOCKOUT_*`, `RATE_LIMIT_*` |
| Frontend links | `FRONTEND_BASE_URL`, `PORTAL_BASE_URL`, `CORS_ALLOWED_ORIGINS` |
| Cache | `REDIS_ENABLED`, `REDIS_HOST`, `REDIS_PORT` |
| Delivery | `EMAIL_*`, `TWILIO_*` |
| Media | `MEDIA_STORAGE_DIR`, `MEDIA_OBJECT_STORAGE_*` |
| Schedulers | `REMINDER_*`, `PAYMENTS_OVERDUE_CRON`, `PORTAL_CLEANUP_CRON`, `DATA_RETENTION_CRON` |
| Observability | `OTEL_ENABLED`, `OTEL_EXPORTER_OTLP_ENDPOINT` |

## 4. Identity and Authorization Implementation

### CRM auth

- `/auth/login` validates credentials and issues JWTs
- multi-membership users receive partial tokens and must switch societe
- token revocation is enforced through `tv` and cached user security state

### Portal auth

- `POST /api/portal/auth/request-link`
- `GET /api/portal/auth/verify`
- separate `ROLE_PORTAL` token path

### Context propagation

- `JwtAuthenticationFilter` sets `SocieteContext`
- `SocieteContextHelper` is the injectable façade used by many services
- `RlsContextAspect` mirrors active societe scope into PostgreSQL transaction-local state

## 5. Persistence Model

Persistence patterns visible in the code:

- UUID identifiers
- service-led transaction boundaries
- repository methods scoped by `societeId`
- optimistic locking on mutable records
- soft deletion or archival for high-value records
- explicit row locking in reservation and deposit paths

Database-level safety:

- one signed contract per property via partial unique index
- RLS on `contact` and `property`

## 6. Caching

Default cache backend:

- Caffeine

Optional backend:

- Redis, activated by `REDIS_ENABLED=true`

Usage patterns confirmed from code:

- user security / token revocation state
- dashboard or societe list style cache usage

## 7. Async and Scheduler Design

Implemented scheduled or asynchronous processes:

| Process | Purpose |
| --- | --- |
| Outbox dispatcher | deliver queued email/SMS with retries |
| Deposit workflow scheduler | expire pending deposits and raise reminders |
| Reservation expiry scheduler | expire reservations and release property state |
| Payment reminder scheduler | pre-due and overdue payment notifications |
| Portal token cleanup scheduler | remove expired portal tokens |
| GDPR retention scheduler | retention sweep / anonymization processing |

Design pattern:

- business transaction persists state first
- secondary communication or follow-up work happens asynchronously

## 8. Storage

### Relational storage

- PostgreSQL is mandatory
- Liquibase manages schema evolution
- Hibernate uses `ddl-auto=validate`

### Files and object storage

- local filesystem is default for media
- S3-compatible storage is optional
- document and media services sit above the storage mechanism

## 9. Delivery Channels

### Email

- `NoopEmailSender` behavior when SMTP is not configured
- direct send for portal magic links
- outbox-based send for most CRM notifications

### SMS

- optional Twilio integration
- no-op behavior when credentials are absent

## 10. Frontend Architecture

The Angular application is organized around:

- route-based surfaces for CRM, super-admin, and portal
- guards for authenticated, admin, and super-admin access
- interceptors for CRM and portal bearer tokens
- standalone components with feature-oriented structure

Current token storage:

- CRM token -> `hlm_access_token`
- Portal token -> `hlm_portal_token`

## 11. CI/CD and Verification

The repository includes GitHub Actions workflows for:

- backend unit and integration testing
- frontend tests and build
- Docker image builds
- compose smoke validation
- Playwright E2E
- security scanning

## 12. Deployment Model

### Local

- Angular dev server on `:4200`
- Spring Boot on `:8080`
- compose-backed PostgreSQL, Redis, MinIO

### Containerized

- frontend image serves built Angular assets through Nginx
- backend image runs Spring Boot
- compose files describe dependencies and health checks

## 13. Observability

Implemented observability features include:

- Actuator `health` and `info`
- correlation IDs through a request filter
- optional OTLP trace export

## 14. Confirmed Technical Gaps

These are implementation-level findings, not assumptions:

- `SecurityConfig` still advertises `POST /tenants`, but no active controller was found.
- `SocieteContext` has a role slot that is currently not populated by the request flow.
- `LoginRateLimiter.cleanupIdleBuckets()` contains a tautological comparison before actual capacity pruning; behavior still works, but the condition is dead logic.
- The active frontend auth model still omits multi-societe selection support exposed by backend DTOs.
- Springdoc is configured to serve `/api-docs`, but the public security matcher still permits `/v3/api-docs/**`.
