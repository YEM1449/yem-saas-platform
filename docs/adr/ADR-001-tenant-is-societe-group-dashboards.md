# ADR-001: Tenant = Societe + Group Dashboards (Aggregates Only)

**Status:** Accepted
**Date:** 2026-02-16
**Epic:** S0-E2

## Context

The CRM-HLM platform is multi-tenant: every request is scoped to a single tenant (= societe) via JWT.

**Repo evidence:**

1. **JWT `tid` claim** -- `JwtProvider.java` embeds `tid` on token generation (line 75) and `extractTenantId()` enforces its presence (lines 128-132).
   Path: `hlm-backend/src/main/java/com/yem/hlm/backend/auth/service/JwtProvider.java`
2. **Request-scoped isolation** -- `JwtAuthenticationFilter` extracts `tid` and writes it to `TenantContext` (ThreadLocal). All downstream services call `TenantContext.getTenantId()` to scope queries.
   Paths: `hlm-backend/.../auth/security/JwtAuthenticationFilter.java`, `hlm-backend/.../tenant/context/TenantContext.java`
3. **Tenant-scoped repositories** -- every repo uses `findByTenant_IdAnd*` predicates (PropertyRepository, ContactRepository, DepositRepository, UserRepository, NotificationRepository -- 17 files total).
4. **Cross-tenant test coverage** -- `CrossTenantIsolationIT` proves that tenant A cannot read tenant B data.
   Path: `hlm-backend/src/test/java/com/yem/hlm/backend/tenant/CrossTenantIsolationIT.java`
5. **Tenant entity** -- `Tenant.java` is a simple JPA entity (UUID PK, `key`, `name`). No `group_id` column exists today.
   Path: `hlm-backend/src/main/java/com/yem/hlm/backend/tenant/domain/Tenant.java`

Business need: "Direction" users must see consolidated KPIs across multiple societes without breaking tenant isolation.

## Decision

1. **Tenant == Societe.** The current `tenant_id` partition key stays. No change to JWT, TenantContext, or repository scoping.
2. **Introduce a "Group" concept above tenants.** A Group aggregates multiple societes for consolidated dashboards.
3. **Group dashboards return aggregates only.** Cross-tenant access to raw entities remains prohibited; group view is aggregated and role-gated.
4. **MVP: a tenant belongs to max 1 group** (nullable FK `group_id` on `tenant`).
5. **Separate group membership mapping.** Group access uses a dedicated `group_membership` table, not the existing per-tenant `app_user` table. Existing tenant-scoped user records are untouched.

## Alternatives Considered

| Alternative | Why rejected |
|---|---|
| **Tenant = Group** (promote group to partition key) | Breaks all existing `findByTenant_Id*` queries, JWT `tid` semantics, and CrossTenantIsolationIT. Massive rework. |
| **Cross-tenant JWT** (JWT carries multiple `tid` values) | Violates single-tenant session model baked into TenantContext (ThreadLocal stores one UUID). Would require rewriting every service call. |
| **Shared identity table now** (merge `app_user` across tenants) | Breaks tenant isolation guarantees and complicates RBAC. Premature -- group membership is a separate concern. |

## Consequences

- **Security:** Tenant isolation is untouched. Group endpoints are a new surface that must be role-gated (e.g. `ROLE_GROUP_ADMIN`).
- **UX:** Direction users get a consolidated dashboard without switching tenants. Drill-down into a specific societe requires a tenant-scoped session (existing flow).
- **Ops:** One new table (`tenant_group`), one FK on `tenant`, one mapping table (`group_membership`). No migration of existing data.

## Implementation Sketch (next epic -- S1)

**Proposed DB shape (high-level, not DDL):**

| Table | Key columns |
|---|---|
| `tenant_group` | `id (PK)`, `name`, `created_at` |
| `tenant` | add nullable `group_id FK -> tenant_group.id` |
| `group_membership` | `group_id FK`, `user_id FK`, `role` (e.g. `GROUP_VIEWER`, `GROUP_ADMIN`) |

**Rules:**
- Group dashboards execute `SELECT ... FROM <entity> WHERE tenant_id IN (select id from tenant where group_id = ?)` and return only aggregated counts/sums.
- No entity-level listing across tenants through group endpoints.

**API ideas (names only, not implemented):**
- `GET /dashboard/group/{groupId}/summary` -- aggregated KPIs
- `POST /auth/switch-tenant` -- (future) allows a group user to switch active tenant context

## Resolved Decisions (formerly Open Questions)

1. **Separate `GroupRole` enum.** Do not overload `UserRole`. Tenant roles (`ROLE_ADMIN`/`MANAGER`/`AGENT`) are tenant-scoped; group access is cross-tenant and needs its own guardrail. Map membership to Spring authorities like `ROLE_GROUP_DIRECTOR`, `ROLE_GROUP_ADMIN`.
2. **Seed-only for MVP.** Groups and memberships are provisioned via Liquibase seed + manual insert for staging/prod rollout (documented). Admin API for group management deferred to Sprint 2 if ops pain shows up.
3. **New JWT on tenant switch.** `POST /auth/switch-tenant` returns a fresh JWT (stateless, clean). Do not mutate `TenantContext` server-side. Switching tenant means resolving the user record for that tenant and issuing a JWT with that `userId` + `tid`.
4. **Minimal audit in Sprint 1.** Log group dashboard access events (`userId`, `groupId`, endpoint, params, timestamp) and membership changes. A simple `audit_event` table suffices; full audit subsystem deferred.
5. **Time-range filters from day 1.** Group dashboard endpoints accept `from`/`to` params (or preset `7d|30d|90d`). Prevents breaking API changes later and keeps KPI definitions consistent.
