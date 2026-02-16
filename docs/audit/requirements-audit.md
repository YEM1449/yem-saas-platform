# Requirements Audit (Spec vs Implementation)

## Executive summary
- Total normalized requirements: **20**
- **DONE**: 10
- **PARTIAL**: 5
- **MISSING**: 4
- **DECISION NEEDED**: 1

## Traceability matrix

| Req ID | Module | Status | Evidence |
|---|---|---|---|
| REQ-001 | MOD-01 | DONE | `AuthController`, `AuthService`, `JwtProvider`, `AuthLoginIT` |
| REQ-002 | MOD-01 | DONE | `@PreAuthorize` in `AdminUserController`, `ContactController`, `PropertyController`, `RbacIT` |
| REQ-003 | MOD-02 | DONE | `JwtAuthenticationFilter` sets `TenantContext`; tenant-scoped repos (`findByTenant...`); `CrossTenantIsolationIT` |
| REQ-004 | MOD-02 | DONE | `TenantController` POST/GET; tenant service/repo; `TenantControllerIT` |
| REQ-005 | MOD-03 | MISSING | No `terrain/foncier` package, no terrain migrations, no UI routes for foncier |
| REQ-006 | MOD-04 | DONE | `ContactController`, `ContactService`, contact status enums + tests (`ContactServiceTest`, `ContactControllerIT`) |
| REQ-007 | MOD-04 | DONE | `PropertyController`, `PropertyService`, `PropertyStatus`, migration `009-create-property-table.yaml`, `PropertyControllerIT` |
| REQ-008 | MOD-04 | DONE | `DepositService` create/confirm/cancel; `DepositStatus`; `DepositControllerIT`; scheduler package |
| REQ-009 | MOD-05 | MISSING | No authorization/admin dossier module, routes, or migrations |
| REQ-010 | MOD-06 | MISSING | No chantier/planning modules in backend/frontend |
| REQ-011 | MOD-07 | MISSING | No stock entities/APIs/migrations |
| REQ-012 | MOD-08 | MISSING | No procurement entities/APIs/migrations |
| REQ-013 | MOD-09 | PARTIAL | Dashboard summary exists but no finance domain/export artifacts |
| REQ-014 | MOD-10 | MISSING | No SAV ticket aggregate/endpoints/UI |
| REQ-015 | MOD-11 | PARTIAL | Property dashboard endpoint exists (`PropertyDashboardController`) but not broad executive KPIs |
| REQ-016 | MOD-12 | PARTIAL | Notification entity/API exists and deposit events emit notifications; no configurable automation rules |
| REQ-017 | MOD-13 | PARTIAL | Internal REST API exists; no dedicated integration contracts/webhooks/rate-limits |
| REQ-018 | MOD-02 | PARTIAL | Isolation exists but cross-society consolidation UX/data model not evidenced |
| REQ-019 | MOD-05 | PARTIAL | Deposit/contact payload fields mention references/notes, but no generic document management module |
| REQ-020 | MOD-01 | DONE | Token revocation (`token_version`, `UserSecurityCacheService`, `TokenRevocationIT`), global error contract (`GlobalExceptionHandler`, `ErrorContractIT`) |

## Key delivery risks
1. V2/V3 modules are largely absent, making roadmap variance high.
2. Semantic ambiguity around deposit/reservation may cause rework in legal and finance flows.
3. Document-management and administrative compliance workflows need explicit domain design before implementation.
