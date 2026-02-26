# Sales Module Scope (MVP) — Real Estate CRM
Date: 2026-02-26

## Purpose
Introduce a **Sales Contract** capability so the CRM can reliably measure sales KPIs (count, amount, conversion, cycle time) and power professional dashboards.

## Business definition
- **Sale = Contract Signed** (recommended).
- Deposit/Reservation is a **pre-sale** step that can be canceled; it is not final revenue.

## MVP In-Scope
1) Create a Sales Contract (Draft)
2) Sign contract (becomes sale)
3) Cancel contract (reverts commercial state)
4) Link contract to:
   - Tenant
   - Project
   - Property (mandatory)
   - Buyer contact (mandatory)
   - Agent (commercial) (mandatory)
   - Optional: originating deposit/reservation
5) Enforce integrity:
   - A property cannot have more than one **active** contract (signed and not canceled).
6) Update property status on transitions:
   - SIGNED => property becomes SOLD
   - CANCELED => property returns to AVAILABLE (or RESERVED depending on active deposit) [OPEN POINT]

## Out of Scope (Phase 2+)
- Full payment schedules & installments
- Accounting export
- Advanced pipeline activities (calls/visits/tasks) unless already present
- Commission management (unless already present)
- Document generation workflows (unless already present)

## KPI Readiness Requirements (must be implemented in MVP)
- `signedAt` timestamp on contract signing
- `canceledAt` timestamp on cancellation
- Price fields: `agreedPrice` and (if available) `listPrice`
- Linking fields: `tenantId`, `projectId`, `propertyId`, `agentId`, `contactId`
