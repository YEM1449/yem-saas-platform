# Sales State Machine — Implementation-Aligned
Date: 2026-03-05

## Contract Lifecycle
```text
DRAFT -> SIGNED      (action: sign)
DRAFT -> CANCELED    (action: cancel)
SIGNED -> CANCELED   (action: cancel)
CANCELED             (terminal)
```

## Contract Preconditions
### Create (`POST /api/contracts`)
- Project exists in tenant and is `ACTIVE`
- Property exists in tenant and belongs to the project
- Buyer contact exists in tenant
- Agent exists in tenant
- For ADMIN/MANAGER callers, `agentId` is required
- If `sourceDepositId` is provided:
  - deposit must exist in tenant
  - deposit must be `CONFIRMED`
  - deposit identifiers must match property/buyer/agent

### Sign (`POST /api/contracts/{id}/sign`)
- Contract is `DRAFT`
- Project is still `ACTIVE`
- Property lock acquired (deadlock-safe ordering)
- No other active signed contract exists for that property

### Cancel (`POST /api/contracts/{id}/cancel`)
- Contract is not already `CANCELED`
- If canceling from `SIGNED`, property lock is acquired before status transition side effects

## Side Effects
### On `SIGNED`
- Set contract status to `SIGNED`
- Set `signedAt`
- Capture immutable buyer snapshot fields
- Set property commercial status to `SOLD`
- Emit commercial audit event `CONTRACT_SIGNED`

### On `CANCELED` from `SIGNED`
- Set contract status to `CANCELED`
- Set `canceledAt`
- If active confirmed deposit exists for property -> property `RESERVED`
- Else -> property `ACTIVE`
- Emit commercial audit event `CONTRACT_CANCELED`

### On `CANCELED` from `DRAFT`
- Contract transitions to `CANCELED`
- No sold-state property rollback needed
- Emit commercial audit event `CONTRACT_CANCELED`

## Property Commercial Status Interactions
```text
ACTIVE -> RESERVED  (deposit create)
RESERVED -> SOLD    (contract sign)
SOLD -> RESERVED    (signed contract cancel + active confirmed deposit)
SOLD -> ACTIVE      (signed contract cancel + no active confirmed deposit)
RESERVED -> ACTIVE  (deposit cancel/expire)
```

## Concurrency Guarantees
- Property-level locking prevents conflicting sign/cancel/deposit transitions.
- DB partial unique index prevents duplicate active signed contracts for same property.
