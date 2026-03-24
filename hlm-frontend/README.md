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

## Auth Notes

Current local-storage keys:

- CRM token -> `hlm_access_token`
- Portal token -> `hlm_portal_token`

Current backend contract notes:

- CRM login is `email + password`
- invitation activation logs the user in
- portal login is magic-link based
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
