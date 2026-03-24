# ADR 001 — Multi-Société Architecture Instead of Multi-Tenant

**Date:** 2026-03-22
**Authors:** YEM Platform Team

---

## Status

Accepted

---

## Context

The platform serves real-estate companies (sociétés) of varying sizes, each with their own team of users (ADMINs, MANAGERs, AGENTs). A single platform operator (SUPER_ADMIN) manages all companies.

Early iterations used a classic single-tenant design where each company had its own database schema or was identified by a `tenantId` stored in a `tenant` table. As the data model grew — users belonging to multiple companies, cross-company reporting for SUPER_ADMINs, invitation flows, and quota management — the single-tenant approach created several problems:

- A user with access to two companies had to maintain two separate accounts.
- SUPER_ADMIN operations (suspend a company, view compliance score, impersonate a user) required cross-schema queries or a separate management database.
- Invitation flows needed a user to exist in a "neutral" space before being assigned to a company.

The team evaluated three approaches:

1. **Per-tenant database** — Full isolation. Too expensive operationally at the company count being targeted. Prevents cross-company admin queries.
2. **Row-level security with `tenant_id` column** — Simpler. Used in many SaaS platforms. But does not model "one user, multiple companies" natively.
3. **Multi-société with a membership table** — A single `app_user` table, a `societe` table, and an `app_user_societe` join table that records each user's role and active status within each company.

---

## Decision

The platform uses a **multi-société** architecture:

- `app_user` — Platform-level user identity. One row per human being. Holds credentials, personal data, `tokenVersion`, and `platformRole` (used only for SUPER_ADMIN designation).
- `societe` — One row per company. Holds legal identity, branding, quotas, and subscription data.
- `app_user_societe` — Membership table. Each row binds one user to one company with a specific role (`ADMIN`, `MANAGER`, or `AGENT`) and an `actif` flag for soft-deletion.

Request-scoped context is carried in `SocieteContext` (a static `ThreadLocal`) rather than a method parameter. The current société is established by `JwtAuthenticationFilter` from the JWT claim `sid` (société ID). SUPER_ADMIN tokens carry no `sid`; `SocieteContext.isSuperAdmin()` returns `true` for them.

Data isolation is enforced at the repository layer: all queries for company-owned resources (properties, contacts, contracts, projects) are scoped with `societeId` from `SocieteContext`. SUPER_ADMIN operations bypass this filter when `isSuperAdmin()` is true.

---

## Consequences

**Positive:**
- A user can belong to multiple companies with different roles in each. The login flow detects multi-membership and issues a partial token, requiring the user to select a société before receiving a scoped JWT.
- SUPER_ADMIN can query across all companies without switching databases.
- Invitation, impersonation, and GDPR anonymisation flows have a natural "user exists at platform level" concept to build on.
- Adding a company is an `INSERT` into `societe` — no schema migrations, no new database.

**Negative:**
- Row-level isolation is enforced in application code, not the database. A bug in `SocieteContext` propagation or a missing `societeId` filter could expose cross-company data. This risk is mitigated by consistently reading `societeId` from `SocieteContext` (never from client payloads) and by integration tests that verify isolation.
- `ThreadLocal` must be explicitly cleared after each request. `JwtAuthenticationFilter` does this in a `finally` block — if a future developer removes this cleanup, context will leak between requests in thread-pool environments.
- The `app_user_societe` table requires careful handling in GDPR anonymisation (deactivating all memberships) and in "last admin" protection logic.

---

## Alternatives Considered

**Per-tenant database isolation:** Rejected. It prevents a user from belonging to multiple companies, makes SUPER_ADMIN operations complex, and is operationally expensive for small companies.

**Row-level security with `societe_id` foreign key on `app_user`:** Rejected. It couples a user to exactly one company, ruling out multi-membership. It also makes the SUPER_ADMIN bootstrap awkward (who does the SUPER_ADMIN "belong" to?).

**Single-tenant (original design):** The starting point. Replaced because it required duplicate user accounts for multi-company access and lacked a clean SUPER_ADMIN tier.
