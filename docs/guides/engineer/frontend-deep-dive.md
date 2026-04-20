# Frontend Deep Dive

This guide explains the Angular application structure and the main frontend patterns.

## 1. Surface Model

The frontend serves three distinct experiences:

- CRM staff experience under `/app/*`
- superadmin experience under `/superadmin/*`
- buyer portal under `/portal/*`

All routes are declared in [../../../hlm-frontend/src/app/app.routes.ts](../../../hlm-frontend/src/app/app.routes.ts).

## 2. Route And Shell Structure

### CRM shell

The CRM shell hosts:

- dashboards
- projects and properties
- contacts
- reservations, ventes, and contracts
- tasks, notifications, messages, commissions, and audit
- admin-only user and template pages

### Superadmin shell

The superadmin shell is intentionally narrow:

- societe list
- create and edit forms
- societe detail and member access

### Portal shell

The portal contains:

- login and token verification
- ventes
- contracts
- payments
- property detail

## 3. Session Handling

### CRM

- the final session uses the `hlm_auth` cookie
- `AuthService` validates the session through `/auth/me`
- multi-societe login is handled in the login UI with a second-step selector

### Portal

- the portal uses `hlm_portal_auth`
- `PortalAuthService` validates the session through `/api/portal/tenant-info`
- `PortalSessionStore` keeps minimal client-side state

## 4. State And Service Style

Patterns visible in the codebase:

- service-per-feature for API calls
- route guards for CRM, admin, superadmin, and portal access
- component-local state and lightweight stores
- translated UI strings through `@ngx-translate`

## 5. Feature Conventions

- prefer standalone components
- keep route-level code lazy-loaded
- keep transport models close to the feature that uses them
- use `data-testid` on interactive elements that matter for E2E tests

## 6. Internationalization

- French is the default language
- English and Arabic are also supported
- language selection is persisted locally and synced to the backend user profile
- Arabic mode implies RTL layout expectations

## 7. E2E And Component Testing

### Unit tests

- Angular unit tests run with Karma/Jasmine
- use stable mocks for auth/session behaviors

### End-to-end tests

- Playwright covers key staff and portal flows
- CI runs against a built Angular app and a real backend
- request helpers must use the correct backend base URL when bypassing the browser SPA

## 8. How To Add A Frontend Feature

1. create the component or feature folder
2. add or extend a feature service
3. register the route
4. expose navigation only when the role and UX call for it
5. add tests
6. verify build and translations

## 9. Important Frontend-Specific Risks

- auth changes can affect three different route families
- navigation labels and translation keys must stay aligned
- CRM and portal session assumptions must not be mixed
- superadmin routes are not just “another CRM page”; they represent a different trust level

## 10. Good Files To Read

- [../../../hlm-frontend/src/app/app.routes.ts](../../../hlm-frontend/src/app/app.routes.ts)
- [../../../hlm-frontend/src/app/core/auth/auth.service.ts](../../../hlm-frontend/src/app/core/auth/auth.service.ts)
- [../../../hlm-frontend/src/app/portal/core/portal-auth.service.ts](../../../hlm-frontend/src/app/portal/core/portal-auth.service.ts)
- [../../../hlm-frontend/src/app/features/shell/shell.component.html](../../../hlm-frontend/src/app/features/shell/shell.component.html)
- [../../../hlm-frontend/src/app/features/login/login.component.html](../../../hlm-frontend/src/app/features/login/login.component.html)
