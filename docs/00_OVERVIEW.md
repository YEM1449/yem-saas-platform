# 00 — Project Overview

## Why This Document Exists
This document gives a precise, high-signal overview of the platform so engineers can:
- understand system scope and boundaries,
- navigate the repository quickly,
- identify critical invariants before making changes,
- choose the right detailed docs for their task.

Use this file as the entry point before jumping into implementation.

## Product Summary
YEM SaaS Platform is a multi-tenant CRM for real-estate promotion teams, with a separate buyer portal.

Primary capabilities:
- CRM operations for prospects, properties, projects, deposits, contracts, payments, commissions, and dashboards.
- Buyer self-service portal via magic-link authentication.
- Multi-tenant isolation enforced from JWT claim `tid` through service/repository boundaries.
- Transactional outbox for asynchronous email/SMS dispatch.

## System Boundaries
### Actors
- CRM user: `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_AGENT`
- Buyer portal user: `ROLE_PORTAL`

### Main channels
- CRM API: `/api/**`
- Portal API: `/api/portal/**`
- Authentication: `/auth/login`, `/api/portal/auth/*`

### Persistence and integration
- PostgreSQL (Liquibase-managed schema)
- Email/SMS provider abstraction (`EmailSender`, `SmsSender`)

## Architecture at a Glance
```text
Angular SPA (hlm-frontend)
  -> /auth, /api, /dashboard, /actuator (via dev proxy in local mode)
Spring Boot API (hlm-backend)
  -> Auth filter populates TenantContext from JWT
  -> Services enforce business rules + RBAC assumptions
  -> Repositories enforce tenant scoping
PostgreSQL
  -> Liquibase additive migrations
```

## Repository Map (What to Open and When)
```text
yem-saas-platform/
├── hlm-backend/          # Spring Boot backend (Java 21)
├── hlm-frontend/         # Angular 19 SPA
├── docs/                 # Human-facing engineering docs
├── context/              # Compact context for coding agents / fast reference
├── scripts/              # Utility scripts (e.g., smoke-auth.sh)
├── .github/workflows/    # CI workflows
├── .env.example          # Runtime env template
└── AGENTS.md             # Canonical project operating instructions
```

### Backend package map (high-value)
- `auth/`: JWT providers, security config, request filter, token-version validation
- `tenant/`: tenant entity + request-scoped tenant context
- `contact/`, `property/`, `project/`, `deposit/`, `contract/`: core commercial workflow
- `payments/`: preferred payment schedule model (v2)
- `payment/`: deprecated payment API surface (v1)
- `dashboard/`: commercial, cash, receivables aggregations
- `outbox/`: async message composition and dispatch
- `portal/`: buyer authentication and read-only portal access
- `media/`: property media storage abstraction + local implementation
- `common/`: shared error envelope and cross-cutting primitives

## Core Business Workflow (Current Implementation)
### Commercial lifecycle
1. Contact enters CRM as prospect.
2. Property is created (starts `DRAFT`), then promoted to `ACTIVE`.
3. Deposit is created (`PENDING`) and property moves to `RESERVED`.
4. Deposit can be confirmed/canceled/expired.
5. Contract is created (`DRAFT`) and then signed (`SIGNED`) to complete sale.
6. Signed contract marks property as `SOLD`.

### Payment lifecycle
- Preferred model (`payments/`): schedule items with issue/send/payment/cancel workflow.
- Legacy model (`payment/`): still available for compatibility but deprecated (sunset target: `2026-12-31`).
- Retirement execution guide: [v2/payment-v1-retirement-plan.v2.md](v2/payment-v1-retirement-plan.v2.md).

### Portal lifecycle
1. Buyer requests magic link (`POST /api/portal/auth/request-link`).
2. Buyer verifies token (`GET /api/portal/auth/verify`).
3. Portal JWT grants access only to own contracts/schedules/property data.

## Non-Negotiable Engineering Invariants
1. Tenant identity is server-derived (`TenantContext`), never trusted from client payload.
2. `@PreAuthorize` must use `hasRole('ADMIN')` style (no `ROLE_` prefix in expression).
3. Applied Liquibase changesets are immutable; schema evolution is additive only.
4. Controllers expose DTOs, not JPA entities.
5. API errors use `ErrorResponse` + `ErrorCode` contract.
6. Frontend uses relative API paths, never hardcoded backend host.
7. Portal and CRM sessions are isolated (`hlm_portal_token` vs `hlm_access_token`).

## Current Delivery Scope (Phase Snapshot)
| Phase | Area | Status |
|------|------|--------|
| 1 | Core CRM (contacts, properties, deposits, contracts baseline) | Done |
| 2 | Outbox messaging (email/SMS) | Done |
| 3 | Commercial intelligence (commissions, receivables, analytics) | Done |
| 4 | Buyer portal (magic link + isolated portal routes) | Done |

## Quality and CI Model
- Backend CI: unit tests, package, integration tests.
- Frontend CI: test + build.
- Security scanning: Snyk (code + OSS dependencies).
- Secret scan: pattern-based audit workflow.

Canonical command source: [`context/COMMANDS.md`](../context/COMMANDS.md)

## Reading Path by Role
### New backend engineer
1. `docs/05_DEV_GUIDE.md`
2. `docs/01_ARCHITECTURE.md`
3. `context/DOMAIN_RULES.md`
4. `context/SECURITY_BASELINE.md`

### New frontend engineer
1. `docs/05_DEV_GUIDE.md`
2. `docs/frontend.md`
3. `docs/api-quickstart.md`
4. `context/CONVENTIONS.md`

### Tech lead / architect
1. `docs/01_ARCHITECTURE.md`
2. `docs/07_RELEASE_AND_DEPLOY.md`
3. `docs/specs/Technical_Spec.md`
4. `context/PROJECT_CONTEXT.md`

## First 60-Minute Onboarding Path
1. Start backend and frontend from `docs/05_DEV_GUIDE.md`.
2. Login with seed user (`acme`, `admin@acme.com`, `Admin123!`).
3. Run auth smoke script: `scripts/smoke-auth.sh`.
4. Execute first API flow in `docs/api-quickstart.md`.
5. Confirm understanding of tenant + RBAC model via `context/SECURITY_BASELINE.md`.

## Related Documents
- Developer setup and workflows: [05_DEV_GUIDE.md](05_DEV_GUIDE.md)
- Architecture deep dive: [01_ARCHITECTURE.md](01_ARCHITECTURE.md)
- API first calls: [api-quickstart.md](api-quickstart.md)
- New engineer ramp-up program: [08_ONBOARDING_COURSE.md](08_ONBOARDING_COURSE.md)
- Completion checklist: [09_NEW_ENGINEER_CHECKLIST.md](09_NEW_ENGINEER_CHECKLIST.md)
