# Functional Specification — YEM SaaS Platform

This document defines every domain module's purpose, actors, numbered requirements, business rules, state machines, cross-module dependencies, and implementation status.

**Implementation status legend:**
- `IMPLEMENTED` — exists in production code, covered by tests
- `PARTIAL` — exists but edge cases / some paths are incomplete
- `NOT_YET_BUILT` — planned, referenced in code comments or open points

---

## Table of Contents

1. [Authentication & Session](#1-authentication--session)
2. [Tenant Provisioning](#2-tenant-provisioning)
3. [User Management](#3-user-management)
4. [Project Management](#4-project-management)
5. [Property Management](#5-property-management)
6. [Contact Management](#6-contact-management)
7. [Deposit Workflow](#7-deposit-workflow)
8. [Property Reservation](#8-property-reservation)
9. [Sale Contracts](#9-sale-contracts)
10. [Payment Schedule](#10-payment-schedule)
11. [Commission Tracking](#11-commission-tracking)
12. [Commercial Dashboard](#12-commercial-dashboard)
13. [Commercial Audit Log](#13-commercial-audit-log)
14. [Client Portal](#14-client-portal)
15. [Notification Centre](#15-notification-centre)
16. [Outbox Messaging (Email / SMS)](#16-outbox-messaging-email--sms)
17. [Property Media](#17-property-media)
18. [GDPR / Law 09-08 Compliance](#18-gdpr--law-09-08-compliance)
19. [Reminder Service](#19-reminder-service)
20. [Observability](#20-observability)

---

## 1. Authentication & Session

### Purpose
Authenticate CRM users with email + password and issue short-lived JWT bearer tokens that carry tenant and role context.

### Actors
| Actor | Role |
|-------|------|
| CRM user (any) | Submits credentials, receives token |
| System | Validates credentials, applies lockout, issues JWT |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-1-1 | The system MUST accept `POST /auth/login` with `{email, password}` and return a signed JWT on success. |
| REQ-1-2 | Failed authentication MUST increment `failed_login_attempts` on the `app_user` row. |
| REQ-1-3 | When `failed_login_attempts >= LOCKOUT_MAX_ATTEMPTS` (default 5), the system MUST set `locked_until = now + LOCKOUT_DURATION_MINUTES` (default 15 min) and reject further login attempts with HTTP 401 / `ACCOUNT_LOCKED` until the duration elapses. |
| REQ-1-4 | On successful login, `failed_login_attempts` and `locked_until` MUST be reset to 0 / null. |
| REQ-1-5 | Login attempts from the same IP MUST be limited to `RATE_LIMIT_LOGIN_IP_MAX` (default 20) per `RATE_LIMIT_LOGIN_WINDOW_SECONDS` (default 60 s); breaching this returns HTTP 429. |
| REQ-1-6 | Login attempts for the same email identity MUST be limited to `RATE_LIMIT_LOGIN_KEY_MAX` (default 10) per window; breaching returns HTTP 429. |
| REQ-1-7 | The JWT MUST carry claims: `sub` (userId), `tid` (tenantId), `roles` (array), `tv` (token version), `iat`, `exp`. |
| REQ-1-8 | The JWT TTL MUST be `JWT_TTL_SECONDS` (default 3600 s). |
| REQ-1-9 | The system MUST reject tokens whose `tv` claim does not match the `token_version` column on `app_user`, with propagation within ~60 s (cache TTL). |
| REQ-1-10 | Disabled users (`enabled = false`) MUST NOT be able to log in. |

### Business Rules
- Only `enabled = true` users with matching BCrypt password hash are authenticated.
- Rate limiting uses Bucket4j in-memory buckets keyed on IP and email.
- Token signing algorithm is HS256; key is `JWT_SECRET` (minimum 32 chars).

### State Machine — Account Lockout

```
[UNLOCKED] ─ failed attempt → increment attempts
           ─ attempts >= threshold → [LOCKED until locked_until]
           ─ successful login → reset attempts → [UNLOCKED]
[LOCKED] ─ now > locked_until → [UNLOCKED]
         ─ any login attempt → 401 ACCOUNT_LOCKED
```

### Cross-Module Dependencies
- Reads `app_user` via `UserRepository`.
- Writes `failed_login_attempts`, `locked_until` on `app_user`.
- Issues JWT consumed by all other modules via `JwtAuthenticationFilter`.

### Implementation Status
`IMPLEMENTED`

---

## 2. Tenant Provisioning

### Purpose
Bootstrap new tenants in a multi-tenant environment. Each tenant is an isolated data namespace; all domain entities carry a `tenant_id` FK.

### Actors
| Actor | Role |
|-------|------|
| System operator | Calls `POST /tenants` to create a new tenant + owner |
| System | Persists tenant row, creates owner user |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-2-1 | `POST /tenants` MUST be publicly accessible (no bearer token required). |
| REQ-2-2 | The system MUST create a `tenant` row with a unique `key` (slug). |
| REQ-2-3 | The system MUST create an associated owner `app_user` row with `ROLE_ADMIN`. |
| REQ-2-4 | Duplicate `key` MUST return HTTP 409. |

### Business Rules
- Tenant key is lowercase, URL-safe, unique across all tenants.
- Tenant owner is the initial `ROLE_ADMIN` for that tenant.
- All subsequent users are created by the tenant's ADMIN users via `POST /admin/users`.

### Cross-Module Dependencies
- Creates `tenant` row referenced by all domain entities.
- Creates `app_user` row for the owner.

### Implementation Status
`IMPLEMENTED`

---

## 3. User Management

### Purpose
Allow ADMIN users to create and manage CRM staff accounts (MANAGER, AGENT) within their tenant.

### Actors
| Actor | Role |
|-------|------|
| ADMIN | Full user CRUD — create, read, update, enable/disable, change role, reset password |
| MANAGER | No access to user management |
| AGENT | No access to user management |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-3-1 | ADMIN MUST be able to create new users via `POST /admin/users` specifying email, password, and role. |
| REQ-3-2 | Password MUST satisfy `StrongPassword` policy: min 12 chars, uppercase, lowercase, digit, special char. |
| REQ-3-3 | Duplicate email within a tenant MUST return HTTP 409 / `USER_EMAIL_TAKEN`. |
| REQ-3-4 | ADMIN MUST be able to list all users in their tenant via `GET /admin/users`. |
| REQ-3-5 | ADMIN MUST be able to enable or disable a user via `PUT /admin/users/{id}/enabled`. |
| REQ-3-6 | Disabling a user MUST increment `token_version` to invalidate any active JWTs within ~60 s. |
| REQ-3-7 | ADMIN MUST be able to change a user's role via `PUT /admin/users/{id}/role`; this MUST also increment `token_version`. |
| REQ-3-8 | ADMIN MUST be able to reset another user's password via `PUT /admin/users/{id}/password`. |

### Business Rules
- Users are scoped to a tenant; no cross-tenant visibility.
- Token revocation propagates via cache TTL (Caffeine 60 s or Redis 60 s).
- `ROLE_PORTAL` is reserved for client portal principals (contact rows), not CRM staff.

### Cross-Module Dependencies
- Writes `token_version` increment consumed by `JwtAuthenticationFilter`.
- User rows referenced by `deposit.agent_id`, `sale_contract.agent_id`, etc.

### Implementation Status
`IMPLEMENTED`

---

## 4. Project Management

### Purpose
Organise real estate developments into named projects (e.g., "Résidence Al Fath"). Properties belong to exactly one project.

### Actors
| Actor | Role |
|-------|------|
| ADMIN | Full CRUD |
| MANAGER | Full CRUD |
| AGENT | Read-only |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-4-1 | ADMIN and MANAGER MUST be able to create a project with name, optional description, location, and start date. |
| REQ-4-2 | All users MUST be able to list and retrieve projects within their tenant. |
| REQ-4-3 | ADMIN and MANAGER MUST be able to update project fields. |
| REQ-4-4 | ADMIN MUST be able to delete a project. |
| REQ-4-5 | Projects MUST be tenant-scoped; agents from other tenants MUST NOT see them. |

### Cross-Module Dependencies
- Properties reference `project_id`.
- `CommercialDashboardService` aggregates KPIs per project.

### Implementation Status
`IMPLEMENTED`

---

## 5. Property Management

### Purpose
Catalogue real estate units (villas, apartments, lots, etc.) with rich metadata, lifecycle tracking, and media attachments.

### Actors
| Actor | Role |
|-------|------|
| ADMIN | Full CRUD including delete, status change |
| MANAGER | Create, read, update, status change; no hard delete |
| AGENT | Read-only |
| System | Automatic status transitions (RESERVED on deposit, SOLD on contract sign) |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-5-1 | ADMIN and MANAGER MUST be able to create a property specifying type, project, price, and type-specific fields. |
| REQ-5-2 | `VILLA` MUST require `surfaceAreaSqm`, `landAreaSqm`, `bedrooms`, `bathrooms`. |
| REQ-5-3 | `APPARTEMENT` MUST require `surfaceAreaSqm`, `bedrooms`, `bathrooms`, `floorNumber`. |
| REQ-5-4 | All users MUST be able to list and retrieve properties with pagination and optional status/type filters. |
| REQ-5-5 | ADMIN MUST be able to soft-delete a property; this MUST set `deleted_at = now()` and `status = DELETED` without physically removing the row. |
| REQ-5-6 | Soft-deleted properties MUST be excluded from all list queries (`WHERE deleted_at IS NULL`). |
| REQ-5-7 | Property status MUST follow: `DRAFT → ACTIVE → RESERVED → SOLD`; alternative endings `WITHDRAWN`, `ARCHIVED`. |
| REQ-5-8 | The system MUST prevent creating a deposit on a property that is not `ACTIVE` or `RESERVED`. |
| REQ-5-9 | Property reference numbers MUST be unique within the tenant. |

### State Machine — Property Status

```
DRAFT ──activate──► ACTIVE
ACTIVE ──deposit/reservation──► RESERVED
RESERVED ──contract signed──► SOLD
ACTIVE|RESERVED ──withdraw──► WITHDRAWN
SOLD|WITHDRAWN ──archive──► ARCHIVED
ADMIN ──soft delete──► (deleted_at set, status = DELETED)
```

### Cross-Module Dependencies
- Referenced by `deposit.property_id`, `sale_contract.property_id`, `contact_interest.property_id`, `property_reservation.property_id`.
- Media files attached via `PropertyMediaController`.
- Commission rules optionally reference `project_id`.

### Implementation Status
`IMPLEMENTED`

---

## 6. Contact Management

### Purpose
Manage the full lifecycle of prospective buyers and clients, including lead qualification, status progression, and GDPR consent fields.

### Actors
| Actor | Role |
|-------|------|
| ADMIN | Full CRUD, status transitions, convert type |
| MANAGER | Full CRUD, status transitions, convert type |
| AGENT | Read-only; view timeline |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-6-1 | ADMIN and MANAGER MUST be able to create a contact with `fullName`, `email`, optional `phone`, and optional `type` (PROSPECT / CLIENT). |
| REQ-6-2 | Duplicate email within a tenant MUST return HTTP 409 / `CONTACT_EMAIL_EXISTS`. |
| REQ-6-3 | Contacts MUST support a status workflow defined by `ContactStatus` with validated transitions. |
| REQ-6-4 | Invalid status transitions MUST return HTTP 422 / `INVALID_STATUS_TRANSITION`. |
| REQ-6-5 | ADMIN and MANAGER MUST be able to convert a contact to PROSPECT (setting `ProspectDetail` fields: budget, surface, location preferences, source). |
| REQ-6-6 | ADMIN and MANAGER MUST be able to convert a contact to CLIENT (setting `ClientDetail` fields: kind, financing type, notary). |
| REQ-6-7 | All users MUST be able to list contacts with pagination and optional status/type filters. |
| REQ-6-8 | All users MUST be able to retrieve a contact's full timeline (deposits, reservations, contracts, interests). |
| REQ-6-9 | Contacts MUST store GDPR consent fields: `consentGiven`, `consentDate`, `consentMethod`, `processingBasis`. |
| REQ-6-10 | ADMIN and MANAGER MUST be able to add/remove property interests for a contact. |

### State Machine — ContactStatus

```
PROSPECT ──qualify──► QUALIFIED_PROSPECT ──deposit──► CLIENT
PROSPECT ──lose──► LOST ──re-engage──► PROSPECT
QUALIFIED_PROSPECT ──lose──► LOST
CLIENT ──activate──► ACTIVE_CLIENT ──complete──► COMPLETED_CLIENT ──refer──► REFERRAL
CLIENT|ACTIVE_CLIENT ──lose──► LOST
REFERRAL is terminal
LOST ──re-engage──► PROSPECT
```

### ContactType

| Type | Meaning |
|------|---------|
| `PROSPECT` | Lead under evaluation |
| `TEMP_CLIENT` | Prospect with an unconfirmed deposit (7-day window) |
| `CLIENT` | Confirmed buyer |

### Cross-Module Dependencies
- Referenced by `deposit.contact_id`, `sale_contract.buyer_contact_id`, `property_reservation.contact_id`.
- GDPR erasure targets `contact` rows (anonymization).
- Portal tokens use `contactId` as `sub` claim.

### Implementation Status
`IMPLEMENTED`

---

## 7. Deposit Workflow

### Purpose
Record a financial commitment (deposit / acompte) by a contact on a specific property, with lifecycle management through confirmation, cancellation, and expiry.

### Actors
| Actor | Role |
|-------|------|
| ADMIN | Full CRUD including cancel and report |
| MANAGER | Full CRUD including cancel |
| AGENT | Read-only |
| System | Auto-expire stale PENDING deposits via scheduler |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-7-1 | ADMIN and MANAGER MUST be able to create a deposit linking a contact to a property with an amount and deposit date. |
| REQ-7-2 | Creating a deposit on a property with an existing `ACTIVE` reservation MUST return HTTP 409 / `PROPERTY_ALREADY_RESERVED`. |
| REQ-7-3 | A new deposit MUST transition the linked property's status to `RESERVED`. |
| REQ-7-4 | Confirming a deposit (`PUT /api/deposits/{id}/confirm`) MUST set status → `CONFIRMED` and update the contact type to `CLIENT`. |
| REQ-7-5 | Cancelling a deposit MUST set status → `CANCELLED` and revert the property status to `ACTIVE`. |
| REQ-7-6 | Expired deposits (no action within configured window) MUST be set to `EXPIRED` by the `DepositWorkflowScheduler`. |
| REQ-7-7 | Deposits MUST have unique auto-generated reference numbers (format: `DEP-{year}-{seq}`). |
| REQ-7-8 | ADMIN and MANAGER MUST be able to retrieve a deposit report grouped by agent. |
| REQ-7-9 | ADMIN and MANAGER MUST be able to generate a PDF reservation document for a deposit. |

### State Machine — DepositStatus

```
PENDING ──confirm──► CONFIRMED
PENDING ──cancel──► CANCELLED
PENDING ──scheduler (expired)──► EXPIRED
CONFIRMED, CANCELLED, EXPIRED are terminal
```

### Cross-Module Dependencies
- Creates `sale_contract` prerequisites (deposit reference in contract).
- Triggers property status change.
- `ReservationService.create()` blocks if ACTIVE reservation exists.
- PDF generation via `ReservationDocumentService` (openhtmltopdf + Thymeleaf).

### Implementation Status
`IMPLEMENTED`

---

## 8. Property Reservation

### Purpose
Allow an "intent to buy" to be recorded without a financial commitment. A lightweight hold that reserves a property for a contact for a configurable period.

### Actors
| Actor | Role |
|-------|------|
| ADMIN | Full CRUD |
| MANAGER | Full CRUD |
| AGENT | Read-only |
| System | Auto-expire reservations via hourly `ReservationExpiryScheduler` |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-8-1 | ADMIN and MANAGER MUST be able to create a reservation linking a contact to an `ACTIVE` property. |
| REQ-8-2 | Reservation creation MUST use a pessimistic write lock on the property row to prevent double-reservation. |
| REQ-8-3 | The default expiry period MUST be +7 days from creation; this MUST be overridable per request. |
| REQ-8-4 | Only one `ACTIVE` reservation per property is allowed; attempting to create a second MUST return HTTP 409. |
| REQ-8-5 | ADMIN and MANAGER MUST be able to cancel an `ACTIVE` reservation. |
| REQ-8-6 | An `ACTIVE` reservation MUST be convertible to a formal deposit (`PUT /api/reservations/{id}/convert`). |
| REQ-8-7 | The `ReservationExpiryScheduler` MUST run hourly and set all `ACTIVE` reservations past their `expiresAt` to `EXPIRED`. |
| REQ-8-8 | Creating a deposit on a property with an `ACTIVE` reservation MUST be blocked (HTTP 409) unless it is the same contact converting their own reservation. |

### State Machine — ReservationStatus

```
ACTIVE ──cancel (manual)──► CANCELLED
ACTIVE ──expiresAt < now (scheduler)──► EXPIRED
ACTIVE ──convert to deposit──► CONVERTED_TO_DEPOSIT
CANCELLED, EXPIRED, CONVERTED_TO_DEPOSIT are terminal
```

### Cross-Module Dependencies
- `DepositService.create()` checks for `ACTIVE` reservations.
- Dashboard KPIs include `propertyHoldsCount` and `propertyHoldsExpiringSoon`.

### Implementation Status
`IMPLEMENTED`

---

## 9. Sale Contracts

### Purpose
Formalise the sale of a property to a buyer contact, linking to the originating deposit and producing a signed PDF contract.

### Actors
| Actor | Role |
|-------|------|
| ADMIN | Full CRUD including sign and cancel |
| MANAGER | Full CRUD including sign and cancel |
| AGENT | Can create contracts for their own sales; cannot sign or cancel |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-9-1 | ADMIN, MANAGER, and AGENT (own sales) MUST be able to create a sale contract referencing a confirmed deposit, property, buyer contact, agreed price, and list price. |
| REQ-9-2 | Creating a contract on a property that is already `SOLD` MUST return HTTP 409 / `PROPERTY_ALREADY_SOLD`. |
| REQ-9-3 | The deposit amount on the contract MUST match the linked deposit amount; mismatch MUST return HTTP 422 / `CONTRACT_DEPOSIT_MISMATCH`. |
| REQ-9-4 | ADMIN and MANAGER MUST be able to sign a `DRAFT` contract (`PUT /api/contracts/{id}/sign`), setting status → `SIGNED` and property status → `SOLD`. |
| REQ-9-5 | ADMIN and MANAGER MUST be able to cancel a contract (`PUT /api/contracts/{id}/cancel`). |
| REQ-9-6 | GDPR erasure MUST be blocked when a `SIGNED` contract exists for the contact (HTTP 409 / `GDPR_ERASURE_BLOCKED`). |
| REQ-9-7 | ADMIN, MANAGER MUST be able to retrieve a PDF of the contract document. |
| REQ-9-8 | ADMIN, MANAGER MUST be able to list all contracts; AGENT sees only their own. |

### State Machine — SaleContractStatus

```
DRAFT ──sign──► SIGNED (property → SOLD)
DRAFT ──cancel──► CANCELED
SIGNED ──cancel (business rescission)──► CANCELED
CANCELED is terminal
```

### Cross-Module Dependencies
- Requires a `CONFIRMED` deposit as prerequisite.
- Triggers property status → `SOLD` on signing.
- Payment schedule items reference `contract_id`.
- Commission is calculated from `agreedPrice` and `listPrice`.
- Portal users view their own contracts.

### Implementation Status
`IMPLEMENTED`

---

## 10. Payment Schedule

### Purpose
Break the sale contract total into installment calls-for-funds (appels de fonds), track their issuance, dispatch, and payment receipts.

### Actors
| Actor | Role |
|-------|------|
| ADMIN | Full schedule CRUD; issue, cancel items |
| MANAGER | Full schedule CRUD; issue, cancel items |
| AGENT | Read-only |
| PORTAL | View own contract's schedule and payments |
| System | Daily overdue scanner; reminder dispatch |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-10-1 | ADMIN and MANAGER MUST be able to create `PaymentScheduleItem` rows for a contract specifying label, amount, due date, and sequence. |
| REQ-10-2 | ADMIN and MANAGER MUST be able to issue an item (`PUT .../issue`), setting status → `ISSUED` and stamping `issuedAt`. |
| REQ-10-3 | ADMIN and MANAGER MUST be able to send an item (`PUT .../send`), dispatching an email via the outbox and setting status → `SENT`. |
| REQ-10-4 | The system MUST allow recording payments against an item (`POST .../payments`), tracking `amountPaid` and `paidAt`. |
| REQ-10-5 | When `sum(schedule_payment.amount) >= item.amount` the item status MUST transition to `PAID` automatically. |
| REQ-10-6 | A daily scheduler MUST scan `ISSUED` and `SENT` items with `due_date < today` and set them to `OVERDUE`. |
| REQ-10-7 | ADMIN and MANAGER MUST be able to cancel non-`PAID` items. |
| REQ-10-8 | Portal users MUST be able to view the payment schedule for their own contracts. |

### State Machine — PaymentScheduleStatus

```
DRAFT ──issue──► ISSUED ──send (outbox)──► SENT
ISSUED|SENT ──daily scanner──► OVERDUE
ISSUED|SENT|OVERDUE ──sum(payments)>=amount──► PAID (terminal)
DRAFT|ISSUED|SENT|OVERDUE ──cancel──► CANCELED (terminal)
```

### Cross-Module Dependencies
- Items reference `contract_id`, `project_id`, `property_id` (denormalized).
- Email dispatch via outbox messaging module.
- Receivables dashboard aggregates item data.

### Implementation Status
`IMPLEMENTED`

---

## 11. Commission Tracking

### Purpose
Automatically calculate agent commissions for each signed contract using tenant-level or project-level commission rules.

### Actors
| Actor | Role |
|-------|------|
| ADMIN | Manage commission rules; view all commissions |
| MANAGER | View all commissions |
| AGENT | View own commissions |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-11-1 | ADMIN MUST be able to create, update, and delete commission rules specifying rate (%) and/or fixed amount, scoped to tenant or project. |
| REQ-11-2 | A project-specific rule MUST take precedence over the tenant-level default rule. |
| REQ-11-3 | Commission MUST be calculated as: `agreedPrice × rate/100 + fixedAmount`. |
| REQ-11-4 | Commission entries MUST be created automatically when a contract is signed. |
| REQ-11-5 | ADMIN and MANAGER MUST be able to list all commissions; AGENT sees only their own commissions. |
| REQ-11-6 | Commissions MUST be scoped to the tenant. |

### Business Rules
- When no rule exists for a project or tenant, commission calculation is skipped.
- `listPrice` and `agreedPrice` difference feeds the discount analytics in the dashboard.

### Cross-Module Dependencies
- Triggered by `SaleContractService.sign()` events.
- Reads `CommissionRule` by `(tenantId, projectId)` with fallback to tenant-default.

### Implementation Status
`IMPLEMENTED`

---

## 12. Commercial Dashboard

### Purpose
Provide real-time KPI aggregations for sales performance, revenue forecasting, deposit activity, and agent productivity.

### Actors
| Actor | Role |
|-------|------|
| ADMIN | View all-tenant summary |
| MANAGER | View all-tenant summary |
| AGENT | View own-agent summary only |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-12-1 | `GET /api/dashboard/commercial` MUST return a tenant-wide summary for ADMIN/MANAGER. |
| REQ-12-2 | For AGENT, the summary MUST be filtered to the requesting agent's own data via `resolveEffectiveAgentId()`. |
| REQ-12-3 | The summary MUST include: `contractsSignedCount`, `totalRevenue`, `avgContractValue`, `depositPendingCount`, `depositConfirmedCount`, `propertiesAvailableCount`, `propertiesSoldCount`, `propertyHoldsCount`, `propertyHoldsExpiringSoon`. |
| REQ-12-4 | The summary MUST include discount analytics: `avgDiscountPercent`, `maxDiscountPercent`, `discountByAgent[]`. |
| REQ-12-5 | The summary MUST include a `prospectsBySource[]` funnel array. |
| REQ-12-6 | `GET /api/dashboard/receivables` MUST return aging buckets (current, 1–30d, 31–60d, 61–90d, 90d+) for outstanding payment schedule items. |
| REQ-12-7 | Dashboard results MUST be cached with a 30 s TTL to reduce database load. |
| REQ-12-8 | Optional `agentId` and `projectId` query params MUST scope the summary for ADMIN/MANAGER users. |
| REQ-12-9 | Optional `from` / `to` date params MUST scope the query window. |

### Cross-Module Dependencies
- Reads from: `sale_contract`, `deposit`, `property`, `property_reservation`, `payment_schedule_item`, `schedule_payment`, `commission`, `contact`, `prospect_detail`.
- Cache names: `commercialDashboard` (30 s, 500 entries), `receivablesDashboard` (30 s, 200 entries).

### Implementation Status
`IMPLEMENTED`

---

## 13. Commercial Audit Log

### Purpose
Record an immutable time-stamped log entry for every significant commercial action (contract signed, deposit confirmed, etc.) for compliance and operational visibility.

### Actors
| Actor | Role |
|-------|------|
| ADMIN | View audit log with filters |
| MANAGER | View audit log with filters |
| System | Write entries on events |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-13-1 | The system MUST write an audit entry for: deposit confirmed, deposit cancelled, contract signed, contract cancelled. |
| REQ-13-2 | Each entry MUST capture: `tenantId`, `actorId` (performing user), `entityType`, `entityId`, `action`, `metadata` (JSON), `occurredAt`. |
| REQ-13-3 | ADMIN and MANAGER MUST be able to query the audit log with filters: `entityType`, `entityId`, `actorId`, date range, and pagination. |
| REQ-13-4 | Audit entries MUST be immutable (append-only). |

### Cross-Module Dependencies
- Written by `DepositService`, `SaleContractService`.
- `CommercialAuditRepository` uses JPQL CAST pattern for nullable `LocalDateTime` params.

### Implementation Status
`IMPLEMENTED`

---

## 14. Client Portal

### Purpose
Give property buyers a self-service read-only view of their own contracts and payment schedules, accessed via a passwordless magic link.

### Actors
| Actor | Role |
|-------|------|
| Contact (buyer) | Request magic link; view own contracts and payments |
| System | Send magic link email; validate one-time token |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-14-1 | `POST /api/portal/auth/request-link` MUST accept an email address and send a magic link to the contact (if found) without revealing whether the email exists. |
| REQ-14-2 | The magic link token MUST be 32-byte SecureRandom, URL-safe base64 encoded; the SHA-256 hex of the token is stored in `portal_token` table. |
| REQ-14-3 | `GET /api/portal/auth/verify?token=...` MUST validate the token, mark it used, and return a portal JWT. |
| REQ-14-4 | Portal JWTs MUST have `sub = contactId`, `roles = ["ROLE_PORTAL"]`, TTL 2 hours. |
| REQ-14-5 | Magic link tokens MUST expire after 48 hours. |
| REQ-14-6 | Used tokens MUST be rejected (`PORTAL_TOKEN_ALREADY_USED`). |
| REQ-14-7 | Portal magic link requests MUST be rate-limited: 3 requests per hour per IP. |
| REQ-14-8 | Portal users MUST be able to list their own contracts (`GET /api/portal/contracts`). |
| REQ-14-9 | Portal users MUST be able to view payment schedule for their own contract. |
| REQ-14-10 | Portal endpoints MUST return 404 for any resource not belonging to the authenticated contact. |

### Business Rules
- Portal JWT filter path in `JwtAuthenticationFilter` detects `ROLE_PORTAL` and skips `UserSecurityCacheService` (contactId is not an `app_user` row).
- Magic link email is sent directly (not via outbox) because there is no `app_user` FK for the public endpoint.

### Cross-Module Dependencies
- Reads `contact` by email.
- Reads `sale_contract` filtered by `buyer_contact_id`.
- Reads `payment_schedule_item` and `schedule_payment` filtered by `contract_id`.

### Implementation Status
`IMPLEMENTED`

---

## 15. Notification Centre

### Purpose
In-app bell notification system delivering real-time CRM alerts to individual users (separate from the transactional outbox email/SMS system).

### Actors
| Actor | Role |
|-------|------|
| All CRM users | View own notifications; mark as read |
| System | Create notification entries on events |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-15-1 | The system MUST create notification entries for relevant CRM events (deposit confirmed, contract signed, etc.). |
| REQ-15-2 | Users MUST be able to list their unread notifications via `GET /api/notifications`. |
| REQ-15-3 | Users MUST be able to mark a notification as read via `PUT /api/notifications/{id}/read`. |
| REQ-15-4 | Notifications MUST be scoped to the target user and tenant. |

### Cross-Module Dependencies
- Package `notification/` (distinct from `outbox/` which is email/SMS).
- Written by service events in `deposit/`, `contract/` modules.

### Implementation Status
`IMPLEMENTED`

---

## 16. Outbox Messaging (Email / SMS)

### Purpose
Guarantee at-least-once transactional delivery of email and SMS messages using the transactional outbox pattern, with exponential backoff retries.

### Actors
| Actor | Role |
|-------|------|
| System | Write outbox rows; dispatch via SMTP / Twilio; retry on failure |
| ADMIN | View sent/failed messages via `GET /api/messages` |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-16-1 | Outbox rows MUST be written within the caller's `@Transactional` boundary so that send and save are atomic. |
| REQ-16-2 | `OutboundDispatcherService` MUST poll the outbox table on a configurable interval (`OUTBOX_POLL_INTERVAL_MS`, default 10 000 ms). |
| REQ-16-3 | The dispatcher MUST use `FOR UPDATE SKIP LOCKED` to allow concurrent multi-instance processing without double-send. |
| REQ-16-4 | Failed sends MUST be retried with exponential backoff: 1 min, 5 min, 30 min delays. |
| REQ-16-5 | After `OUTBOX_MAX_RETRIES` (default 3) failed attempts, the message MUST be set to `FAILED` (permanent). |
| REQ-16-6 | SMTP is only activated when `EMAIL_HOST` is non-blank; otherwise the no-op provider is used. |
| REQ-16-7 | Twilio SMS is only activated when `TWILIO_ACCOUNT_SID` is non-blank; otherwise the no-op provider is used. |
| REQ-16-8 | ADMIN MUST be able to list messages and view delivery status via `GET /api/messages`. |
| REQ-16-9 | The scheduler MUST be disableable via `spring.task.scheduling.enabled=false` (test profile). |

### Business Rules
- Magic link emails (public endpoint, no user context) bypass the outbox and call `EmailSender.send()` directly.
- Outbox dispatcher batch size is configurable via `OUTBOX_BATCH_SIZE` (default 50).

### Cross-Module Dependencies
- `EmailSender` / `SmsSender` interfaces implemented by SMTP / Twilio providers or no-op defaults.
- Used by payment schedule item `send()` action, welcome emails, etc.

### Implementation Status
`IMPLEMENTED`

---

## 17. Property Media

### Purpose
Attach images and documents to properties. Storage backend is local filesystem by default; S3-compatible object storage is opt-in.

### Actors
| Actor | Role |
|-------|------|
| ADMIN | Upload, delete media |
| MANAGER | Upload, delete media |
| AGENT | View media |
| All | Download media files |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-17-1 | ADMIN and MANAGER MUST be able to upload media files for a property via `POST /api/properties/{id}/media`. |
| REQ-17-2 | The system MUST store the file using the active `MediaStorage` implementation (local or object storage). |
| REQ-17-3 | All users MUST be able to list media for a property via `GET /api/properties/{id}/media`. |
| REQ-17-4 | All users MUST be able to download a media file via `GET /api/properties/{id}/media/{mediaId}`. |
| REQ-17-5 | ADMIN and MANAGER MUST be able to delete a media file. |
| REQ-17-6 | Local storage MUST write files to the configured directory (`MEDIA_UPLOAD_DIR`, default `./uploads`). |
| REQ-17-7 | Object storage is activated by `MEDIA_OBJECT_STORAGE_ENABLED=true` and configured via `MEDIA_S3_*` env vars. |

### Business Rules
- `LocalFileMediaStorage` is `@Primary`; `ObjectStorageMediaStorage` is `@ConditionalOnProperty("app.media.object-storage.enabled")`.
- Media keys for object storage are UUID-based string identifiers, not `UUID` objects.

### Cross-Module Dependencies
- Reads `property` to validate tenant ownership.
- MinIO in Docker Compose provides a local S3-compatible backend for end-to-end testing.

### Implementation Status
`IMPLEMENTED`

---

## 18. GDPR / Law 09-08 Compliance

### Purpose
Provide data subjects with access to their personal data, the ability to correct it, and the right to erasure — compliant with GDPR and Moroccan Law 09-08.

### Actors
| Actor | Role |
|-------|------|
| ADMIN | Export data, anonymize contact |
| MANAGER | Export data, rectify contact |
| System | Data retention scheduler |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-18-1 | ADMIN and MANAGER MUST be able to export all personal data for a contact as a JSON payload via `GET /api/gdpr/contacts/{id}/export`. |
| REQ-18-2 | The export MUST include: contact fields, prospect details, client details, deposits, contracts, interests. |
| REQ-18-3 | The export endpoint MUST be rate-limited (general API rate limiter, key = `gdpr-export:{tenantId}:{contactId}`). |
| REQ-18-4 | ADMIN MUST be able to anonymize a contact via `DELETE /api/gdpr/contacts/{id}/anonymize`. |
| REQ-18-5 | Anonymization MUST be blocked when a `SIGNED` sale contract exists for the contact (HTTP 409 / `GDPR_ERASURE_BLOCKED`). |
| REQ-18-6 | Anonymization MUST zero PII fields: `full_name` → `"ANONYMIZED"`, `email` → `"anonymized-{id}@deleted.invalid"`, `phone` → null, `first_name` / `last_name` → null. |
| REQ-18-7 | Anonymization MUST set `anonymized_at = now()` and clear `ProspectDetail` and `ClientDetail` rows. |
| REQ-18-8 | ADMIN and MANAGER MUST be able to rectify (update) a contact's data via `PUT /api/gdpr/contacts/{id}/rectify`. |
| REQ-18-9 | The system MUST provide a privacy notice via `GET /api/gdpr/privacy-notice`. |
| REQ-18-10 | A `DataRetentionScheduler` MUST run nightly to flag or process records past their retention window. |
| REQ-18-11 | Contacts MUST store consent metadata: `consentGiven`, `consentDate`, `consentMethod` (enum), `processingBasis` (enum). |

### Business Rules
- Anonymization is NOT hard delete — FK integrity is preserved for financial audit trails.
- `anonymized_at` provides a verifiable erasure record.
- `ConsentMethod` values: `WEB_FORM`, `EMAIL`, `VERBAL`, `WRITTEN`.
- `ProcessingBasis` values: `CONSENT`, `CONTRACT`, `LEGAL_OBLIGATION`, `LEGITIMATE_INTEREST`.

### Cross-Module Dependencies
- Reads from `contact`, `prospect_detail`, `client_detail`, `deposit`, `sale_contract`, `contact_interest`.
- Anonymization blocks on `sale_contract.status = SIGNED`.

### Implementation Status
`IMPLEMENTED`

---

## 19. Reminder Service

### Purpose
Send automated payment reminders for overdue or upcoming payment schedule items via email/SMS through the outbox.

### Actors
| Actor | Role |
|-------|------|
| System | Daily scheduler; identify overdue items; dispatch reminders |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-19-1 | The `ReminderService` MUST run on a daily schedule to identify `OVERDUE` payment schedule items. |
| REQ-19-2 | For each overdue item, the system MUST dispatch a reminder email/SMS to the buyer contact via the outbox. |
| REQ-19-3 | Reminders MUST be idempotent — the same item MUST NOT generate duplicate reminders for the same overdue day. |
| REQ-19-4 | The scheduler MUST be disableable via `spring.task.scheduling.enabled=false`. |

### Cross-Module Dependencies
- Reads `payment_schedule_item` where `status = OVERDUE`.
- Dispatches via `OutboxMessageService` → outbox table → `OutboundDispatcherService`.

### Implementation Status
`IMPLEMENTED`

---

## 20. Observability

### Purpose
Expose health endpoints, metrics, and distributed traces for operational monitoring.

### Actors
| Actor | Role |
|-------|------|
| Ops team | Monitor health, metrics, traces |
| System | Emit OTLP traces and metrics |

### Requirements

| ID | Requirement |
|----|-------------|
| REQ-20-1 | `GET /actuator/health` MUST be publicly accessible and return `{"status":"UP"}` when the application is healthy. |
| REQ-20-2 | `GET /actuator/info` MUST be publicly accessible. |
| REQ-20-3 | The system MUST emit OpenTelemetry traces via OTLP when `OTEL_EXPORTER_OTLP_ENDPOINT` is configured. |
| REQ-20-4 | The system MUST assign a `X-Correlation-ID` header to each request (generated if absent) for log correlation. |
| REQ-20-5 | Application logs MUST include `tenantId` and `correlationId` in the MDC context. |

### Business Rules
- Micrometer OTLP exporter is auto-configured via `management.otlp.*` properties.
- `RequestCorrelationFilter` injects the correlation ID into MDC and the response header.

### Cross-Module Dependencies
- `RequestCorrelationFilter` runs before `JwtAuthenticationFilter` in the Spring Security filter chain.

### Implementation Status
`IMPLEMENTED`
