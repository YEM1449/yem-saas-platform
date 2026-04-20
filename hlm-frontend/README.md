# HLM Frontend

The frontend is an Angular 19 application serving three user surfaces from one codebase.

## Surfaces

| Prefix | Audience | Purpose |
| --- | --- | --- |
| `/login`, `/app/*` | CRM staff | commercial operations and back-office workflows |
| `/superadmin/*` | `SUPER_ADMIN` | platform administration |
| `/portal/*` | buyers | self-service access to contracts and payment information |

## Development

```bash
cd hlm-frontend
npm ci
npm start
```

Default dev URL: `http://localhost:4200`

The dev proxy forwards `/auth`, `/api`, and `/api/portal` to the backend.

## Key Frontend Patterns

- standalone components and lazy-loaded routes
- cookie-based session validation via `/auth/me` and portal tenant info
- translation support for French, English, and Arabic
- route guards for CRM, admin, superadmin, and portal areas
- Playwright E2E coverage for auth, contacts, tasks, pipeline, portal, and superadmin flows

## Build And Test

```bash
cd hlm-frontend
npm test -- --watch=false
npm run build
npx playwright test
```

## Read Next

- [../docs/context/MODULES.md](../docs/context/MODULES.md)
- [../docs/spec/functional-spec.md](../docs/spec/functional-spec.md)
- [../docs/guides/engineer/frontend-deep-dive.md](../docs/guides/engineer/frontend-deep-dive.md)
- [../docs/guides/user/overview.md](../docs/guides/user/overview.md)
