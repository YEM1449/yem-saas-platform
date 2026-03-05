# Alignment Changes to Existing Modules (Sales)
Date: 2026-03-05

This document tracks how Sales requirements align with existing modules and what remains optional improvement work.

## Alignment Status Matrix
| Module | Alignment Objective | Current Status |
|--------|----------------------|----------------|
| `project` | Block sales activity on archived projects | Implemented (`ProjectActiveGuard` on create/sign) |
| `property` | Centralized commercial status transitions for reservation/sale rollback | Implemented via workflow service |
| `deposit` | Reservation lifecycle integrated with sales cancellation fallback | Implemented |
| `contract` | Formal sale lifecycle with auditable transitions | Implemented |
| `audit` | Immutable commercial event trail | Implemented |
| `dashboard` | Sales KPIs sourced from signed contracts | Implemented |
| `commission` | Commission rules and reporting on sales | Implemented |
| `portal` | Buyer read-only contract/payment/property access | Implemented |

## Module-by-Module Notes

### 1) Project Module
Delivered:
- Sales contract creation/signing requires project status `ACTIVE`.
- Archived projects reject new commercial writes.

Optional enhancement:
- Add explicit reporting toggles for archived projects in UI-level filters where needed.

### 2) Property Module
Delivered:
- Commercial transitions are not handled ad hoc in controllers.
- Sale/rollback transitions are coordinated through workflow service methods.
- Double-selling is guarded by service checks + DB partial unique index.

Optional enhancement:
- Add explicit domain event publication for downstream async projections if scaling requires it.

### 3) Contacts / Buyer Identity
Delivered:
- Contract links to buyer contact.
- Buyer snapshot captured at sign time to preserve legal/commercial traceability.

Optional enhancement:
- Extend buyer model for advanced legal person/company workflows if required by future business rules.

### 4) Deposit Module
Delivered:
- Deposit confirmation and cancellation states feed property commercial state.
- Contract cancellation checks active confirmed deposits to determine fallback state.

Optional enhancement:
- Add richer explicit cohort analytics between deposit creation/confirmation and contract sign.

### 5) Sales Module (Core)
Delivered:
- Full contract lifecycle (`DRAFT`, `SIGNED`, `CANCELED`).
- PDF generation for signed/legal document workflows.
- Tenant and role enforcement including AGENT ownership restrictions.

### 6) Users / RBAC
Delivered:
- ADMIN/MANAGER sign/cancel operations.
- AGENT visibility restricted to own contracts in service logic.
- Role-change token revocation support via token version checks.

### 7) KPI / Analytics Layer
Delivered:
- Commercial summary endpoint with signed-contract semantics.
- Discount analytics based on `listPrice` and `agreedPrice`.
- Receivables and commission dashboards available.

Optional enhancement:
- Pre-aggregated materialized analytics tables if query volume/latency requires it.

## Remaining Optional Backlog (Non-Blocking)
1. Standardize historical v1/v2 payment API migration timeline for external integrators.
2. Define long-term event-driven analytics projection architecture.
3. Extend legal document versioning and immutable signing metadata if e-signature is introduced.
