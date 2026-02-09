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
