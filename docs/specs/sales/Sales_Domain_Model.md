# Sales Domain Model (MVP)
Date: 2026-02-26

## Entities

### SaleContract (new)
Fields (minimum):
- id (UUID)
- tenantId (UUID)
- projectId (UUID)
- propertyId (UUID)
- buyerContactId (UUID)
- agentId (UUID)
- status: DRAFT | SIGNED | CANCELED | COMPLETED(optional)
- listPrice (Money) [OPEN POINT if not stored today]
- agreedPrice (Money)
- discountAmount (Money) [derived optional]
- discountPercent (decimal) [derived optional]
- createdAt (timestamp)
- signedAt (timestamp nullable)
- canceledAt (timestamp nullable)
- sourceDepositId (UUID nullable)

Constraints:
- tenant isolation on all foreign keys
- Unique: (tenantId, propertyId) where status=SIGNED and canceledAt is null (implementation: partial unique index in Postgres) [OPEN POINT if DB differs]

### Deposit / Reservation (existing)
Required fields for KPI link:
- id, tenantId, projectId, propertyId, buyerContactId, agentId
- status: CREATED | CONFIRMED | CANCELED | EXPIRED
- createdAt, confirmedAt, canceledAt, expiresAt(optional)

### Property (existing)
- id, tenantId, projectId (mandatory)
- commercialStatus: AVAILABLE | RESERVED | SOLD
- reservedAt(optional), soldAt(optional)

## Relationships
- Project 1..* Property
- Property 0..* Deposit (but only 0..1 active confirmed at a time)
- Property 0..1 active signed SaleContract
- Deposit 0..1 SaleContract (if contract originates from deposit)

## Events (for analytics scalability)
- DepositConfirmed
- DepositCanceled/Expired
- ContractSigned
- ContractCanceled
These can initially be internal service-layer events used for cache eviction or projection updates.
