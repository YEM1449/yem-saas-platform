# YEM SaaS Platform — Comprehensive Audit Report

**Date:** 2026-03-22  
**Auditors:** Elite Software Expert Team  
**Repository:** `yem-saas-platform` (GitHub: YEM1449/yem-saas-platform)  
**Scope:** Full reverse engineering, multi-company audit, risk analysis, improvement strategy, domain extension, architecture evolution, documentation, and deployment guide.

---

## 1. Executive Summary

The YEM SaaS Platform (internally named **HLM — Habitat / Logement / Immobilier**) is a **real-estate CRM** purpose-built for the Moroccan market. It manages the full commercial lifecycle of property development companies: from prospect acquisition, through property reservation and deposit management, to sale contracts, payment schedules, commission tracking, and a client-facing portal.

**Key findings:**

- **Architecture quality is HIGH.** The codebase is well-organized into 17 cohesive feature modules following a clean layered pattern (Controller → Service → Repository → Entity). The multi-company migration (from `tenant_id` to `societe_id`) has been executed thoroughly across 38 Liquibase changesets.
- **Multi-company isolation is STRONG but not bulletproof.** Société-scoped queries are present across all 12+ domain services, and a `CrossSocieteIsolationIT` test validates the core isolation. However, there are specific gaps at the scheduler layer and one missing null-guard pattern that represent medium-risk vulnerabilities.
- **The domain model is RICH and well-suited** for real-estate CRM. It already covers contacts (with GDPR compliance), properties (9 types), projects, reservations, deposits, sale contracts, payment schedules, commissions, notifications, outbound messaging, media, audit trail, and a client portal with magic-link auth.
- **Deployment infrastructure** is Docker-based (PostgreSQL 16, Redis 7, MinIO, Spring Boot 3.x backend, Angular 19 frontend behind Nginx) and is production-ready with minor configuration adjustments.

**Critical issues found:** 2  
**Medium issues found:** 5  
**Low issues found:** 4

---

## 2. Repository Reverse Engineering (STEP 0)

### 2.1 Project Structure

```
yem-saas-platform/
├── hlm-backend/         # Spring Boot 3.x (Java 21, Maven)
│   ├── src/main/java/com/yem/hlm/backend/
│   │   ├── audit/          # Commercial audit trail
│   │   ├── auth/           # JWT auth, security config, rate limiting
│   │   ├── commission/     # Commission rules & calculation
│   │   ├── common/         # Shared DTOs, errors, events, validation
│   │   ├── contact/        # Contacts/Prospects/Clients CRM
│   │   ├── contract/       # Sale contracts + PDF generation
│   │   ├── dashboard/      # Commercial & receivables dashboards
│   │   ├── deposit/        # Deposit management + workflow
│   │   ├── gdpr/           # GDPR/Law 09-08 compliance
│   │   ├── media/          # Property media (local + S3)
│   │   ├── notification/   # In-app notifications
│   │   ├── outbox/         # Outbound email/SMS (transactional outbox)
│   │   ├── payments/       # Payment schedules, call-for-funds
│   │   ├── portal/         # Client-facing portal (magic-link)
│   │   ├── project/        # Real-estate projects
│   │   ├── property/       # Property inventory (9 types)
│   │   ├── reminder/       # Scheduled reminders
│   │   ├── reservation/    # Property reservations
│   │   ├── societe/        # Multi-company core (entities + context)
│   │   └── user/           # User management
│   └── src/main/resources/
│       ├── application.yml
│       ├── db/changelog/   # 38 Liquibase changesets
│       └── templates/      # Thymeleaf HTML for PDF generation
├── hlm-frontend/        # Angular 19 (standalone components, signals)
│   └── src/app/
│       ├── core/           # Auth, models, store
│       ├── features/       # Feature modules (contacts, properties, etc.)
│       └── portal/         # Client portal UI
├── nginx/               # Reverse proxy config
├── scripts/             # Dev/deploy scripts
├── docs/                # Architecture, spec, guides
└── docker-compose.yml   # Full stack orchestration
```

### 2.2 Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Backend | Spring Boot | 3.x (Java 21) |
| ORM | Hibernate / JPA | via Spring Data |
| Database | PostgreSQL | 16-alpine |
| Cache | Redis (optional, Caffeine fallback) | 7-alpine |
| Object Storage | MinIO (S3-compatible) | latest |
| Migrations | Liquibase | YAML changesets |
| Frontend | Angular | 19 (standalone components, signals) |
| Auth | JWT (HMAC-SHA) | Custom JwtProvider |
| PDF Generation | Thymeleaf + (likely OpenHTMLToPDF) | — |
| Messaging | Transactional Outbox (email/SMS) | SMTP/Twilio |
| Observability | OpenTelemetry (optional) | — |
| Container | Docker Compose | Multi-service |

### 2.3 Inferred Domain Model

Based exclusively on JPA entities found in the codebase:

```
┌─────────────┐          ┌──────────────────┐          ┌──────────────┐
│   Societe   │──────────│  AppUserSociete  │──────────│   User       │
│  (company)  │  1:N     │ (membership+role)│  N:1     │ (app_user)   │
│─────────────│          │──────────────────│          │──────────────│
│ id (UUID)   │          │ userId (PK)      │          │ id (UUID)    │
│ key         │          │ societeId (PK)   │          │ email        │
│ nom         │          │ role             │          │ passwordHash │
│ siretIce    │          │ actif            │          │ platformRole │
│ adresse     │          └──────────────────┘          │ tokenVersion │
│ emailDpo    │                                        │ enabled      │
│ pays        │                                        └──────────────┘
│ actif       │
└──────┬──────┘
       │ societeId (FK on every entity below)
       │
  ┌────┴────────────────────────────────────────────┐
  │                                                  │
┌─┴──────────┐     ┌────────────┐     ┌─────────────┴──┐
│  Project   │─────│  Property  │─────│    Contact     │
│────────────│ 1:N │────────────│     │────────────────│
│ id         │     │ id         │     │ id             │
│ societeId  │     │ societeId  │     │ societeId      │
│ name       │     │ projectId  │     │ firstName      │
│ status     │     │ type (9)   │     │ lastName       │
│            │     │ status     │     │ contactType    │
│            │     │ price      │     │ status         │
│            │     │ currency   │     │ consentGiven   │
│            │     │ 30+ fields │     │ (GDPR fields)  │
│            │     └─────┬──────┘     └───────┬────────┘
│            │           │                     │
│            │     ┌─────┴──────────────┐     │
│            │     │                    │     │
│         ┌──┴─────┴──┐ ┌──────────┐ ┌─┴─────┴──────┐
│         │ Deposit   │ │Reserv.   │ │ContactInterest│
│         │───────────│ │──────────│ │───────────────│
│         │ societeId │ │societeId │ │ societeId     │
│         │ contactId │ │contactId │ │ contactId     │
│         │ propertyId│ │propertyId│ │ propertyId    │
│         │ agentId   │ │          │ │ interestStatus│
│         │ status    │ └──────────┘ └───────────────┘
│         │ amount    │
│         └─────┬─────┘
│               │
│    ┌──────────┴───────────┐
│    │                      │
│ ┌──┴────────────┐  ┌─────┴──────────┐
│ │ SaleContract  │  │ PaymentSchedule│
│ │───────────────│  │  Item          │
│ │ societeId     │  │────────────────│
│ │ projectId     │  │ societeId      │
│ │ propertyId    │  │ contractId     │
│ │ buyerContactId│  │ amount/dueDate │
│ │ agentId       │  │ status         │
│ │ agreedPrice   │  └────────────────┘
│ │ status        │
│ └───────────────┘
│
├── CommissionRule      (societeId, projectId?, ratePercent, fixedAmount)
├── Notification        (societeId, recipientUserId, type, refId)
├── OutboundMessage     (societeId, recipientUserId, channel, status)
├── CommercialAuditEvent(societeId, eventType, actorUserId, entityType)
├── PropertyMedia       (societeId, propertyId, fileKey)
├── ProspectDetail      (contactId, budgetMin, budgetMax, source)
├── ClientDetail        (contactId, clientKind)
└── PortalToken         (societeId, contactId, token, expiresAt)
```

### 2.4 Naming Conventions

| Aspect | Convention |
|--------|-----------|
| Entity naming | Singular PascalCase (`Contact`, `SaleContract`) |
| Table naming | snake_case (`sale_contract`, `app_user_societe`) |
| Company column | `societe_id` (UUID, NOT NULL on all domain tables) |
| Package structure | `module/api/`, `module/api/dto/`, `module/domain/`, `module/repo/`, `module/service/` |
| DTO pattern | Java Records for request/response |
| Exception pattern | Custom per-domain exceptions (`ContactNotFoundException`, `PropertyAlreadySoldException`) |
| Controller paths | `/api/{resource}` (REST conventions) |
| Roles | `ADMIN`, `MANAGER`, `AGENT` (stored without ROLE_ prefix in DB) |

### 2.5 Architectural Patterns

1. **Layered Architecture:** Controller → Service → Repository → JPA Entity
2. **ThreadLocal Company Context:** `SocieteContext` (static ThreadLocal) set by `JwtAuthenticationFilter`, cleared in `finally` block
3. **Event-Driven Audit:** Spring `ApplicationEventPublisher` + `@EventListener` for audit trail
4. **Transactional Outbox:** Outbound messages (email/SMS) queued in DB, dispatched by scheduler
5. **Pessimistic Locking:** `SELECT ... FOR UPDATE` on Property for atomic reservation/deposit
6. **Soft Delete:** Properties use `deletedAt` timestamp; Contacts use `deleted` boolean
7. **GDPR by Design:** Consent tracking, anonymization service, data retention scheduler
8. **Optimistic Concurrency:** `@Version` on Contact and Property entities
9. **Portal Magic-Link Auth:** Separate JWT flow for client-facing portal (no password)
10. **Rate Limiting:** IP + identity-based for login; Bucket4j-style in-memory

---

## 3. Multi-Company Logic Audit (STEP 1)

### 3.1 How "Company" (Société) is Implemented

The multi-company architecture is centered around the `Societe` entity and uses a **shared-database, shared-schema** model with **row-level isolation** via `societe_id` columns.

**Core components:**

| Component | Role |
|-----------|------|
| `Societe` entity | Company master record (name, ICE/SIRET, address, DPO email, country) |
| `AppUserSociete` entity | Many-to-many between User and Societe with per-société role (ADMIN/MANAGER/AGENT) |
| `SocieteContext` (ThreadLocal) | Request-scoped company context: `societeId`, `userId`, `role`, `superAdmin` |
| `JwtAuthenticationFilter` | Extracts `sid` (societeId) claim from JWT → sets `SocieteContext` |
| `JwtProvider` | Generates JWTs with `sub` (userId), `sid` (societeId), `roles` claims |

**Login flow:**
1. User authenticates with email/password → `AuthService.login()`
2. System resolves `AppUserSociete` memberships
3. If 1 membership → auto-selects société, returns full JWT with `sid` claim
4. If N memberships → returns partial token + société list → client calls `/auth/switch-societe`
5. SUPER_ADMIN with no memberships → platform-level JWT (no `sid`)

### 3.2 How Isolation is Propagated

**Request path:**

```
HTTP Request → JwtAuthenticationFilter → SocieteContext.setSocieteId(uuid) → Service → Repository
                                                                                         ↓
                                                                              findBySocieteIdAndId(...)
```

**In services:** Every service method calls `requireSocieteId()` which reads from `SocieteContext.getSocieteId()` and throws `CrossTenantAccessException` if null.

**In repositories:** All queries include `societe_id` as the first parameter. Examples:
- `findBySocieteIdAndId(UUID societeId, UUID id)`
- `findBySocieteIdAndDeletedAtIsNull(UUID societeId)`
- Custom `@Query` methods always include `WHERE ... societe_id = :societeId`

**Verified in these modules:**

| Module | `requireSocieteId()` | Scoped Queries | Status |
|--------|---------------------|----------------|--------|
| Contact | ✅ | ✅ | Correct |
| Property | ✅ | ✅ | Correct |
| Project | ✅ | ✅ | Correct |
| Deposit | ✅ | ✅ | Correct |
| Reservation | ✅ | ✅ | Correct |
| Contract | ✅ | ✅ | Correct |
| Commission | ✅ (passed from controller) | ✅ | Correct |
| PaymentSchedule | ✅ | ✅ | Correct |
| Notification | ✅ | ✅ | Correct |
| Media | ✅ | ✅ | Correct |
| Audit | ✅ | ✅ | Correct |
| User Admin | ✅ | ✅ (via AppUserSociete join) | Correct |
| GDPR | ✅ | ✅ | Correct |
| Outbox | ✅ | ✅ | Correct |
| Portal | ✅ (via portal JWT `sid`) | ✅ | Correct |

### 3.3 Authorization Model

```
Platform Level:  SUPER_ADMIN (User.platformRole)
                     ↓ manages Societe + AppUserSociete
                     
Société Level:   ADMIN / MANAGER / AGENT (AppUserSociete.role)
                     ↓ controlled by @PreAuthorize + SecurityConfig
                     
Portal Level:    ROLE_PORTAL (portal JWT for clients)
                     ↓ read-only access to own contracts/payments/properties
```

**Access matrix (from SecurityConfig):**

| Endpoint | SUPER_ADMIN | ADMIN | MANAGER | AGENT | PORTAL |
|----------|-------------|-------|---------|-------|--------|
| `/api/societes/**` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `/api/**` (CRM) | ✅* | ✅ | ✅ | ✅ | ❌ |
| `/api/portal/**` | ❌ | ❌ | ❌ | ❌ | ✅ |
| Commission rules | ✅ | ✅ | ❌ | ❌ | ❌ |
| User management | ✅ | ✅ | ❌ | ❌ | ❌ |

*SUPER_ADMIN can access CRM endpoints when scoped to a société.

### 3.4 Identified Weaknesses

**W1 — Commission Controller passes `SocieteContext.getSocieteId()` directly without null-check:**
In `CommissionController`, `SocieteContext.getSocieteId()` is called without `requireSocieteId()`. If a SUPER_ADMIN token (no `sid`) accidentally reaches these endpoints, `societeId` would be `null`, potentially causing unscoped queries.

**W2 — Schedulers do not consistently set `SocieteContext`:**
- `DataRetentionScheduler` correctly calls `SocieteContext.setSystem()` → ✅
- `ReminderScheduler/ReminderService` bypasses SocieteContext by passing `societeId` directly to queries → Functionally correct but inconsistent
- `DepositWorkflowScheduler` queries all deposits globally without any société context → Functionally correct (uses entity's `societeId`) but relies on implicit trust
- `ReservationExpiryScheduler` same pattern → Functionally correct but inconsistent

**W3 — No database-level Row Level Security (RLS):**
Isolation is purely application-level. A raw SQL query or a new repository method without `societeId` filtering would leak data.

---

## 4. Risk & Consistency Analysis (STEP 2)

### 🔴 Critical Risks

**R1 — Commission endpoints: null societeId bypass risk**
- **Location:** `CommissionController.java` lines 56, 68, 79, 86, 93, 100
- **Issue:** `SocieteContext.getSocieteId()` is used directly (not via `requireSocieteId()`). A SUPER_ADMIN token without société scope could pass `null` to `CommissionService`, leading to unscoped queries on `CommissionRuleRepository` and `SaleContractRepository`.
- **Impact:** Potential data leakage across sociétés for commission data.
- **Mitigation:** Replace all `SocieteContext.getSocieteId()` calls in the controller with a `requireSocieteId()` helper (like in all other services).

**R2 — Dashboard controllers: same null societeId pattern**
- **Location:** `CommercialDashboardController.java`, `ReceivablesDashboardController.java`
- **Issue:** Same pattern as R1 — `SocieteContext.getSocieteId()` without null-check.
- **Impact:** Dashboard data could aggregate across sociétés for SUPER_ADMIN tokens.

### 🟠 Medium Risks

**R3 — No database-level isolation (RLS)**
- **Issue:** All isolation is enforced at the application layer. A missing `societeId` filter in a new repository method would silently expose cross-company data.
- **Recommendation:** Add PostgreSQL Row Level Security (RLS) policies as a defense-in-depth layer.

**R4 — Scheduler context inconsistency**
- **Issue:** Schedulers use three different patterns: (a) `SocieteContext.setSystem()`, (b) direct `societeId` passing, (c) global queries. While functionally correct, the inconsistency increases maintenance risk.
- **Recommendation:** Standardize all schedulers to use `SocieteContext.setSystem()` at entry + `SocieteContext.clear()` in finally block.

**R5 — User entity is globally unique by email**
- **Issue:** `app_user.email` has a `UNIQUE` constraint. A user belonging to Société A cannot be created with the same email in Société B. This is by design (via `AppUserSociete` many-to-many), but it means a user in one société can be added to another, inheriting access to both.
- **Recommendation:** This is intentional but should be documented clearly. Add validation in `SocieteService.addUserToSociete()` to verify the user's email domain if company-specific email policies are needed.

**R6 — Portal token not scoped by société in cleanup**
- **Issue:** `PortalTokenCleanupScheduler` deletes expired tokens globally. While the query is correct (expires-based), it lacks `societeId` scoping in case of future multi-tenant storage backends.
- **Severity:** Low-medium (currently safe due to PostgreSQL global table).

**R7 — CORS configuration defaults to localhost**
- **Issue:** `CORS_ALLOWED_ORIGINS` defaults to `http://localhost:4200,http://127.0.0.1:4200`. In production, this MUST be changed to the exact frontend domain or cross-origin attacks become possible.
- **Recommendation:** Fail startup if `CORS_ALLOWED_ORIGINS` contains `localhost` when profile is `production`.

### 🟢 Low Risks

**R8 — JWT secret via environment variable**
- The `JWT_SECRET` is required but has no minimum length enforcement at startup. A weak secret would compromise all tokens.
- **Recommendation:** Add a startup check for minimum 256-bit secret length.

**R9 — Swagger UI accessible in production**
- SecurityConfig permits `/v3/api-docs/**` and `/swagger-ui/**`. The `application-production.yml` may disable this, but it's not enforced in the base SecurityConfig.
- **Note:** A `SwaggerProductionIT` test exists verifying this — good practice already present.

**R10 — No audit log for société switching**
- When a user switches société via `/auth/switch-societe`, no security audit event is recorded. This is a gap in the audit trail.

**R11 — Deposit `runHourlyWorkflow` queries all pending deposits globally**
- While it correctly uses each deposit's `societeId` field for notifications, it loads all pending deposits into memory across all sociétés. For large deployments, this could be a performance concern.

---

## 5. Improvement Strategy (STEP 3)

### 5.1 Critical Fixes (Immediate)

#### Fix 1: Add `requireSocieteId()` guard to all controllers

**Affected files:**
- `CommissionController.java`
- `CommercialDashboardController.java`
- `ReceivablesDashboardController.java`

**Pattern (matching existing codebase style):**

```java
// Add to each controller class (or extract to a shared base/utility)
private UUID requireSocieteId() {
    UUID societeId = SocieteContext.getSocieteId();
    if (societeId == null) {
        throw new CrossTenantAccessException("Missing société context");
    }
    return societeId;
}

// Replace all: SocieteContext.getSocieteId()
// With:        requireSocieteId()
```

#### Fix 2: Add société-switch audit logging

In `AuthService.switchSociete()`, add after minting the token:

```java
securityAuditLogger.logSuccessfulLogin(
    user.getEmail(), userId, extractClientIp(), 
    role + " [SWITCH→" + societeId + "]"
);
```

### 5.2 Medium-Priority Improvements (Sprint 1-2)

#### Improvement 1: Extract `SocieteContextHelper` utility

Create a shared utility to standardize société context access across all modules:

```java
@Component
public class SocieteContextHelper {
    
    public UUID requireSocieteId() {
        UUID sid = SocieteContext.getSocieteId();
        if (sid == null) throw new CrossSocieteAccessException("Missing société context");
        return sid;
    }
    
    public UUID requireUserId() {
        UUID uid = SocieteContext.getUserId();
        if (uid == null) throw new CrossSocieteAccessException("Missing user context");
        return uid;
    }
    
    public void runAsSystem(Runnable task) {
        try {
            SocieteContext.setSystem();
            task.run();
        } finally {
            SocieteContext.clear();
        }
    }
}
```

#### Improvement 2: Standardize scheduler pattern

All schedulers should follow this template:

```java
@Scheduled(cron = "...")
public void scheduled() {
    try {
        SocieteContext.setSystem();
        doWork();
    } finally {
        SocieteContext.clear();
    }
}
```

#### Improvement 3: PostgreSQL Row-Level Security (defense-in-depth)

Add RLS policies to critical tables. This requires setting the `societe_id` as a session variable:

```sql
-- Enable RLS on each table
ALTER TABLE contact ENABLE ROW LEVEL SECURITY;
CREATE POLICY societe_isolation ON contact
    USING (societe_id = current_setting('app.current_societe_id')::uuid);

-- In application: SET app.current_societe_id before queries
```

**Note:** This is a significant change and should be implemented incrementally, starting with the `contact` and `property` tables.

#### Improvement 4: CORS production safety

```java
@PostConstruct
public void validateCorsConfig() {
    if (isProduction() && corsAllowedOrigins.contains("localhost")) {
        throw new IllegalStateException(
            "CORS_ALLOWED_ORIGINS must not contain 'localhost' in production!");
    }
}
```

### 5.3 Low-Priority Improvements (Backlog)

- **JWT secret length validation** at startup (minimum 32 bytes)
- **Batch scheduler queries** by société (instead of global) to improve memory efficiency
- **Add `@CreatedBy` / `@LastModifiedBy`** Spring Data auditing as an alternative to manual `createdBy`/`updatedBy` setting
- **Extract a shared `BaseEntity`** with `id`, `societeId`, `createdAt`, `updatedAt` to reduce entity boilerplate

---

## 6. Domain Completeness — CRM Extension Analysis (STEP 4)

### 6.1 Existing Business Capabilities

The system already implements a comprehensive real-estate CRM workflow:

| Capability | Status | Notes |
|-----------|--------|-------|
| **Contact Management** | ✅ Complete | Full lifecycle: PROSPECT → QUALIFIED → CLIENT |
| **GDPR Compliance** | ✅ Complete | Consent tracking, anonymization, data retention scheduler |
| **Property Inventory** | ✅ Complete | 9 types with type-specific validation, soft delete |
| **Project Management** | ✅ Complete | Group properties, KPIs, archive lifecycle |
| **Property Reservations** | ✅ Complete | Expiry scheduler, auto-release |
| **Deposit Management** | ✅ Complete | Workflow: PENDING → CONFIRMED/EXPIRED/CANCELED |
| **Sale Contracts** | ✅ Complete | PDF generation, status lifecycle, buyer snapshot |
| **Payment Schedules** | ✅ Complete | Call-for-funds workflow, reminders, PDF |
| **Commission Tracking** | ✅ Complete | Rate rules (project-specific + default), calculation |
| **Dashboards** | ✅ Complete | Commercial, receivables, cash, property KPIs |
| **Audit Trail** | ✅ Complete | Event-sourced commercial audit |
| **Notifications** | ✅ Complete | In-app + outbound email/SMS |
| **Client Portal** | ✅ Complete | Magic-link auth, view contracts/payments/properties |
| **Media Management** | ✅ Complete | Local + S3-compatible storage |
| **Contact Interests** | ✅ Complete | Link contacts to properties of interest |
| **Timeline** | ✅ Complete | Aggregated contact activity history |

### 6.2 Detected Gaps and Proposed Extensions

**Gap 1: No Activity / Task Management**
- **Current state:** No task or follow-up entity. Reminders are notification-based only.
- **Recommendation:** Add a lightweight `Task` entity tied to contacts/properties:

```
Task (societeId, assigneeUserId, contactId?, propertyId?, 
      title, description, dueDate, status [OPEN/DONE/CANCELED], createdAt)
```

This aligns with the existing pattern and fills the "what should I do next?" gap for agents.

**Gap 2: No Document Storage per Contact/Contract**
- **Current state:** Media is only for properties. Contracts generate PDFs on-the-fly but don't store uploaded documents (ID copies, contracts signed externally, etc.).
- **Recommendation:** Add a generic `Document` entity:

```
Document (societeId, entityType [CONTACT/CONTRACT/DEPOSIT], entityId, 
          fileName, fileKey, mimeType, uploadedBy, createdAt)
```

Reuse the existing `MediaStorageService` (local + S3).

**Gap 3: No Lead Source Attribution for Properties**
- **Current state:** `ProspectDetail.source` tracks where a prospect came from, but there's no campaign/source entity to aggregate across the funnel.
- **Recommendation:** Consider a `LeadSource` reference table (societeId, name, type [ONLINE/REFERRAL/WALK_IN/EVENT]) that `ProspectDetail.source` could reference as a FK instead of a free-text field.

**Gap 4: No Bulk Property Import Validation Report**
- **Current state:** `PropertyImportService` exists, but there's no mechanism to preview or download validation errors for large CSV imports.
- **Recommendation:** Return structured import results with row-level error detail (the `ImportResultResponse` DTO already exists — verify it includes per-row errors).

**Gap 5: No Recurring Payment Schedule Templates**
- **Current state:** Payment schedule items are created manually per contract.
- **Recommendation:** Add `PaymentTemplate` (societeId, name, items[]) that can be applied to new contracts, reducing manual entry for standard payment plans.

---

## 7. Target Architecture (STEP 5)

### 7.1 Current Architecture

```
┌─────────────────────────────────────────────────┐
│                   Angular 19                     │
│  (Standalone components, Signals, HttpClient)    │
└──────────────────────┬──────────────────────────┘
                       │ HTTP (JSON + JWT Bearer)
                       ▼
┌─────────────────────────────────────────────────┐
│                    Nginx                         │
│  (Reverse proxy, static assets, TLS termination) │
└──────────────────────┬──────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────┐
│              Spring Boot 3.x                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────┐ │
│  │ Security │ │  JWT     │ │  SocieteContext   │ │
│  │  Config  │→│  Filter  │→│  (ThreadLocal)    │ │
│  └──────────┘ └──────────┘ └────────┬─────────┘ │
│                                      │           │
│  ┌─────────────────────────────────────────────┐ │
│  │  17 Feature Modules (api→service→repo)      │ │
│  │  Contact | Property | Project | Deposit ... │ │
│  └────────────────────┬────────────────────────┘ │
│                       │ JPA (Hibernate)           │
└───────────────────────┼─────────────────────────┘
                       │
            ┌──────────┼──────────┐
            ▼          ▼          ▼
       PostgreSQL   Redis      MinIO
        (data)     (cache)   (media/S3)
```

### 7.2 Proposed Improved Architecture

The current architecture is well-designed. We propose **evolutionary improvements**, not a rewrite:

```
┌─────────────────────────────────────────────────────────┐
│                   Angular 19 (unchanged)                 │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│                    Nginx (unchanged)                     │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│              Spring Boot 3.x (enhanced)                  │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  CROSS-CUTTING LAYER (NEW)                        │   │
│  │  ┌────────────────┐ ┌──────────────────────────┐ │   │
│  │  │SocieteContext   │ │ SocieteContextHelper NEW │ │   │
│  │  │(ThreadLocal)    │ │ (requireSocieteId(),     │ │   │
│  │  │                 │ │  runAsSystem())           │ │   │
│  │  └────────────────┘ └──────────────────────────┘ │   │
│  │  ┌────────────────┐ ┌──────────────────────────┐ │   │
│  │  │JwtAuthFilter   │ │  @SocieteScoped AOP NEW  │ │   │
│  │  │(sets context)  │ │ (auto-verify societeId)  │ │   │
│  │  └────────────────┘ └──────────────────────────┘ │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  17+ FEATURE MODULES (existing + extensions)      │   │
│  │  + Task module (NEW)                              │   │
│  │  + Document module (NEW)                          │   │
│  └────────────────────┬─────────────────────────────┘   │
│                       │ JPA + RLS policies               │
└───────────────────────┼─────────────────────────────────┘
                       │
            ┌──────────┼──────────┐
            ▼          ▼          ▼
       PostgreSQL   Redis      MinIO
     + RLS policies  (unchanged) (unchanged)
```

**Key changes (incremental, non-breaking):**

1. **`SocieteContextHelper`** — Centralized utility replacing scattered `requireSocieteId()` methods
2. **Optional `@SocieteScoped` AOP annotation** — Automatically verifies société context before method execution (alternative to per-method checks)
3. **PostgreSQL RLS** — Defense-in-depth row isolation (phased rollout)
4. **Task module** — Lightweight follow-up/task management
5. **Document module** — Generic document attachment for contacts/contracts

---

## 8. Documentation V2 (STEP 6)

### 8.1 System As It Really Is

The YEM SaaS Platform is a **multi-company real-estate CRM** (internally codename "HLM") targeting the Moroccan property development market. It is NOT a multi-tenant platform in the traditional SaaS sense — it uses a **shared-schema, row-level isolation** approach where `societe_id` (company ID) scopes every data record.

**Terminology correction:** The codebase still contains legacy references to "tenant" in index names (e.g., `idx_contact_tenant_status`, `idx_property_tenant_type_status`) and in one exception class (`CrossTenantAccessException`). These should be renamed to `societe` for consistency, but are cosmetic and do not affect functionality.

### 8.2 Data Flow — Complete Request Lifecycle

```
1. Client sends HTTP request with "Authorization: Bearer <jwt>"
2. JwtAuthenticationFilter:
   a. Validates JWT signature + expiry
   b. Extracts userId (sub), societeId (sid), roles
   c. Checks token version against UserSecurityCacheService (revocation)
   d. Sets SocieteContext ThreadLocal: societeId, userId, role, superAdmin
   e. Creates Spring Security Authentication object
3. Spring Security:
   a. Evaluates authorizeHttpRequests rules
   b. Evaluates @PreAuthorize annotations on controllers
4. Controller:
   a. Reads SocieteContext.getSocieteId() (should use requireSocieteId())
   b. Delegates to Service
5. Service:
   a. Calls requireSocieteId() → gets UUID from ThreadLocal
   b. Passes societeId to all Repository queries
   c. Validates business rules
   d. Publishes domain events (audit, notifications)
6. Repository:
   a. All queries include WHERE societe_id = :societeId
   b. Pessimistic locking for concurrent writes (deposits, reservations)
7. Response flows back; JwtAuthenticationFilter.finally clears SocieteContext
```

### 8.3 Company Logic — Developer Guide

**To add a new entity that must be company-scoped:**

1. Add `@Column(name = "societe_id", nullable = false) private UUID societeId;` to the entity
2. Add indices with `societe_id` as the first column
3. In the repository, make ALL query methods include `societeId` as a parameter
4. In the service, call `requireSocieteId()` (or inject `SocieteContextHelper`) at every entry point
5. In the constructor, accept `societeId` and set it from `SocieteContext`
6. Add integration tests using the `CrossSocieteIsolationIT` pattern

**To add a new scheduler:**

1. Use `@ConditionalOnProperty(name = "spring.task.scheduling.enabled", matchIfMissing = true)`
2. In the scheduled method body, wrap with `SocieteContext.setSystem()` / `SocieteContext.clear()`
3. If iterating over sociétés, use the pattern from `ReminderService.runProspectFollowUp()`: query distinct societeIds, then process each

### 8.4 Outdated Concepts to Clean Up

| Item | Location | Action |
|------|----------|--------|
| `CrossTenantAccessException` | `contact/service/` | Rename to `CrossSocieteAccessException` |
| Index names with `tenant` | All entity `@Index` annotations | Rename (via Liquibase migration) |
| Method `findByTenantIdAndIdForUpdate` | `PropertyRepository` | Rename to `findBySocieteIdAndIdForUpdate` |
| Comment references to "tenant" | Various Javadoc | Update to "société" |

---

## 9. Deployment Guide (STEP 7)

### 9.1 Stack Detection

| Component | Detected | Build System |
|-----------|----------|-------------|
| Backend | Spring Boot 3.x (Java 21) | Maven (`mvnw`) |
| Frontend | Angular 19 | npm |
| Database | PostgreSQL 16 | Docker image |
| Cache | Redis 7 | Docker image |
| Object Storage | MinIO | Docker image |
| Reverse Proxy | Nginx | Docker image |

### 9.2 Free Hosting Options

| Component | Free Provider | Notes |
|-----------|--------------|-------|
| Backend + Frontend | **Railway.app** (free tier) OR **Render.com** | Both support Docker and free PostgreSQL |
| PostgreSQL | **Neon.tech** (free 0.5GB) or **Supabase** (500MB) | Managed PostgreSQL with free tier |
| Redis | **Upstash** (free 10K commands/day) | Redis-compatible, serverless |
| Object Storage | **Cloudflare R2** (free 10GB) | S3-compatible, no egress fees |
| DNS/TLS | **Cloudflare** (free) | Free SSL, DNS, CDN |

### 9.3 Environment Variables (Required)

```bash
# === REQUIRED ===
JWT_SECRET=<min-32-character-random-string>
POSTGRES_DB=hlm
POSTGRES_USER=hlm_user
POSTGRES_PASSWORD=<strong-password>

# === BACKEND ===
DB_URL=jdbc:postgresql://<host>:5432/hlm
DB_USER=hlm_user
DB_PASSWORD=<same-as-above>
SERVER_PORT=8080
CORS_ALLOWED_ORIGINS=https://your-domain.com
FORWARD_HEADERS_STRATEGY=FRAMEWORK

# === REDIS (optional but recommended) ===
REDIS_ENABLED=true
REDIS_HOST=<redis-host>
REDIS_PORT=6379
REDIS_PASSWORD=<redis-password>

# === OBJECT STORAGE (optional) ===
MEDIA_OBJECT_STORAGE_ENABLED=false
MEDIA_STORAGE_DIR=/tmp/hlm-uploads

# === EMAIL (optional) ===
EMAIL_HOST=smtp.gmail.com
EMAIL_PORT=587
EMAIL_USER=your@email.com
EMAIL_PASSWORD=app-password
EMAIL_FROM=noreply@your-domain.com

# === PORTAL ===
PORTAL_BASE_URL=https://your-domain.com
```

### 9.4 Build and Deploy Steps

#### Backend Build

```bash
cd hlm-backend
./mvnw clean package -DskipTests
# Output: target/hlm-backend-*.jar

# Docker build:
docker build -t hlm-backend:latest .
```

#### Frontend Build

```bash
cd hlm-frontend
npm install
npm run build -- --configuration=production
# Output: dist/hlm-frontend/browser/

# Docker build:
docker build -t hlm-frontend:latest .
```

#### Docker Compose (All-in-One)

```bash
# Copy .env.example to .env and fill in values
cp .env.example .env

# Start all services
docker compose up -d

# Production mode (with resource limits):
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### 9.5 Validation Checklist

```bash
# 1. Health check
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# 2. Login test
curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@hlm.local","password":"<seeded-password>"}'
# Expected: {"accessToken":"eyJ...","expiresIn":3600}

# 3. Multi-société isolation test
# Login as user A (société A) → create contact
# Login as user B (société B) → verify contact NOT visible

# 4. Frontend loads
curl -s http://localhost/ | grep "hlm-frontend"
# Expected: Angular app index.html
```

### 9.6 Troubleshooting

| Issue | Cause | Fix |
|-------|-------|-----|
| Backend fails to start | Missing `JWT_SECRET` | Set `JWT_SECRET` env var (min 32 chars) |
| 401 on all requests | JWT secret mismatch | Ensure backend and token use same secret |
| CORS errors in browser | Wrong `CORS_ALLOWED_ORIGINS` | Set to exact frontend URL (with protocol) |
| DB connection refused | PostgreSQL not ready | Wait for healthcheck; check `DB_URL` |
| Liquibase fails | Schema conflict | Ensure clean DB or run `liquibase repair` |
| Frontend shows blank page | API URL mismatch | Check `environment.production.ts` `apiUrl` |
| Redis connection fails | Redis not enabled | Set `REDIS_ENABLED=false` to use Caffeine |
| Media upload fails | Storage dir not writable | Ensure `MEDIA_STORAGE_DIR` exists and is writable |

---

## 10. Summary of Recommendations (Priority Order)

### Immediate (This Week)

| # | Action | Risk Addressed | Effort |
|---|--------|---------------|--------|
| 1 | Add `requireSocieteId()` to Commission + Dashboard controllers | 🔴 R1, R2 | 1 hour |
| 2 | Add audit logging for société switch | 🟢 R10 | 30 min |
| 3 | Set `CORS_ALLOWED_ORIGINS` for production | 🟠 R7 | 15 min |

### Sprint 1 (2 weeks)

| # | Action | Risk Addressed | Effort |
|---|--------|---------------|--------|
| 4 | Extract `SocieteContextHelper` utility | 🟠 R4 | 2 hours |
| 5 | Standardize scheduler SocieteContext pattern | 🟠 R4 | 3 hours |
| 6 | Add JWT secret length validation at startup | 🟢 R8 | 30 min |
| 7 | Rename legacy "tenant" references | Consistency | 2 hours |

### Sprint 2-3 (1 month)

| # | Action | Risk Addressed | Effort |
|---|--------|---------------|--------|
| 8 | Implement PostgreSQL RLS on critical tables | 🟠 R3 | 1 week |
| 9 | Add Task module (lightweight) | Gap 1 | 3 days |
| 10 | Add Document attachment module | Gap 2 | 3 days |
| 11 | Batch scheduler queries by société | 🟢 R11 | 1 day |

---

## === CONTINUE FROM HERE ===

**Remaining items for next iteration:**

- Detailed code patches (pull-request-ready diffs) for all Critical fixes
- Full PostgreSQL RLS migration scripts
- Task module entity + API + service scaffolding
- Document module entity + API + service scaffolding
- Frontend route audit (ensure société context propagation in Angular interceptor)
- Load testing recommendations
- CI/CD pipeline configuration (GitHub Actions)
- Monitoring and alerting setup (Prometheus + Grafana free tier)
