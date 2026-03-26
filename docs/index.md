# YEM SaaS Platform Documentation

> Last updated: 2026-03-25 — 52 Liquibase changesets, Wave 4 complete.

The platform is a multi-société real-estate CRM (Spring Boot 3.5.8 + Angular 19 + PostgreSQL 16).
Runtime model uses `societe` + `app_user_societe`. Older `tenant` terminology in legacy course
material is historical only — the source-of-truth docs below reflect the current codebase.

**Rule**: when any guide conflicts with `context/` or `spec/` documents, trust `context/` and
`spec/` first.

---

## Start Here

| Goal | Document |
| --- | --- |
| I'm new — give me an overview | [01-overview/README.md](01-overview/README.md) |
| Set up locally (first time) | [07-deployment/local-setup.md](07-deployment/local-setup.md) |
| Quick answers | [10-faq/README.md](10-faq/README.md) |
| Operations runbook | [runbook-operations.md](runbook-operations.md) |

---

## Enterprise Documentation (numbered sections)

| Section | Contents |
| --- | --- |
| [01-overview/](01-overview/README.md) | Platform overview, feature inventory, roles, topology, tech stack, seed accounts |
| [02-architecture/](02-architecture/README.md) | Runtime architecture, request lifecycle, multi-société isolation, AOP ordering, async propagation |
| [03-backend/](03-backend/README.md) | Backend developer guide: module structure, adding features, test patterns, RBAC, error handling |
| [04-frontend/](04-frontend/README.md) | Angular guide: route trees, guards, interceptors, test patterns, Jasmine/Playwright pitfalls |
| [05-database/](05-database/README.md) | Liquibase rules, changeset history (001–052), entity reference, RLS coverage |
| [06-security/](06-security/README.md) | JWT claims, auth flows, token revocation, rate limiting, RLS policy logic, GDPR controls |
| [07-deployment/](07-deployment/README.md) | Local setup (full env var table), production deployment, TLS, bootstrap, hardening checklist |
| [08-integrations/](08-integrations/README.md) | Email/SMTP, Twilio SMS, Redis, MinIO/S3, OTel — activation and configuration |
| [09-troubleshooting/](09-troubleshooting/README.md) | Symptom → cause → fix: startup, auth, IT tests, Docker, frontend, business logic, CI |
| [10-faq/](10-faq/README.md) | Frequently asked questions organized by topic |

---

## Source of Truth — Context

Authoritative descriptions of the running system derived from code and tests.

| Document | Purpose |
| --- | --- |
| [context/ARCHITECTURE.md](context/ARCHITECTURE.md) | Runtime architecture, request flow, isolation layers, async/scheduler, AOP ordering |
| [context/DATA_MODEL.md](context/DATA_MODEL.md) | Domain model, key tables, RLS coverage, Wave 4 additions |
| [context/MODULES.md](context/MODULES.md) | Backend and frontend module responsibilities |
| [context/SECURITY_BASELINE.md](context/SECURITY_BASELINE.md) | AuthN/authZ, JWT, revocation, rate limits, lockout, portal, 3-layer isolation |
| [context/DECISIONS.md](context/DECISIONS.md) | Important design decisions visible in the codebase |

---

## Source of Truth — Specifications

| Document | Purpose |
| --- | --- |
| [spec/functional-spec.md](spec/functional-spec.md) | Business workflows, actors, state transitions, enforced rules |
| [spec/technical-spec.md](spec/technical-spec.md) | Stack, configuration, deployment model, integration points (updated Wave 4) |
| [spec/api-reference.md](spec/api-reference.md) | Full endpoint catalog with auth requirements and payload guidance |
| [spec/requirements-spec.md](spec/requirements-spec.md) | Functional and non-functional requirements |

---

## Architecture Decision Records

Immutable records of significant design decisions.

| Record | Decision |
| --- | --- |
| [adr/001-multi-societe-not-multi-tenant.md](adr/001-multi-societe-not-multi-tenant.md) | Shared-schema multi-tenancy with `societe_id` column |
| [adr/002-admin-cannot-escalate-to-admin.md](adr/002-admin-cannot-escalate-to-admin.md) | Only SUPER_ADMIN can assign ADMIN role |
| [adr/003-jwt-tokenversion-revocation.md](adr/003-jwt-tokenversion-revocation.md) | Token revocation via `tv` claim + cached `token_version` |
| [adr/004-optimistic-locking-version-field.md](adr/004-optimistic-locking-version-field.md) | Optimistic locking via `@Version` on mutable entities |

---

## Operations

| Document | Purpose |
| --- | --- |
| [runbook-operations.md](runbook-operations.md) | Day-to-day operations: deployment, health checks, smoke tests, admin operations |

---

## Learning Material (Courses)

Course modules updated for the `societe`-centric model (modules 01 and 02 fully refreshed):

| Module | Topic | Status |
| --- | --- | --- |
| [course/01-multi-tenancy.md](course/01-multi-tenancy.md) | Multi-société isolation, SocieteContext, RLS | ✅ Refreshed 2026-03-25 |
| [course/02-jwt-authentication.md](course/02-jwt-authentication.md) | JWT claims, auth flow, revocation, partial tokens | ✅ Refreshed 2026-03-25 |
| [course/03-token-revocation.md](course/03-token-revocation.md) | Token revocation mechanics | Historical |
| [course/04-rbac.md](course/04-rbac.md) | Role-based access control | Historical |
| [course/05-filter-chain.md](course/05-filter-chain.md) | Spring Security filter chain | Historical |
| [course/06-domain-layer.md](course/06-domain-layer.md) | Domain layer design | Historical |
| [course/07-liquibase-migrations.md](course/07-liquibase-migrations.md) | Liquibase patterns | Historical |
| [course/08-error-handling.md](course/08-error-handling.md) | Error handling | Historical |
| [course/09-caching.md](course/09-caching.md) | Caching with Caffeine/Redis | Historical |
| [course/10-outbox-pattern.md](course/10-outbox-pattern.md) | Transactional outbox | Historical |
| [course/11–19](course/) | Contact state machine through OTel | Historical |

"Historical" = content is still useful but written before Wave 4 and may reference older
terminology. The numbered sections (01–10) above are the current authoritative entry points.

---

## Supporting Guides

| Document | Purpose |
| --- | --- |
| [guides/engineer/getting-started.md](guides/engineer/getting-started.md) | Legacy day-one setup (use 07-deployment/ instead) |
| [guides/engineer/docker.md](guides/engineer/docker.md) | Docker and compose workflows |
| [guides/engineer/ci-cd.md](guides/engineer/ci-cd.md) | CI pipeline overview |
| [guides/engineer/testing.md](guides/engineer/testing.md) | Testing strategy |
| [guides/engineer/troubleshooting.md](guides/engineer/troubleshooting.md) | Legacy troubleshooting (use 09-troubleshooting/ instead) |
| [guides/engineer/object-storage.md](guides/engineer/object-storage.md) | Media storage backends |
| [guides/engineer/https-tls.md](guides/engineer/https-tls.md) | TLS and reverse-proxy setup |
| [guides/user/](guides/user/) | User-facing guides (contacts, properties, sales pipeline, portal) |
