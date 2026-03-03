# Technical Specification — CRM-HLM Platform

| Field | Value |
|---|---|
| **Version** | 1.0 |
| **Date** | 2026-02-28 |
| **Audience** | CTO, Engineering Leads, DevOps |
| **Status** | Draft |

---

## 1. Architecture Overview

### 1.1 C4 Context (Text)

```
┌──────────────────────────────────────────────────┐
│                    Users                          │
│   (Admin / Manager / Agent via Web Browser)       │
└──────────────┬───────────────────────────────────┘
               │ HTTPS
               ▼
┌──────────────────────────────────────────────────┐
│          Angular 19 SPA (hlm-frontend)            │
│   Login | Shell | Features (Properties, Projects, │
│   Contacts, Prospects, Deposits, Contracts,       │
│   Dashboard, Messages, Notifications, Admin)      │
└──────────────┬───────────────────────────────────┘
               │ HTTP (dev proxy → /auth, /api,
               │        /dashboard, /actuator)
               ▼
┌──────────────────────────────────────────────────┐
│       Spring Boot 3.x API (hlm-backend)           │
│   Java 21 | JWT Auth | Multi-Tenant | RBAC        │
│   ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐            │
│   │ auth │ │tenant│ │ user │ │common│            │
│   ├──────┤ ├──────┤ ├──────┤ ├──────┤            │
│   │projct│ │ prop │ │contct│ │depst │            │
│   ├──────┤ ├──────┤ ├──────┤ ├──────┤            │
│   │cntrct│ │dashbd│ │outbox│ │audit │            │
│   ├──────┤ ├──────┤                               │
│   │notif │ │prpDsh│                               │
│   └──────┘ └──────┘                               │
└──────────────┬───────────────────────────────────┘
               │ JDBC (PostgreSQL)
               ▼
┌──────────────────────────────────────────────────┐
│           PostgreSQL 14+                          │
│   Liquibase-managed schema (19 changesets)        │
│   Tenant-scoped rows | Partial unique indexes     │
└──────────────────────────────────────────────────┘
```

### 1.2 Key Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Backend Runtime | Java | 21 |
| Backend Framework | Spring Boot | 3.x |
| Security | Spring Security + JWT (HS256) | — |
| ORM | Hibernate (validate mode) | — |
| DB Migrations | Liquibase | — |
| Database | PostgreSQL | 14+ |
| PDF Generation | Thymeleaf + OpenHTMLToPDF (pdfbox 2.x) | 1.0.10 |
| Caching | Caffeine | — |
| Observability | Micrometer (via spring-boot-starter-actuator) | — |
| Frontend Framework | Angular | 19 |
| Frontend Language | TypeScript | — |
| Testing (backend) | JUnit 5 + Testcontainers (PostgreSQL) | — |
| Build (backend) | Maven (mvnw) | — |
| Build (frontend) | npm / Angular CLI | — |

---

## 2. Repo Module Map

### 2.1 Backend Feature Packages

Location: `hlm-backend/src/main/java/com/yem/hlm/backend/`

| Package | Sub-packages | Purpose |
|---|---|---|
| `auth` | `api/`, `api/dto/`, `config/`, `security/`, `service/` | JWT login, token validation, filter, RBAC config, cache |
| `tenant` | `api/`, `api/dto/`, `context/`, `domain/`, `repo/`, `service/` | Tenant bootstrap, TenantContext ThreadLocal |
| `user` | `api/`, `api/dto/`, `domain/`, `repo/`, `service/` | Admin user management, roles |
| `project` | `api/`, `api/dto/`, `domain/`, `repo/`, `service/` | Project CRUD, KPIs, archive guard |
| `property` | `api/`, `api/dto/`, `domain/`, `repo/`, `service/` | Property CRUD, commercial workflow, dashboard |
| `contact` | `api/`, `api/dto/`, `domain/`, `repo/`, `service/` | Contact/prospect management, interests |
| `deposit` | `api/`, `api/dto/`, `config/`, `domain/`, `repo/`, `scheduler/`, `service/`, `service/pdf/` | Deposit lifecycle, auto-expiry, reservation PDF |
| `contract` | `api/`, `api/dto/`, `domain/`, `repo/`, `service/`, `service/pdf/` | Sales contract lifecycle, buyer snapshot, contract PDF |
| `dashboard` | `api/`, `api/dto/`, `service/` | Commercial KPI dashboard |
| `outbox` | `api/`, `api/dto/`, `domain/`, `repo/`, `scheduler/`, `service/`, `service/provider/` | Outbound messaging (Email/SMS) |
| `audit` | `api/`, `api/dto/`, `domain/`, `repo/`, `service/` | Commercial audit trail |
| `notification` | `api/`, `api/dto/`, `domain/`, `repo/`, `service/` | In-app notifications |
| `common` | `error/`, `openapi/` | ErrorCode, ErrorResponse, GlobalExceptionHandler, OpenAPI config |

### 2.2 Frontend Feature Folders

Location: `hlm-frontend/src/app/`

| Folder | Key Files | Purpose |
|---|---|---|
| `core/auth/` | `auth.service.ts`, `auth.guard.ts`, `admin.guard.ts`, `auth.interceptor.ts` | Authentication, guards, JWT interceptor |
| `core/models/` | `*.model.ts` (13 files) | TypeScript interfaces for API DTOs |
| `features/login/` | `login.component.ts/html` | Login page |
| `features/shell/` | `shell.component.ts/html` | App shell with navigation bar |
| `features/properties/` | `properties.component.ts/html`, `property.service.ts` | Property list |
| `features/projects/` | `projects.component.ts/html`, `project-detail.component.ts/html`, `project.service.ts` | Project list + detail with KPIs |
| `features/contacts/` | `contacts.component.ts/html`, `contact-detail.component.ts/html`, `contact.service.ts` | Contact list + detail |
| `features/prospects/` | `prospects.component.ts/html`, `prospect-detail.component.ts/html`, `prospect.service.ts`, `deposit.service.ts`, `contact-interest.service.ts` | Prospect pipeline, deposits, interests |
| `features/contracts/` | `contracts.component.ts/html`, `contract.service.ts` | Contract list with PDF download + messaging |
| `features/dashboard/` | `commercial-dashboard.component.ts/html`, `commercial-dashboard-sales.component.ts`, `commercial-dashboard.service.ts` | KPI dashboard + sales drill-down |
| `features/outbox/` | `outbox.component.ts/html`, `outbox.service.ts` | Message list + compose |
| `features/notifications/` | `notifications.component.ts/html`, `notification.service.ts` | In-app notifications |
| `features/admin-users/` | `admin-users.component.ts/html`, `admin-user.service.ts`, `admin-user.model.ts` | Admin user management |

---

## 3. Multi-Tenancy Implementation

### 3.1 Tenant Resolution Flow

```
HTTP Request → JwtAuthenticationFilter
  → Extract JWT from Authorization header
  → Decode and validate JWT (HS256, secret, expiry)
  → Extract claims: sub (userId), tid (tenantId), roles, tv (tokenVersion)
  → Validate tv against UserSecurityCacheService
  → Set TenantContext.setTenantId(tid) + TenantContext.setUserId(sub)
  → Set Spring Security Authentication with roles
  → Chain.doFilter()
  → (finally) TenantContext.clear()
```

**Key classes**:
- `auth/security/JwtAuthenticationFilter.java` — OncePerRequestFilter
- `tenant/context/TenantContext.java` — ThreadLocal storage for `tenantId` + `userId`
- `auth/service/JwtProvider.java` — JWT encode/decode/validate

### 3.2 TenantContext Usage

- Services read `TenantContext.getTenantId()` to scope all operations.
- Repositories have methods like `findByTenantId(UUID)`, `findByTenantIdAndId(UUID, UUID)`.
- Cross-tenant access returns `NotFoundException` (→ 404), never 403, to prevent information leakage.

### 3.3 Tenant-Scoped Repositories

All repositories filter by `tenant_id`:
- `PropertyRepository`: `findByTenantIdAndDeletedFalse()`, `findByTenantIdAndId()`
- `ContactRepository`: `findByTenantId()`, `countActiveProspects(UUID tenantId)`
- `DepositRepository`: `findByTenantId()`, `findForPdf()` (JOIN FETCH)
- `SaleContractRepository`: `findForPdf()` (JOIN FETCH)
- `OutboundMessageRepository`: queries scoped by tenant
- `CommercialAuditRepository`: `findByTenantId()`
- `NotificationRepository`: `findByTenantId()`

---

## 4. Security

### 4.1 Auth Flow

1. `POST /auth/login` → `AuthService.login()` → validates credentials → `JwtProvider.generateToken()` → JWT response.
2. JWT contains: `sub` (UUID userId), `tid` (UUID tenantId), `roles` (list), `tv` (int tokenVersion), `exp`.
3. Algorithm: HS256. Secret injected via `security.jwt.secret` env var. Min 32 chars.
4. TTL: `security.jwt.ttl-seconds` (env `JWT_TTL_SECONDS`).

### 4.2 JWT Claims Structure

```json
{
  "sub": "uuid-user-id",
  "tid": "uuid-tenant-id",
  "roles": ["ROLE_ADMIN"],
  "tv": 1,
  "iat": 1709100000,
  "exp": 1709186400
}
```

### 4.3 Token Revocation Strategy

- `User.tokenVersion` (int, DB column) is the source of truth.
- Incremented on: role change (`AdminUserService.changeRole()`), account disable (`AdminUserService.setEnabled(false)`).
- `JwtAuthenticationFilter` calls `UserSecurityCacheService.getSecurityInfo(userId)`:
  - Returns `UserSecurityInfo(tokenVersion, enabled, roles)`.
  - Rejects if: info null (user deleted), not enabled, or `tv != tokenVersion`.
- Cache: Spring `@Cacheable("userSecurityCache")` backed by Caffeine. Evicted on role/disable changes.

**Evidence**: `auth/service/UserSecurityCacheService.java`, `auth/security/JwtAuthenticationFilter.java`, `user/service/AdminUserService.java`. Tests: `TokenRevocationIT`.

### 4.4 RBAC Enforcement

- Roles: `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_AGENT` (defined in `UserRole` enum).
- Controller-level: `@PreAuthorize("hasRole('ADMIN')")`, `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")`.
- Service-level: AGENT restrictions (e.g., `SaleContractService` filters by agent userId, `CommercialDashboardService` forces `agentId`).

### 4.5 Error Handling Contract

- `ErrorResponse` record: `{timestamp, status, error, code, message, path, fieldErrors}`.
- `ErrorCode` enum: 20+ codes covering validation, auth, conflicts, business rules.
- `GlobalExceptionHandler`: maps domain exceptions to HTTP status + ErrorCode.
- Security handlers: `CustomAuthenticationEntryPoint` (401), `CustomAccessDeniedHandler` (403).

---

## 5. Backend Design

### 5.1 Layering

Each feature package follows:
```
api/              → @RestController + DTOs (records)
  dto/            → Request/Response records
service/          → Business logic, tenant checks, transactional boundaries
  pdf/            → PDF generation services (deposit, contract)
  provider/       → Provider interfaces (outbox)
repo/             → Spring Data JPA repositories (tenant-scoped)
domain/           → JPA entities + enums
config/           → Feature-specific Spring config
scheduler/        → Scheduled tasks
```

### 5.2 Transactional Rules & Locking

**Critical commercial flows use pessimistic locking**:
- `DepositService.confirm()`: Acquires `PESSIMISTIC_WRITE` lock on `Property` row before confirming. Rejects if property is SOLD → 409.
- `SaleContractService.sign()`: Acquires `PESSIMISTIC_WRITE` lock on `Property` before marking as SOLD.
- `SaleContractService.cancel()`: Acquires `PESSIMISTIC_WRITE` lock on `Property` before reverting status.

**Lock ordering** (enforced across all 4 concurrent flows): Property lock acquired BEFORE Deposit/Contract row saves. Prevents deadlocks.

**Transactional boundaries**:
- Service methods are `@Transactional`.
- Audit events use `@Transactional(propagation = REQUIRED)` — participate in caller's transaction.
- Outbox message insertion is a separate transaction from dispatch.

### 5.3 Property Commercial Workflow Service

`PropertyCommercialWorkflowService` (`property/service/`) is the **single source of truth** for all property status transitions:
- `reserve(Property, LocalDateTime)` → ACTIVE → RESERVED
- `releaseReservation(Property)` → RESERVED → ACTIVE
- `sell(Property, LocalDateTime)` → RESERVED/ACTIVE → SOLD
- `cancelSaleToAvailable(Property)` → SOLD → ACTIVE
- `cancelSaleToReserved(Property)` → SOLD → RESERVED

### 5.4 Outbox Pattern (Messaging)

```
Compose (sync)                    Dispatch (async)
─────────────                    ────────────────
POST /api/messages                OutboundDispatcherScheduler
  → MessageComposeService           (polls every 5s)
  → validate + insert PENDING       → OutboundDispatcherService
  → return 202 + messageId           → SELECT FOR UPDATE SKIP LOCKED
                                     → call EmailSender / SmsSender
                                     → on success: SENT
                                     → on failure: retry (backoff)
                                     → after max retries: FAILED
```

Backoff intervals: attempt 1 → +1 min, attempt 2 → +5 min, attempt 3+ → +30 min. Max retries: 3 (configurable).

### 5.5 PDF Generation Architecture

```
DocumentService (e.g. ReservationDocumentService)
  → RBAC check (tenant + ownership)
  → Build model record (e.g. ReservationDocumentModel)
  → Call DocumentGenerationService
      → Thymeleaf processes HTML template
      → OpenHTMLToPDF renders HTML → PDF bytes
  → Return ResponseEntity<byte[]> with Content-Disposition
```

**Templates**: `hlm-backend/src/main/resources/templates/documents/`
- `reservation.html` — Reservation certificate
- `contract.html` — Sales contract (5 sections: property, prices, buyer, agent, signatures)

**N+1 avoidance**: Dedicated `findForPdf()` repository methods with `JOIN FETCH` on related entities.

---

## 6. Database

### 6.1 Liquibase Strategy

- Master changelog: `db/changelog/db.changelog-master.yaml`
- 19 changesets (001 → 019), additive-only. Never edit applied changesets.
- Hibernate: `ddl-auto: validate` — all schema changes through Liquibase.
- Location: `hlm-backend/src/main/resources/db/changelog/changes/`

### 6.2 Core Tables / Entities

| Table | Entity | Key Columns | FK Relationships |
|---|---|---|---|
| `tenant` | `Tenant` | id, tenant_key, name | — |
| `users` | `User` | id, tenant_id, email, password_hash, role, enabled, token_version | → tenant |
| `project` | `Project` | id, tenant_id, name, status | → tenant |
| `property` | `Property` | id, tenant_id, project_id, reference_code, type, status, price, reserved_at, deleted | → tenant, → project |
| `contact` | `Contact` | id, tenant_id, email, full_name, phone, status, type, deleted | → tenant |
| `prospect_detail` | `ProspectDetail` | id, contact_id, budget, source, notes | → contact |
| `client_detail` | `ClientDetail` | id, contact_id, kind, company, ice, siret | → contact |
| `contact_interest` | `ContactInterest` | id, contact_id, property_id, status | → contact, → property |
| `deposit` | `Deposit` | id, tenant_id, contact_id, property_id, agent_id, reference, amount, currency, status, due_date | → tenant, → contact, → property, → user |
| `sale_contract` | `SaleContract` | id, tenant_id, property_id, buyer_contact_id, agent_id, status, agreed_price, list_price, buyer_* snapshot fields | → tenant, → property, → contact, → user |
| `outbound_message` | `OutboundMessage` | id, tenant_id, channel, status, recipient, body, contact_id, retries, next_retry_at, correlation_type, correlation_id | → tenant |
| `commercial_audit_event` | `CommercialAuditEvent` | id, tenant_id, event_type, actor_user_id, correlation_type, correlation_id, occurred_at, payload_json | → tenant |
| `notification` | `Notification` | id, tenant_id, user_id, type, read, deposit_id | → tenant, → user |

### 6.3 Key Indexes & Constraints

- `uk_sc_property_signed` — Partial unique index on `sale_contract(property_id) WHERE status = 'SIGNED'` — prevents double-selling.
- `property.reference_code` unique per tenant.
- `contact.email` unique per tenant.
- `deposit` unique on (tenant_id, contact_id, property_id) for active deposits.
- All tables have `tenant_id` FK for isolation.
- KPI-related indexes on `sale_contract` and `deposit` for dashboard query performance.

---

## 7. API Inventory

### 7.1 Auth Module

| Method | Path | RBAC | Request DTO | Response DTO |
|---|---|---|---|---|
| POST | `/auth/login` | Public | `LoginRequest` | `LoginResponse` |
| GET | `/auth/me` | Authenticated | — | `{userId, tenantId}` |

### 7.2 Tenant Module

| Method | Path | RBAC | Request DTO | Response DTO |
|---|---|---|---|---|
| POST | `/tenants` | Public (bootstrap) | `TenantCreateRequest` | `TenantResponse` |
| GET | `/tenants/{id}` | Authenticated (own tenant) | — | `TenantResponse` |

### 7.3 User Management Module

| Method | Path | RBAC | Request DTO | Response DTO |
|---|---|---|---|---|
| GET | `/api/admin/users` | ADMIN | — | `List<UserResponse>` |
| POST | `/api/admin/users` | ADMIN | `CreateUserRequest` | `UserResponse` |
| PATCH | `/api/admin/users/{id}/role` | ADMIN | `ChangeRoleRequest` | `UserResponse` |
| PATCH | `/api/admin/users/{id}/enabled` | ADMIN | `SetEnabledRequest` | `UserResponse` |
| POST | `/api/admin/users/{id}/reset-password` | ADMIN | — | `ResetPasswordResponse` |

### 7.4 Project Module

| Method | Path | RBAC | Request DTO | Response DTO |
|---|---|---|---|---|
| POST | `/api/projects` | ADMIN, MANAGER | `ProjectCreateRequest` | `ProjectResponse` |
| GET | `/api/projects` | Authenticated | — | `List<ProjectResponse>` |
| GET | `/api/projects/{id}` | Authenticated | — | `ProjectResponse` |
| PUT | `/api/projects/{id}` | ADMIN, MANAGER | `ProjectUpdateRequest` | `ProjectResponse` |
| DELETE | `/api/projects/{id}` | ADMIN | — | 204 (archives) |
| GET | `/api/projects/{id}/kpis` | ADMIN, MANAGER | — | `ProjectKpiDTO` |

### 7.5 Property Module

| Method | Path | RBAC | Request DTO | Response DTO |
|---|---|---|---|---|
| POST | `/api/properties` | ADMIN, MANAGER | `PropertyCreateRequest` | `PropertyResponse` |
| GET | `/api/properties/{id}` | Authenticated | — | `PropertyResponse` |
| GET | `/api/properties` | Authenticated | Query: `type`, `status` | `List<PropertyResponse>` |
| PUT | `/api/properties/{id}` | ADMIN, MANAGER | `PropertyUpdateRequest` | `PropertyResponse` |
| DELETE | `/api/properties/{id}` | ADMIN | — | 204 |

### 7.6 Property Dashboard Module

| Method | Path | RBAC | Request DTO | Response DTO |
|---|---|---|---|---|
| GET | `/dashboard/properties/summary` | ADMIN, MANAGER | Query: `from`, `to`, `preset` | `PropertySummaryDTO` |
| GET | `/dashboard/properties/sales-kpi` | ADMIN, MANAGER | Query: `from`, `to`, `preset` | `PropertySalesKpiDTO` |

### 7.7 Contact Module

| Method | Path | RBAC | Request DTO | Response DTO |
|---|---|---|---|---|
| POST | `/api/contacts` | ADMIN, MANAGER | `CreateContactRequest` | `ContactResponse` |
| GET | `/api/contacts/{id}` | Authenticated | — | `ContactResponse` |
| GET | `/api/contacts` | Authenticated | Query: `status`, `q`, page/size/sort | `Page<ContactResponse>` |
| PATCH | `/api/contacts/{id}` | ADMIN, MANAGER | `UpdateContactRequest` | `ContactResponse` |
| PATCH | `/api/contacts/{id}/status` | ADMIN, MANAGER | `UpdateStatusRequest` | `ContactResponse` |
| POST | `/api/contacts/{id}/convert-to-client` | ADMIN, MANAGER | `ConvertToClientRequest` | `ContactResponse` |
| POST | `/api/contacts/{id}/interests` | ADMIN, MANAGER | `ContactInterestRequest` | 201 |
| DELETE | `/api/contacts/{id}/interests/{propertyId}` | ADMIN | — | 204 |
| GET | `/api/contacts/{id}/interests` | Authenticated | — | `List<ContactInterestResponse>` |
| GET | `/api/properties/{propertyId}/contacts` | Authenticated | — | `List<UUID>` |

### 7.8 Deposit Module

| Method | Path | RBAC | Request DTO | Response DTO |
|---|---|---|---|---|
| POST | `/api/deposits` | ADMIN, MANAGER | `CreateDepositRequest` | `DepositResponse` |
| GET | `/api/deposits/{id}` | Authenticated | — | `DepositResponse` |
| GET | `/api/deposits/{id}/documents/reservation.pdf` | Authenticated (AGENT: own) | — | `application/pdf` |
| POST | `/api/deposits/{id}/confirm` | ADMIN, MANAGER | — | `DepositResponse` |
| POST | `/api/deposits/{id}/cancel` | ADMIN, MANAGER | — | `DepositResponse` |
| GET | `/api/deposits/report` | ADMIN, MANAGER | Query: status, agentId, contactId, propertyId, from, to | `DepositReportResponse` |

### 7.9 Contract Module

| Method | Path | RBAC | Request DTO | Response DTO |
|---|---|---|---|---|
| POST | `/api/contracts` | Authenticated | `CreateContractRequest` | `ContractResponse` |
| POST | `/api/contracts/{id}/sign` | ADMIN, MANAGER | — | `ContractResponse` |
| POST | `/api/contracts/{id}/cancel` | ADMIN, MANAGER | — | `ContractResponse` |
| GET | `/api/contracts` | Authenticated (AGENT: own) | Query: status, projectId, agentId, from, to | `List<ContractResponse>` |
| GET | `/api/contracts/{id}/documents/contract.pdf` | Authenticated (AGENT: own) | — | `application/pdf` |

### 7.10 Dashboard Module

| Method | Path | RBAC | Request DTO | Response DTO |
|---|---|---|---|---|
| GET | `/api/dashboard/commercial` | Authenticated | Query: from, to (YYYY-MM-DD), projectId, agentId | `CommercialDashboardSummaryDTO` |
| GET | `/api/dashboard/commercial/summary` | Authenticated | Query: from, to (ISO datetime), projectId, agentId | `CommercialDashboardSummaryDTO` |
| GET | `/api/dashboard/commercial/sales` | Authenticated | Query: from, to, projectId, agentId, page, size | `CommercialDashboardSalesDTO` |

### 7.11 Outbox Module

| Method | Path | RBAC | Request DTO | Response DTO |
|---|---|---|---|---|
| POST | `/api/messages` | ADMIN, MANAGER, AGENT | `SendMessageRequest` | 202 + `SendMessageResponse` |
| GET | `/api/messages` | ADMIN, MANAGER, AGENT | Query: channel, status, contactId, from, to, page, size | `Page<MessageResponse>` |

### 7.12 Audit Module

| Method | Path | RBAC | Request DTO | Response DTO |
|---|---|---|---|---|
| GET | `/api/audit/commercial` | ADMIN, MANAGER | Query: from, to, correlationType, correlationId, limit (max 500) | `List<AuditEventResponse>` |

### 7.13 Notification Module

| Method | Path | RBAC | Request DTO | Response DTO |
|---|---|---|---|---|
| GET | `/api/notifications` | Authenticated | Query: read, size | `List<NotificationResponse>` |
| POST | `/api/notifications/{id}/read` | Authenticated | — | `NotificationResponse` |

---

## 8. Frontend

### 8.1 Routing

```
/login                              → LoginComponent (public)
/app                                → ShellComponent (auth guard)
  /app/properties                   → PropertiesComponent
  /app/contacts                     → ContactsComponent
  /app/contacts/:id                 → ContactDetailComponent
  /app/prospects                    → ProspectsComponent
  /app/prospects/:id                → ProspectDetailComponent
  /app/notifications                → NotificationsComponent
  /app/messages                     → OutboxComponent
  /app/projects                     → ProjectsComponent
  /app/projects/:id                 → ProjectDetailComponent
  /app/admin/users                  → AdminUsersComponent (admin guard)
  /app/contracts                    → ContractsComponent
  /app/dashboard/commercial         → CommercialDashboardComponent
  /app/dashboard/commercial/sales   → CommercialDashboardSalesComponent
  /app (default)                    → redirects to /app/properties
/ (root)                            → redirects to /login
```

### 8.2 Guards

- `authGuard` (`core/auth/auth.guard.ts`): Checks for JWT in localStorage; redirects to `/login` if missing.
- `adminGuard` (`core/auth/admin.guard.ts`): Checks for ROLE_ADMIN in JWT; blocks non-admin access to `/app/admin/*`.

### 8.3 Interceptor

- `auth.interceptor.ts`: Attaches `Authorization: Bearer <token>` to all HTTP requests. On 401 response, clears token and redirects to `/login`.

### 8.4 Services

| Service | Location | API Calls |
|---|---|---|
| `AuthService` | `core/auth/` | Login, logout, token management |
| `PropertyService` | `features/properties/` | CRUD properties |
| `ProjectService` | `features/projects/` | CRUD projects, KPIs |
| `ContactService` | `features/contacts/` | CRUD contacts, status |
| `ProspectService` | `features/prospects/` | Prospect-specific ops |
| `DepositService` | `features/prospects/` | Deposit CRUD, reservation PDF download |
| `ContactInterestService` | `features/prospects/` | Interest management |
| `ContractService` | `features/contracts/` | Contract CRUD, PDF download |
| `CommercialDashboardService` | `features/dashboard/` | Dashboard summary + sales |
| `OutboxService` | `features/outbox/` | Send + list messages |
| `NotificationService` | `features/notifications/` | List + mark read |
| `AdminUserService` | `features/admin-users/` | User management |

### 8.5 Dashboard UI Strategy

- **CommercialDashboardComponent**: Filter bar (date range, project, agent) → KPI summary cards → bar charts (sales by day, deposits by day) → top-10 tables (by project, by agent) → inventory status/type breakdown.
- **CommercialDashboardSalesComponent**: Paginated drill-down table of individual sales.
- Both components use `CommercialDashboardService` with single API calls (no client-side aggregation).

---

## 9. Testing Strategy

### 9.1 Test Types

| Type | Runner | Location | Purpose |
|---|---|---|---|
| Unit Tests | `./mvnw test` | `src/test/**/*Test.java` | Service logic, domain rules, JWT validation |
| Integration Tests | `./mvnw failsafe:integration-test` | `src/test/**/*IT.java` | Full HTTP round-trip with real PostgreSQL (Testcontainers) |
| Frontend Build | `npm run build` | `hlm-frontend/` | Compile-time check for Angular |
| Smoke Test | `scripts/smoke-auth.sh` | — | Auth + protected endpoint verification |

### 9.2 Key IT Classes

| IT Class | Tests | What It Guarantees |
|---|---|---|
| `TenantControllerIT` | Tenant bootstrap | Tenant creation, key uniqueness |
| `CrossTenantIsolationIT` | Cross-tenant access | Data never leaks between tenants |
| `RbacIT` | Role enforcement | ADMIN/MANAGER/AGENT permissions |
| `AdminUserControllerIT` | User CRUD | Create, role change, enable/disable |
| `TokenRevocationIT` | JWT revocation | Role change → immediate session invalidation |
| `PropertyControllerIT` | Property CRUD | CRUD, type validation, archived project guard |
| `ProjectControllerIT` | Project CRUD + KPIs | CRUD, archive, KPI endpoint |
| `ContactServiceIT` | Contact logic | Status transitions, interests, conversion |
| `ContactControllerIT` | Contact API | CRUD, search, pagination |
| `DepositControllerIT` | Deposit lifecycle | Create→Confirm→Cancel, property status changes |
| `ContractControllerIT` | 11 tests | Full contract lifecycle, double-selling, buyer snapshot, AGENT restriction |
| `ContractPdfIT` | 6 tests | PDF generation, RBAC, cross-tenant |
| `ReservationPdfIT` | 8 tests | PDF generation, RBAC, cross-tenant |
| `CommercialDashboardIT` | 5 tests | Summary, AGENT scoping, active prospects |
| `OutboxIT` | 9 tests | Message send, tenant isolation, dispatch |
| `CommercialAuditIT` | 4 tests | Event recording, tenant isolation, AGENT 403 |
| `ErrorContractIT` | Error format | Consistent error response structure |

### 9.3 Local Run Commands

```bash
# Backend unit tests
cd hlm-backend && ./mvnw test

# Backend integration tests (requires Docker)
cd hlm-backend && ./mvnw failsafe:integration-test

# Single IT class
cd hlm-backend && ./mvnw failsafe:integration-test -Dit.test=ContractControllerIT

# Frontend build check
cd hlm-frontend && npm run build

# Auth smoke test
TENANT_KEY=acme EMAIL=admin@acme.com PASSWORD='Admin123!' ./scripts/smoke-auth.sh
```

---

## 10. Operations

### 10.1 Configuration

**Required environment variables**:

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC URL | — (required) |
| `DB_USER` | DB username | — (required) |
| `DB_PASSWORD` | DB password | — (required) |
| `JWT_SECRET` | HS256 signing secret (≥ 32 chars) | — (required, fail-fast) |
| `JWT_TTL_SECONDS` | JWT token TTL | 86400 |

**Optional configuration** (with defaults):

| Variable | Purpose | Default |
|---|---|---|
| `OUTBOX_BATCH_SIZE` | Messages per dispatch batch | 20 |
| `OUTBOX_MAX_RETRIES` | Max retry attempts | 3 |
| `OUTBOX_POLL_INTERVAL_MS` | Dispatch polling interval | 5000 |

### 10.2 Profiles

- Default: development mode with Liquibase auto-run.
- Test: `spring.task.scheduling.enabled=false` (disables outbox scheduler + deposit expiry scheduler for IT determinism).

### 10.3 Build & Deploy

```bash
# Backend build
cd hlm-backend && ./mvnw -DskipTests=false verify

# Frontend production build
cd hlm-frontend && npm run build

# Frontend dev server (with proxy)
cd hlm-frontend && npm start
```

Frontend proxy (`proxy.conf.json`) forwards `/auth`, `/api`, `/dashboard`, `/actuator` to `http://localhost:8080`.

### 10.4 Observability

- **Actuator**: Spring Boot Actuator endpoints available at `/actuator`.
- **Dashboard metrics** (Micrometer):
  - `Timer("commercial_dashboard_summary_duration")` — cache-miss computation time.
  - `Counter("commercial_dashboard_summary_cache_misses_total")` — cache miss count.
  - `Counter("commercial_dashboard_summary_requests_total")` — total requests.
  - Slow-query WARN log when computation > 300 ms.

---

## 11. Implementation Status & Tech Debt

### 11.1 Implemented (with evidence)

| Module | Evidence | Status |
|---|---|---|
| Auth + JWT + Revocation | `AuthController`, `JwtAuthenticationFilter`, `TokenRevocationIT` | ✅ Complete |
| Multi-tenant | `TenantContext`, `CrossTenantIsolationIT` | ✅ Complete |
| RBAC | `SecurityConfig`, `@PreAuthorize`, `RbacIT` | ✅ Complete |
| User Management | `AdminUserController`, `AdminUserControllerIT` | ✅ Complete |
| Projects | `ProjectController`, `ProjectActiveGuard`, `ProjectControllerIT` | ✅ Complete |
| Properties | `PropertyController`, `PropertyCommercialWorkflowService`, `PropertyControllerIT` | ✅ Complete |
| Contacts/Prospects | `ContactController`, `ContactService`, `ContactServiceIT` | ✅ Complete |
| Deposits | `DepositController`, `DepositService`, `DepositControllerIT` | ✅ Complete |
| Reservation PDF | `ReservationDocumentService`, `DocumentGenerationService`, `ReservationPdfIT` | ✅ Complete |
| Sales Contracts | `ContractController`, `SaleContractService`, `ContractControllerIT` (11 tests) | ✅ Complete |
| Contract PDF | `ContractDocumentService`, `ContractPdfIT` | ✅ Complete |
| Buyer Snapshot | `SaleContract` snapshot fields, changeset 018, `ContractControllerIT` | ✅ Complete |
| Commercial Dashboard | `CommercialDashboardController`, `CommercialDashboardService`, `CommercialDashboardIT` | ✅ Complete |
| Outbox Messaging | `MessageController`, `OutboundDispatcherService`, `OutboxIT` | ✅ Complete |
| Commercial Audit | `CommercialAuditController`, `CommercialAuditService`, `CommercialAuditIT` | ✅ Complete |
| Notifications | `NotificationController`, `NotificationService` | ✅ Complete |

### 11.2 Tech Debt & Gaps

| # | Item | Priority | Recommendation |
|---|---|---|---|
| 1 | Noop email/SMS providers | High | Implement real `EmailSender` (SMTP/SendGrid) + `SmsSender` (Twilio) |
| 2 | No Appels de Fonds PDF | High | New template + service for payment schedule document |
| 3 | No media/photo support on Property | Medium | Add file storage (S3/MinIO) + Property media endpoints |
| 4 | No CSV import/export | Medium | Add bulk import for properties + contacts |
| 5 | Fixed contact status machine | Low | Make pipeline stages configurable per tenant |
| 6 | No i18n | Low | Angular i18n + backend message localization |
| 7 | No lint/format tooling | Low | Add Checkstyle/Spotless (backend) + ESLint (frontend) |
| 8 | Frontend test coverage | Medium | Add Karma/Jest unit tests for components |
| 9 | Missing CDC modules | Varies | Prospection foncière, construction, stocks, purchases, finance, SAV |
