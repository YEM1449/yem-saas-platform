# DOMAIN_RULES.md — Business Rules & Domain Logic

_Updated: 2026-03-04_

## Property Status Machine
```
DRAFT → ACTIVE → RESERVED → SOLD
                           → CANCELLED (releases back to ACTIVE)
ACTIVE → CANCELLED
DRAFT → CANCELLED
```
- Only ACTIVE properties can receive a deposit.
- A deposit → property moves to RESERVED.
- Cancelling/expiring a deposit → property moves back to ACTIVE.
- SOLD is terminal; CANCELLED of a SOLD is not modeled.

## Property Types
9 types: APARTMENT, VILLA, COMMERCIAL, OFFICE, PARKING, STORAGE, LAND, DUPLEX, STUDIO

## Contact Status Machine (Prospect Pipeline)
```
NEW_LEAD → CONTACTED → QUALIFIED_PROSPECT → VISIT_SCHEDULED
         → OFFER_SENT → NEGOTIATION → CLIENT (converted)
         → LOST (at any active stage)
```
- Status is fixed in code (not configurable per tenant — see OP-003 in _OPEN_POINTS.md).
- Only ADMIN/MANAGER can change contact status.
- `ContactType`: PROSPECT, CLIENT, PARTNER, OTHER.

## Deposit Business Rules
- One active deposit per property (unique constraint on active deposits).
- Duplicate deposit attempt → 409.
- Deposit expiry: scheduled job (`DepositExpiryScheduler`) auto-expires after configured days.
- Expiry restores property to ACTIVE.
- Only ADMIN/MANAGER can create/confirm/cancel deposits.

## Contract Lifecycle
```
DRAFT → SIGNED → (terminal)
      → CANCELLED → (terminal)
```
- DRAFT: created by ADMIN/MANAGER/AGENT.
- SIGNED/CANCELLED: requires ADMIN/MANAGER.
- Buyer snapshot stored on signing: name, email, phone (immutable after signing).

## Commission Calculation
- Formula: `commission = agreedPrice × (rate / 100) + fixedAmount`
- Rule priority: project-specific rule > tenant default rule > 0 (no rule)
- Rules managed by ADMIN/MANAGER.
- If `rate` is null → 0 rate component. If `fixedAmount` is null → 0 fixed component.

## Portal Token Rules
- Token TTL: 48 hours
- One-time use: once verified, `usedAt` is set and token is unusable.
- Raw token never stored (only SHA-256 hex hash).
- Portal JWT TTL: 2 hours.
- Portal users see only their own contracts (`contact.id` = JWT `sub`).

## Multi-Tenancy Rules
- Every entity has a `tenant` FK.
- The `tenantId` comes ONLY from the JWT `tid` claim (never from request body).
- Cross-tenant access: impossible by design (repositories filter by tenant_id).
- Cross-tenant isolation is tested in `CrossTenantIsolationIT`.

## Receivables Aging Buckets
- Current: `dueDate >= today`
- 1–30 days: `dueDate < today AND dueDate >= today - 30`
- 31–60 days: `dueDate < today - 30 AND dueDate >= today - 60`
- 61–90 days: `dueDate < today - 60 AND dueDate >= today - 90`
- 90+ days: `dueDate < today - 90`

## Prospect Source Values
`ProspectDetail.source`: ORGANIC, REFERRAL, ADVERTISEMENT, SOCIAL, EVENT, OTHER

## Outbox Message States
```
PENDING → (dispatch attempt) → SENT
                              → PENDING (retry, backoff: 1m, 5m, 30m)
                              → FAILED (after maxRetries)
```

## Token Revocation (CRM users)
- `User.tokenVersion` (int) incremented on: role change, account disable.
- Every request: JWT `tv` claim must match DB `tokenVersion`; mismatch → 401.
- Cache `userSecurityCache` avoids per-request DB hits; evicted on role/disable change.

## RBAC Summary
| Role | Create | Update | Delete | KPIs | User Mgmt |
|------|--------|--------|--------|------|-----------|
| ADMIN | ✅ | ✅ | ✅ | ✅ | ✅ |
| MANAGER | ✅ | ✅ | ❌ | ✅ | ❌ |
| AGENT | ❌ (most) | ❌ | ❌ | own only | ❌ |
| PORTAL | ❌ | ❌ | ❌ | ❌ | ❌ |
