# Modules Reference

One section per domain package. Derived from source code in `hlm-backend/src/main/java/com/yem/hlm/backend/`.

## Table of Contents

1. [auth](#auth)
2. [tenant](#tenant)
3. [user](#user)
4. [project](#project)
5. [property](#property)
6. [contact](#contact)
7. [deposit](#deposit)
8. [reservation](#reservation)
9. [contract](#contract)
10. [dashboard](#dashboard)
11. [audit](#audit)
12. [notification](#notification)
13. [outbox](#outbox)
14. [commission](#commission)
15. [payments](#payments)
16. [portal](#portal)
17. [media](#media)
18. [reminder](#reminder)
19. [gdpr](#gdpr)
20. [common](#common)

---

## auth

**Owns:** Login flow, JWT generation, password management, rate limiting, account lockout, Spring Security configuration.

**Key classes:**

| Class | Role |
|-------|------|
| `AuthController` | `POST /auth/login` — public endpoint |
| `AuthMeController` | `GET /auth/me` — returns current user info |
| `AuthService` | Login logic: BCrypt verification, lockout check, rate limit check, JWT generation |
| `JwtProvider` | HS256 JWT encode/decode using Spring Security OAuth2 `JwtEncoder`/`JwtDecoder` |
| `JwtProperties` | `@ConfigurationProperties("security.jwt")` for `secret` and `ttl-seconds` |
| `JwtBeansConfig` | Registers `JwtEncoder`, `JwtDecoder`, `NimbusJwtDecoder` beans |
| `SecurityConfig` | Spring Security filter chain: CORS, CSRF disabled, route security rules |
| `JwtAuthenticationFilter` | `OncePerRequestFilter` — extracts JWT, validates, populates TenantContext |
| `UserSecurityCacheService` | Loads `UserSecurityInfo` (enabled + tokenVersion) from cache or DB |
| `LoginRateLimiter` | Bucket4j per-IP and per-email token buckets for login rate limiting |
| `CorsConfig` | `CorsConfigurationSource` bean reading `app.cors.allowed-origins` |

**Key business rules:**
- Lockout after `LOCKOUT_MAX_ATTEMPTS` (default 5) consecutive failures for `LOCKOUT_DURATION_MINUTES` (default 15).
- Token revocation via `token_version` column checked on every request.
- Portal tokens (`ROLE_PORTAL`) skip `UserSecurityCacheService` — portal sessions are stateless.

**Imports from:** `tenant.context`, `user.domain`, `user.repo`

---

## tenant

**Owns:** Tenant entity, TenantContext ThreadLocal, tenant bootstrap API.

**Key classes:**

| Class | Role |
|-------|------|
| `Tenant` | `@Entity` for the `tenant` table. Constructor: `new Tenant(String key, String name)` |
| `TenantContext` | Static utility with `ThreadLocal<UUID>` for tenantId and userId |
| `TenantRepository` | `findByKey(String key)` for login lookup |
| `TenantController` | `POST /tenants` — public endpoint to create a new tenant |
| `TenantService` | Creates tenant + owner user atomically |

**Imports from:** `user.domain`, `user.repo`

---

## user

**Owns:** CRM user management (ADMIN-only admin panel).

**Key classes:**

| Class | Role |
|-------|------|
| `AdminUserController` | `GET/POST /api/admin/users`, `PATCH /{id}/role`, `PATCH /{id}/enabled`, `POST /{id}/reset-password` |
| `AdminUserService` | Creates users with BCrypt password, changes role (increments `token_version`), enables/disables (increments `token_version`), generates temporary password on reset |
| `UserRepository` | `findByTenantIdAndEmail(UUID, String)` etc. |
| `UserRole` | Enum: `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_AGENT` |
| `User` | `@Entity` for the `app_user` table |

**Key business rules:**
- Default role for new users is `ROLE_AGENT`.
- Changing role or disabling a user increments `token_version`, invalidating all current tokens within ~60 seconds.

**Imports from:** `tenant.domain`, `auth.service` (for cache eviction)

---

## project

**Owns:** Real-estate project catalog.

**Key classes:**

| Class | Role |
|-------|------|
| `ProjectController` | `GET/POST /api/projects`, `GET/PUT/DELETE /api/projects/{id}`, `GET /api/projects/{id}/kpis` |
| `ProjectService` | CRUD, archive (soft status change), KPI aggregation |
| `Project` | `@Entity` for `project` table. Status: `ACTIVE` or `ARCHIVED` |
| `ProjectActiveGuard` | Service helper: throws `ArchivedProjectAssignmentException` if project is ARCHIVED |

**Key business rules:**
- Archiving a project does not delete it. Properties that reference it keep the FK.
- ARCHIVED projects cannot have new properties assigned (enforced in `PropertyService.create()`).

**Imports from:** `tenant.context`, `property.repo` (for KPI queries)

---

## property

**Owns:** Property catalog — 9 types, soft delete, CSV import, dashboard KPIs.

**Key classes:**

| Class | Role |
|-------|------|
| `PropertyController` | `GET/POST /api/properties`, `GET/PUT/DELETE /api/properties/{id}`, `POST /api/properties/import` |
| `PropertyDashboardController` | `GET /api/properties/dashboard/sales-kpi` etc. |
| `PropertyService` | CRUD with type validation, soft delete (`deleted_at` timestamp) |
| `PropertyImportService` | CSV parsing via Apache Commons CSV, row-level validation, bulk insert |
| `PropertyType` | Enum: `VILLA`, `APPARTEMENT`, `DUPLEX`, `STUDIO`, `T2`, `T3`, `COMMERCE`, `LOT`, `TERRAIN_VIERGE` |
| `PropertyStatus` | Enum: `ACTIVE`, `RESERVED`, `SOLD`, `DELETED` |
| `Property` | `@Entity` for `property` table |

**Key business rules:**
- Type-specific required fields enforced in `PropertyService.validateTypeFields()`.
- VILLA requires: `surfaceAreaSqm`, `landAreaSqm`, `bedrooms`, `bathrooms`.
- APPARTEMENT requires: `surfaceAreaSqm`, `bedrooms`, `bathrooms`, `floorNumber`.
- Soft delete sets `deleted_at` and status `DELETED`; all list queries filter `WHERE deleted_at IS NULL`.

**Imports from:** `tenant.context`, `project.service`, `media.service`

---

## contact

**Owns:** Prospect and client contact lifecycle, property interests, status transitions, activity timeline.

**Key classes:**

| Class | Role |
|-------|------|
| `ContactController` | Full CRUD + status, convert-to-prospect, convert-to-client, interests CRUD, timeline |
| `ContactService` | Status machine enforcement, interest management, pagination |
| `ContactTimelineService` | Aggregates audit events + outbox messages + notifications into a unified timeline |
| `Contact` | `@Entity` with `contact_type`, `status`, and GDPR consent fields |
| `ContactStatus` | Enum: `NEW`, `CONTACTED`, `QUALIFIED_PROSPECT`, `ACTIVE_CLIENT`, `INACTIVE`, `LOST` |
| `ProspectDetail` | One-to-one entity for `prospect_detail` table |
| `ClientDetail` | One-to-one entity for `client_detail` table |
| `ContactInterest` | Many-to-many bridge for property interests with `InterestStatus` |

**Key business rules:**
- `convertToProspect()` transitions status to `QUALIFIED_PROSPECT` and creates `ProspectDetail`.
- `convertToClient()` transitions to `ACTIVE_CLIENT` and creates `ClientDetail`.
- `ACTIVE_CLIENT → INACTIVE` and `INACTIVE → LOST` are valid transitions; reversal is blocked.
- Interest is unique per `(contactId, propertyId)`.

**Imports from:** `tenant.context`, `property.domain`, `audit.service`, `outbox.service`, `notification.service`

---

## deposit

**Owns:** Booking deposit workflow, PDF reservation certificate.

**Key classes:**

| Class | Role |
|-------|------|
| `DepositController` | `POST /api/deposits`, `GET /api/deposits/{id}`, `POST /{id}/confirm`, `POST /{id}/cancel`, `GET /{id}/documents/reservation.pdf`, `GET /deposits/report` |
| `DepositService` | Create with pessimistic check for existing active reservation, confirm, cancel |
| `DepositWorkflowScheduler` | Daily cron: marks PENDING deposits past due-date |
| `ReservationDocumentService` | Generates PDF reservation certificate using Thymeleaf + openhtmltopdf |
| `Deposit` | `@Entity` for `deposit` table |
| `DepositStatus` | Enum: `PENDING`, `CONFIRMED`, `CANCELLED`, `CONVERTED` |
| `DepositReferenceGenerator` | Generates `DEP-YYYY-NNN` reference strings |

**Key business rules:**
- Cannot create a deposit if an `ACTIVE` `property_reservation` exists for the property (`DepositService` blocks this).
- Confirming a deposit sets property status to `RESERVED`.
- Cancelling a deposit reverts property to `ACTIVE`.

**Imports from:** `tenant.context`, `contact.domain`, `property.service`, `notification.service`, `audit.service`

---

## reservation

**Owns:** Short-term property hold (réservation informelle before deposit).

**Key classes:**

| Class | Role |
|-------|------|
| `ReservationController` | `POST/GET /api/reservations`, `GET/POST /{id}/cancel`, `POST /{id}/convert-to-deposit` |
| `ReservationService` | Create with pessimistic write lock on property, cancel, convert-to-deposit |
| `ReservationExpiryScheduler` | Hourly cron: marks ACTIVE reservations past `expiry_date` as EXPIRED |
| `PropertyReservation` | `@Entity` for `property_reservation` table |
| `ReservationStatus` | Enum: `ACTIVE`, `EXPIRED`, `CANCELLED`, `CONVERTED_TO_DEPOSIT` |

**Key business rules:**
- `ReservationService.create()` acquires a pessimistic write lock (`FOR UPDATE`) on the property row to prevent concurrent reservations.
- Only `ACTIVE` → `CANCELLED` or `ACTIVE` → `CONVERTED_TO_DEPOSIT` transitions are valid.
- Default expiry is 7 days from creation.

**Imports from:** `tenant.context`, `property.repo`, `deposit.service`, `tenant.domain`

---

## contract

**Owns:** Sale contract lifecycle, PDF generation.

**Key classes:**

| Class | Role |
|-------|------|
| `ContractController` | `POST/GET /api/contracts`, `GET/POST /{id}`, `POST /{id}/sign`, `POST /{id}/cancel`, `GET /{id}/documents/contract.pdf` |
| `SaleContractService` | Create DRAFT, sign (→ property SOLD), cancel (→ property reverts) |
| `ContractDocumentService` | PDF via Thymeleaf + openhtmltopdf |
| `SaleContract` | `@Entity` for `sale_contract` table |
| `SaleContractStatus` | Enum: `DRAFT`, `SIGNED`, `CANCELLED` |

**Key business rules:**
- Cannot sign if property already has a SIGNED contract (`PropertyAlreadySoldException`).
- Partial unique index `uk_sc_property_signed` enforces at DB level.
- `AGENT` callers on `POST /api/contracts` are automatically set as the agent.
- `AGENT` callers can only see their own contracts.
- Signing fires `CommercialAuditEvent` and `Notification` to the agent.

**Imports from:** `tenant.context`, `property.service`, `deposit.service`, `audit.service`, `notification.service`

---

## dashboard

**Owns:** Commercial KPI dashboard, receivables dashboard, cash dashboard.

**Key classes:**

| Class | Role |
|-------|------|
| `CommercialDashboardController` | `GET /api/dashboard/commercial` (alias), `/summary`, `/sales` |
| `CommercialDashboardService` | 16 JPQL/native queries: signed contracts KPIs, inventory, funnel, discounts, commissions, holds |
| `ReceivablesDashboardController` | `GET /api/dashboard/receivables` |
| `ReceivablesDashboardService` | Aging buckets (0-30, 31-60, 61-90, 90+ days) from payment schedule items |
| `CashDashboardController` | `GET /api/dashboard/cash` |
| `CommercialDashboardSummaryDTO` | Java record with 16+ fields including `propertyHoldsCount`, `propertyHoldsExpiringSoon` |

**Key business rules:**
- `AGENT` callers have `agentId` forced to their own ID by `resolveEffectiveAgentId()`.
- Summary responses cached 30 s in `commercialDashboardSummaryCache`.
- Date range defaults to last 30 days when `from`/`to` are null.

**Imports from:** `tenant.context`, `contract.repo`, `deposit.repo`, `property.repo`, `commission.service`, `reservation.repo`

---

## audit

**Owns:** Immutable commercial audit event log.

**Key classes:**

| Class | Role |
|-------|------|
| `CommercialAuditController` | `GET /api/audit/commercial` — ADMIN/MANAGER only |
| `CommercialAuditService` | Writes and queries `commercial_audit_event` table |
| `CommercialAuditEvent` | `@Entity` for `commercial_audit_event` table |

**Imports from:** `tenant.context`

---

## notification

**Owns:** In-app CRM bell notifications (not outbound email/SMS — that is `outbox`).

**Key classes:**

| Class | Role |
|-------|------|
| `NotificationController` | `GET /api/notifications` (paginated), `POST /api/notifications/{id}/read`, `POST /api/notifications/read-all` |
| `NotificationService` | Creates and retrieves notifications scoped to `(tenantId, recipientUserId)` |
| `Notification` | `@Entity` for `notification` table |

**Imports from:** `tenant.context`, `user.repo`

---

## outbox

**Owns:** Transactional outbox pattern for EMAIL and SMS delivery.

**Key classes:**

| Class | Role |
|-------|------|
| `MessageController` | `POST /api/messages` — queues an outbound message |
| `OutboundDispatcherService` | Fixed-delay scheduler: `FOR UPDATE SKIP LOCKED` batch claim, send, retry with exponential backoff |
| `OutboxMessageService` | Creates `outbound_message` rows inside the caller's transaction |
| `SmtpEmailSender` | Active when `app.email.host` is non-blank (`@ConditionalOnExpression`) |
| `NoopEmailSender` | Default when SMTP is not configured |
| `TwilioSmsSender` | Active when `TWILIO_ACCOUNT_SID` is set |
| `NoopSmsSender` | Default when Twilio is not configured |
| `OutboundMessage` | `@Entity` for `outbound_message` table |

**Key business rules:**
- Exponential backoff delays: `{1, 5, 30}` minutes. After `OUTBOX_MAX_RETRIES` attempts → permanent `FAILED`.
- `SmtpEmailSender` uses `@ConditionalOnExpression("!'${app.email.host:}'.isBlank()")` (not `@ConditionalOnProperty`) to handle empty-string env vars.
- Dispatcher query uses `FOR UPDATE SKIP LOCKED` for multi-instance safety. Must run inside `@Transactional`.

**Imports from:** `tenant.context`, `user.repo`

---

## commission

**Owns:** Commission rules and per-agent commission queries.

**Key classes:**

| Class | Role |
|-------|------|
| `CommissionController` | `GET /api/commissions/my`, `GET /api/commissions`, CRUD on `/api/commission-rules` |
| `CommissionService` | Resolves effective rule (project-specific over tenant default), calculates commission |
| `CommissionRule` | `@Entity` for `commission_rule` table |

**Key business rules:**
- Formula: `commission = agreedPrice × rate/100 + fixedAmount`.
- If a project-specific rule exists, it takes precedence over the tenant-wide default.
- Commission data is derived from `SIGNED` sale contracts at query time (not pre-computed).

**Imports from:** `tenant.context`, `contract.repo`

---

## payments

**Owns:** Payment schedule items (appels de fonds), partial payment recording, overdue cron, reminders.

**Key classes:**

| Class | Role |
|-------|------|
| `PaymentScheduleController` | Full CRUD on schedule items and payments — see controller for all 11 endpoints |
| `PaymentScheduleService` | Create/update/delete schedule items |
| `CallForFundsWorkflowService` | State transitions: DRAFT→ISSUED→SENT/OVERDUE→PAID, add payment |
| `CallForFundsPdfService` | PDF of "appel de fonds" using Thymeleaf + openhtmltopdf |
| `ReminderService` | Daily cron: sends overdue notifications and pre-due warnings |
| `PaymentsOverdueScheduler` | Daily at 06:00: marks items past due-date as OVERDUE |

**Key business rules:**
- Payment schedule items have states: `DRAFT`, `ISSUED`, `SENT`, `OVERDUE`, `PAID`, `CANCELED`.
- Partial payments are allowed (multiple `schedule_payment` rows per item).
- `PaymentsOverdueCron` default: `0 0 6 * * *` (configurable via `PAYMENTS_OVERDUE_CRON`).

**Imports from:** `tenant.context`, `contract.repo`, `outbox.service`, `notification.service`

---

## portal

**Owns:** Client-facing buyer portal — magic link auth, ROLE_PORTAL JWT, read-only contract and payment views.

**Key classes:**

| Class | Role |
|-------|------|
| `PortalAuthController` | `POST /api/portal/auth/request-link`, `GET /api/portal/auth/verify` — public |
| `PortalAuthService` | Generates 32-byte SecureRandom token, stores SHA-256 hash in `portal_token`, sends email |
| `PortalJwtProvider` | Same encoder/decoder beans as `JwtProvider`; emits `roles: ["ROLE_PORTAL"]` with 2h TTL |
| `PortalContractsController` | `GET /api/portal/contracts` — ROLE_PORTAL only |
| `PortalPaymentsController` | `GET /api/portal/contracts/{id}/payments` — ROLE_PORTAL only |
| `PortalPropertyController` | `GET /api/portal/properties/{id}` — ROLE_PORTAL only |
| `PortalTokenCleanupScheduler` | Daily at 03:00: deletes expired/used `portal_token` rows |

**Key business rules:**
- Magic link token is raw 32-byte SecureRandom, URL-safe base64 in the email URL.
- SHA-256 hex stored in DB (not raw token).
- Token TTL: 48 hours. One-time use: `used_at` set on first `verify` call.
- Portal principal = contactId (not a CRM userId).
- All portal data queries scope to `(tenantId, contactId)`.

**Imports from:** `tenant.repo`, `contact.repo`, `outbox.service`, `payments.service`, `contract.service`

---

## media

**Owns:** Property photo and PDF upload, download, and deletion.

**Key classes:**

| Class | Role |
|-------|------|
| `PropertyMediaController` | `POST/GET /api/properties/{id}/media`, `GET /api/media/{mediaId}/download`, `DELETE /api/media/{mediaId}` |
| `PropertyMediaService` | Upload validation (size, MIME type), storage delegation |
| `LocalFileMediaStorage` | Default: stores files under `MEDIA_STORAGE_DIR` (default `./uploads`) |
| `ObjectStorageMediaStorage` | S3-compatible: active when `MEDIA_OBJECT_STORAGE_ENABLED=true` |
| `PropertyMedia` | `@Entity` for `property_media` table |

**Constraints:**
- Max file size: `MEDIA_MAX_FILE_SIZE` (default 10 MB).
- Allowed MIME types: `MEDIA_ALLOWED_TYPES` (default: `image/jpeg,image/png,image/webp,application/pdf`).

**Imports from:** `tenant.context`, `property.repo`

---

## reminder

**Owns:** Daily scheduled reminders for deposits and stale prospects.

**Key classes:**

| Class | Role |
|-------|------|
| `ReminderService` (in `reminder/`) | Deposit due-date warnings (7, 3, 1 days before) and stale prospect alerts |
| `ReminderScheduler` | Daily at 08:00 cron (`REMINDER_CRON`) |

**Configuration:**

| Env var | Default | Purpose |
|---------|---------|---------|
| `REMINDER_ENABLED` | `true` | Enable/disable the reminder scheduler |
| `REMINDER_CRON` | `0 0 8 * * *` | Cron expression |
| `REMINDER_DEPOSIT_WARN_DAYS` | `7,3,1` | Days before deposit due-date |
| `REMINDER_PROSPECT_STALE_DAYS` | `14` | Days of inactivity = stale |

**Imports from:** `deposit.repo`, `contact.repo`, `outbox.service`, `notification.service`

---

## gdpr

**Owns:** GDPR / Law 09-08 compliance — data export, erasure (anonymization), rectification view, privacy notice.

**Key classes:**

| Class | Role |
|-------|------|
| `GdprController` | `GET /export`, `DELETE /anonymize`, `GET /rectify`, `GET /privacy-notice` |
| `GdprService` | Delegates to `DataExportBuilder` and `AnonymizationService` |
| `DataExportBuilder` | Assembles `DataExportResponse` containing all personal data for one contact |
| `AnonymizationService` | Zeros PII fields; blocked if SIGNED contracts exist |
| `DataRetentionScheduler` | Daily at 02:00: sweeps contacts past retention period |
| `PrivacyNoticeLoader` | Loads `privacy-notice.json` from classpath |
| `DataExportResponse` | DTO with contact details, deposit history, contract history, interests |

**Imports from:** `tenant.context`, `contact.repo`, `deposit.repo`, `contract.repo`, `notification.repo`

---

## common

**Owns:** Cross-cutting concerns shared by all other modules.

**Key classes:**

| Class | Role |
|-------|------|
| `ErrorResponse` | Standard JSON error envelope: `timestamp`, `status`, `error`, `code`, `message`, `path`, `fieldErrors` |
| `ErrorCode` | Enum of all 40+ application error codes |
| `GlobalExceptionHandler` | `@RestControllerAdvice` mapping all application exceptions to `ErrorResponse` |
| `FieldError` | Record `(field, message)` for validation errors |
| `StrongPassword` / `StrongPasswordValidator` | Custom JSR-303 annotation: min 12 chars, mixed case, digit, special |
| `RateLimiterService` | General-purpose Bucket4j token bucket service |
| `RateLimitProperties` | Config for rate limit capacity and refill period |
| `RequestCorrelationFilter` | Adds `correlationId` UUID to MDC for every request |
| `OpenApiConfig` | SpringDoc OpenAPI/Swagger configuration |
