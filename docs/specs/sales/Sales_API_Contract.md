# Sales API Contract (MVP)
Date: 2026-02-26

Base path suggestion: `/api/contracts` (or `/api/sales` — choose consistent naming with repo)

## Endpoints

### POST /api/contracts
Create a contract (DRAFT).

Request:
- projectId (UUID) required
- propertyId (UUID) required
- buyerContactId (UUID) required
- agentId (UUID) required (or implicit from auth for AGENT role)
- agreedPrice (Money) required
- listPrice (Money) optional
- sourceDepositId (UUID) optional

Validation:
- Project must be ACTIVE
- Property must belong to project and tenant
- If sourceDepositId provided: must be CONFIRMED and match ids

Responses:
- 201 Created with contract DTO
- 400 Bad Request for invalid rules (archived project, wrong status, mismatch)
- 404 Not Found for cross-tenant IDs (tenant-scoped lookups)

### POST /api/contracts/<built-in function id>/sign
Sign a contract (becomes sale).

Response:
- 200 OK with updated contract DTO
- 409 Conflict if property already sold/contract already signed [OPEN POINT: your API conventions]

### POST /api/contracts/<built-in function id>/cancel
Cancel a contract.

### GET /api/contracts
List contracts with filters:
- from/to (date range)
- projectId
- agentId
- status

## DTO
ContractDTO (MVP):
- id, projectId, propertyId, buyerContactId, agentId
- status, agreedPrice, listPrice, discountPercent
- createdAt, signedAt, canceledAt
