# DOMAIN_RULES.md — Business Rules & Domain Logic

_Updated: 2026-03-05_

## Property Lifecycle
Enum: `DRAFT`, `ACTIVE`, `RESERVED`, `SOLD`, `WITHDRAWN`, `ARCHIVED`

Core transitions:
- `ACTIVE -> RESERVED` when a deposit is created.
- `RESERVED -> ACTIVE` when deposit becomes `CANCELLED` or `EXPIRED`.
- `RESERVED/ACTIVE -> SOLD` when contract is signed.
- `SOLD -> RESERVED` or `SOLD -> ACTIVE` when signed contract is canceled (depends on active confirmed deposit existence).

Guardrails:
- Only `ACTIVE` properties can receive new deposits.
- Double-selling is blocked by service checks plus DB unique constraint on signed contract per property.

## Project Lifecycle
Enum: `ACTIVE`, `ARCHIVED`

Rules:
- New property assignment/reassignment requires project status `ACTIVE`.
- Archived projects reject commercial writes (400 `ARCHIVED_PROJECT`).
- `DELETE /api/projects/{id}` is logical archive, not physical delete.

## Contact Status Machine
Enum: `PROSPECT`, `QUALIFIED_PROSPECT`, `CLIENT`, `ACTIVE_CLIENT`, `COMPLETED_CLIENT`, `REFERRAL`, `LOST`

Allowed transitions:
```text
PROSPECT -> QUALIFIED_PROSPECT | LOST
QUALIFIED_PROSPECT -> PROSPECT | CLIENT | LOST
CLIENT -> ACTIVE_CLIENT | COMPLETED_CLIENT | LOST
ACTIVE_CLIENT -> COMPLETED_CLIENT | LOST
COMPLETED_CLIENT -> REFERRAL
LOST -> PROSPECT
REFERRAL -> (terminal in current model)
```

## Deposit Workflow
Enum: `PENDING`, `CONFIRMED`, `CANCELLED`, `EXPIRED`

Rules:
- Create sets `PENDING` and reserves property.
- Confirm allowed only for `PENDING`.
- Cancel/expire releases property back to `ACTIVE` when applicable.
- Tenant isolation is strict; cross-tenant IDs return 404.

## Sale Contract Workflow
Enum: `DRAFT`, `SIGNED`, `CANCELED`

Rules:
- `create`: allowed for CRM roles (tenant-scoped).
- `sign`: ADMIN/MANAGER; moves property to `SOLD`.
- `cancel`: ADMIN/MANAGER; can cancel from `DRAFT` or `SIGNED`.
- Signing captures immutable buyer snapshot on contract row.

Commercial KPI semantics (locked):
- Reservation = deposit `PENDING` or `CONFIRMED`.
- Sale = contract `SIGNED`.
- Revenue = `agreedPrice`.
- Discount analytics = `listPrice - agreedPrice` when `listPrice` is set.

## Payment Workflows
Two coexisting models:

1. `payment/` (v1, deprecated API surface)
- Tranche statuses: `PLANNED`, `ISSUED`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`.
- Payment call statuses: `DRAFT`, `ISSUED`, `OVERDUE`, `CLOSED`.

2. `payments/` (v2, preferred)
- Schedule item statuses: `DRAFT`, `ISSUED`, `SENT`, `OVERDUE`, `PAID`, `CANCELED`.
- Send action queues outbox message.
- Reminder scheduler handles pre-due and overdue workflows with idempotency.

## Outbox States
```text
PENDING -> SENT
PENDING -> PENDING (retry with backoff: 1m, 5m, 30m)
PENDING -> FAILED (after max retries)
```

## Portal Auth Rules
- Magic-link token TTL: 48h, one-time use.
- Raw token is never stored; SHA-256 hash stored in DB.
- Portal JWT TTL: 2h; claim set is `sub` (contactId), `tid`, `roles=[ROLE_PORTAL]`.
- Portal users can read only their own contracts, schedules, and related property data.

## Multi-Tenancy Rules
- Tenant ID source is JWT claim `tid`, not client input.
- TenantContext is request-scoped and must be cleared after request.
- Repositories must filter by tenant in all tenant-scoped data access.

## Token Revocation (CRM)
- `User.tokenVersion` increments on role change or account disable.
- CRM JWT claim `tv` must match current token version, else 401.
- `userSecurityCache` is used for performance and evicted on relevant updates.

## Receivables Aging Buckets
- Current: due date today or in future.
- 1-30 days overdue.
- 31-60 days overdue.
- 61-90 days overdue.
- 90+ days overdue.
