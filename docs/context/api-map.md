# API Map

This is the high-level route map for the live application. Use [../spec/api-reference.md](../spec/api-reference.md) for the detailed contract.

## 1. Surface Overview

| Surface | Path family | Auth model |
| --- | --- | --- |
| Public staff auth | `/auth/*` | public entry, cookie issuance |
| CRM API | `/api/*` | staff cookie or bearer auth |
| Superadmin API | `/api/admin/*` | `SUPER_ADMIN` only |
| Portal auth | `/api/portal/auth/*` | public entry, portal cookie issuance |
| Portal business API | `/api/portal/*` | `ROLE_PORTAL` only |
| Health and docs | `/actuator/*`, `/swagger-ui*`, `/v3/api-docs/*` | limited public or authenticated access depending on route |

## 2. Public Authentication Routes

| Method | Route | Purpose |
| --- | --- | --- |
| `POST` | `/auth/login` | staff login |
| `POST` | `/auth/switch-societe` | finalize multi-societe selection |
| `POST` | `/auth/logout` | clear staff session cookie |
| `GET` | `/auth/invitation/{token}` | validate invitation |
| `POST` | `/auth/invitation/{token}/activer` | activate account |
| `GET` | `/auth/me` | current user profile and session validation |
| `PUT` | `/auth/me/langue` | persist preferred interface language |

## 3. Portal Authentication Routes

| Method | Route | Purpose |
| --- | --- | --- |
| `POST` | `/api/portal/auth/request-link` | request a buyer magic link |
| `GET` | `/api/portal/auth/verify` | consume magic link and start session |
| `POST` | `/api/portal/auth/logout` | clear portal session |

## 4. Company And Platform Administration

| Route family | Responsibility |
| --- | --- |
| `/api/admin/societes*` | societe lifecycle, compliance, members, logos, impersonation |
| `/api/users*` | admin user operations and suggestions |
| `/api/mon-espace/utilisateurs*` | company-member management, invitations, GDPR actions |
| `/api/me*` | self-profile updates |
| `/api/end-impersonation` | stop acting as another user |

## 5. CRM Domain APIs

### Catalog and inventory

- `/api/projects*`
- `/api/immeubles*`
- `/api/projects/*/tranches*`
- `/api/properties*`
- `/api/properties/{id}/media`
- `/api/media/*`

### Customer and pipeline

- `/api/contacts*`
- `/api/reservations*`
- `/api/deposits*`
- `/api/ventes*`
- `/api/contracts*`
- `/api/contracts/{id}/schedule`
- `/api/schedule-items*`

### Operational productivity

- `/api/tasks*`
- `/api/notifications*`
- `/api/messages*`
- `/api/documents*`
- `/api/templates*`
- `/api/audit/commercial`

### Analytics

- `/api/dashboard/home`
- `/api/dashboard/commercial*`
- `/api/dashboard/receivables*`
- `/api/dashboard/commercial/cash`
- `/api/dashboard/kpi/*`
- `/api/kpis/tranche/*`
- `/dashboard/properties/*`

### Compliance and supporting services

- `/api/gdpr*`
- `/api/commissions*`
- `/api/commission-rules*`

## 6. Portal Business APIs

| Route family | Purpose |
| --- | --- |
| `/api/portal/tenant-info` | branding and session validation payload |
| `/api/portal/ventes*` | buyer-visible vente records and vente documents |
| `/api/portal/contracts*` | buyer-visible contracts and payment schedules |
| `/api/portal/properties*` | buyer-visible property detail |

## 7. API Conventions Worth Preserving

- staff sessions are generally cookie-based, not token-in-body based
- portal and CRM routes are intentionally disjoint
- role restrictions are enforced in both `SecurityConfig` and controller-level annotations
- entity ownership and societe ownership often return `404` rather than `403` to avoid leaking existence
- download endpoints are explicit and file-type aware, especially for PDFs
