# API Map — HLM CRM

Complete endpoint inventory for all modules. Updated 2026-04-05.

## Security tiers

| Tier | Path prefix | Guard |
|---|---|---|
| Public | `/auth/**`, `/api/portal/auth/**` | No JWT |
| Portal | `/api/portal/**` | ROLE_PORTAL (magic-link JWT, httpOnly cookie) |
| CRM | `/api/**` | ROLE_ADMIN / ROLE_MANAGER / ROLE_AGENT |
| Platform | `/api/admin/**` | ROLE_SUPER_ADMIN only |

---

## Authentication (`/auth`)

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/auth/login` | public | Password login → JWT httpOnly cookie |
| POST | `/auth/logout` | public | Clear JWT cookie |
| GET | `/auth/invitation/{token}` | public | Validate invitation token → InvitationDetails |
| POST | `/auth/invitation/{token}/activer` | public | Activate account (set password + CGU) |
| GET | `/auth/refresh` | public | Refresh JWT from cookie |

---

## Portal Auth (`/api/portal/auth`)

| Method | Path | Access | Description |
|---|---|---|---|
| POST | `/api/portal/auth/request-link` | public | Request magic-link email for buyer |
| GET | `/api/portal/auth/verify?token=` | public | Verify magic-link → portal JWT cookie |
| POST | `/api/portal/auth/logout` | public | Clear portal JWT cookie |

---

## Portal Buyer (`/api/portal`) — ROLE_PORTAL

| Method | Path | Description |
|---|---|---|
| GET | `/api/portal/tenant-info` | Société name + buyer name (session validation) |
| GET | `/api/portal/ventes` | Buyer's ventes (with echeances + documents) |
| GET | `/api/portal/ventes/{id}` | Single vente detail |
| GET | `/api/portal/contracts` | Buyer's contracts |
| GET | `/api/portal/contracts/{id}/payments` | Payment schedule for a contract |
| GET | `/api/portal/properties/{id}` | Property detail (buyer view) |

---

## Users — CRM Admin (`/api/users`)

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/users` | ADMIN | List all users in société |
| POST | `/api/users` | ADMIN | Create user (invite by email) |
| GET | `/api/users/{id}` | ADMIN | Get user detail |
| PUT | `/api/users/{id}` | ADMIN | Update user |
| DELETE | `/api/users/{id}` | ADMIN | Deactivate user |
| GET | `/api/users/suggest?q=` | ADMIN/MANAGER/AGENT | Typeahead search |

## User Management (`/api/mon-espace/utilisateurs`)

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/mon-espace/utilisateurs` | ADMIN/MANAGER | List membres |
| POST | `/api/mon-espace/utilisateurs` | ADMIN/MANAGER | Invite utilisateur |
| POST | `/api/mon-espace/utilisateurs/{id}/reinviter` | ADMIN/MANAGER | Re-send invitation |
| PUT | `/api/mon-espace/utilisateurs/{id}` | ADMIN | Update membre |
| DELETE | `/api/mon-espace/utilisateurs/{id}` | ADMIN | Désactiver |

## Self Profile (`/api/me`)

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/me` | ADMIN/MANAGER/AGENT | Get own profile |
| PATCH | `/api/me` | ADMIN/MANAGER/AGENT | Update prenom/nom/telephone/poste |

---

## Ventes (`/api/ventes`)

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/ventes` | A/M/AG | List ventes (filter: `?contactId=`) |
| POST | `/api/ventes` | ADMIN/MANAGER | Create vente |
| GET | `/api/ventes/{id}` | A/M/AG | Get vente detail |
| PATCH | `/api/ventes/{id}/statut` | ADMIN/MANAGER | Advance state machine |
| POST | `/api/ventes/{id}/portal/invite` | ADMIN/MANAGER | Send magic-link to buyer |
| POST | `/api/ventes/{id}/echeances` | ADMIN/MANAGER | Add payment milestone |
| PATCH | `/api/ventes/{id}/echeances/{eid}/statut` | ADMIN/MANAGER | Mark echeance paid |
| POST | `/api/ventes/{id}/documents` | A/M/AG | Upload document (multipart) |
| GET | `/api/ventes/{id}/documents` | A/M/AG | List documents |

**Vente state machine:**
```
COMPROMIS → FINANCEMENT → ACTE_NOTARIE → LIVRE (terminal)
  │ (any)  → ANNULE (terminal)
```
Invalid transitions → HTTP 409 `INVALID_STATUS_TRANSITION`.

---

## Contacts (`/api/contacts`)

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/contacts` | A/M/AG | List contacts |
| POST | `/api/contacts` | A/M/AG | Create contact |
| GET | `/api/contacts/{id}` | A/M/AG | Get contact |
| PUT | `/api/contacts/{id}` | A/M/AG | Update contact |
| DELETE | `/api/contacts/{id}` | ADMIN | Delete contact |
| POST | `/api/contacts/{id}/qualify` | A/M/AG | Convert to prospect |
| PATCH | `/api/contacts/{id}/status` | A/M/AG | Update contact status |
| GET | `/api/contacts/{id}/interests` | A/M/AG | List property interests |
| POST | `/api/contacts/{id}/interests` | A/M/AG | Add interest |
| DELETE | `/api/contacts/{id}/interests/{pid}` | A/M/AG | Remove interest |
| GET | `/api/contacts/{id}/timeline` | A/M/AG | Audit timeline |

---

## Properties (`/api/properties`)

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/properties` | A/M/AG | List (filters: `?projectId&immeubleId&type&status`) |
| POST | `/api/properties` | ADMIN/MANAGER | Create |
| GET | `/api/properties/{id}` | A/M/AG | Detail |
| PUT | `/api/properties/{id}` | ADMIN/MANAGER | Update |
| DELETE | `/api/properties/{id}` | ADMIN | Delete |
| POST | `/api/properties/{id}/media` | A/M/AG | Upload media |
| DELETE | `/api/properties/{id}/media/{key}` | ADMIN/MANAGER | Delete media |

---

## Projects (`/api/projects`)

| Method | Path | Roles |
|---|---|---|
| GET | `/api/projects` | A/M/AG |
| POST | `/api/projects` | ADMIN/MANAGER |
| GET | `/api/projects/{id}` | A/M/AG |
| PUT | `/api/projects/{id}` | ADMIN/MANAGER |
| DELETE | `/api/projects/{id}` | ADMIN |
| POST | `/api/projects/{id}/logo` | ADMIN/MANAGER |

## Immeubles (`/api/immeubles`)

| Method | Path | Roles | Notes |
|---|---|---|---|
| GET | `/api/immeubles` | A/M/AG | Filter: `?projectId=` |
| POST | `/api/immeubles` | ADMIN/MANAGER | |
| GET | `/api/immeubles/{id}` | A/M/AG | |
| PUT | `/api/immeubles/{id}` | ADMIN/MANAGER | |
| DELETE | `/api/immeubles/{id}` | ADMIN | |

---

## Reservations (`/api/reservations`)

| Method | Path | Roles |
|---|---|---|
| GET | `/api/reservations` | A/M/AG |
| POST | `/api/reservations` | A/M/AG |
| GET | `/api/reservations/{id}` | A/M/AG |
| PATCH | `/api/reservations/{id}/cancel` | ADMIN/MANAGER |
| POST | `/api/reservations/{id}/convert-deposit` | ADMIN/MANAGER |

## Deposits (`/api/deposits`)

| Method | Path | Roles |
|---|---|---|
| GET | `/api/deposits` | A/M/AG |
| POST | `/api/deposits` | ADMIN/MANAGER |
| GET | `/api/deposits/{id}` | A/M/AG |
| POST | `/api/deposits/{id}/confirm` | ADMIN/MANAGER |
| POST | `/api/deposits/{id}/cancel` | ADMIN/MANAGER |
| GET | `/api/deposits/{id}/pdf` | A/M/AG |

---

## Contracts & Payments

| Method | Path | Roles |
|---|---|---|
| GET/POST | `/api/contracts` | A/M/AG |
| GET/PUT | `/api/contracts/{id}` | A/M/AG |
| POST | `/api/contracts/{id}/schedule` | ADMIN/MANAGER |
| GET | `/api/contracts/{id}/schedule` | A/M/AG |

---

## Dashboard

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/dashboard/commercial/summary` | A/M/AG | KPIs (salesCount, depositsCount, etc.) |
| GET | `/api/dashboard/commercial/sales` | A/M/AG | Paginated sales table |
| GET | `/api/dashboard/receivables` | A/M/AG | Créances aging buckets |
| GET | `/api/dashboard/cash` | A/M/AG | Cash flow summary |

---

## Documents (`/api/documents`)

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/api/documents?entityType=&entityId=` | A/M/AG | List by entity |
| POST | `/api/documents` | A/M/AG | Upload (multipart) |
| DELETE | `/api/documents/{id}` | ADMIN/MANAGER | Delete |
| GET | `/api/documents/{id}/download` | A/M/AG | Download file |

**EntityType values:** `CONTACT`, `CONTRACT`, `DEPOSIT`, `PROPERTY`, `RESERVATION`, `PROJECT`, `VENTE`

---

## Tasks (`/api/tasks`)

| Method | Path | Roles |
|---|---|---|
| GET | `/api/tasks` | A/M/AG (own tasks by default) |
| POST | `/api/tasks` | A/M/AG |
| GET/PUT/DELETE | `/api/tasks/{id}` | A/M/AG |

---

## Super Admin (`/api/admin`) — ROLE_SUPER_ADMIN

| Method | Path | Description |
|---|---|---|
| GET/POST | `/api/admin/societes` | List / create société |
| GET/PUT/DELETE | `/api/admin/societes/{id}` | Manage société |
| POST | `/api/admin/societes/{id}/logo` | Upload logo |
| GET | `/api/admin/bootstrap` | Bootstrap super-admin (one-time) |

---

## Misc

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/notifications` | A/M/AG | In-app notifications |
| POST | `/api/messages` | ADMIN | Outbox: send email/SMS |
| GET | `/api/commissions` | A/M/AG | Commission rules |
| POST/PUT | `/api/commissions` | ADMIN | Manage rules |
| GET | `/api/audit` | A/M/AG | Audit log |
| GET | `/actuator/health` | public | Health check |
| GET | `/actuator/prometheus` | internal | Metrics |
