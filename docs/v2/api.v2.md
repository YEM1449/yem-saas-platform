# API Catalog v2

This catalog reflects currently implemented backend endpoints and is organized for integrators and frontend engineers.

Base URL (local): `http://localhost:8080`

## 1. API Conventions
### Authentication
- CRM login endpoint: `POST /auth/login`
- CRM token usage: `Authorization: Bearer <JWT>`
- Portal auth endpoints: `/api/portal/auth/*`

### Error contract
- Error envelope: `ErrorResponse`
- Key status groups: `400`, `401`, `403`, `404`, `409`

### Tenant and role behavior
- Tenant derived from JWT `tid` claim.
- `ROLE_PORTAL` is isolated to `/api/portal/**`.
- AGENT access is ownership-scoped in several services.

## 2. Auth and Tenant
| Method | Endpoint | Auth | Notes |
|--------|----------|------|-------|
| POST | `/auth/login` | Public | Returns CRM JWT |
| GET | `/auth/me` | CRM JWT | Returns `userId`, `tenantId`, `role` (when available) |
| POST | `/tenants` | Public | Tenant bootstrap |
| GET | `/tenants/{id}` | CRM JWT | Tenant-scoped access checks |

## 3. User Administration
| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/admin/users` | ADMIN |
| POST | `/api/admin/users` | ADMIN |
| PATCH | `/api/admin/users/{id}/role` | ADMIN |
| PATCH | `/api/admin/users/{id}/enabled` | ADMIN |
| POST | `/api/admin/users/{id}/reset-password` | ADMIN |

## 4. Projects and Properties
### Projects
| Method | Endpoint | Role |
|--------|----------|------|
| POST | `/api/projects` | ADMIN, MANAGER |
| GET | `/api/projects` | Authenticated CRM |
| GET | `/api/projects/{id}` | Authenticated CRM |
| PUT | `/api/projects/{id}` | ADMIN, MANAGER |
| DELETE | `/api/projects/{id}` | ADMIN |
| GET | `/api/projects/{id}/kpis` | ADMIN, MANAGER |

### Properties
| Method | Endpoint | Role |
|--------|----------|------|
| POST | `/api/properties` | ADMIN, MANAGER |
| GET | `/api/properties` | Authenticated CRM |
| GET | `/api/properties/{id}` | Authenticated CRM |
| PUT | `/api/properties/{id}` | ADMIN, MANAGER |
| DELETE | `/api/properties/{id}` | ADMIN |
| POST | `/api/properties/import` | ADMIN, MANAGER |

### Property media
| Method | Endpoint | Role |
|--------|----------|------|
| POST | `/api/properties/{id}/media` | ADMIN, MANAGER |
| GET | `/api/properties/{id}/media` | Authenticated CRM |
| GET | `/api/media/{mediaId}/download` | Authenticated CRM |
| DELETE | `/api/media/{mediaId}` | ADMIN |

### Property dashboard (legacy domain dashboard)
| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/dashboard/properties/summary` | ADMIN, MANAGER |
| GET | `/dashboard/properties/sales-kpi` | ADMIN, MANAGER |

## 5. Contacts, Prospects, Timeline
| Method | Endpoint | Role |
|--------|----------|------|
| POST | `/api/contacts` | ADMIN, MANAGER |
| GET | `/api/contacts` | Authenticated CRM |
| GET | `/api/contacts/{id}` | Authenticated CRM |
| PATCH | `/api/contacts/{id}` | ADMIN, MANAGER |
| PATCH | `/api/contacts/{id}/status` | ADMIN, MANAGER |
| POST | `/api/contacts/{id}/convert-to-client` | ADMIN, MANAGER |
| POST | `/api/contacts/{id}/interests` | ADMIN, MANAGER |
| DELETE | `/api/contacts/{id}/interests/{propertyId}` | ADMIN |
| GET | `/api/contacts/{id}/interests` | Authenticated CRM |
| GET | `/api/properties/{propertyId}/contacts` | Authenticated CRM |
| GET | `/api/contacts/{id}/timeline` | Authenticated CRM |

## 6. Deposits (Reservation)
| Method | Endpoint | Role |
|--------|----------|------|
| POST | `/api/deposits` | ADMIN, MANAGER |
| GET | `/api/deposits/{id}` | Authenticated CRM |
| POST | `/api/deposits/{id}/confirm` | ADMIN, MANAGER |
| POST | `/api/deposits/{id}/cancel` | ADMIN, MANAGER |
| GET | `/api/deposits/report` | ADMIN, MANAGER |
| GET | `/api/deposits/{id}/documents/reservation.pdf` | Authenticated CRM (AGENT own only) |

## 7. Contracts (Sales)
| Method | Endpoint | Role |
|--------|----------|------|
| POST | `/api/contracts` | Authenticated CRM |
| GET | `/api/contracts` | Authenticated CRM (AGENT own only) |
| GET | `/api/contracts/{id}` | Authenticated CRM (AGENT own only) |
| POST | `/api/contracts/{id}/sign` | ADMIN, MANAGER |
| POST | `/api/contracts/{id}/cancel` | ADMIN, MANAGER |
| GET | `/api/contracts/{id}/documents/contract.pdf` | Authenticated CRM (AGENT own only) |

## 8. Payments
### v1 (deprecated, still served)
| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/contracts/{contractId}/payment-schedule` | Authenticated CRM |
| POST | `/api/contracts/{contractId}/payment-schedule` | ADMIN, MANAGER |
| PATCH | `/api/contracts/{contractId}/payment-schedule/tranches/{trancheId}` | ADMIN, MANAGER |
| POST | `/api/contracts/{contractId}/payment-schedule/tranches/{trancheId}/issue-call` | ADMIN, MANAGER |
| GET | `/api/payment-calls` | Authenticated CRM |
| GET | `/api/payment-calls/{id}` | Authenticated CRM |
| GET | `/api/payment-calls/{id}/documents/appel-de-fonds.pdf` | Authenticated CRM |
| GET | `/api/payment-calls/{id}/payments` | Authenticated CRM |
| POST | `/api/payment-calls/{id}/payments` | ADMIN, MANAGER |

### v2 (preferred)
| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/contracts/{contractId}/schedule` | Authenticated CRM |
| POST | `/api/contracts/{contractId}/schedule` | ADMIN, MANAGER |
| PUT | `/api/schedule-items/{itemId}` | ADMIN, MANAGER |
| DELETE | `/api/schedule-items/{itemId}` | ADMIN, MANAGER |
| POST | `/api/schedule-items/{itemId}/issue` | ADMIN, MANAGER |
| POST | `/api/schedule-items/{itemId}/send` | ADMIN, MANAGER |
| POST | `/api/schedule-items/{itemId}/cancel` | ADMIN, MANAGER |
| GET | `/api/schedule-items/{itemId}/pdf` | Authenticated CRM |
| GET | `/api/schedule-items/{itemId}/payments` | Authenticated CRM |
| POST | `/api/schedule-items/{itemId}/payments` | ADMIN, MANAGER |
| POST | `/api/schedule-items/reminders/run` | ADMIN |

## 9. Dashboards and Audit
### Dashboards
| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/dashboard/commercial` | Authenticated CRM |
| GET | `/api/dashboard/commercial/summary` | Authenticated CRM |
| GET | `/api/dashboard/commercial/sales` | Authenticated CRM |
| GET | `/api/dashboard/commercial/cash` | Authenticated CRM |
| GET | `/api/dashboard/receivables` | ADMIN, MANAGER, AGENT |

### Audit
| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/audit/commercial` | ADMIN, MANAGER |

## 10. Messaging and Notifications
| Method | Endpoint | Role |
|--------|----------|------|
| POST | `/api/messages` | ADMIN, MANAGER, AGENT |
| GET | `/api/messages` | ADMIN, MANAGER, AGENT |
| GET | `/api/notifications` | Authenticated CRM |
| POST | `/api/notifications/{id}/read` | Authenticated CRM |

## 11. Portal API
### Public portal auth
| Method | Endpoint | Auth |
|--------|----------|------|
| POST | `/api/portal/auth/request-link` | Public |
| GET | `/api/portal/auth/verify` | Public |

### Portal-protected data
| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/portal/contracts` | ROLE_PORTAL |
| GET | `/api/portal/contracts/{id}/documents/contract.pdf` | ROLE_PORTAL |
| GET | `/api/portal/contracts/{contractId}/payment-schedule` | ROLE_PORTAL |
| GET | `/api/portal/properties/{id}` | ROLE_PORTAL |
| GET | `/api/portal/tenant-info` | ROLE_PORTAL |

## 12. Best Practices for Integrators
1. Always treat IDs as tenant-scoped; never assume global visibility.
2. Handle `401/403/404` distinctly in frontend flows.
3. Prefer payment v2 endpoints for new integrations.
4. Use portal token only for `/api/portal/**` calls.
5. Build for idempotency/retry around asynchronous messaging operations.
