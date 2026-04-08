# Engineer Onboarding Course

This course is the structured learning path for engineers onboarding onto the YEM SaaS Platform. It complements the implementation references in `docs/context/` and `docs/spec/`, but it is written as a guided progression rather than a static reference manual.

## Before You Start

Use these companion references while working through the modules:

- [../context/ARCHITECTURE.md](../context/ARCHITECTURE.md)
- [../context/DATA_MODEL.md](../context/DATA_MODEL.md)
- [../context/SECURITY_BASELINE.md](../context/SECURITY_BASELINE.md)
- [../guides/engineer/getting-started.md](../guides/engineer/getting-started.md)

Important terminology note:

- the active runtime and business language use `société` / `societe`
- some older migration filenames still contain `tenant` because Liquibase history is immutable
- when a module references those older filenames, it does so as historical implementation detail, not as current domain language

## How To Use This Course

The modules are ordered. If you are new to the codebase, read them in sequence.

Each module is expected to do four things:

- explain one architectural topic in the context of this repository
- map the concept to real source files
- call out the invariants and tradeoffs that matter in production
- give you a small exercise so you can verify understanding in code

Recommended pace:

- full onboarding pass: 1 to 2 days
- security-only pass: Modules 01 to 05, 15, 18, 19
- backend/data pass: Modules 06, 07, 09, 10, 14, 16, 17
- business-domain pass: Modules 11, 12, 13, 15

## Suggested Learning Paths

### Path A — Security and Request Flow

Start here if you will work on authentication, authorization, guards, cookies, or CI/E2E:

1. [01-multi-tenancy.md](01-multi-tenancy.md)
2. [02-jwt-authentication.md](02-jwt-authentication.md)
3. [03-token-revocation.md](03-token-revocation.md)
4. [04-rbac.md](04-rbac.md)
5. [05-filter-chain.md](05-filter-chain.md)
6. [15-client-portal.md](15-client-portal.md)
7. [18-rate-limiting-and-lockout.md](18-rate-limiting-and-lockout.md)

### Path B — Backend Delivery and Persistence

Start here if you will add features, schema changes, integrations, or scheduled jobs:

1. [06-domain-layer.md](06-domain-layer.md)
2. [07-liquibase-migrations.md](07-liquibase-migrations.md)
3. [09-caching.md](09-caching.md)
4. [10-outbox-pattern.md](10-outbox-pattern.md)
5. [16-pdf-generation.md](16-pdf-generation.md)
6. [17-object-storage.md](17-object-storage.md)
7. [19-observability-otel.md](19-observability-otel.md)

### Path C — Product and Business Workflow

Start here if you will work on CRM user journeys or buyer-facing flows:

1. [11-contact-state-machine.md](11-contact-state-machine.md)
2. [12-property-lifecycle.md](12-property-lifecycle.md)
3. [13-sales-pipeline.md](13-sales-pipeline.md)
4. [14-gdpr-compliance.md](14-gdpr-compliance.md)
5. [15-client-portal.md](15-client-portal.md)

## Module Index

| # | Module | Focus | Good Follow-up |
| --- | --- | --- | --- |
| 01 | [Multi-Tenancy](01-multi-tenancy.md) | Three-layer société isolation and RLS | 02, 05, 07 |
| 02 | [JWT Authentication](02-jwt-authentication.md) | CRM, partial, portal, and SUPER_ADMIN token models | 03, 04, 05 |
| 03 | [Token Revocation](03-token-revocation.md) | `tokenVersion`, cache checks, explicit eviction, TTL behavior | 02, 18 |
| 04 | [RBAC](04-rbac.md) | URL rules, method security, role mapping, membership roles | 05, 06 |
| 05 | [Filter Chain](05-filter-chain.md) | Request correlation, JWT resolution, authorization flow | 01, 02, 04 |
| 06 | [Domain Layer](06-domain-layer.md) | Package structure, entities, repositories, services, controllers | 07, 11, 12 |
| 07 | [Liquibase Migrations](07-liquibase-migrations.md) | Immutable schema history, rollout discipline, historical rename path | 06, 14 |
| 08 | [Error Handling](08-error-handling.md) | Error contracts and exception translation | 06, 13 |
| 09 | [Caching](09-caching.md) | Caffeine vs Redis and cache usage patterns | 03, 19 |
| 10 | [Outbox Pattern](10-outbox-pattern.md) | Reliable async delivery for email and SMS | 19 |
| 11 | [Contact State Machine](11-contact-state-machine.md) | Contact lifecycle rules and transitions | 13, 14 |
| 12 | [Property Lifecycle](12-property-lifecycle.md) | Property creation, availability, and archival semantics | 13 |
| 13 | [Sales Pipeline](13-sales-pipeline.md) | Reservation to deposit to sale workflow | 12, 15 |
| 14 | [GDPR Compliance](14-gdpr-compliance.md) | Export, rectification, anonymization, and retention | 07, 17 |
| 15 | [Client Portal](15-client-portal.md) | Buyer magic-link auth and cookie-backed portal sessions | 02, 05 |
| 16 | [PDF Generation](16-pdf-generation.md) | Thymeleaf + OpenHTMLtoPDF document generation | 10, 17 |
| 17 | [Object Storage](17-object-storage.md) | Local vs S3-compatible media storage | 16, 19 |
| 18 | [Rate Limiting and Lockout](18-rate-limiting-and-lockout.md) | Abuse prevention, throttling, and account lockout | 02, 03 |
| 19 | [Observability and OTel](19-observability-otel.md) | Health, request IDs, traces, and runtime diagnostics | 05, 09, 10 |

## What “Current” Means In These Modules

When a module describes the current platform, it should align with the active implementation:

- société-scoped runtime context via `SocieteContext`
- JWT claims using `sid` for société scope
- CRM access token stored client-side as `hlm_access_token`
- portal session carried by the `hlm_portal_auth` httpOnly cookie
- Playwright E2E in CI running against the static `ci` Angular build

If you find a mismatch between a module and the code, update the module in the same change that updates the code.

## Recommended Study Loop

For each module:

1. Read the concept section.
2. Open the listed source files.
3. Follow one concrete request or data flow end to end.
4. Complete the exercise or at least mentally simulate it.
5. Update your notes with one invariant you should not break.

## Course Maintenance Rules

When you extend or modify the course:

- prefer real file paths from this repository over abstract examples
- keep historical notes only where implementation history genuinely matters
- use `societe` terminology unless the immutable artifact itself still says `tenant`
- call out operational edge cases, not just the happy path
- when auth or API behavior changes, update the relevant course module in the same PR
