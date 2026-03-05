# Sales API Contract — Implementation-Aligned
Date: 2026-03-05

Base path: `/api/contracts`

## 1) Create Contract
### `POST /api/contracts`
Creates a contract in `DRAFT`.

Auth:
- Any authenticated CRM role

Request body (`CreateContractRequest`):
- `projectId` (UUID, required)
- `propertyId` (UUID, required)
- `buyerContactId` (UUID, required)
- `agentId` (UUID, required for ADMIN/MANAGER, constrained for AGENT)
- `agreedPrice` (decimal > 0, required)
- `listPrice` (decimal, optional)
- `sourceDepositId` (UUID, optional)

Responses:
- `201` with `ContractResponse`
- `400` on rule violations (archived project, missing agentId for admin/manager, mismatched source deposit)
- `404` on tenant-scoped not found

## 2) Sign Contract
### `POST /api/contracts/{id}/sign`
Transitions `DRAFT -> SIGNED`.

Auth:
- `ADMIN` or `MANAGER`

Side effects:
- Property moves to `SOLD`
- Buyer snapshot captured on contract

Responses:
- `200` with updated `ContractResponse`
- `409` or state conflict response when not signable (already signed/sold race)
- `404` on tenant-scoped not found

## 3) Cancel Contract
### `POST /api/contracts/{id}/cancel`
Transitions to `CANCELED` (from `DRAFT` or `SIGNED`).

Auth:
- `ADMIN` or `MANAGER`

Side effects when canceling from `SIGNED`:
- Property reverts to `RESERVED` if active confirmed deposit exists
- Otherwise property reverts to `ACTIVE`

Responses:
- `200` with updated `ContractResponse`
- `409` on invalid state
- `404` on tenant-scoped not found

## 4) List Contracts
### `GET /api/contracts`
Lists contracts with optional filters.

Auth:
- Any authenticated CRM role

Query params:
- `status` (`DRAFT`, `SIGNED`, `CANCELED`)
- `projectId` (UUID)
- `agentId` (UUID)
- `from` (ISO datetime)
- `to` (ISO datetime)

Behavior:
- AGENT callers are scoped to own contracts regardless of provided `agentId`.

Response:
- `200` list of `ContractResponse`

## 5) Get Contract by ID
### `GET /api/contracts/{id}`
Returns single tenant-scoped contract.

Auth:
- Any authenticated CRM role
- AGENT ownership restrictions apply

Response:
- `200` `ContractResponse`
- `404` when not found or outside ownership scope

## 6) Download Contract PDF
### `GET /api/contracts/{id}/documents/contract.pdf`
Returns generated contract PDF.

Auth:
- Any authenticated CRM role
- AGENT ownership restrictions apply

Response:
- `200` `application/pdf`
- header: `Content-Disposition: attachment; filename="contract_<id>.pdf"`

## 7) Error Contract
All error responses follow shared envelope:
- `ErrorResponse`
- stable `ErrorCode`

Representative codes in sales paths:
- archived project guard
- invalid contract/deposit state
- not found / unauthorized / forbidden
