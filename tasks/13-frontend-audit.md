# Task 13 — Angular Frontend Société Isolation Audit

## Priority: HIGH
## Effort: 2 hours

## Problem

The backend audit is complete, but the Angular frontend hasn't been verified for:
- Does the auth interceptor always attach the JWT Bearer token?
- Does the AuthStore signal propagate societeId correctly after société switch?
- Are there any API calls that bypass the interceptor?
- Does the login flow handle the multi-société selection correctly?
- Does the frontend correctly clear state when switching sociétés?

## Files to Audit

### Critical Path
1. `hlm-frontend/src/app/core/auth/auth.interceptor.ts` — verify every HTTP call gets Bearer token
2. `hlm-frontend/src/app/core/auth/auth.service.ts` — verify login/switch/logout flows
3. `hlm-frontend/src/app/core/store/auth.store.ts` — verify societeId is stored and reactive
4. `hlm-frontend/src/app/core/auth/auth.guard.ts` — verify route protection
5. `hlm-frontend/src/app/core/auth/admin.guard.ts` — verify admin role check

### Feature Services (check all use the interceptor)
6. All `*.service.ts` files in `features/` — verify they use HttpClient (not raw fetch)
7. `hlm-frontend/src/app/portal/core/portal.interceptor.ts` — verify portal token handling

### State Management
8. Verify that switching société clears cached data (contacts, properties, etc.)
9. Verify that the frontend doesn't cache data across société switches in memory

## What to Check

For each service file, verify:
- Uses Angular `HttpClient` (which goes through interceptor)
- Uses relative URLs (`/api/...`) not absolute URLs
- Does not manually set Authorization headers (interceptor does this)

For the auth flow:
- Multi-société login returns `selectSociete` response → frontend shows société picker → calls `/auth/switch-societe`
- The new JWT (with `sid` claim) replaces the partial token
- AuthStore.societeId is updated
- All cached feature data is invalidated

## Deliverables

- [ ] Audit report of findings
- [ ] Fix any issues found
- [ ] Add guards if any API calls bypass the interceptor

## Tests to Run

```bash
cd hlm-frontend && npm test -- --watch=false
cd hlm-frontend && npm run build -- --configuration=production
```
