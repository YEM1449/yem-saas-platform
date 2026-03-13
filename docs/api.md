# API Catalog

> Only endpoints found in controllers are listed. See `hlm-backend/src/main/java/.../api` for full request/response shapes.

## Common error contract
All endpoints return `ErrorResponse` with `ErrorCode` when failing. See `common/error/ErrorResponse` and `ErrorCode`.

- **400**: `VALIDATION_ERROR`, `INVALID_CLIENT_CONVERSION`, `INVALID_DEPOSIT_REQUEST`, `INVALID_PROPERTY_TYPE`, `INVALID_PERIOD`
- **401**: `UNAUTHORIZED`
- **403**: `FORBIDDEN`
- **404**: `NOT_FOUND`, `RESERVATION_NOT_FOUND`
- **409**: `TENANT_KEY_EXISTS`, `CONTACT_EMAIL_EXISTS`, `CONTACT_INTEREST_EXISTS`, `DEPOSIT_ALREADY_EXISTS`, `PROPERTY_ALREADY_RESERVED`, `PROPERTY_NOT_AVAILABLE_FOR_RESERVATION`, `INVALID_DEPOSIT_STATE`, `INVALID_RESERVATION_STATE`, `PROPERTY_REFERENCE_CODE_EXISTS`

---

## Auth

### `POST /auth/login`
- **Auth:** Public
- **DTOs:** `LoginRequest` → `LoginResponse`
- **Notes:** Returns JWT for tenant-scoped access.
- **Common errors:** `UNAUTHORIZED`, `VALIDATION_ERROR`

### `GET /auth/me`
- **Auth:** Required
- **DTOs:** none (returns map with `userId`, `tenantId`)
- **Common errors:** `UNAUTHORIZED`

---

## Tenants

### `POST /tenants`
- **Auth:** Public (bootstrap)
- **DTOs:** `TenantCreateRequest` → `TenantResponse`
- **Common errors:** `TENANT_KEY_EXISTS`, `VALIDATION_ERROR`

### `GET /tenants/{id}`
- **Auth:** Required
- **Tenant guard:** must match token `tid` or 403
- **DTOs:** `TenantResponse`
- **Common errors:** `FORBIDDEN`, `NOT_FOUND`, `UNAUTHORIZED`

---

## Properties

### `POST /api/properties`
- **Auth:** Required
- **Roles:** `ADMIN`, `MANAGER`
- **DTOs:** `PropertyCreateRequest` → `PropertyResponse`
- **Common errors:** `VALIDATION_ERROR`, `PROPERTY_REFERENCE_CODE_EXISTS`, `INVALID_PROPERTY_TYPE`, `UNAUTHORIZED`, `FORBIDDEN`

### `GET /api/properties/{id}`
- **Auth:** Required
- **Roles:** any authenticated
- **DTOs:** `PropertyResponse`
- **Common errors:** `NOT_FOUND`, `UNAUTHORIZED`

### `GET /api/properties`
- **Auth:** Required
- **Roles:** any authenticated
- **Query params:** `type`, `status`
- **DTOs:** `PropertyResponse[]`
- **Common errors:** `UNAUTHORIZED`

### `PUT /api/properties/{id}`
- **Auth:** Required
- **Roles:** `ADMIN`, `MANAGER`
- **DTOs:** `PropertyUpdateRequest` → `PropertyResponse`
- **Common errors:** `VALIDATION_ERROR`, `NOT_FOUND`, `UNAUTHORIZED`, `FORBIDDEN`

### `DELETE /api/properties/{id}`
- **Auth:** Required
- **Roles:** `ADMIN`
- **DTOs:** none (204)
- **Common errors:** `NOT_FOUND`, `UNAUTHORIZED`, `FORBIDDEN`

---

## Property Dashboard

### `GET /dashboard/properties/summary`
- **Auth:** Required
- **Roles:** `ADMIN`, `MANAGER`
- **Query params:** `from`, `to`, `preset` (`DashboardPeriod`)
- **DTOs:** `PropertySummaryDTO`
- **Common errors:** `INVALID_PERIOD`, `UNAUTHORIZED`, `FORBIDDEN`

---

## Contacts

### `POST /api/contacts`
- **Auth:** Required
- **DTOs:** `CreateContactRequest` → `ContactResponse`
- **Common errors:** `VALIDATION_ERROR`, `CONTACT_EMAIL_EXISTS`, `UNAUTHORIZED`

### `GET /api/contacts/{id}`
- **Auth:** Required
- **DTOs:** `ContactResponse`
- **Common errors:** `NOT_FOUND`, `UNAUTHORIZED`

### `GET /api/contacts`
- **Auth:** Required
- **Query params:** `status`, `q`, pageable (`page`, `size`, `sort`)
- **DTOs:** `Page<ContactResponse>`
- **Common errors:** `UNAUTHORIZED`

### `PATCH /api/contacts/{id}`
- **Auth:** Required
- **DTOs:** `UpdateContactRequest` → `ContactResponse`
- **Common errors:** `VALIDATION_ERROR`, `CONTACT_EMAIL_EXISTS`, `NOT_FOUND`, `UNAUTHORIZED`

### `POST /api/contacts/{id}/convert-to-prospect`
- **Auth:** Required
- **Roles:** `ADMIN`, `MANAGER`
- **DTOs:** `ConvertToProspectRequest` (optional body) → `ContactResponse`
- **Notes:** Transitions `LOST` → `PROSPECT` or `PROSPECT` → `QUALIFIED_PROSPECT`. Enriches `ProspectDetail` with optional budget/source. No-op if already `CLIENT`.
- **Common errors:** `NOT_FOUND`, `UNAUTHORIZED`, `FORBIDDEN`

### `POST /api/contacts/{id}/convert-to-client`
- **Auth:** Required
- **DTOs:** `ConvertToClientRequest` → `ContactResponse`
- **Common errors:** `INVALID_CLIENT_CONVERSION`, `NOT_FOUND`, `UNAUTHORIZED`

### `POST /api/contacts/{id}/interests`
- **Auth:** Required
- **DTOs:** `ContactInterestRequest` → 201 (no body)
- **Common errors:** `CONTACT_INTEREST_EXISTS`, `NOT_FOUND`, `UNAUTHORIZED`

### `DELETE /api/contacts/{id}/interests/{propertyId}`
- **Auth:** Required
- **DTOs:** none (204)
- **Common errors:** `NOT_FOUND`, `UNAUTHORIZED`

### `GET /api/contacts/{id}/interests`
- **Auth:** Required
- **DTOs:** `ContactInterestResponse[]`
- **Common errors:** `NOT_FOUND`, `UNAUTHORIZED`

### `GET /api/properties/{propertyId}/contacts`
- **Auth:** Required
- **DTOs:** `UUID[]`
- **Common errors:** `UNAUTHORIZED`

---

## Deposits

### `POST /api/deposits`
- **Auth:** Required
- **DTOs:** `CreateDepositRequest` → `DepositResponse`
- **Common errors:** `VALIDATION_ERROR`, `DEPOSIT_ALREADY_EXISTS`, `INVALID_DEPOSIT_REQUEST`, `UNAUTHORIZED`

### `GET /api/deposits/{id}`
- **Auth:** Required
- **DTOs:** `DepositResponse`
- **Common errors:** `NOT_FOUND`, `UNAUTHORIZED`

### `POST /api/deposits/{id}/confirm`
- **Auth:** Required
- **DTOs:** `DepositResponse`
- **Common errors:** `INVALID_DEPOSIT_STATE`, `NOT_FOUND`, `UNAUTHORIZED`

### `POST /api/deposits/{id}/cancel`
- **Auth:** Required
- **DTOs:** `DepositResponse`
- **Common errors:** `INVALID_DEPOSIT_STATE`, `NOT_FOUND`, `UNAUTHORIZED`

### `GET /api/deposits/report`
- **Auth:** Required
- **Query params:** `status`, `agentId`, `contactId`, `propertyId`, `from`, `to`
- **DTOs:** `DepositReportResponse`
- **Common errors:** `UNAUTHORIZED`

---

## Payments (v2 — Current)

> **v1 removed.** The `payment/` backend package (`PaymentScheduleController`, `PaymentCallController`) was deleted in Epic/sec-improvement (2026-03-06). The v1 endpoints below no longer exist. Use v2 exclusively.

### v1 endpoints (deleted — for migration reference only)
- ~~`GET /api/contracts/{contractId}/payment-schedule`~~
- ~~`POST /api/contracts/{contractId}/payment-schedule`~~
- ~~`PATCH /api/contracts/{contractId}/payment-schedule/tranches/{trancheId}`~~
- ~~`POST /api/contracts/{contractId}/payment-schedule/tranches/{trancheId}/issue-call`~~
- ~~`GET /api/payment-calls`~~
- ~~`GET /api/payment-calls/{id}`~~
- ~~`GET /api/payment-calls/{id}/documents/appel-de-fonds.pdf`~~
- ~~`GET /api/payment-calls/{id}/payments`~~
- ~~`POST /api/payment-calls/{id}/payments`~~

### v2 endpoints (current)
- `GET /api/contracts/{contractId}/schedule`
- `POST /api/contracts/{contractId}/schedule`
- `PUT /api/schedule-items/{itemId}`
- `DELETE /api/schedule-items/{itemId}`
- `POST /api/schedule-items/{itemId}/issue`
- `POST /api/schedule-items/{itemId}/send`
- `POST /api/schedule-items/{itemId}/cancel`
- `GET /api/schedule-items/{itemId}/pdf`
- `GET /api/schedule-items/{itemId}/payments`
- `POST /api/schedule-items/{itemId}/payments`

Migration reference:
- [docs/v2/payment-v1-retirement-plan.v2.md](v2/payment-v1-retirement-plan.v2.md)

---

## Notifications

### `GET /api/notifications`
- **Auth:** Required
- **Query params:** `read`, `size`
- **DTOs:** `NotificationResponse[]`
- **Common errors:** `UNAUTHORIZED`

### `POST /api/notifications/{id}/read`
- **Auth:** Required
- **DTOs:** `NotificationResponse`
- **Common errors:** `NOT_FOUND`, `UNAUTHORIZED`

---

## Reservations

Lightweight property holds created before a formal deposit. Statuses: `ACTIVE`, `EXPIRED`, `CANCELLED`, `CONVERTED_TO_DEPOSIT`.

### `POST /api/reservations`
- **Auth:** Required
- **Roles:** `ADMIN`, `MANAGER`
- **DTOs:** `CreateReservationRequest` → `ReservationResponse`
- **Notes:** Property must be `ACTIVE` with no existing `ACTIVE` reservation. Acquires pessimistic write lock. Transitions property to `RESERVED`. Default expiry is +7 days if not specified.
- **Common errors:** `VALIDATION_ERROR`, `NOT_FOUND`, `PROPERTY_NOT_AVAILABLE_FOR_RESERVATION`, `UNAUTHORIZED`, `FORBIDDEN`

### `GET /api/reservations/{id}`
- **Auth:** Required
- **Roles:** any authenticated
- **DTOs:** `ReservationResponse`
- **Common errors:** `NOT_FOUND`, `UNAUTHORIZED`

### `GET /api/reservations`
- **Auth:** Required
- **Roles:** any authenticated
- **DTOs:** `ReservationResponse[]` (all tenant reservations, newest first)
- **Common errors:** `UNAUTHORIZED`

### `POST /api/reservations/{id}/cancel`
- **Auth:** Required
- **Roles:** `ADMIN`, `MANAGER`
- **DTOs:** `ReservationResponse`
- **Notes:** Only `ACTIVE` reservations can be cancelled. Releases property back to `ACTIVE`.
- **Common errors:** `NOT_FOUND`, `INVALID_RESERVATION_STATE`, `UNAUTHORIZED`, `FORBIDDEN`

### `POST /api/reservations/{id}/convert-to-deposit`
- **Auth:** Required
- **Roles:** `ADMIN`, `MANAGER`
- **DTOs:** `ConvertReservationToDepositRequest` → `DepositResponse`
- **Notes:** Transitions reservation to `CONVERTED_TO_DEPOSIT`, briefly releases property, then creates a deposit via `DepositService` (which re-reserves). Stores `convertedDepositId` on the reservation.
- **Common errors:** `NOT_FOUND`, `INVALID_RESERVATION_STATE`, `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`
