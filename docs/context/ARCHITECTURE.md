# Architecture Overview

This document describes the implemented architecture of the current YEM SaaS Platform.

## 1. System Intent

The platform supports real-estate commercial operations for multiple societes on a shared application stack.
It combines:

- a staff CRM for `ADMIN`, `MANAGER`, and `AGENT`
- a platform console for `SUPER_ADMIN`
- a buyer portal authenticated with one-time magic links

The repository is a monorepo:

| Path | Responsibility |
| --- | --- |
| [../../hlm-backend](../../hlm-backend) | API, schedulers, business rules, migrations |
| [../../hlm-frontend](../../hlm-frontend) | CRM, superadmin UI, portal UI |
| [../../nginx](../../nginx) | reverse-proxy and TLS reference configuration |
| [../../docs](../../docs) | context, specifications, guides, and learning material |

## 2. Runtime Topology

```text
Browser
  Staff login and CRM      -> /login, /app/*
  Superadmin console       -> /superadmin/*
  Buyer portal             -> /portal/*
        |
        v
Nginx or Angular dev proxy
  /auth
  /api
  /api/portal
        |
        v
Spring Boot 3.5.11
  Security filter chain
  JWT and cookie handling
  Controllers
  Services / transactions
  Repositories
  Schedulers / outbox / PDF generation
        |
        v
PostgreSQL 16
  Liquibase-managed schema
  societe-scoped aggregates
  row-level security policies

Optional infrastructure:
  Redis, MinIO/S3, SMTP/Brevo, Twilio, Prometheus-compatible metrics, OTLP tracing
```

## 3. Identity And Access Model

### Core records

| Record | Meaning |
| --- | --- |
| `app_user` | global identity, password, token version, lockout, profile |
| `societe` | company, quotas, branding, compliance, subscription, lifecycle |
| `app_user_societe` | company membership and societe-scoped role |

### Role model

- `SUPER_ADMIN` is a platform role stored on `app_user.platform_role`
- `ADMIN`, `MANAGER`, and `AGENT` are societe roles stored on `app_user_societe.role`
- a user can belong to multiple societes
- the active societe is carried by the JWT `sid` claim for CRM sessions

## 4. Session Architecture

### Staff CRM sessions

1. `POST /auth/login` receives email and password.
2. `AuthService` performs rate limiting, password validation, lockout checks, and membership lookup.
3. Single-membership users receive a full staff JWT.
4. Multi-membership users receive a partial token and a list of available societes.
5. The client calls `POST /auth/switch-societe` to obtain the final scoped session.
6. `AuthController` stores the final JWT in the `hlm_auth` httpOnly cookie.
7. `JwtAuthenticationFilter` extracts the cookie or bearer token, validates it, loads role information, and populates `SecurityContextHolder` plus `SocieteContext`.

### Buyer portal sessions

1. `POST /api/portal/auth/request-link` receives `email` and `societeKey`.
2. `PortalAuthService` generates a 32-byte token, stores only the SHA-256 hash, and sends the link.
3. `GET /api/portal/auth/verify?token=...` validates the token, marks it used, and issues a portal JWT.
4. `PortalAuthController` stores the token in the `hlm_portal_auth` cookie scoped to `/api/portal`.

### Why two cookie types?

- staff and buyer sessions do not share the same authorities
- the portal cookie is path-restricted so it cannot bleed into CRM requests
- portal tokens do not participate in CRM user revocation checks

## 5. Request Lifecycle

### CRM request flow

1. Browser sends request with `hlm_auth`.
2. `JwtAuthenticationFilter` validates signature, expiry, authorities, `sid`, and token version.
3. `SocieteContext` is populated with user ID, role, societe ID, and impersonation info when relevant.
4. `SecurityConfig` and method-level `@PreAuthorize` checks authorize the route.
5. Controller delegates quickly to a service.
6. Service reads `societeId` from the current context and calls repositories with societe-scoped predicates.
7. `RlsContextAspect` sets `SET LOCAL app.current_societe_id` inside the active transaction.
8. PostgreSQL enforces RLS as a final barrier.
9. The filter clears ThreadLocals in a `finally` block.

### Portal request flow

1. Browser sends request with `hlm_portal_auth`.
2. `JwtAuthenticationFilter` recognizes `ROLE_PORTAL`.
3. `SocieteContext` is still populated with `sid`, but the principal is the buyer contact ID.
4. Portal controllers and services apply buyer-specific ownership checks before returning data.

## 6. Application Layering

The backend is domain-oriented but follows a consistent layered style:

```text
Controller
  -> request validation and transport concerns
Service
  -> business rules, transactions, orchestration
Repository
  -> JPA access with societe-scoped queries
Entity / DTO
  -> persistence state and transport contracts
```

Cross-cutting flows live in dedicated modules:

- `auth` for cookies, JWT parsing, revocation, rate limiting, and lockout
- `societe` for context propagation, impersonation, RLS, and membership lookup
- `common` for validation, error contracts, and shared utilities
- `outbox`, `notification`, and `gdpr` for asynchronous or compliance-heavy concerns

## 7. Frontend Surface Map

### CRM

- dashboard, cash, and receivables views
- projects, immeubles, tranches, and properties
- contacts, reservations, deposits, ventes, contracts, and schedules
- commissions, notifications, messages, tasks, and audit activity
- admin-only user and template management

### Superadmin

- societe list, create, detail, edit, logo, compliance, stats, and member inspection
- impersonation entry point back into the CRM

### Portal

- login and verification screens
- vente list and detail
- contract list
- payment schedule views
- property detail view

## 8. Data Isolation Strategy

Isolation is implemented in three layers:

### Application layer

- services must obtain the active societe from context
- repository queries always include `societeId`
- controllers never trust client-provided societe identifiers for scoping

### Framework layer

- route families are separated in `SecurityConfig`
- `SUPER_ADMIN` routes are isolated under `/api/admin/**`
- portal routes require `ROLE_PORTAL`

### Database layer

- `RlsContextAspect` sets `app.current_societe_id` inside the current transaction
- PostgreSQL RLS policies filter rows by `societe_id`
- system-mode schedulers can use the nil UUID bypass when explicitly designed to do so

## 9. Concurrency And Consistency

### Optimistic locking

Mutable entities such as `societe`, `contact`, `property`, and `vente` use `@Version` to detect lost updates.

### Schedulers and distributed coordination

- `OutboundDispatcherScheduler` dispatches queued messages
- `DepositWorkflowScheduler` manages deposit and reminder progression
- `PortalTokenCleanupScheduler` removes expired or used portal tokens
- `DataRetentionScheduler` runs privacy retention tasks
- ShedLock prevents duplicate execution in multi-instance deployments

### Async context propagation

`SocieteContextTaskDecorator` propagates the active context into `@Async` work so background tasks do not lose scoping information.

## 10. Storage And Document Architecture

- generic documents and vente-specific documents use the media storage abstraction
- property media uses the same storage strategy
- the default implementation is local disk
- S3-compatible object storage is enabled by configuration
- PDFs are rendered from Thymeleaf templates through OpenHTMLToPDF

## 11. Deployment Shapes

### Local developer stack

- Docker Compose starts PostgreSQL, Redis, MinIO, backend, and frontend
- Angular `ng serve` can also be used for UI iteration

### Reverse-proxied production

- Nginx terminates TLS
- Spring Boot usually runs on plain HTTP behind the proxy
- `FORWARD_HEADERS_STRATEGY=FRAMEWORK` ensures secure redirects and cookie behavior
- secure cookies and exact CORS origins must be configured explicitly

## 12. Architectural Themes Worth Preserving

- one repository, three user surfaces, one consistent security model
- separate auth mechanisms for staff and buyers
- explicit societe scoping at every layer
- asynchronous delivery for messages and reminders
- code-first documentation grounded in controllers, entities, and route maps
