# 01 — Architecture

## C4 Level 1 — System Context

```
┌─────────────────────────────────────────────────────────┐
│                     Internet                            │
│                                                         │
│  ┌──────────────┐    ┌──────────────────────────────┐  │
│  │ CRM User     │    │ Property Buyer (Portal)       │  │
│  │ (Admin /     │    │ Authenticated via magic link  │  │
│  │  Manager /   │    │ ROLE_PORTAL JWT               │  │
│  │  Agent)      │    └──────────────┬───────────────┘  │
│  └──────┬───────┘                   │                   │
│         │                           │                   │
│         ▼                           ▼                   │
│  ┌─────────────────────────────────────────────────┐   │
│  │         YEM SaaS Platform                       │   │
│  │  Angular SPA  ◄──► Spring Boot API              │   │
│  │  (hlm-frontend)    (hlm-backend)                │   │
│  └─────────────────────────┬───────────────────────┘   │
│                             │                           │
│                    ┌────────▼────────┐                  │
│                    │   PostgreSQL     │                  │
│                    │  (per-tenant    │                  │
│                    │   row-level)    │                  │
│                    └────────────────┘                  │
│                                                         │
│  External: SMTP (email), SMS provider                   │
└─────────────────────────────────────────────────────────┘
```

## C4 Level 2 — Container Diagram

```
hlm-frontend (Angular 19 SPA)
  - Serves static assets
  - Routes: /login (public), /portal/* (portal), /app/* (CRM users)
  - Two HTTP interceptors: auth interceptor (CRM JWT) + portal interceptor
  - Dev proxy: /auth /api /dashboard /actuator → :8080

hlm-backend (Spring Boot 3.5.8)
  - REST API on :8080
  - Auth endpoints: /auth/**
  - CRM API: /api/**
  - Portal API: /api/portal/**
  - Dashboard: /dashboard/**
  - Actuator: /actuator/**
  - Liquibase auto-runs migrations on startup

PostgreSQL
  - Single database, multi-tenant via tenant_id FK on every entity
  - Schema managed exclusively by Liquibase (ddl-auto: validate)

Email/SMS providers
  - EmailSender / SmsSender interfaces; Noop implementations in test/dev
  - Real implementations injected via @ConditionalOnMissingBean
```

## C4 Level 3 — Backend Package Map

```
com.yem.hlm.backend/
│
├── auth/               JWT provision, security config, login/logout
│   ├── api/            AuthController, AuthDTO
│   ├── config/         SecurityConfig, JwtBeansConfig, CorsConfig
│   ├── service/        JwtProvider, PortalJwtProvider, UserSecurityCacheService
│   └── security/       JwtAuthenticationFilter, CustomAuthEntry/AccessDenied
│
├── tenant/             Tenant entity, TenantContext (ThreadLocal)
│   ├── api/            TenantController (admin only)
│   ├── context/        TenantContext, TenantContextHolder
│   ├── domain/         Tenant entity
│   └── repo/           TenantRepository
│
├── user/               User entity, RBAC roles
│   ├── api/            UserController
│   ├── domain/         User entity, UserRole enum
│   └── repo/           UserRepository
│
├── contact/            Contacts + Prospects + ProspectDetail
│   ├── api/            ContactController
│   ├── domain/         Contact, ProspectDetail, ContactType, ContactStatus
│   └── repo/           ContactRepository, ProspectDetailRepository
│
├── property/           Properties (real estate units)
│   ├── api/            PropertyController
│   ├── domain/         Property, PropertyStatus enum
│   └── repo/           PropertyRepository
│
├── project/            Real estate projects (groups of properties)
│   ├── api/            ProjectController
│   ├── domain/         Project
│   └── repo/           ProjectRepository
│
├── deposit/            Reservation deposits
│   ├── api/            DepositController
│   ├── domain/         Deposit
│   └── repo/           DepositRepository
│
├── contract/           Sale contracts
│   ├── api/            ContractController
│   ├── domain/         SaleContract
│   └── repo/           SaleContractRepository
│
├── commission/         Commission rules + calculations (Phase 3)
│   ├── api/            CommissionRuleController
│   ├── domain/         CommissionRule
│   └── repo/           CommissionRuleRepository
│
├── dashboard/          KPI + commercial intelligence dashboards
│   ├── api/            CommercialDashboardController, ReceivablesDashboardController
│   ├── service/        CommercialDashboardService
│   └── dto/            CommercialDashboardSummaryDTO, ReceivablesBucketDTO
│
├── notification/       In-app CRM bell notifications
│   ├── api/            NotificationController
│   ├── domain/         Notification
│   └── repo/           NotificationRepository
│
├── outbox/             Transactional outbox for email/SMS (Phase 2)
│   ├── api/            MessageController (/api/messages)
│   ├── domain/         OutboundMessage
│   ├── repo/           OutboundMessageRepository
│   └── service/        OutboundDispatcherService, OutboxScheduler
│
├── portal/             Client-facing portal (Phase 4)
│   ├── api/            PortalAuthController, PortalContractController
│   ├── domain/         PortalToken
│   ├── repo/           PortalTokenRepository
│   └── service/        PortalContractService
│
├── payment/            Payment Schedule v1: tranches, PaymentCall (Appel de Fonds PDF), payment recording
│   ├── api/            PaymentScheduleController (/api/contracts/{id}/payment-schedule, deprecated)
│   │                   PaymentCallController (/api/payment-calls, /api/payment-calls/{id}/payments)
│   ├── domain/         PaymentSchedule, PaymentTranche, PaymentCall, Payment, TrancheStatus
│   └── service/        PaymentScheduleService, PaymentCallService, PaymentCallDocumentService
│                       PaymentOverdueScheduler (cron: marks overdue calls)
│
├── payments/           Payment Schedule v2: workflow items (issue/send/cancel), Call-for-Funds PDF
│   │                   + reminders + Cash Dashboard. Richer than payment/ — newer implementation.
│   ├── api/            PaymentScheduleController (/api/contracts/{id}/schedule, /api/schedule-items)
│   │                   CashDashboardController (/api/dashboard/commercial/cash)
│   ├── domain/         PaymentScheduleItem, SchedulePayment, ScheduleItemReminder, PaymentScheduleStatus
│   └── service/        PaymentScheduleService, CallForFundsWorkflowService, CallForFundsPdfService
│                       CashDashboardService, ReminderService, ReminderScheduler
│   NOTE: Both payment/ and payments/ coexist. payment/ is the v1 tranche model; payments/ is the
│         v2 item workflow model with reminders. Both serve active API routes.
│
├── media/              Property file upload/download (local filesystem; cloud-swap ready)
│   ├── api/            PropertyMediaController
│   ├── service/        MediaStorageService (interface) — LocalFileMediaStorage (default)
│   │                   Cloud swap: provide @Primary bean implementing MediaStorageService
│   └── config/         MEDIA_STORAGE_DIR (env var, default: ./uploads), max 10 MB per file
│
├── reminder/           Scheduled reminders (overdue, deposit warnings)
├── audit/              AuditLog entity + JPA listener
└── common/             ErrorResponse, ErrorCode, GlobalExceptionHandler, shared DTOs
```

## Authentication Request Flow

```
Client Request (Authorization: Bearer <JWT>)
  │
  ▼
JwtAuthenticationFilter
  ├── Decode + validate JWT (JwtDecoder bean)
  ├── Extract: sub (userId or contactId), tid (tenantId), roles
  ├── if ROLE_PORTAL → skip UserSecurityCacheService (contactId ≠ userId)
  ├── else → load UserDetails from UserSecurityCacheService
  ├── Set TenantContext.tenantId + TenantContext.userId (ThreadLocal)
  └── Set Spring Security Authentication
  │
  ▼
SecurityConfig (route authorization)
  ├── /auth/** → permitAll
  ├── /api/portal/auth/** → permitAll
  ├── /api/portal/** → hasRole("PORTAL")
  ├── /api/** → hasAnyRole("ADMIN","MANAGER","AGENT")
  └── /dashboard/** → hasAnyRole("ADMIN","MANAGER","AGENT")
  │
  ▼
Controller → Service → Repository (queries scoped by tenant_id)
  │
  ▼
Response
```

## Portal Authentication Flow (Phase 4)

```
1. POST /api/portal/auth/request-link  (email → tenant)
   └── Generate 32-byte SecureRandom token (URL-safe base64)
       Store SHA-256 hex in portal_token table (48h TTL)
       Send magic link email via EmailSender.send()

2. GET /api/portal/auth/verify?token=<raw-token>
   └── SHA-256(raw-token) → lookup in portal_token (not expired, not used)
       Mark token used
       Generate PortalJwt (sub=contactId, roles=["ROLE_PORTAL"], tid=tenantId, TTL=2h)
       Return JWT

3. Client stores JWT in localStorage (key: hlm_portal_token)
   portalInterceptor attaches JWT only to /api/portal/ requests
```

## Multi-Tenancy Enforcement

| Layer | Mechanism |
|-------|----------|
| JWT | `tid` claim (UUID) required on every request |
| Filter | `JwtAuthenticationFilter` populates `TenantContext` (ThreadLocal) |
| Service | Services read `TenantContext.getTenantId()` for all queries |
| Repository | Queries include `AND tenant_id = :tenantId` |
| Entity | All tenant-scoped entities have `@ManyToOne Tenant tenant` |
| Security | Never trust tenant ID from client request body |

## Caching

- Framework: Caffeine (in-process, per-node)
- Config: `CacheConfig` — each cache registered with its own TTL via `registerCustomCache()`
- Named caches: `commercialDashboard`, `receivablesDashboard`, + others
- Cache keys include resolved tenant + agent parameters
- Cache invalidation: TTL-based (no manual eviction currently)

## Error Handling

```json
{
  "timestamp": "2026-03-04T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Field 'email' must not be blank",
  "path": "/api/contacts",
  "fieldErrors": [
    {"field": "email", "message": "must not be blank"}
  ]
}
```

- Class: `common/error/ErrorResponse`
- Codes: `common/error/ErrorCode` enum
- Handler: `common/error/GlobalExceptionHandler` (@RestControllerAdvice)
- 401/403 handlers: `CustomAuthenticationEntryPoint`, `CustomAccessDeniedHandler`

## Liquibase Migration Strategy

- Master changelog: `db/changelog/db.changelog-master.yaml`
- Changeset naming: `NNN_description.yaml` (e.g., `025_portal_token.yaml`)
- **Rule: NEVER edit applied changesets. Always add new ones.**
- Current range: 001–027+ (exact max in `db.changelog-master.yaml`)
- `ddl-auto: validate` — Hibernate validates against actual schema at boot

## CI Architecture

```
Push/PR
  │
  ├── backend-ci.yml    → Unit tests → Package → Integration tests (Docker)
  ├── frontend-ci.yml   → npm test (ChromeHeadless) → npm build
  ├── snyk.yml          → OSS dep scan + Code SAST (if SNYK_TOKEN set)
  └── secret-scan.yml   → Pattern-based secret audit (audit-only)

  (Removed: `dependency-review.yml` and `codeql.yml` because GHAS is not enabled)
```
