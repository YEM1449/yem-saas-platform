# Sales Domain Model — Implementation-Aligned
Date: 2026-03-05

## Core Entities

### 1) `SaleContract`
Primary sales entity. A contract becomes a sale when status is `SIGNED`.

Key fields:
- `id` (UUID)
- `tenant_id` (UUID)
- `project_id` (UUID)
- `property_id` (UUID)
- `buyer_contact_id` (UUID)
- `agent_id` (UUID)
- `status` (`DRAFT`, `SIGNED`, `CANCELED`)
- `agreed_price` (DECIMAL)
- `list_price` (DECIMAL nullable)
- `source_deposit_id` (UUID nullable)
- `created_at`, `updated_at`, `signed_at`, `canceled_at`

Buyer snapshot fields (changeset 018, populated on sign):
- `buyer_type`
- `buyer_display_name`
- `buyer_phone`
- `buyer_email`
- `buyer_ice`
- `buyer_address`

### 2) `Deposit`
Reservation stage before sale.

Relevant statuses:
- `PENDING`
- `CONFIRMED`
- `CANCELLED`
- `EXPIRED`

Relevant linkage for sales consistency:
- `tenant`
- `property`
- `contact` (buyer)
- `agent`

### 3) `Property`
Commercial status interacts with sales lifecycle.

Commercially relevant statuses:
- `ACTIVE`
- `RESERVED`
- `SOLD`

(Additional statuses like `DRAFT`, `WITHDRAWN`, `ARCHIVED` remain valid in broader property lifecycle.)

## Relationships
- Project `1..*` Property
- Property `0..*` Deposit
- Property `0..1` active signed SaleContract (enforced by partial unique index)
- Deposit `0..1` Source SaleContract reference

## Integrity Constraints
1. Tenant isolation on all lookups and transitions.
2. A property cannot have more than one active signed contract:
- index: `uk_sc_property_signed`
- condition: `status='SIGNED' and canceled_at is null`
3. Source deposit consistency is validated at service layer when `sourceDepositId` is provided.

## Domain Events / Audit Trail
Commercial audit stream records:
- `CONTRACT_CREATED`
- `CONTRACT_SIGNED`
- `CONTRACT_CANCELED`
- plus related deposit events in the same commercial timeline.

## Derived Analytics Concepts
- Sale count: signed contracts in period
- Sales amount: sum of `agreedPrice` for signed contracts
- Discount analytics: `listPrice - agreedPrice` when `listPrice` is present
- Deposit-to-sale conversion: ratio and delay metrics derived from deposit + contract timestamps
