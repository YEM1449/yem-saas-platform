# HLM Frontend

Angular 19 frontend for the YEM SaaS Platform.

## Surfaces

The application currently exposes three route trees:

| Prefix | Audience |
| --- | --- |
| `/app/*` | CRM users |
| `/superadmin/*` | platform `SUPER_ADMIN` |
| `/portal/*` | buyer portal |

## Local Development

```bash
cd hlm-frontend
npm ci
npm start
```

Default dev URL:

- `http://localhost:4200`

The dev proxy forwards `/auth`, `/api`, and `/api/portal` to the backend.

## Production Build

```bash
cd hlm-frontend
npm run build
```

## CI-style E2E

The default local Playwright flow uses `ng serve`, but GitHub Actions runs E2E against the static `ci` build:

```bash
cd hlm-frontend
npx ng build --configuration=ci
CI=1 PLAYWRIGHT_API_BASE=http://localhost:8080 npx playwright test
```

## Auth Notes

Current client-side auth/session state:

- CRM token -> `hlm_access_token`
- Portal session -> `hlm_portal_auth` httpOnly cookie plus in-memory `PortalSessionStore`

Current backend contract notes:

- CRM login is `email + password`
- invitation activation logs the user in
- portal login is magic-link based and verified through `/portal/login?token=...`
- backend supports a multi-societe selection step after login when needed

## Current Feature Inventory

- login and invitation activation
- CRM shell with projects, properties, contacts, prospects, reservations, contracts, schedules, dashboards, commissions, messages, notifications, audit, tasks, and admin users
- super-admin societe management
- buyer portal contracts, payments, and property detail

## Further Reading

- [../docs/guides/engineer/getting-started.md](../docs/guides/engineer/getting-started.md)
- [../docs/context/ARCHITECTURE.md](../docs/context/ARCHITECTURE.md)
- [../docs/spec/functional-spec.md](../docs/spec/functional-spec.md)
