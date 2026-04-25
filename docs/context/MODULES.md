# Module Reference

This document maps the main responsibilities of the current backend and frontend modules.

## 1. Backend Module Inventory

| Module | Responsibility | Representative APIs |
| --- | --- | --- |
| `admin` | first `SUPER_ADMIN` bootstrap | startup-only bootstrap logic |
| `auth` | login, invitation activation, JWT issuance, cookies, revocation, lockout | `/auth/*`, `/auth/me` |
| `audit` | append-only commercial audit timeline | `/api/audit/commercial` |
| `commission` | commission reporting and rule management | `/api/commissions*`, `/api/commission-rules*` |
| `common` | shared DTOs, errors, validation, rate limiting, OpenAPI support | internal |
| `contact` | contact lifecycle, qualification, interests, timeline | `/api/contacts*` |
| `contract` | contract lifecycle and PDF generation | `/api/contracts*` |
| `dashboard` | home, commercial, cockpit, KPI, and receivables dashboards | `/api/dashboard*`, `/api/kpis*` |
| `deposit` | deposit workflow and reservation PDF generation | `/api/deposits*` |
| `document` | generic business attachments | `/api/documents*` |
| `gdpr` | contact privacy exports, anonymization, and retention actions | `/api/gdpr*` |
| `immeuble` | building management inside projects | `/api/immeubles*` |
| `media` | property media upload, download, and storage abstraction | `/api/properties/{id}/media`, `/api/media/*` |
| `notification` | in-app notifications | `/api/notifications*` |
| `outbox` | outbound message creation and dispatch | `/api/messages*` |
| `payments` | payment schedules, payments, reminders, cash dashboard | `/api/contracts/{id}/schedule`, `/api/schedule-items*` |
| `portal` | portal authentication and buyer-facing reads | `/api/portal*` |
| `project` | real-estate program management and project KPIs | `/api/projects*` |
| `property` | inventory CRUD, status changes, and import | `/api/properties*`, `/dashboard/properties*` |
| `reminder` | reminder orchestration for due items | scheduler/service support |
| `reservation` | lightweight property holds | `/api/reservations*` |
| `societe` | company administration, impersonation, context propagation, RLS | `/api/admin/societes*`, `/api/end-impersonation` |
| `task` | follow-up and task management | `/api/tasks*` |
| `tranche` | tranche generation and tranche status management | `/api/projects/*/tranches*` |
| `user` | legacy user/admin surface and self profile | `/api/users*`, `/api/me` |
| `usermanagement` | richer member invitation and lifecycle management | `/api/mon-espace/utilisateurs*` |
| `vente` | commercial sale pipeline | `/api/ventes*` |
| `viewer3d` | 3D building model metadata, mesh-to-lot mapping, and lot status snapshot | `/api/projects/*/3d-model`, `/api/projects/*/3d-properties-status`, `/api/portal/projects/*/3d-model`, `/api/portal/projects/*/3d-properties-status` |

## 2. Cross-Cutting Backend Ownership

### Authentication and session management

- `AuthService` authenticates staff users and handles multi-societe selection
- `JwtAuthenticationFilter` turns cookies or bearer headers into the active security context
- `UserSecurityCacheService` accelerates token revocation checks
- `PortalAuthService` owns the portal magic-link workflow

### Multi-societe isolation

- `SocieteContext` carries current request scope
- `SocieteContextHelper` centralizes access and system-mode helpers
- `RlsContextAspect` writes the current societe into PostgreSQL transaction-local state
- `SocieteContextTaskDecorator` propagates scope to async work

### Delivery and automation

- `OutboundDispatcherService` sends queued email and SMS records
- `DocumentGenerationService` and contract PDF services produce formal documents
- schedulers under `deposit`, `gdpr`, `outbox`, and `portal` keep the system moving without user action

## 3. Backend Module Relationships

### Commercial lifecycle chain

```text
contact
  -> reservation
  -> deposit
  -> vente
  -> contract
  -> payment schedule
  -> notifications / outbox / portal
```

### Administrative lifecycle chain

```text
societe
  -> app_user_societe
  -> usermanagement / user
  -> permissions
  -> branding / quotas / compliance
```

## 4. Frontend Feature Areas

| Area | Route prefix | Responsibility |
| --- | --- | --- |
| Login and activation | `/login`, `/activation` | staff sign-in, invitation activation, societe selection |
| CRM shell | `/app/*` | daily staff work and operational reporting |
| Superadmin shell | `/superadmin/*` | platform administration |
| Portal shell | `/portal/*` | buyer-facing self-service |

### CRM feature modules

| Feature | Primary route |
| --- | --- |
| dashboard | `/app/dashboard*` |
| projects | `/app/projects*` |
| properties | `/app/properties*` |
| contacts | `/app/contacts*` |
| immeubles | `/app/immeubles` |
| reservations | `/app/reservations*` |
| ventes | `/app/ventes*` |
| contracts and schedule | `/app/contracts*` |
| tasks | `/app/tasks` |
| notifications | `/app/notifications` |
| messages | `/app/messages` |
| commissions | `/app/commissions` |
| audit | `/app/audit` |
| admin users | `/app/admin/users` |
| templates | `/app/templates*` |
| 3D viewer | `/app/projets/:projetId/viewer-3d`, `/app/dashboard/commercial/3d` |

### Portal feature modules

- portal login and verification
- vente list and detail
- contracts list
- payment schedule detail
- property detail
- 3D building viewer (read-only, access-guarded by buyer vente ownership)

## 5. Module Notes That Matter To Maintainers

### `user` and `usermanagement` both exist for a reason

- `user` provides older or narrower admin-oriented endpoints under `/api/users`
- `usermanagement` provides the richer company-member lifecycle used for invitations, role changes, and GDPR-related member actions
- both need to remain consistent when security or membership rules evolve

### `vente` and `contract` serve different business views

- `vente` is the pipeline record used to move a deal through compromis, financing, notary, and delivery
- `contract` is the formal contract and schedule subsystem
- user guides and APIs must explain that they complement each other rather than duplicate each other

### `property`, `immeuble`, and `tranche` form the inventory hierarchy

- not every property uses every level
- professional inventory workflows increasingly rely on these relationships
- docs and onboarding should treat this as a first-class model, not as optional noise

### `viewer3d` is a read-only projection of existing inventory data

- the module does not own commercial state — it reads from `property` status via a cached snapshot
- the mesh-to-lot mapping table is the only 3D-specific data; all business logic stays in existing modules
- portal access for the viewer reuses the same vente-ownership check pattern used elsewhere in the portal module
