# Contributing

## Branching conventions
- Use short-lived feature branches: `feature/<topic>`, `fix/<topic>`, `docs/<topic>`.
- Keep branches scoped to a single concern (especially tenant isolation changes).

## PR checklist
- [ ] Tenant scoping enforced (queries include `tenant_id`).
- [ ] RBAC rules updated where relevant.
- [ ] Integration tests updated or added.
- [ ] No secrets added to code or docs.
- [ ] Liquibase changes are additive with a new changeset.

## Commands to run before PR
- `cd hlm-backend`
- `./mvnw test`
- Optional: `./mvnw -DskipTests=false verify`

## Board hygiene

A GitHub issue is **Done** when all of the following are true:

1. Code changes are merged to `main`.
2. Unit tests pass (`./mvnw test` for backend, `npm run build` for frontend).
3. Integration tests pass if the issue touches backend logic (`./mvnw failsafe:integration-test`).
4. Documentation is updated (README, runbook, API docs) if the change affects developer-facing behavior.

Move the issue to **Done** only after merge. If CI is red on `main`, reopen.
