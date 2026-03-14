# PROJECT_CONTEXT.md — LLM Context Pack

_Compact execution context. Updated: 2026-03-11._

## Product Snapshot
Multi-tenant SaaS CRM for real-estate promotion teams.
- CRM users: `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_AGENT`
- Buyer portal users: `ROLE_PORTAL` (read-only, magic-link auth)
- Isolation model: tenant-scoped data (`tid` JWT claim -> `TenantContext` -> tenant-filtered queries)

## Fast Read Order (Prompt Efficiency)
Use this order to minimize context load and mistakes:
1. `context/PROJECT_CONTEXT.md` (this file) for scope and invariants.
2. `context/ARCHITECTURE.md` for request flow and module boundaries.
3. `context/DOMAIN_RULES.md` for lifecycle/state-machine behavior.
4. `context/SECURITY_BASELINE.md` for JWT/RBAC/endpoint exposure.
5. `context/COMMANDS.md` for canonical run/test commands.
6. `context/CONVENTIONS.md` for coding and testing conventions.

## Task Routing Map
Pick files/packages by intent:

| Task | Primary Backend Area | Typical Frontend Area | Tests to Touch |
|------|----------------------|-----------------------|----------------|
| Auth/JWT/RBAC | `auth/`, `user/` | `core/auth/`, guards/interceptors | `*IT` + auth unit tests |
| Tenant isolation issue | `tenant/`, feature `service/` + `repo/` | feature service using `/api` | cross-tenant IT |
| CRM feature CRUD | feature `api/service/repo/domain` | `features/*` | service unit + controller IT |
| Commercial workflow (deposit/contract) | `deposit/`, `contract/`, `property/` | `features/prospect-detail`, `features/contracts` | workflow IT |
| Payments v2 | `payments/` | `features/contracts/payment-schedule*`, cash dashboard | `PaymentScheduleIT` |
| Portal behavior | `portal/` + `auth/security` | `portal/*` | portal IT suites |
| KPI/dashboard | `dashboard/` + aggregate repos | `features/dashboard/*` | dashboard IT |
| Messaging/reminders | `outbox/`, `reminder/`, `notification/` | `features/outbox` | outbox/reminder IT |

## Stack (Quick)
- Backend: Spring Boot 3.5.8, Java 21, Maven, PostgreSQL, Liquibase, Caffeine
- Frontend: Angular 19.2, standalone components, TypeScript 5.7
- Auth: JWT HS256, Spring Security, OAuth2 Resource Server
- Testing: JUnit 5, Surefire (`*Test`), Failsafe + Testcontainers (`*IT`)
- CI: 4 workflows (`backend-ci`, `frontend-ci`, `snyk`, `secret-scan`)

## Repo Layout
```text
hlm-backend/src/main/java/com/yem/hlm/backend/  # backend packages
hlm-frontend/src/app/                            # Angular app
docs/                                            # developer and product docs
context/                                         # compact LLM/operator context
.github/workflows/                               # CI workflows
```

## Core Invariants (Never Break)
1. Tenant identity is server-derived only (`TenantContext.getTenantId()`), never payload-derived.
2. `@PreAuthorize("hasRole('ADMIN')")` style only (never `hasRole('ROLE_ADMIN')`).
3. Liquibase is additive-only; never modify applied changesets.
4. Controllers return DTOs only; entities stay internal.
5. Errors must use `ErrorCode` + `ErrorResponse`.
6. Frontend API calls must stay relative (`/api`, `/auth`), never hardcoded backend host.
7. Portal JWT `sub` is `contactId`; CRM JWT `sub` is `userId`.
8. `payment/` (v1) package is **deleted**; all payment work targets `payments/` (v2) only.

## Role Model (Operational)
- `ROLE_ADMIN`: full tenant operations, admin-only endpoints, user/rule management.
- `ROLE_MANAGER`: broad operational write access, no tenant admin/user management.
- `ROLE_AGENT`: mostly read; ownership-scoped on sensitive workflows (deposits/contracts/payments depending endpoint).
- `ROLE_PORTAL`: access only under `/api/portal/**` for own buyer data.

## Auth Flows
- CRM: `POST /auth/login` -> JWT (`sub=userId`, `tid`, `roles`, `tv` tokenVersion).
- Portal: `POST /api/portal/auth/request-link` -> email link -> `GET /api/portal/auth/verify` -> portal JWT (`sub=contactId`, `tid`, `roles=[ROLE_PORTAL]`, no `tv`).

## Execution Checklist (For Any Change)
1. Identify impacted bounded context (backend package + frontend feature + docs).
2. Preserve tenant and RBAC enforcement before changing business logic.
3. Add/adjust tests:
   - Unit tests for deterministic service logic.
   - Integration tests for endpoint contract, RBAC, and tenant isolation.
4. Run canonical commands from `context/COMMANDS.md`.
5. Update docs/context when behavior, commands, API surface, or workflow semantics changed.

## Seed Data (Local)
- Tenant key: `acme`
- Admin: `admin@acme.com` / `Admin123!`
- Loaded by Liquibase on startup

## Sprint 2 Changes (2026-03-14)

- **SmtpEmailSender** fully wired (`spring.mail.*` bridge with SMTP timeouts); `TwilioSmsSender` added (Twilio SDK 10.4.1, conditional on `TWILIO_ACCOUNT_SID`)
- **Portal magic-link email** now sends branded HTML template; URL fixed to `/portal/verify?token=`
- **`PortalTokenCleanupScheduler`** added (daily 03:00, `deleteExpiredAndUsed` JPQL)
- **DB migration 028**: orphaned v1 payment tables dropped (`payment`, `payment_call`, `payment_tranche`, `payment_schedule`) with `preConditions MARK_RAN`
- **HTTPS implemented**: Mode A (embedded Tomcat, `SSL_ENABLED`, PKCS12, TLS 1.2/1.3, HSTS) + Mode B (Nginx, `nginx/nginx.conf`, `FORWARD_HEADERS_STRATEGY=FRAMEWORK`)
- **`TlsRedirectConfig`**: HTTP→HTTPS redirect connector active when `server.ssl.enabled=true`
- **HSTS** now conditional on `SSL_ENABLED=true`; disabled over plain HTTP
- **TLS test**: `TlsConfigIT` (HTTPS health check + HSTS header assertion)
- **New file**: `nginx/nginx.conf` — production TLS termination reference
- **New file**: `docs/https.md` — complete HTTPS setup guide
- **Angular Audit Trail** component at `/app/audit` (ADMIN/MANAGER only, `GET /api/audit/commercial`)
- **ESLint** configured in `hlm-frontend/` (`@angular-eslint`, `npm run lint`)

## Canonical References
- Architecture: `context/ARCHITECTURE.md`
- Domain rules: `context/DOMAIN_RULES.md`
- Security: `context/SECURITY_BASELINE.md`
- Commands: `context/COMMANDS.md`
- Conventions: `context/CONVENTIONS.md`
- Full docs hub: `docs/README.md`
