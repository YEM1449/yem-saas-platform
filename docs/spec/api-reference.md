# API Reference

This document summarizes the current API contract at the level needed by engineers, testers, and integrators.

## 1. Contract Conventions

- JSON is the default payload format unless the route explicitly handles multipart or binary responses
- final browser sessions are typically cookie-based
- some flows return empty token bodies because the actual JWT is stored in a cookie
- ownership and scoping failures may return `404` rather than `403` to reduce information leakage
- business validation failures are returned through the standardized error contract

## 2. Authentication And Session Endpoints

### Staff auth

| Method | Route | Notes |
| --- | --- | --- |
| `POST` | `/auth/login` | returns direct session or partial-token selection response |
| `POST` | `/auth/switch-societe` | consumes partial token in `Authorization: Bearer ...` |
| `POST` | `/auth/logout` | clears `hlm_auth` |
| `GET` | `/auth/invitation/{token}` | invitation inspection |
| `POST` | `/auth/invitation/{token}/activer` | activation plus session start |
| `GET` | `/auth/me` | current user details |
| `PUT` | `/auth/me/langue` | updates preferred language |

### Portal auth

| Method | Route | Notes |
| --- | --- | --- |
| `POST` | `/api/portal/auth/request-link` | generic response to avoid user enumeration |
| `GET` | `/api/portal/auth/verify` | verifies magic link and sets `hlm_portal_auth` |
| `POST` | `/api/portal/auth/logout` | clears portal cookie |

## 3. Administrative APIs

### Superadmin

Route family: `/api/admin/societes`

Capabilities:

- list and filter societes
- read societe detail, stats, and compliance views
- create and update societes
- suspend and reactivate societes
- manage logos
- list and manage societe members
- send invitations
- impersonate a member

### Company member management

Route family: `/api/mon-espace/utilisateurs`

Capabilities:

- list members
- get member detail
- invite and reinvite
- patch user profile or role
- remove a member
- unblock a user
- export or anonymize user data

### Legacy / compatibility admin user surface

Route family: `/api/users`

Capabilities:

- list users
- create user
- suggest users
- change role
- enable or disable
- reset password

## 4. CRM Domain APIs

### Contacts

Route family: `/api/contacts`

Capabilities:

- create, get, list, and patch contacts
- change status
- convert to prospect or client
- manage property interests
- read contact timeline

### Inventory

Route families:

- `/api/projects`
- `/api/immeubles`
- `/api/projects/{projectId}/tranches`
- `/api/properties`
- `/api/properties/{id}/media`
- `/api/media`

Capabilities:

- CRUD over projects and buildings where permitted
- tranche generation and tranche status update
- property creation, update, status update, import, and soft deletion
- media upload and download

### Reservation and deposit

Route families:

- `/api/reservations`
- `/api/deposits`

Capabilities:

- create, view, list, and cancel reservations
- convert reservation to deposit
- create, confirm, cancel, inspect, and report on deposits
- download reservation PDFs

### Sales and contracts

Route families:

- `/api/ventes`
- `/api/contracts`
- `/api/contracts/{contractId}/schedule`
- `/api/schedule-items`

Capabilities:

- create and progress ventes
- manage vente echeances, financing, documents, and portal invitations
- generate and sign vente contracts in the vente flow
- create, sign, cancel, list, and read formal contracts
- manage payment schedules, payments, reminders, and payment PDFs

### Collaboration and governance

Route families:

- `/api/tasks`
- `/api/notifications`
- `/api/messages`
- `/api/documents`
- `/api/templates`
- `/api/audit/commercial`

Capabilities:

- task CRUD and due-now retrieval
- notification listing and mark-as-read
- outbound message creation and history
- generic document upload, list, download, delete
- template list, edit, delete, preview, and image upload
- commercial audit retrieval

### Analytics and compliance

Route families:

- `/api/dashboard/*`
- `/dashboard/properties/*`
- `/api/commissions*`
- `/api/commission-rules*`
- `/api/gdpr*`

Capabilities:

- dashboards across commercial, receivables, cash, KPI, and alerts views
- commission reporting and rule administration
- GDPR export, anonymization, rectification, privacy notice, and processing-register reads

## 5. Portal APIs

### Session and branding

- `GET /api/portal/tenant-info`

### Buyer-visible records

- `GET /api/portal/ventes`
- `GET /api/portal/ventes/{id}`
- `GET /api/portal/ventes/{id}/documents/{docId}/download`
- `POST /api/portal/ventes/{id}/documents`
- `GET /api/portal/contracts`
- `GET /api/portal/contracts/{id}/payments`
- `GET /api/portal/properties/{id}`

## 6. Error Contract

The backend uses a standardized error response from `GlobalExceptionHandler`.

Common categories:

- validation failure
- not found
- conflict / invalid state transition
- unauthorized
- forbidden
- rate limit exceeded
- lockout or security rule failure

Important design expectation:

- frontend code should present server messages where they are safe and business-meaningful
- tests should assert both status code and error category for high-value flows

## 7. Binary And Multipart Endpoints

Common binary responses:

- reservation PDFs
- contract PDFs
- payment call PDFs
- generic document downloads
- portal vente document downloads

Common multipart requests:

- property media upload
- generic document upload
- vente document upload
- project and societe logos
- template image upload

## 8. Authentication Expectations By Client Type

| Client type | Expected auth transport |
| --- | --- |
| browser CRM | `hlm_auth` cookie |
| browser portal | `hlm_portal_auth` cookie |
| societe selection step | partial bearer token |
| automated test client | bearer or cookie, depending on scenario |

## 9. API Maintenance Rules

- when adding an endpoint, document it here and in `context/api-map.md`
- when changing auth semantics, update both CRM and portal documentation
- when adding a new entity family, document upload/download and ownership behavior explicitly
