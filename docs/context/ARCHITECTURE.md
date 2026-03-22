# Architecture Overview

This document describes the implemented architecture of the YEM SaaS Platform from the current codebase.

## System Purpose

The platform supports real-estate sales operations for one or more companies (`societes`) on a shared application stack. It combines:

- CRM workflows for staff users (`ADMIN`, `MANAGER`, `AGENT`)
- platform-level governance for `SUPER_ADMIN`
- a buyer-facing portal authenticated by one-time magic links

The codebase is a monorepo:

| Path | Role |
| --- | --- |
| [hlm-backend](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend) | Spring Boot API, business logic, schedulers, Liquibase migrations |
| [hlm-frontend](/home/yem/CRM-HLM/yem-saas-platform/hlm-frontend) | Angular 19 CRM SPA, super-admin UI, client portal |
| [docs](/home/yem/CRM-HLM/yem-saas-platform/docs) | Context, specifications, guides, runbooks |
| [scripts](/home/yem/CRM-HLM/yem-saas-platform/scripts) | Smoke tests and API support material |

## Runtime Topology

```text
Browser
  |- CRM SPA                 -> /login, /app/*
  |- Super-admin SPA         -> /superadmin/*
  |- Buyer portal            -> /portal/*
  v
Nginx / Angular dev proxy
  |- /auth
  |- /api
  |- /api/portal
  v
Spring Boot 3.5.11
  |- Security filter chain
  |- REST controllers
  |- Services / transactions
  |- JPA repositories
  |- Schedulers / outbox dispatcher
  v
PostgreSQL 16
  |- Liquibase-managed schema
  |- optional defense-in-depth RLS on contact/property

Optional infrastructure
  |- Redis            shared cache for multi-instance deployments
  |- S3-compatible    object storage for media/documents
  |- SMTP             outbound email
  |- Twilio           outbound SMS
  |- OTLP collector   tracing export
```

## Identity and Scope Model

The live model is centered on three records:

| Record | Purpose |
| --- | --- |
| `app_user` | Platform identity, credentials, lockout state, token version, personal profile |
| `societe` | Company metadata, quotas, branding, compliance, subscription lifecycle |
| `app_user_societe` | User membership inside a company, including role and active flag |

Important implementation details:

- `SUPER_ADMIN` is stored in `app_user.platform_role`.
- Company-scoped roles live in `app_user_societe.role`.
- A user may belong to multiple societes.
- CRM JWTs carry `sid` for the active societe.
- Portal JWTs use the buyer contact ID as `sub` and still carry `sid`.

## Request Lifecycle

### CRM requests

1. The client calls `/auth/login` with `email` and `password`.
2. `AuthService` checks login rate limits, lockout state, password validity, and active memberships.
3. If the user has exactly one active membership, the backend issues a full CRM JWT.
4. If the user has multiple memberships, the backend issues a short-lived partial token plus the available societes.
5. The client can exchange that partial token on `POST /auth/switch-societe` for a full scoped JWT.
6. On subsequent API requests, `JwtAuthenticationFilter` validates the token, rejects partial tokens outside `/auth/switch-societe`, checks token revocation for CRM users, and populates `SecurityContextHolder` plus `SocieteContext`.
7. Controllers delegate quickly to services.
8. Transactional service methods trigger `RlsContextAspect`, which sets PostgreSQL session variable `app.current_societe_id`.
9. Services and repositories apply societe-scoped access rules.
10. The filter clears `SocieteContext` in a `finally` block.

### Portal requests

1. The buyer calls `POST /api/portal/auth/request-link` with `email` and `societeKey`.
2. `PortalAuthService` rate-limits the request, stores only a SHA-256 token hash, and sends a one-time link.
3. The frontend calls `GET /api/portal/auth/verify?token=...`.
4. The backend marks the magic-link token as used and returns a separate 2-hour `ROLE_PORTAL` JWT.
5. Portal controllers only expose contracts, payment schedules, property details, and tenant branding tied to the current buyer contact.

## Backend Layering

The backend is mostly organized by domain, with a consistent controller/service/repository pattern.

```text
api/        HTTP contract and DTOs
service/    business rules, orchestration, transactions
repo/       Spring Data JPA repositories
domain/     entities and enums
```

Cross-cutting modules include:

- `auth`: JWT, Spring Security, revocation, lockout, rate limiting
- `common`: error handling, validation, generic pagination, correlation filter
- `societe`: context handling, super-admin company management, membership model
- `admin`: bootstrap of the first `SUPER_ADMIN`

## Frontend Surfaces

The Angular app exposes three route trees:

| Prefix | Audience | Notes |
| --- | --- | --- |
| `/app/*` | CRM users | Property, contact, reservation, contract, dashboard, task, messaging, audit, admin users |
| `/superadmin/*` | `SUPER_ADMIN` | Societe creation, editing, membership view, impersonation |
| `/portal/*` | Buyer contacts | Contract list, property view, payment schedule |

Current route inventory is defined in [app.routes.ts](/home/yem/CRM-HLM/yem-saas-platform/hlm-frontend/src/app/app.routes.ts).

## Asynchronous and Scheduled Components

| Component | Behavior |
| --- | --- |
| `OutboundDispatcherService` | Polls `outbound_message`, claims batches with `FOR UPDATE SKIP LOCKED`, retries failed sends |
| `DepositWorkflowScheduler` | Expires overdue pending deposits and produces due reminders |
| `ReservationExpiryScheduler` | Expires active reservations after their expiry date |
| `ReminderService` | Marks payment items overdue and emits reminders |
| `PortalTokenCleanupScheduler` | Deletes expired or already-used portal tokens |
| `DataRetentionScheduler` | Runs GDPR retention sweeps |

Schedulers that operate across companies use `SocieteContextHelper.runAsSystem(...)` and pass societe IDs explicitly where needed.

## Data Isolation Model

Isolation is implemented primarily in application code:

- repository calls are scoped by `societeId`
- `SecurityConfig` separates platform, CRM, and portal routes
- method-level `@PreAuthorize` refines permissions
- `RlsContextAspect` adds database-level defense in depth for `contact` and `property`

Important nuance:

- PostgreSQL RLS is currently enabled only for `contact` and `property`, not the full schema
- the migration history still contains older `tenant` artifacts used for data transition, but the runtime code uses `societe`

## Notable Consistency Findings

The current architecture also contains some legacy edges that matter for maintainers:

- `SecurityConfig` still permits `POST /tenants`, but there is no active tenant-bootstrap controller in the current backend.
- [AdminUserController](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/user/api/AdminUserController.java) remains on `/api/admin/users`, while the active frontend uses `/api/mon-espace/utilisateurs`.
- The Angular login flow currently assumes every successful `/auth/login` returns a full token and does not yet implement the multi-societe selection step exposed by the backend.
