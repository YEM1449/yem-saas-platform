# Prompt Playbook - HLM CRM

This playbook is for AI-assisted contribution workflows.
The canonical repository context now lives in:

- [../index.md](../index.md)
- [../context/ARCHITECTURE.md](../context/ARCHITECTURE.md)
- [../context/MODULES.md](../context/MODULES.md)
- [../spec/api-reference.md](../spec/api-reference.md)

Use this file for task recipes, not as a replacement for the main documentation set.

## 1. Adding A New Domain Entity

1. add a Liquibase changeset
2. add the entity, repository, service, and controller
3. make scoping explicit with `societe_id`
4. update tests
5. update docs

## 2. Extending An Existing API

1. confirm the route family and role requirements
2. add DTOs and controller changes
3. add or update service logic
4. keep repository queries societe-safe
5. test happy path, role blocking, and cross-societe behavior

## 3. Adding Or Changing An Angular Feature

1. add or extend the feature component and service
2. place it in the correct route family
3. keep auth assumptions aligned with CRM or portal behavior
4. add stable selectors for testing
5. verify build and tests

## 4. Common Safety Checks

- does this change affect `hlm_auth` or `hlm_portal_auth` behavior?
- does it introduce a new tenant-scoped table or query?
- does it alter a lifecycle transition?
- does it require docs to be updated?

## 5. Useful Local Commands

```bash
cd hlm-backend && ./mvnw test
cd hlm-backend && ./mvnw failsafe:integration-test failsafe:verify
cd hlm-frontend && npm test -- --watch=false
cd hlm-frontend && npm run build
cd hlm-frontend && npx playwright test
docker compose up -d --wait --wait-timeout 180
```
