# API Reference

Complete reference for every REST endpoint in the YEM SaaS Platform backend. All endpoints are tenant-scoped via the JWT `tid` claim — no endpoint can access data from a different tenant. All authenticated endpoints require `Authorization: Bearer <token>` header.

## Table of Contents

1. [Error Envelope](#error-envelope)
2. [Auth](#auth)
3. [Admin Users](#admin-users)
4. [Tenants](#tenants)
5. [Projects](#projects)
6. [Properties](#properties)
7. [Contacts](#contacts)
8. [Deposits](#deposits)
9. [Reservations](#reservations)
10. [Contracts](#contracts)
11. [Payment Schedule](#payment-schedule)
12. [Commercial Dashboard](#commercial-dashboard)
13. [Receivables Dashboard](#receivables-dashboard)
14. [Cash Dashboard](#cash-dashboard)
15. [Property Dashboard (KPIs)](#property-dashboard-kpis)
16. [Audit Log](#audit-log)
17. [Notifications](#notifications)
18. [Outbox Messages](#outbox-messages)
19. [Commissions](#commissions)
20. [Media](#media)
21. [Portal Auth](#portal-auth)
22. [Portal Contracts](#portal-contracts)
23. [Portal Payments](#portal-payments)
24. [Portal Property](#portal-property)
25. [GDPR](#gdpr)
26. [Actuator](#actuator)

---

## Error Envelope

All errors return an `ErrorResponse` JSON object:

```json
{
  "timestamp": "2024-03-15T10:00:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Validation failed for request",
  "path": "/api/contacts",
  "fieldErrors": [
    {"field": "email", "message": "must not be blank"}
  ]
}
```

### ErrorCode values

| Code | HTTP Status | Meaning |
|------|-------------|---------|
| `VALIDATION_ERROR` | 400 | Bean validation failed |
| `UNAUTHORIZED` | 401 | Bad credentials or missing token |
| `ACCOUNT_LOCKED` | 401 | Account locked after too many failed logins |
| `PORTAL_TOKEN_INVALID` | 401 | Magic link token invalid/expired/used |
| `FORBIDDEN` | 403 | Authenticated but insufficient role |
| `NOT_FOUND` | 404 | Resource not found |
| `TENANT_KEY_EXISTS` | 409 | Tenant key already taken |
| `CONTACT_EMAIL_EXISTS` | 409 | Contact email already registered |
| `USER_EMAIL_EXISTS` | 409 | User email already registered |
| `CONTACT_INTEREST_EXISTS` | 409 | Interest for this contact+property already exists |
| `DEPOSIT_ALREADY_EXISTS` | 409 | Deposit for this property already active |
| `PROPERTY_ALREADY_RESERVED` | 409 | Property has an active reservation |
| `INVALID_DEPOSIT_STATE` | 409 | Deposit state transition not permitted |
| `INVALID_STATUS_TRANSITION` | 409 | Contact status transition not allowed |
| `PROPERTY_ALREADY_SOLD` | 409 | Property already has a SIGNED contract |
| `INVALID_CONTRACT_STATE` | 409 | Contract state transition not allowed |
| `PROPERTY_NOT_AVAILABLE_FOR_RESERVATION` | 409 | Property already reserved/deposited/sold |
| `INVALID_RESERVATION_STATE` | 409 | Reservation state transition not allowed |
| `GDPR_ERASURE_BLOCKED` | 409 | Contact has SIGNED contracts; erasure blocked |
| `INVALID_DEPOSIT_REQUEST` | 400 | Bad deposit request data |
| `CONTRACT_DEPOSIT_MISMATCH` | 400 | Source deposit does not match contract |
| `INVALID_PROPERTY_TYPE` | 400 | Missing required field for property type |
| `PROPERTY_REFERENCE_CODE_EXISTS` | 409 | Reference code already used in tenant |
| `ARCHIVED_PROJECT` | 400 | Cannot assign property to archived project |
| `PROJECT_NAME_EXISTS` | 409 | Project name already used in tenant |
| `INVALID_PERIOD` | 400 | Dashboard period is invalid |
| `MEDIA_TOO_LARGE` | 400 | File exceeds max size |
| `MEDIA_TYPE_NOT_ALLOWED` | 400 | MIME type not in allowed list |
| `MEDIA_NOT_FOUND` | 404 | Media record not found |
| `INVALID_PAYMENT_SCHEDULE_STATE` | 409 | Payment schedule item state transition not allowed |
| `PAYMENT_INVALID_AMOUNT` | 400 | Payment amount is zero or negative |
| `COMMISSION_RULE_NOT_FOUND` | 404 | Commission rule not found |
| `GDPR_EXPORT_NOT_FOUND` | 404 | Contact not found for data export |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests |
| `LOGIN_RATE_LIMITED` | 429 | Too many login attempts |
| `IMPORT_VALIDATION_ERROR` | 400 | CSV import validation failed |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

---

## Auth

### POST /auth/login

Authenticates a CRM user and returns a JWT.

**Auth:** None (public)

**Request body:**
```json
{
  "email": "admin@acme.com",
  "password": "Admin123!Secure",
  "tenantKey": "acme"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `email` | string | Yes | |
| `password` | string | Yes | |
| `tenantKey` | string | Yes | Identifies which tenant the user belongs to |

**Response 200:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "userId": "22222222-2222-2222-2222-222222222222",
  "tenantId": "11111111-1111-1111-1111-111111111111",
  "role": "ROLE_ADMIN",
  "email": "admin@acme.com"
}
```

**Errors:** `401 UNAUTHORIZED` (bad credentials), `401 ACCOUNT_LOCKED`, `429 LOGIN_RATE_LIMITED`

```bash
curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@acme.com","password":"Admin123!Secure","tenantKey":"acme"}'
```

### GET /auth/me

Returns current authenticated user information.

**Auth:** Any authenticated CRM role

**Response 200:**
```json
{
  "userId": "22222222-...",
  "email": "admin@acme.com",
  "role": "ROLE_ADMIN",
  "tenantId": "11111111-..."
}
```

---

## Admin Users

All endpoints require `ROLE_ADMIN`. Path: `/api/admin/users`

### GET /api/admin/users

List all users in the current tenant.

**Query params:** `q` (optional) — search by email or name

**Response 200:** `UserResponse[]`

```json
[
  {
    "id": "uuid",
    "email": "agent@acme.com",
    "role": "ROLE_AGENT",
    "enabled": true,
    "tenantId": "uuid"
  }
]
```

### POST /api/admin/users

Create a new user.

**Request body:**
```json
{
  "email": "newagent@acme.com",
  "password": "TestPass123!",
  "role": "ROLE_AGENT"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `email` | string | Yes | Unique within tenant |
| `password` | string | Yes | Must pass `@StrongPassword` (min 12 chars, mixed case, digit, special) |
| `role` | string | Yes | `ROLE_ADMIN`, `ROLE_MANAGER`, or `ROLE_AGENT` |

**Response 201:** `UserResponse`

**Errors:** `409 USER_EMAIL_EXISTS`, `400 VALIDATION_ERROR`

### PATCH /api/admin/users/{id}/role

Change a user's role (increments token_version, invalidating existing tokens).

**Request body:** `{"role": "ROLE_MANAGER"}`

**Response 200:** `UserResponse`

### PATCH /api/admin/users/{id}/enabled

Enable or disable a user account.

**Request body:** `{"enabled": false}`

**Response 200:** `UserResponse`

### POST /api/admin/users/{id}/reset-password

Generate a new temporary password for the user.

**Response 200:**
```json
{"temporaryPassword": "TmpXxx123!AbCd"}
```

---

## Tenants

### POST /tenants

Bootstrap a new tenant with its first admin user.

**Auth:** None (public)

**Request body:**
```json
{
  "tenantKey": "newagency",
  "tenantName": "New Real Estate Agency",
  "ownerEmail": "owner@newagency.com",
  "ownerPassword": "Admin123!Secure"
}
```

**Response 201:** `TenantResponse` with `OwnerResponse`

**Errors:** `409 TENANT_KEY_EXISTS`

---

## Projects

Path: `/api/projects`

### GET /api/projects

List all projects for the tenant.

**Auth:** Any CRM role

**Query params:** `activeOnly=true` (optional, default `false`)

**Response 200:** `ProjectResponse[]`

```json
[
  {
    "id": "uuid",
    "name": "Résidence Les Palmiers",
    "description": "...",
    "status": "ACTIVE",
    "city": "Casablanca",
    "region": "Grand Casablanca",
    "createdAt": "2024-01-15T10:00:00"
  }
]
```

### POST /api/projects

Create a project.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Request body:**
```json
{
  "name": "Résidence Les Palmiers",
  "description": "...",
  "city": "Casablanca",
  "region": "Grand Casablanca"
}
```

**Response 201:** `ProjectResponse`

**Errors:** `409 PROJECT_NAME_EXISTS`

### GET /api/projects/{id}

Get a single project.

**Auth:** Any CRM role

**Response 200:** `ProjectResponse` or `404 NOT_FOUND`

### PUT /api/projects/{id}

Update a project.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Request body:** `ProjectUpdateRequest` (same fields as create, all optional)

**Response 200:** `ProjectResponse`

### DELETE /api/projects/{id}

Archive a project (sets `status = ARCHIVED`).

**Auth:** `ROLE_ADMIN`

**Response 204**

### GET /api/projects/{id}/kpis

Get aggregated KPIs for a project.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Response 200:** `ProjectKpiDTO`
```json
{
  "projectId": "uuid",
  "totalProperties": 50,
  "availableProperties": 30,
  "reservedProperties": 5,
  "soldProperties": 15,
  "totalDeposits": 20,
  "confirmedDeposits": 15,
  "totalSignedContracts": 15,
  "totalRevenue": 15000000.00
}
```

---

## Properties

Path: `/api/properties`

### GET /api/properties

List all non-deleted properties.

**Auth:** Any CRM role

**Query params:**
- `type` (optional) — one of `VILLA`, `APPARTEMENT`, `DUPLEX`, `STUDIO`, `T2`, `T3`, `COMMERCE`, `LOT`, `TERRAIN_VIERGE`
- `status` (optional) — one of `ACTIVE`, `RESERVED`, `SOLD`

**Response 200:** `PropertyResponse[]`

### POST /api/properties

Create a property.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Request body (example for VILLA):**
```json
{
  "type": "VILLA",
  "referenceCode": "VIL-001",
  "title": "Villa Prestige Ain Diab",
  "price": 3500000.00,
  "currency": "MAD",
  "city": "Casablanca",
  "surfaceAreaSqm": 250.0,
  "landAreaSqm": 400.0,
  "bedrooms": 5,
  "bathrooms": 4,
  "projectId": "uuid"
}
```

**Type-specific required fields:**

| Type | Required fields |
|------|----------------|
| `VILLA` | `surfaceAreaSqm`, `landAreaSqm`, `bedrooms`, `bathrooms` |
| `APPARTEMENT` | `surfaceAreaSqm`, `bedrooms`, `bathrooms`, `floorNumber` |
| `DUPLEX` | `surfaceAreaSqm`, `bedrooms`, `bathrooms`, `floors` |
| `STUDIO` | `surfaceAreaSqm`, `floorNumber` |
| `T2` | `surfaceAreaSqm`, `bedrooms`, `bathrooms`, `floorNumber` |
| `T3` | `surfaceAreaSqm`, `bedrooms`, `bathrooms`, `floorNumber` |
| `COMMERCE` | `surfaceAreaSqm` |
| `LOT` | `landAreaSqm`, `zoning`, `isServiced` |
| `TERRAIN_VIERGE` | `landAreaSqm` |

**Response 201:** `PropertyResponse`

**Errors:** `400 INVALID_PROPERTY_TYPE`, `409 PROPERTY_REFERENCE_CODE_EXISTS`, `400 ARCHIVED_PROJECT`

### GET /api/properties/{id}

Get a property by ID.

**Auth:** Any CRM role

**Response 200:** `PropertyResponse` or `404 NOT_FOUND`

### PUT /api/properties/{id}

Update a property.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Response 200:** `PropertyResponse`

### DELETE /api/properties/{id}

Soft-delete a property (sets `deleted_at`, status `DELETED`).

**Auth:** `ROLE_ADMIN`

**Response 204**

### POST /api/properties/import

Import properties from a CSV file.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Request:** `multipart/form-data` with field `file` containing the CSV.

**Response 200/422:** `ImportResultResponse`
```json
{
  "imported": 45,
  "errors": [
    {"row": 3, "message": "surfaceAreaSqm is required for APPARTEMENT"}
  ]
}
```

---

## Contacts

Path: `/api/contacts` and `/api/properties/{id}/contacts`

### GET /api/contacts

List contacts with pagination and filtering.

**Auth:** Any CRM role

**Query params:**
- `contactType` (optional, multi-value) — `PROSPECT`, `CLIENT`, `BOTH`
- `status` (optional) — see `ContactStatus` enum
- `q` (optional) — full-text search on name/email/phone
- `page`, `size` (Pageable)

**Response 200:** `Page<ContactResponse>`

### POST /api/contacts

Create a contact.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Request body:**
```json
{
  "fullName": "Mohammed Alami",
  "email": "m.alami@example.com",
  "phone": "+212600000001",
  "contactType": "PROSPECT",
  "consentGiven": true,
  "consentMethod": "FORM"
}
```

**Response 201:** `ContactResponse`

**Errors:** `409 CONTACT_EMAIL_EXISTS`

### GET /api/contacts/{id}

Get a contact by ID.

**Auth:** Any CRM role

**Response 200:** `ContactResponse` or `404 NOT_FOUND`

### PATCH /api/contacts/{id}

Update contact details (partial update — null fields ignored).

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Response 200:** `ContactResponse`

### PATCH /api/contacts/{id}/status

Update contact status.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Request body:** `{"status": "CONTACTED"}`

**Response 200:** `ContactResponse`

**Errors:** `409 INVALID_STATUS_TRANSITION`

### POST /api/contacts/{id}/convert-to-prospect

Promote a contact to `QUALIFIED_PROSPECT`.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Request body (optional):**
```json
{
  "budgetMin": 500000.00,
  "budgetMax": 1000000.00,
  "source": "WEB",
  "notes": "Interested in 3-bedroom properties"
}
```

**Response 200:** `ContactResponse`

### POST /api/contacts/{id}/convert-to-client

Promote a contact to `ACTIVE_CLIENT`.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Request body:**
```json
{
  "clientKind": "INDIVIDUAL"
}
```

**Response 200:** `ContactResponse`

### POST /api/contacts/{id}/interests

Add a property interest.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Request body:** `{"propertyId": "uuid", "status": "INTERESTED", "notes": "..."}`

**Response 201**

**Errors:** `409 CONTACT_INTEREST_EXISTS`

### GET /api/contacts/{id}/interests

List property interests for a contact.

**Auth:** Any CRM role

**Response 200:** `ContactInterestResponse[]`

### DELETE /api/contacts/{id}/interests/{propertyId}

Remove a property interest.

**Auth:** `ROLE_ADMIN`

**Response 204**

### GET /api/properties/{propertyId}/contacts

List contact UUIDs interested in a property.

**Auth:** Any CRM role

**Response 200:** `UUID[]`

### GET /api/contacts/{id}/timeline

Get unified activity timeline for a contact (audit events + outbox messages + notifications).

**Auth:** Any CRM role

**Query params:** `limit` (default 50, max 500)

**Response 200:** `TimelineEventResponse[]`

---

## Deposits

Path: `/api/deposits`

### POST /api/deposits

Create a deposit (booking).

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Request body:**
```json
{
  "contactId": "uuid",
  "propertyId": "uuid",
  "agentId": "uuid",
  "amount": 50000.00,
  "currency": "MAD",
  "dueDate": "2024-04-15T00:00:00"
}
```

**Response 201:** `DepositResponse`

**Errors:** `409 DEPOSIT_ALREADY_EXISTS`, `409 PROPERTY_ALREADY_RESERVED`

### GET /api/deposits/{id}

Get a deposit by ID.

**Auth:** Any CRM role

**Response 200:** `DepositResponse` or `404 NOT_FOUND`

### POST /api/deposits/{id}/confirm

Confirm a PENDING deposit (property becomes RESERVED).

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Response 200:** `DepositResponse`

**Errors:** `409 INVALID_DEPOSIT_STATE`

### POST /api/deposits/{id}/cancel

Cancel a deposit (property reverts to ACTIVE).

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Response 200:** `DepositResponse`

### GET /api/deposits/{id}/documents/reservation.pdf

Download the PDF reservation certificate.

**Auth:** Any CRM role (AGENT sees own deposits only)

**Response 200:** `application/pdf` binary

### GET /api/deposits/report

Get aggregated deposit report.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Query params:**
- `status` (optional) — `PENDING`, `CONFIRMED`, `CANCELLED`, `CONVERTED`
- `agentId` (optional)
- `contactId` (optional)
- `propertyId` (optional)
- `from`, `to` (optional) — ISO datetime format

**Response 200:** `DepositReportResponse`

---

## Reservations

Path: `/api/reservations`

### POST /api/reservations

Create a reservation (short-term informal hold).

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Request body:**
```json
{
  "contactId": "uuid",
  "propertyId": "uuid",
  "reservationPrice": 5000.00,
  "notes": "Client visiting on Tuesday"
}
```

**Response 201:** `ReservationResponse`

**Errors:** `409 PROPERTY_NOT_AVAILABLE_FOR_RESERVATION`

### GET /api/reservations

List all reservations for the tenant.

**Auth:** Any CRM role

**Response 200:** `ReservationResponse[]`

### GET /api/reservations/{id}

Get a single reservation.

**Auth:** Any CRM role

**Response 200:** `ReservationResponse` or `404 NOT_FOUND`

### POST /api/reservations/{id}/cancel

Cancel an ACTIVE reservation.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Response 200:** `ReservationResponse`

**Errors:** `409 INVALID_RESERVATION_STATE`

### POST /api/reservations/{id}/convert-to-deposit

Convert an ACTIVE reservation into a formal deposit.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Request body:**
```json
{
  "agentId": "uuid",
  "amount": 50000.00,
  "currency": "MAD",
  "dueDate": "2024-04-30T00:00:00"
}
```

**Response 200:** `DepositResponse` (the newly created deposit)

---

## Contracts

Path: `/api/contracts`

### POST /api/contracts

Create a DRAFT sale contract.

**Auth:** Any CRM role (AGENT is automatically set as agent)

**Request body:**
```json
{
  "projectId": "uuid",
  "propertyId": "uuid",
  "buyerContactId": "uuid",
  "agentId": "uuid",
  "agreedPrice": 1200000.00,
  "listPrice": 1300000.00,
  "sourceDepositId": "uuid"
}
```

**Response 201:** `ContractResponse`

**Errors:** `409 PROPERTY_ALREADY_SOLD`, `400 CONTRACT_DEPOSIT_MISMATCH`

### GET /api/contracts

List contracts.

**Auth:** Any CRM role (AGENT sees own contracts only)

**Query params:**
- `status` (optional) — `DRAFT`, `SIGNED`, `CANCELLED`
- `projectId` (optional)
- `agentId` (optional, ignored for AGENT role)
- `from`, `to` (optional) — ISO datetime for `signed_at` range

**Response 200:** `ContractResponse[]`

### GET /api/contracts/{id}

Get a single contract.

**Auth:** Any CRM role (AGENT sees own contracts only, else 404)

**Response 200:** `ContractResponse`

### POST /api/contracts/{id}/sign

Sign a DRAFT contract (property becomes SOLD).

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Response 200:** `ContractResponse`

**Errors:** `409 INVALID_CONTRACT_STATE`, `409 PROPERTY_ALREADY_SOLD`

### POST /api/contracts/{id}/cancel

Cancel a DRAFT or SIGNED contract.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Response 200:** `ContractResponse`

### GET /api/contracts/{id}/documents/contract.pdf

Download the contract PDF.

**Auth:** Any CRM role (AGENT sees own contracts only)

**Response 200:** `application/pdf` binary

---

## Payment Schedule

### GET /api/contracts/{contractId}/schedule

List payment schedule items for a contract.

**Auth:** Any CRM role

**Response 200:** `PaymentScheduleItemResponse[]`

### POST /api/contracts/{contractId}/schedule

Create a schedule item.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Request body:**
```json
{
  "sequence": 1,
  "label": "Dépôt de garantie",
  "amount": 120000.00,
  "dueDate": "2024-05-01",
  "notes": "..."
}
```

**Response 201:** `PaymentScheduleItemResponse`

### PUT /api/schedule-items/{itemId}

Update a DRAFT schedule item.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Response 200:** `PaymentScheduleItemResponse`

### DELETE /api/schedule-items/{itemId}

Delete a schedule item (not if PAID).

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Response 204**

### POST /api/schedule-items/{itemId}/issue

Transition DRAFT → ISSUED.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Response 200:** `PaymentScheduleItemResponse`

### POST /api/schedule-items/{itemId}/send

Transition ISSUED/SENT/OVERDUE → SENT, sends outbox message.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Request body:** `{"channel": "EMAIL", "recipientEmail": "client@example.com"}`

**Response 200:** `PaymentScheduleItemResponse`

### POST /api/schedule-items/{itemId}/cancel

Cancel a schedule item.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Response 200:** `PaymentScheduleItemResponse`

### GET /api/schedule-items/{itemId}/pdf

Download the call-for-funds PDF.

**Auth:** Any CRM role

**Response 200:** `application/pdf` binary

### GET /api/schedule-items/{itemId}/payments

List payments recorded against a schedule item.

**Auth:** Any CRM role

**Response 200:** `PaymentResponse[]`

### POST /api/schedule-items/{itemId}/payments

Record a (partial) payment.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Request body:**
```json
{
  "amountPaid": 60000.00,
  "paidAt": "2024-05-15T14:00:00",
  "channel": "BANK_TRANSFER",
  "paymentReference": "VIR-2024-0512",
  "notes": "First installment"
}
```

**Response 201:** `PaymentResponse`

**Errors:** `400 PAYMENT_INVALID_AMOUNT`

### POST /api/schedule-items/reminders/run

Manually trigger the payment reminder run.

**Auth:** `ROLE_ADMIN`

**Response 202**

---

## Commercial Dashboard

Path: `/api/dashboard/commercial`

### GET /api/dashboard/commercial

Alias for summary — accepts YYYY-MM-DD date params.

**Auth:** Any CRM role (AGENT scoped to own data)

**Query params:** `from` (date), `to` (date), `projectId` (UUID), `agentId` (UUID)

**Response 200:** `CommercialDashboardSummaryDTO`

### GET /api/dashboard/commercial/summary

Full KPI summary (cached 30s).

**Auth:** Any CRM role

**Query params:** `from` (datetime), `to` (datetime), `projectId`, `agentId`

**Response 200:** `CommercialDashboardSummaryDTO`
```json
{
  "totalSignedContracts": 12,
  "totalRevenue": 14400000.00,
  "avgDealSize": 1200000.00,
  "conversionRate": 0.65,
  "activeDeposits": 5,
  "confirmedDeposits": 18,
  "availableProperties": 35,
  "reservedProperties": 5,
  "soldProperties": 12,
  "propertyHoldsCount": 3,
  "propertyHoldsExpiringSoon": 1,
  "avgDiscountPercent": 4.2,
  "maxDiscountPercent": 8.5,
  "prospectsBySource": [
    {"source": "WEB", "count": 12},
    {"source": "REFERRAL", "count": 7}
  ],
  "salesByDay": [...],
  "topAgents": [...]
}
```

### GET /api/dashboard/commercial/sales

Paginated signed contracts drill-down.

**Auth:** Any CRM role

**Query params:** same as summary + `page` (default 0), `size` (default 20)

**Response 200:** `CommercialDashboardSalesDTO`

---

## Receivables Dashboard

### GET /api/dashboard/receivables

Aging analysis of payment schedule items.

**Auth:** Any CRM role

**Response 200:** `ReceivablesDashboardResponse`
```json
{
  "totalOutstanding": 2400000.00,
  "bucket0to30Days": 800000.00,
  "bucket31to60Days": 600000.00,
  "bucket61to90Days": 400000.00,
  "bucketOver90Days": 600000.00,
  "overdueByProject": [...]
}
```

---

## Cash Dashboard

### GET /api/dashboard/cash

Cash-in summary from recorded payments.

**Auth:** Any CRM role

**Response 200:** Cash dashboard DTO with total received, recent payments, and project breakdown.

---

## Property Dashboard (KPIs)

### GET /api/properties/dashboard/sales-kpi

Property sales KPIs with optional period filter.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Query params:** `period` (e.g. `LAST_30_DAYS`, `LAST_90_DAYS`, `THIS_YEAR`)

**Response 200:** `PropertySalesKpiDTO`

---

## Audit Log

### GET /api/audit/commercial

Query the commercial audit event log.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Query params:**
- `from`, `to` — ISO datetime
- `correlationType` — e.g. `DEPOSIT`, `CONTRACT`
- `correlationId` — UUID
- `limit` — 1–500, default 100

**Response 200:** `AuditEventResponse[]`
```json
[
  {
    "id": "uuid",
    "eventType": "CONTRACT_SIGNED",
    "correlationType": "CONTRACT",
    "correlationId": "uuid",
    "actorUserId": "uuid",
    "payload": {...},
    "createdAt": "2024-03-15T10:00:00"
  }
]
```

---

## Notifications

Path: `/api/notifications`

### GET /api/notifications

List in-app notifications for the current user.

**Auth:** Any CRM role

**Query params:** `page`, `size` (Pageable), `unreadOnly=true` (optional)

**Response 200:** `Page<NotificationResponse>`

### POST /api/notifications/{id}/read

Mark a notification as read.

**Auth:** Any CRM role

**Response 200:** `NotificationResponse`

### POST /api/notifications/read-all

Mark all unread notifications as read.

**Auth:** Any CRM role

**Response 200**

---

## Outbox Messages

### POST /api/messages

Queue an outbound EMAIL or SMS message.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Request body:**
```json
{
  "channel": "EMAIL",
  "recipientEmail": "client@example.com",
  "subject": "Your contract is ready",
  "body": "Dear Mohammed, ...",
  "correlationType": "CONTRACT",
  "correlationId": "uuid"
}
```

**Response 201:** `OutboundMessageResponse`

**Errors:** `400 INVALID_RECIPIENT`, `400 CONTACT_CHANNEL_MISSING`

---

## Commissions

### GET /api/commissions/my

My earned commissions from signed contracts.

**Auth:** Any CRM role (returns own commissions)

**Query params:** `from`, `to` (date, optional)

**Response 200:** `CommissionDTO[]`

### GET /api/commissions

All commissions (optional agent filter).

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Query params:** `agentId` (optional), `from`, `to` (date)

**Response 200:** `CommissionDTO[]`

### GET /api/commission-rules

List commission rules for the tenant.

**Auth:** `ROLE_ADMIN`

**Response 200:** `CommissionRuleResponse[]`

### POST /api/commission-rules

Create a commission rule.

**Auth:** `ROLE_ADMIN`

**Request body:**
```json
{
  "projectId": null,
  "rate": 3.00,
  "fixedAmount": 5000.00,
  "currency": "MAD"
}
```

Note: `projectId: null` creates a tenant-wide default rule. A project-specific rule takes precedence.

**Response 201:** `CommissionRuleResponse`

### PUT /api/commission-rules/{id}

Update a commission rule.

**Auth:** `ROLE_ADMIN`

**Response 200:** `CommissionRuleResponse`

### DELETE /api/commission-rules/{id}

Delete a commission rule.

**Auth:** `ROLE_ADMIN`

**Response 204**

---

## Media

### POST /api/properties/{id}/media

Upload a photo or PDF for a property.

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Request:** `multipart/form-data` with field `file`

Max size: 10 MB (configurable via `MEDIA_MAX_FILE_SIZE`).
Allowed types: `image/jpeg`, `image/png`, `image/webp`, `application/pdf`.

**Response 201:** `PropertyMediaResponse`
```json
{
  "id": "uuid",
  "propertyId": "uuid",
  "filename": "facade.jpg",
  "contentType": "image/jpeg",
  "fileSize": 245678,
  "createdAt": "2024-03-15T10:00:00"
}
```

**Errors:** `400 MEDIA_TOO_LARGE`, `400 MEDIA_TYPE_NOT_ALLOWED`

### GET /api/properties/{id}/media

List media for a property.

**Auth:** Any CRM role

**Response 200:** `PropertyMediaResponse[]`

### GET /api/media/{mediaId}/download

Download a media file.

**Auth:** Any CRM role

**Response 200:** Binary stream with appropriate `Content-Type` and `Content-Disposition: attachment` headers.

### DELETE /api/media/{mediaId}

Delete a media file.

**Auth:** `ROLE_ADMIN`

**Response 204**

---

## Portal Auth

Both endpoints are public (no JWT required).

### POST /api/portal/auth/request-link

Request a magic link email for the client portal.

**Request body:**
```json
{
  "email": "buyer@example.com",
  "tenantKey": "acme"
}
```

**Response 200:** `MagicLinkResponse`
```json
{
  "message": "Magic link sent to buyer@example.com",
  "linkUrl": "http://localhost:4200/portal/login?token=aBcDeFgH..."
}
```

**Errors:** `429 RATE_LIMIT_EXCEEDED` (3 requests per hour per IP)

### GET /api/portal/auth/verify?token=xxx

Verify a magic link token and obtain a portal JWT.

**Response 200:** `PortalTokenVerifyResponse`
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 7200,
  "contactId": "uuid"
}
```

**Errors:** `401 PORTAL_TOKEN_INVALID`

---

## Portal Contracts

All require `ROLE_PORTAL`.

### GET /api/portal/contracts

List sale contracts for the authenticated buyer contact.

**Response 200:** `PortalContractResponse[]`

### GET /api/portal/contracts/{contractId}

Get a single contract.

**Response 200:** `PortalContractResponse` or `404 NOT_FOUND`

---

## Portal Payments

### GET /api/portal/contracts/{contractId}/payments

List payment schedule items for a portal contract.

**Auth:** `ROLE_PORTAL`

**Response 200:** `PaymentScheduleItemResponse[]`

---

## Portal Property

### GET /api/portal/properties/{id}

Get property details for a buyer's contract.

**Auth:** `ROLE_PORTAL`

**Response 200:** `PropertyResponse` or `404 NOT_FOUND`

---

## GDPR

Path: `/api/gdpr`

### GET /api/gdpr/contacts/{contactId}/export

Export all personal data for a contact (Art. 15 / Art. 20 GDPR).

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Response 200:** `DataExportResponse`
```json
{
  "contactId": "uuid",
  "exportedAt": "2024-03-15T10:00:00",
  "contact": {
    "fullName": "Mohammed Alami",
    "email": "m.alami@example.com",
    "phone": "+212600000001",
    "consentGiven": true,
    "consentDate": "2024-01-10T09:00:00"
  },
  "deposits": [...],
  "contracts": [...],
  "propertyInterests": [...]
}
```

**Errors:** `404 GDPR_EXPORT_NOT_FOUND`

### DELETE /api/gdpr/contacts/{contactId}/anonymize

Anonymize all PII for a contact (Art. 17 right to erasure).

**Auth:** `ROLE_ADMIN`

**Response 200**

**Errors:** `409 GDPR_ERASURE_BLOCKED` (contact has SIGNED contracts)

### GET /api/gdpr/contacts/{contactId}/rectify

Get mutable personal data fields for rectification review (Art. 16).

**Auth:** `ROLE_ADMIN` or `ROLE_MANAGER`

**Response 200:** `RectifyContactResponse`

### GET /api/gdpr/privacy-notice

Get the operator's privacy notice text (Art. 13 / Law 09-08 Art. 5).

**Auth:** Any CRM role

**Response 200:**
```json
{
  "version": "1.0",
  "lastUpdated": "2024-01-01",
  "text": "..."
}
```

---

## Actuator

### GET /actuator/health

**Auth:** None (public)

**Response 200:**
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

Redis health component appears when `REDIS_ENABLED=true`. Mail health only appears when `MAIL_HEALTH_ENABLED=true`.

### GET /actuator/info

**Auth:** None (public)

Returns application version information.
