# API Reference

This is a route catalog for the current backend. For exact OpenAPI schemas, use the running `/api-docs` or Swagger UI in environments where it is enabled.

## 1. Authentication

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/auth/login` | public | CRM login by email and password |
| `POST` | `/auth/switch-societe` | partial or full bearer token in header | select active societe for a multi-membership user |
| `GET` | `/auth/me` | CRM bearer token | validate current session and return current identity |
| `GET` | `/auth/invitation/{token}` | public | read invitation details |
| `POST` | `/auth/invitation/{token}/activer` | public | activate invited account |

### Login request

```json
{
  "email": "admin@demo.ma",
  "password": "Admin123!Secure"
}
```

### Login response modes

Single-societe or super-admin:

```json
{
  "accessToken": "jwt",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "requiresSocieteSelection": false,
  "societes": null
}
```

Multi-societe:

```json
{
  "accessToken": "partial-jwt",
  "tokenType": "Partial",
  "expiresIn": 300,
  "requiresSocieteSelection": true,
  "societes": [
    { "id": "uuid", "nom": "Demo Immobilier" }
  ]
}
```

## 2. Super-Admin Societe Management

Base path: `/api/admin/societes`

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/admin/societes` | `SUPER_ADMIN` | list societes with filters |
| `GET` | `/api/admin/societes/{id}` | `SUPER_ADMIN` | get full societe details |
| `GET` | `/api/admin/societes/{id}/stats` | `SUPER_ADMIN` | usage and quota stats |
| `GET` | `/api/admin/societes/{id}/compliance` | `SUPER_ADMIN` | compliance snapshot |
| `POST` | `/api/admin/societes` | `SUPER_ADMIN` | create societe |
| `PUT` | `/api/admin/societes/{id}` | `SUPER_ADMIN` | update societe |
| `POST` | `/api/admin/societes/{id}/desactiver` | `SUPER_ADMIN` | deactivate societe |
| `POST` | `/api/admin/societes/{id}/reactiver` | `SUPER_ADMIN` | reactivate societe |
| `GET` | `/api/admin/societes/{id}/membres` | `SUPER_ADMIN` | list members |
| `POST` | `/api/admin/societes/{id}/membres` | `SUPER_ADMIN` | attach an existing user to a societe |
| `POST` | `/api/admin/societes/{id}/invite` | `SUPER_ADMIN` | invite a member |
| `PUT` | `/api/admin/societes/{id}/membres/{userId}/role` | `SUPER_ADMIN` | change member role |
| `DELETE` | `/api/admin/societes/{id}/membres/{userId}` | `SUPER_ADMIN` | remove member |
| `POST` | `/api/admin/societes/{id}/impersonate/{userId}` | `SUPER_ADMIN` | mint impersonation token |

## 3. Company Member Management

Base path: `/api/mon-espace/utilisateurs`

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/mon-espace/utilisateurs` | `ADMIN`, `MANAGER`, `SUPER_ADMIN` | paginated member list |
| `GET` | `/api/mon-espace/utilisateurs/{userId}` | `ADMIN`, `MANAGER`, `SUPER_ADMIN` | member detail |
| `POST` | `/api/mon-espace/utilisateurs` | `ADMIN`, `SUPER_ADMIN` | invite member |
| `POST` | `/api/mon-espace/utilisateurs/{userId}/reinviter` | `ADMIN`, `SUPER_ADMIN` | resend invitation |
| `PATCH` | `/api/mon-espace/utilisateurs/{userId}` | `ADMIN`, `SUPER_ADMIN` | update member profile |
| `PATCH` | `/api/mon-espace/utilisateurs/{userId}/role` | `ADMIN`, `SUPER_ADMIN` | change member role |
| `DELETE` | `/api/mon-espace/utilisateurs/{userId}` | `ADMIN`, `SUPER_ADMIN` | remove member |
| `POST` | `/api/mon-espace/utilisateurs/{userId}/debloquer` | `ADMIN`, `SUPER_ADMIN` | manually unblock account |
| `GET` | `/api/mon-espace/utilisateurs/{userId}/export-donnees` | `ADMIN`, `MANAGER`, `SUPER_ADMIN` | user GDPR export |
| `DELETE` | `/api/mon-espace/utilisateurs/{userId}/anonymiser` | `ADMIN`, `SUPER_ADMIN` | user anonymization |

## 4. Projects

Base path: `/api/projects`

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/projects` | any CRM role | list projects |
| `POST` | `/api/projects` | `ADMIN`, `MANAGER` | create project |
| `GET` | `/api/projects/{id}` | any CRM role | get project |
| `PUT` | `/api/projects/{id}` | `ADMIN`, `MANAGER` | update project |
| `DELETE` | `/api/projects/{id}` | `ADMIN` | archive project |
| `GET` | `/api/projects/{id}/kpis` | `ADMIN`, `MANAGER` | project KPIs |

## 5. Properties and Media

### Properties

Base path: `/api/properties`

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/properties` | any CRM role | list properties |
| `POST` | `/api/properties` | `ADMIN`, `MANAGER` | create property |
| `GET` | `/api/properties/{id}` | any CRM role | get property |
| `PUT` | `/api/properties/{id}` | `ADMIN`, `MANAGER` | update property |
| `DELETE` | `/api/properties/{id}` | `ADMIN` | soft-delete property |
| `POST` | `/api/properties/import` | `ADMIN`, `MANAGER` | CSV import |

### Property media

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/properties/{id}/media` | `ADMIN`, `MANAGER` | upload media |
| `GET` | `/api/properties/{id}/media` | any CRM role | list property media |
| `GET` | `/api/media/{mediaId}/download` | any CRM role | download media |
| `DELETE` | `/api/media/{mediaId}` | `ADMIN` | delete media |

## 6. Contacts

Base path: `/api/contacts`

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/contacts` | any CRM role | paginated list with filters |
| `POST` | `/api/contacts` | `ADMIN`, `MANAGER` | create contact |
| `GET` | `/api/contacts/{id}` | any CRM role | get contact |
| `PATCH` | `/api/contacts/{id}` | `ADMIN`, `MANAGER` | update fields |
| `PATCH` | `/api/contacts/{id}/status` | `ADMIN`, `MANAGER` | change status |
| `POST` | `/api/contacts/{id}/convert-to-prospect` | `ADMIN`, `MANAGER` | qualify as prospect |
| `POST` | `/api/contacts/{id}/convert-to-client` | `ADMIN`, `MANAGER` | backward-compatible client conversion route now backed by deposit workflow |
| `POST` | `/api/contacts/{id}/interests` | `ADMIN`, `MANAGER` | add interest |
| `DELETE` | `/api/contacts/{id}/interests/{propertyId}` | `ADMIN` | remove interest |
| `GET` | `/api/contacts/{id}/interests` | any CRM role | list interests |
| `GET` | `/api/contacts/{id}/timeline` | any CRM role | contact timeline |

Related path:

- `GET /api/properties/{propertyId}/contacts`

## 7. Reservations and Deposits

### Reservations

Base path: `/api/reservations`

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/reservations` | any CRM role | list reservations |
| `POST` | `/api/reservations` | `ADMIN`, `MANAGER` | create reservation |
| `GET` | `/api/reservations/{id}` | any CRM role | get reservation |
| `POST` | `/api/reservations/{id}/cancel` | `ADMIN`, `MANAGER` | cancel reservation |
| `POST` | `/api/reservations/{id}/convert-to-deposit` | `ADMIN`, `MANAGER` | convert reservation to deposit |

### Deposits

Base path: `/api/deposits`

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/deposits` | `ADMIN`, `MANAGER` | create deposit |
| `GET` | `/api/deposits/{id}` | any CRM role | get deposit |
| `POST` | `/api/deposits/{id}/confirm` | `ADMIN`, `MANAGER` | confirm deposit |
| `POST` | `/api/deposits/{id}/cancel` | `ADMIN`, `MANAGER` | cancel deposit |
| `GET` | `/api/deposits/{id}/documents/reservation.pdf` | any CRM role | reservation PDF |
| `GET` | `/api/deposits/report` | `ADMIN`, `MANAGER` | filtered deposit report |

## 8. Contracts and Collections

### Contracts

Base path: `/api/contracts`

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/contracts` | any CRM role | list contracts |
| `POST` | `/api/contracts` | any CRM role | create draft contract |
| `GET` | `/api/contracts/{id}` | any CRM role | get contract |
| `POST` | `/api/contracts/{id}/sign` | `ADMIN`, `MANAGER` | sign contract |
| `POST` | `/api/contracts/{id}/cancel` | `ADMIN`, `MANAGER` | cancel contract |
| `GET` | `/api/contracts/{id}/documents/contract.pdf` | any CRM role | contract PDF |

### Payment schedule and payments

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/contracts/{contractId}/schedule` | any CRM role | list schedule items |
| `POST` | `/api/contracts/{contractId}/schedule` | `ADMIN`, `MANAGER` | create schedule item |
| `PUT` | `/api/schedule-items/{itemId}` | `ADMIN`, `MANAGER` | update schedule item |
| `DELETE` | `/api/schedule-items/{itemId}` | `ADMIN`, `MANAGER` | delete schedule item |
| `POST` | `/api/schedule-items/{itemId}/issue` | `ADMIN`, `MANAGER` | issue item |
| `POST` | `/api/schedule-items/{itemId}/send` | `ADMIN`, `MANAGER` | send call for funds |
| `POST` | `/api/schedule-items/{itemId}/cancel` | `ADMIN`, `MANAGER` | cancel item |
| `GET` | `/api/schedule-items/{itemId}/pdf` | any CRM role | download PDF |
| `GET` | `/api/schedule-items/{itemId}/payments` | any CRM role | list payments |
| `POST` | `/api/schedule-items/{itemId}/payments` | `ADMIN`, `MANAGER` | add payment |
| `POST` | `/api/schedule-items/reminders/run` | `ADMIN` | manual reminder run |

## 9. Dashboard and Reporting

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/dashboard/commercial` | any CRM role | commercial summary alias with date params |
| `GET` | `/api/dashboard/commercial/summary` | any CRM role | commercial summary |
| `GET` | `/api/dashboard/commercial/sales` | any CRM role | paginated sales drill-down |
| `GET` | `/api/dashboard/commercial/events` | any CRM role | SSE refresh stream |
| `GET` | `/api/dashboard/commercial/cash` | any CRM role | cash dashboard |
| `GET` | `/api/dashboard/receivables` | any CRM role | receivables dashboard |
| `GET` | `/dashboard/properties/summary` | `ADMIN`, `MANAGER` | property dashboard summary |
| `GET` | `/dashboard/properties/sales-kpi` | `ADMIN`, `MANAGER` | property sales KPI |

## 10. Commissions, Audit, Notifications, Messages

### Commissions

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/commissions/my` | any CRM role | own commissions |
| `GET` | `/api/commissions` | `ADMIN`, `MANAGER` | filtered commission query |
| `GET` | `/api/commission-rules` | `ADMIN` | list rules |
| `POST` | `/api/commission-rules` | `ADMIN` | create rule |
| `PUT` | `/api/commission-rules/{id}` | `ADMIN` | update rule |
| `DELETE` | `/api/commission-rules/{id}` | `ADMIN` | delete rule |

### Audit

- `GET /api/audit/commercial` -> `ADMIN`, `MANAGER`

### Notifications

- `GET /api/notifications`
- `POST /api/notifications/{id}/read`

### Messages

- `POST /api/messages`
- `GET /api/messages`

## 11. Tasks and Documents

### Tasks

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/tasks` | any CRM role | paginated list |
| `POST` | `/api/tasks` | any CRM role | create task |
| `GET` | `/api/tasks/{id}` | any CRM role | get task |
| `PUT` | `/api/tasks/{id}` | any CRM role | update task |
| `DELETE` | `/api/tasks/{id}` | `ADMIN` | delete task |
| `GET` | `/api/tasks/by-contact/{contactId}` | any CRM role | tasks for contact |
| `GET` | `/api/tasks/by-property/{propertyId}` | any CRM role | tasks for property |

**Default list behavior:** `GET /api/tasks` without query params returns tasks scoped to the current user's `assigneeId`. Pass `?status=` to return all tasks of a given status regardless of assignee. If no `assigneeId` is provided on create, it defaults to the creator.

### Documents

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/documents` | any CRM role | upload document via multipart/form-data |
| `GET` | `/api/documents` | any CRM role | list documents by entity |
| `GET` | `/api/documents/{id}/download` | any CRM role | download document |
| `DELETE` | `/api/documents/{id}` | `ADMIN`, `MANAGER` | delete document |

## 12. GDPR

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/gdpr/contacts/{contactId}/export` | `ADMIN`, `MANAGER` | contact export |
| `DELETE` | `/api/gdpr/contacts/{contactId}/anonymize` | `ADMIN` | contact anonymization |
| `GET` | `/api/gdpr/contacts/{contactId}/rectify` | `ADMIN`, `MANAGER` | rectification data |
| `GET` | `/api/gdpr/privacy-notice` | any CRM role | privacy notice |

## 13. Portal

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/portal/auth/request-link` | public | request magic link |
| `GET` | `/api/portal/auth/verify` | public | verify magic link |
| `GET` | `/api/portal/contracts` | `ROLE_PORTAL` | buyer contracts |
| `GET` | `/api/portal/contracts/{id}/documents/contract.pdf` | `ROLE_PORTAL` | contract PDF |
| `GET` | `/api/portal/contracts/{contractId}/payment-schedule` | `ROLE_PORTAL` | payment schedule |
| `GET` | `/api/portal/properties/{id}` | `ROLE_PORTAL` | property detail |
| `GET` | `/api/portal/tenant-info` | `ROLE_PORTAL` | branding info |

## 14. Actuator and Docs

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/actuator/health` | public | health status |
| `GET` | `/actuator/info` | public | service metadata |
| `GET` | `/api-docs` | environment-dependent | OpenAPI JSON |
| `GET` | `/swagger-ui.html` | environment-dependent | Swagger UI |

## 15. Self-Service Profile (Wave 4)

Base path: `/api/me`

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/me` | any CRM role | get own profile |
| `PATCH` | `/api/me` | any CRM role | update own profile |

Editable fields: `prenom`, `nomFamille`, `telephone`, `poste`, `langueInterface`
Read-only: `email`, `role`, `photoUrl`

## 16. Admin User Management

Base path: `/api/users` (NOT `/api/admin/users` — that prefix is SUPER_ADMIN-only in `SecurityConfig`)

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/users` | `ADMIN`, `SUPER_ADMIN` | list CRM users |
| `POST` | `/api/users` | `ADMIN`, `SUPER_ADMIN` | create CRM user |
| `GET` | `/api/users/{id}` | `ADMIN`, `SUPER_ADMIN` | get user |
| `PATCH` | `/api/users/{id}` | `ADMIN`, `SUPER_ADMIN` | update user |
| `DELETE` | `/api/users/{id}` | `ADMIN`, `SUPER_ADMIN` | remove user |

## 17. Known API-Level Oddities

- `POST /tenants` is still permitted in security configuration, but no active controller was found.
- `AdminUserController` is on `/api/users` (moved from `/api/admin/users`; the `/api/admin/**` prefix is reserved for SUPER_ADMIN in `SecurityConfig`). The HR membership surface is `/api/mon-espace/utilisateurs`.
