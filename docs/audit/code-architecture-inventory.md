# Code Architecture Inventory

## Stack detected
- **Backend**: Spring Boot (Java 21), module-per-feature package organization under `hlm-backend/src/main/java/com/yem/hlm/backend`.
- **Frontend**: Angular SPA under `hlm-frontend/src/app`.
- **Database**: PostgreSQL schema managed by Liquibase changelogs (`hlm-backend/src/main/resources/db/changelog`).
- **Testing**: JUnit + integration tests with Testcontainers; Angular unit/build pipeline present.

## Backend modules inventory
- `auth`: JWT issuance/validation, security filter chain, `/auth` API.
- `tenant`: tenant onboarding, tenant context.
- `user`: admin user management and role assignment.
- `contact`: CRM contacts, prospect/client workflow, interests.
- `property`: property catalog + dashboard summary.
- `deposit`: reservation/deposit workflow and scheduler.
- `notification`: in-app notifications read/list actions.
- `common/error`: standardized API error contract.

## Frontend feature inventory
- `features/login`: authentication entrypoint.
- `features/shell`: secured app container and nav.
- `features/properties`: property listing/management UX.
- `features/contacts` + `features/prospects`: CRM contact workflows.
- `features/notifications`: notification feed.
- `features/admin-users`: admin-only user management page.

## Database migration inventory
- `001..002`: tenant + user bootstrap and seed.
- `003..005`: contact model expansion and relational details.
- `006..007`: deposit constraints and currency hardening.
- `008`: user roles extension.
- `009`: property table.
- `010..011`: seed fix + user token-version revocation support.
