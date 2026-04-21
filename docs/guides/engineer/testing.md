# Testing Guide

This guide explains how quality is validated across the platform.

## 1. Test Layers

### Backend unit tests

- fast feedback on service, validator, and helper logic
- run with `./mvnw test`

### Backend integration tests

- validate database, security, and full workflow behavior
- use Testcontainers where needed
- run with `./mvnw failsafe:integration-test failsafe:verify`

### Frontend unit tests

- validate Angular components and services
- run with `npm test -- --watch=false`

### End-to-end tests

- validate live behavior across frontend and backend
- run with `npx playwright test`

## 2. What Should Be Covered

- auth and session behavior
- role and scope restrictions
- lifecycle transitions
- document and upload flows
- dashboards and filtering
- portal access rules

## 3. Backend Testing Rules

- prefer realistic business scenarios for workflows
- keep multi-societe boundaries explicit in tests
- avoid class-level `@Transactional` in integration tests
- use unique identifiers in data setup to avoid brittle collisions

## 4. Frontend Testing Rules

- use stable selectors
- mock services cleanly for component tests
- keep translation expectations predictable
- verify both happy path and blocked/guarded navigation when it matters

## 5. Playwright Rules

- use the correct API base for direct `page.request` calls
- keep selectors stable through `data-testid`
- cover both CRM and portal flows when shared auth behavior changes
- keep route assumptions aligned with actual Angular routing

## 6. Suggested Change Matrix

| Change type | Minimum test expectation |
| --- | --- |
| copy-only UI tweak | affected frontend unit test if meaningful, production build |
| route or guard change | build plus route-focused unit or E2E test |
| backend service change | unit test, plus IT if behavior touches persistence or security |
| auth change | backend tests and at least one browser/E2E validation |
| schema change | integration coverage strongly recommended |

## 7. Common Failure Patterns

- wrong role expectation
- stale docs about auth transport
- assuming a portal session behaves like a CRM session
- incorrect societe scoping in repo queries
- brittle test data that reuses hardcoded identifiers

## 8. Useful Commands

```bash
cd hlm-backend && ./mvnw test
cd hlm-backend && ./mvnw failsafe:integration-test failsafe:verify
cd hlm-frontend && npm test -- --watch=false
cd hlm-frontend && npm run build
cd hlm-frontend && npx playwright test
```
