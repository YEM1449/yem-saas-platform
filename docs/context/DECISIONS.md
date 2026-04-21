# Design Decisions

This document captures the major architectural choices visible in the current implementation.
For immutable historical records, also see the ADRs in [../adr](../adr).

## 1. Shared Schema Multi-Societe Model

- Decision: use one PostgreSQL schema with `societe_id` on tenant-scoped aggregates
- Why: operational simplicity, shared reporting, and lower infrastructure overhead than database-per-tenant
- Consequence: isolation must be enforced rigorously in code, tests, and RLS
- Related ADR: [../adr/001-multi-societe-not-multi-tenant.md](../adr/001-multi-societe-not-multi-tenant.md)

## 2. Separate Platform Role From Societe Role

- Decision: keep `SUPER_ADMIN` on `app_user.platform_role` and business roles on `app_user_societe`
- Why: platform governance and company operations have different trust boundaries
- Consequence: session issuance and authorization logic must merge two role sources safely

## 3. Cookie-Based Final Sessions

- Decision: store final JWTs in httpOnly cookies instead of exposing them to the SPA
- Why: reduce XSS token theft risk and simplify browser session behavior
- Consequence: frontend session validation happens through backend calls such as `/auth/me` and portal tenant info

## 4. Partial Token For Multi-Societe Selection

- Decision: return a short-lived bearer token only for the societe-selection step
- Why: allow a user to finish login without exposing the final staff session token to JavaScript
- Consequence: `/auth/switch-societe` remains `permitAll` but validates the partial token internally

## 5. Separate Portal Authentication Stack

- Decision: buyer access uses portal-specific magic links, JWTs, and cookies
- Why: buyers are not staff users and should not share the CRM session model
- Consequence: portal authorization logic is intentionally isolated and lighter than CRM revocation logic
- Related ADR: [../adr/003-jwt-tokenversion-revocation.md](../adr/003-jwt-tokenversion-revocation.md)

## 6. ThreadLocal Societe Context Plus RLS

- Decision: combine `SocieteContext` in application code with PostgreSQL RLS in the database
- Why: application code needs the active scope for orchestration, while the database provides a last-resort safety net
- Consequence: AOP ordering and async propagation become critical implementation details

## 7. Transaction Before RLS Aspect

- Decision: ensure transaction interception wraps `RlsContextAspect`
- Why: `SET LOCAL` only matters inside an open transaction
- Consequence: changing AOP order can silently break row-level security behavior
- Related ADR: [../adr/004-optimistic-locking-version-field.md](../adr/004-optimistic-locking-version-field.md)

## 8. Domain-Oriented Backend Modules

- Decision: organize backend code by business domain rather than horizontal layers only
- Why: localize rules, DTOs, repositories, and services around a bounded business area
- Consequence: cross-cutting concerns such as auth, common errors, and societe context must stay disciplined to avoid duplication

## 9. Keep `vente` And `contract` As Complementary Aggregates

- Decision: preserve the commercial pipeline aggregate (`vente`) separately from the formal contract aggregate (`sale_contract`)
- Why: users need a negotiation and delivery workflow that is richer than the legal contract record alone
- Consequence: documentation and onboarding must explain the distinction clearly to avoid confusion

## 10. Outbox For Reliable Delivery

- Decision: queue outbound messages in the database and dispatch them asynchronously
- Why: sending email or SMS should not make business transactions brittle
- Consequence: monitoring, retries, and idempotency matter as much as delivery providers themselves

## 11. S3-Compatible Storage Abstraction

- Decision: hide storage vendor details behind a media storage interface
- Why: allow local disk for development and S3-compatible providers in production without controller changes
- Consequence: provider-specific behavior must be handled at the storage adapter layer

## 12. Compliance Data Lives In The Domain Model

- Decision: GDPR and local compliance metadata is embedded in contacts, users, and societes instead of being delegated to a separate privacy service
- Why: privacy constraints directly affect the business lifecycle and user experience
- Consequence: feature teams must treat compliance fields as core data, not as optional afterthoughts
