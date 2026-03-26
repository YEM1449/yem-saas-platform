# YEM SaaS Platform — Overview

> Last updated: 2026-03-25

## Executive Summary

YEM is a multi-société real-estate CRM SaaS platform that lets multiple companies share a
single application stack while keeping their data strictly isolated. Staff users manage
properties, contacts, reservations, contracts, and commissions. Buyers access their contracts
through a branded self-service portal using one-time magic links.

---

## What the System Does

| Domain | Features |
| --- | --- |
| **Company governance** | SUPER_ADMIN creates and manages sociétés, assigns admins, impersonates members |
| **User management** | ADMIN invites, roles, deactivates members; quota enforcement; invitation rate limiting |
| **Properties** | Inventory management with project grouping, media uploads, CSV import |
| **Contacts** | Unified prospect/client records, interest tracking, GDPR export/anonymization |
| **Reservations** | Property holds (7-day default), pessimistic locking, expiry scheduler |
| **Deposits** | Financial commitment against a property, confirmation/cancellation workflow |
| **Contracts** | Sale contracts, signing, cancellation, PDF generation |
| **Collections** | Payment schedules, installment tracking, cash-in recording, overdue handling |
| **Commissions** | Per-societe or per-project rules, commission computation from agreed price |
| **Dashboard** | Commercial KPIs, receivables aging, discount analytics, cash dashboard |
| **Tasks** | Follow-up items linked to contacts or properties, assignee management |
| **Documents** | Generic multipart file attachments per business entity |
| **Messaging** | Transactional outbox for email/SMS with retry and exponential backoff |
| **Notifications** | In-app bell notifications for CRM staff |
| **Buyer portal** | Magic-link authenticated portal: contracts, payment schedule, property details |
| **Audit** | Append-only commercial event log |
| **GDPR** | Contact/user export, anonymization, consent tracking, automated retention sweeps |
| **Self-service profile** | Staff can update their own profile via `GET/PATCH /api/me` |

---

## User Roles

| Role | Scope | Permissions |
| --- | --- | --- |
| `SUPER_ADMIN` | Platform | Manage sociétés, assign ADMIN, impersonate members; no direct CRM access |
| `ROLE_ADMIN` | Société | Full CRUD on all CRM resources; user management; GDPR erasure |
| `ROLE_MANAGER` | Société | Create, read, update (no delete); no user management |
| `ROLE_AGENT` | Société | Read-only on most resources; can create own tasks and contracts |
| `ROLE_PORTAL` | Société (buyer) | Read-only portal: own contracts, payment schedule, property details |

**Role assignment rules:**
- Only `SUPER_ADMIN` can assign `ADMIN` to a societe member.
- A company `ADMIN` can only assign `MANAGER` or `AGENT`.
- Roles are stored as `ADMIN`/`MANAGER`/`AGENT` in `app_user_societe.role` (no `ROLE_` prefix).

---

## System Topology

```
Browser
  ├── CRM SPA               /app/*       (ADMIN / MANAGER / AGENT)
  ├── Super-admin SPA       /superadmin/* (SUPER_ADMIN)
  └── Buyer portal SPA      /portal/*    (ROLE_PORTAL, magic-link)
            │
            ▼
  Nginx (production) / Angular proxy (local dev)
    ├── /auth       → Spring Boot :8080
    ├── /api        → Spring Boot :8080
    └── /api/portal → Spring Boot :8080
            │
            ▼
  Spring Boot 3.5.8 (Java 21)
    ├── Security filter chain
    │     JwtAuthenticationFilter → SocieteContext (ThreadLocal)
    ├── REST controllers (23 modules)
    ├── Services + JPA repositories
    │     TransactionInterceptor (outer) → RlsContextAspect (inner, sets app.current_societe_id)
    ├── @Async workers + SocieteContextTaskDecorator
    └── Schedulers + ShedLock (distributed locking via shedlock table)
            │
            ▼
  PostgreSQL 16
    ├── Liquibase schema (52 changesets, next: 053)
    └── RLS policies on 13 domain tables
            │
  Optional infrastructure
    ├── Redis          shared cache (REDIS_ENABLED=true)
    ├── MinIO / S3     object storage (MEDIA_OBJECT_STORAGE_ENABLED=true)
    ├── SMTP           email delivery (EMAIL_HOST set)
    ├── Twilio         SMS delivery (TWILIO_ACCOUNT_SID set)
    └── OTel Collector tracing export (OTEL_ENABLED=true)
```

---

## Key Design Principles

| Principle | Implementation |
| --- | --- |
| **Multi-société isolation** | `societe_id` on every domain table; `SocieteContext` ThreadLocal; `requireSocieteId()` in every service; PostgreSQL RLS on all 13 domain tables |
| **Stateless auth** | JWT with `sid` claim; `tv` claim for revocation; no session store |
| **Defense in depth** | 3-layer isolation: application → AOP (RlsContextAspect) → database (RLS) |
| **Transactional outbox** | Messages persisted atomically with business data; polled by dispatcher with retry |
| **Fail-fast configuration** | `@NotBlank` on `JWT_SECRET`; `@Validated` config classes; app refuses to start if misconfigured |
| **Additive schema evolution** | Liquibase changesets are immutable once deployed; `ddl-auto=validate` |
| **Horizontal scalability** | Stateless, Redis-backed cache, ShedLock for scheduler deduplication |

---

## Technology Stack

| Layer | Technology | Version |
| --- | --- | --- |
| Backend language | Java | 21 |
| Backend framework | Spring Boot | 3.5.8 |
| Persistence | Spring Data JPA + Hibernate | 6 |
| Database | PostgreSQL | 16 |
| Schema migrations | Liquibase | 5.0.0 |
| Security | Spring Security + OAuth2 Resource Server JWT | — |
| Cache | Caffeine (default) / Redis (optional) | — |
| Scheduler locking | ShedLock | 6.9.1 |
| Documents/PDF | Thymeleaf + OpenHTMLtoPDF | — |
| Object storage | Local FS / AWS SDK v2 (S3-compatible) | — |
| Frontend framework | Angular | 19 |
| Frontend language | TypeScript | 5 |
| Frontend serving | Angular dev server (local) / Nginx (prod) | — |
| Unit tests | Karma/Jasmine | 5.x |
| E2E tests | Playwright | — |
| Integration tests | Testcontainers PostgreSQL | — |
| CI/CD | GitHub Actions | — |
| Containers | Docker + Compose v2 | — |

---

## Repository Map

| Path | Purpose |
| --- | --- |
| `hlm-backend/` | Spring Boot application — source, tests, Liquibase changesets |
| `hlm-frontend/` | Angular application — CRM, super-admin, portal SPAs |
| `docs/` | Architecture, specifications, guides, runbook, courses |
| `scripts/` | Smoke test scripts and API helpers |
| `.github/workflows/` | CI/CD pipelines |
| `nginx/` | Nginx configuration for containerized deployment |
| `docker-compose.yml` | Full stack compose definition |

---

## Seed Accounts

| Email | Password | Role | Societe |
| --- | --- | --- | --- |
| `admin@acme.com` | `Admin123!Secure` | `ROLE_ADMIN` | acme |
| `superadmin@yourcompany.com` | `YourSecure2026!` | `SUPER_ADMIN` | — |

> Change these credentials before any non-disposable deployment.

---

## Quick Navigation

| You want to... | Go to |
| --- | --- |
| Set up locally | [07-deployment/local-setup.md](../07-deployment/local-setup.md) |
| Understand the architecture | [02-architecture/README.md](../02-architecture/README.md) |
| Add a backend feature | [03-backend/README.md](../03-backend/README.md) |
| Work on the frontend | [04-frontend/README.md](../04-frontend/README.md) |
| Write a Liquibase changeset | [05-database/README.md](../05-database/README.md) |
| Understand security/JWT/RLS | [06-security/README.md](../06-security/README.md) |
| Deploy to production | [07-deployment/production.md](../07-deployment/production.md) |
| Configure email/Redis/S3 | [08-integrations/README.md](../08-integrations/README.md) |
| Debug a problem | [09-troubleshooting/README.md](../09-troubleshooting/README.md) |
| Get quick answers | [10-faq/README.md](../10-faq/README.md) |
| See all API endpoints | [spec/api-reference.md](../spec/api-reference.md) |
| Operations runbook | [runbook-operations.md](../runbook-operations.md) |
| Architecture decisions | [adr/](../adr/) |
| Course modules | [course/](../course/) |
