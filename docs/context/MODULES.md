# Module Reference

This document maps the main responsibilities of the current codebase.

## Backend Modules

| Module | Primary Responsibility | Main Entry Points | Notes |
| --- | --- | --- | --- |
| `auth` | Login, JWT, revocation, lockout, Spring Security | `/auth/*`, `/auth/me` | Supports partial tokens and societe switching |
| `societe` | Company admin model and super-admin management | `/api/admin/societes/*` | Owns `SocieteContext`, membership model, impersonation |
| `admin` | First `SUPER_ADMIN` bootstrap | startup only | Activated by `APP_BOOTSTRAP_ENABLED=true` |
| `usermanagement` | Active company member management | `/api/mon-espace/utilisateurs/*` | Invitations, role changes, removals, GDPR export/anonymize |
| `user` | Legacy admin-user API surface | `/api/users/*` | Path was moved from `/api/admin/users` to avoid the `SUPER_ADMIN`-only security block; frontend uses this path |
| `project` | Project catalog and KPIs | `/api/projects/*` | Archive instead of hard delete |
| `property` | Inventory CRUD and CSV import | `/api/properties/*`, `/dashboard/properties/*` | Type-specific validation, soft delete |
| `contact` | Contact lifecycle, interests, timeline | `/api/contacts/*` | Prospect and client states plus GDPR fields |
| `reservation` | Lightweight property holds | `/api/reservations/*` | Can convert to deposit |
| `deposit` | Financial reservation workflow | `/api/deposits/*` | Locks property rows, drives contact/property transitions |
| `contract` | Sales contract lifecycle and PDFs | `/api/contracts/*` | Signed contracts capture buyer snapshots |
| `payments` | Schedule items, payments, reminders, cash dashboard | `/api/contracts/{id}/schedule`, `/api/schedule-items/*`, `/api/dashboard/commercial/cash` | Partial payment support |
| `commission` | Commission reporting and rule management | `/api/commissions*`, `/api/commission-rules*` | Project rule overrides societe default |
| `dashboard` | Commercial and receivables dashboards | `/api/dashboard/*` | Includes SSE refresh stream |
| `notification` | In-app notifications | `/api/notifications/*` | Deduplicates some notification types via persistence rules |
| `outbox` | Message composition and async delivery | `/api/messages/*` | Dispatcher sends email and SMS outside the main transaction |
| `media` | Property media storage | `/api/properties/{id}/media`, `/api/media/*` | Local filesystem by default, object storage optional |
| `document` | Generic attachment upload/list/download | `/api/documents/*` | Works across contact, property, deposit, reservation, contract |
| `task` | Follow-up task management | `/api/tasks/*` | Default list is current user’s tasks |
| `portal` | Buyer portal auth and data views | `/api/portal/*` | Separate token type and role |
| `gdpr` | Contact privacy export, anonymization, privacy notice | `/api/gdpr/*` | User GDPR logic lives partly under `usermanagement` |
| `audit` | Commercial workflow audit | `/api/audit/commercial` | Append-only operational history |
| `common` | Shared DTOs, errors, validation, rate limiting | internal | Used across all modules |

## Cross-Module Workflow Ownership

### Authentication and session

- `AuthService` owns CRM login and societe selection.
- `JwtAuthenticationFilter` owns token parsing and `SocieteContext` population.
- `UserSecurityCacheService` turns JWT revocation into a cache-backed check.

### CRM lead-to-sale path

- `ContactService` creates and qualifies contacts.
- `ReservationService` creates non-financial holds.
- `DepositService` creates and confirms deposits.
- `SaleContractService` creates, signs, and cancels contracts.
- `PaymentScheduleService` and `CallForFundsWorkflowService` handle post-signature collections.

### Client portal

- `PortalAuthService` owns magic-link issuance and verification.
- `PortalContractService` authorizes all buyer-facing portal reads.

### Company administration

- `SocieteController` and `SocieteService` are platform-level tools for `SUPER_ADMIN`.
- `UserManagementController` and `UserManagementService` are company-level membership operations.

## Frontend Modules

| Frontend Area | Route Prefix | Responsibility |
| --- | --- | --- |
| Login and activation | `/login`, `/activation` | CRM login and invitation activation |
| CRM shell | `/app/*` | Main staff experience |
| Super-admin shell | `/superadmin/*` | Company administration |
| Portal shell | `/portal/*` | Buyer-facing experience |

### CRM features exposed in the current route map

- dashboard
- projects
- properties
- prospects
- contacts
- reservations
- contracts
- payment schedule
- receivables
- commissions
- messages
- notifications
- audit
- tasks
- admin users

## Module-Level Notes Worth Preserving

### `user` versus `usermanagement`

Two user-management surfaces exist in the backend:

- [UserManagementController](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/usermanagement/UserManagementController.java) is the active, richer company-member flow used by the frontend.
- [AdminUserController](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend/src/main/java/com/yem/hlm/backend/user/api/AdminUserController.java) is on `/api/users` (moved from `/api/admin/users` — the `/api/admin/**` prefix is reserved for `SUPER_ADMIN` only).

### `societe` versus `tenant`

The runtime code uses `societe`, but some migration history, exception names, and older docs still mention `tenant`. Maintain new code against the current `societe` naming unless you are touching migration-compatibility paths.
