# Sales Module Scope — Implementation-Aligned
Date: 2026-03-05

## Purpose
Define the practical scope of the Sales domain as implemented in the platform today, and clarify what is intentionally out of scope.

## Canonical Business Definition
- **Sale = `SaleContract.status == SIGNED`**
- Deposit/reservation is a pre-sale step and must not be counted as final sale revenue.

## Delivered Scope (Current)
1. Sales contract lifecycle
- Create contract in `DRAFT`
- Sign contract (`DRAFT -> SIGNED`)
- Cancel contract (`DRAFT -> CANCELED` or `SIGNED -> CANCELED`)

2. Mandatory contract linkage
- `tenantId`
- `projectId`
- `propertyId`
- `buyerContactId`
- `agentId`
- optional `sourceDepositId`

3. Integrity and anti-double-sell
- Service-layer guard to prevent double selling
- DB partial unique index `uk_sc_property_signed` for signed active contracts

4. Property commercial side effects
- Contract sign -> property moves to `SOLD`
- Cancel of signed contract -> property reverts to:
  - `RESERVED` if active confirmed deposit exists
  - `ACTIVE` otherwise

5. KPI readiness fields
- `createdAt`, `signedAt`, `canceledAt`
- `agreedPrice`, optional `listPrice`
- buyer snapshot fields on signature (changeset 018)

6. Auditability
- Commercial audit events recorded for contract lifecycle transitions

## Related Modules and Dependencies
- `project/`: project must be `ACTIVE` for create/sign operations
- `property/`: commercial status transitions are centralized via workflow service
- `deposit/`: optional source linkage + cancellation fallback logic
- `dashboard/`: commercial KPIs read signed contract data
- `commission/`: commission computation on agreed prices

## Explicit Out of Scope (for this module document)
- Full accounting export workflows
- Legal e-signature orchestration
- Advanced legal lifecycle beyond `DRAFT/SIGNED/CANCELED`
- Payment orchestration details (covered in payments module specs)

## API Surface (Sales Core)
- `POST /api/contracts`
- `POST /api/contracts/{id}/sign`
- `POST /api/contracts/{id}/cancel`
- `GET /api/contracts`
- `GET /api/contracts/{id}`
- `GET /api/contracts/{id}/documents/contract.pdf`

## Implementation Notes
- AGENT callers are ownership-scoped on list/detail/document access.
- ADMIN/MANAGER can manage sign/cancel transitions.
- Cross-tenant resource access is resolved as not found / forbidden by tenant-scoped lookups and guards.
