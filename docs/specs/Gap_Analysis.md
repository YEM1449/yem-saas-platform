# Gap Analysis (CDC vs Current Implementation)

_Last updated: 2026-02-26 (Batch 2)_

This file lists the main gaps to close between the CDC expectations and the current implementation state.


## Top gaps (prioritized)

1) **Security P1** — Role change / disable must invalidate active JWTs immediately. ✅ CLOSED: `tv` claim + `UserSecurityCacheService` + `JwtAuthenticationFilter` + `TokenRevocationIT` tests.

2) **Context load / IT stability** — Fix the failing Spring test ApplicationContext (root-cause), then make all ITs green. Status: ONGOING — verify with `./mvnw failsafe:integration-test`.

3) **Project KPIs** — Confirm exact KPI list required by CDC + ensure backend endpoint(s) + UI coverage + tests. ✅ CLOSED: `GET /api/projects/{id}/kpis` (ADMIN/MANAGER), `ProjectControllerIT` coverage, Angular `project-detail` KPI view.

4) **ARCHIVED project guardrail** — Property create/update must reject ARCHIVED projects. ✅ CLOSED (Batch 1): `ProjectActiveGuard` + `ArchivedProjectAssignmentException` + 400 `ARCHIVED_PROJECT` + tests.

5) **Commercial MVP completeness** — SaleContract entity, lifecycle (DRAFT→SIGNED→CANCELED), property status on sign/cancel, double-booking guard. Status: PARTIAL (Batch 2 closed property status SSOT; Batch 3+ covers double-booking + SaleContract entity).


## By CDC P1 backlog item

See `docs/spec/Backlog_Status.md` for a compact status table.


## Traceability rules

- When implementing a gap, reference CDC section + requirement IDs from `Requirements_Index.md` in PR descriptions.

- If you intentionally deviate, add an entry in `Spec_Deltas.md`.
