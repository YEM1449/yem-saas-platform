# API Catalog

> Only endpoints found in controllers are listed. See `hlm-backend/src/main/java/.../api` for full request/response shapes.

## Common error contract
All endpoints return `ErrorResponse` with `ErrorCode` when failing. See `common/error/ErrorResponse` and `ErrorCode`.

- **400**: `VALIDATION_ERROR`, `INVALID_CLIENT_CONVERSION`, `INVALID_DEPOSIT_REQUEST`, `INVALID_PROPERTY_TYPE`, `INVALID_PERIOD`
- **401**: `UNAUTHORIZED`
- **403**: `FORBIDDEN`
- **404**: `NOT_FOUND`
- **409**: `TENANT_KEY_EXISTS`, `CONTACT_EMAIL_EXISTS`, `CONTACT_INTEREST_EXISTS`, `DEPOSIT_ALREADY_EXISTS`, `PROPERTY_ALREADY_RESERVED`, `INVALID_DEPOSIT_STATE`, `PROPERTY_REFERENCE_CODE_EXISTS`

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
