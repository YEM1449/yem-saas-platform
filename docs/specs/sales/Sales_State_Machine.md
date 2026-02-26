# Sales State Machine (MVP)
Date: 2026-02-26

## Contract lifecycle
- DRAFT
  - -> SIGNED (action: sign)
  - -> CANCELED (action: cancel) [optional: cancel allowed from draft]
- SIGNED
  - -> CANCELED (action: cancel) [OPEN POINT: do we allow cancel after signing? business rules]
  - -> COMPLETED (action: complete) [optional]
- CANCELED (terminal)

## Pre-conditions
### Create contract
- Project must be ACTIVE
- Property must belong to project and be AVAILABLE or RESERVED by the same buyer/agent [OPEN POINT]
- If sourceDepositId is provided: deposit must be CONFIRMED and match tenant/project/property/buyer/agent

### Sign contract
- Contract is DRAFT
- Property is not already SOLD by another active contract
- Optionally: deposit must exist/confirmed [OPEN POINT depending on business]

## Side effects
### On SIGNED
- Set Property status to SOLD
- Set property.soldAt = signedAt (or derive from contract)
- Invalidate dashboard caches for tenant/project/agent

### On CANCELED
- If no other active confirmed deposit exists for property:
  - Set Property status to AVAILABLE
- Else:
  - Set Property status to RESERVED and keep buyer association [OPEN POINT]
