# 09 — New Engineer Checklist

Use this checklist as a formal readiness gate, not just a todo list.

How to use:
- check items only after objective verification,
- attach command outputs/screenshots when relevant,
- keep this file aligned with onboarding deliverables.

## Engineer Metadata
- Engineer:
- Role (Backend / Frontend / Fullstack):
- Start date:
- Reviewer / Mentor:
- Date checklist completed:

---

## Gate 1 — Access and Environment (Blocker Gate)
### Access prerequisites
- [ ] GitHub repository access granted.
- [ ] Access to required secrets/processes (Snyk visibility if security-related work expected).
- [ ] Local machine has Docker permissions.

### Tooling verification
- [ ] Java 21 installed (`java -version`).
- [ ] Docker available (`docker info`).
- [ ] Node 18+ installed (`node -v`).
- [ ] npm 9+ installed (`npm -v`).

### Runtime verification
- [ ] `.env` created from `.env.example`.
- [ ] Backend starts and `/actuator/health` returns `200`.
- [ ] Frontend starts on `http://localhost:4200`.
- [ ] Seed login works (`acme` / `admin@acme.com` / `Admin123!`).
- [ ] Auth smoke test passes (`scripts/smoke-auth.sh`).

Gate result:
- [ ] PASS Gate 1

---

## Gate 2 — Architecture and Security Understanding
### Required reading completed
- [ ] `docs/00_OVERVIEW.md`
- [ ] `docs/01_ARCHITECTURE.md`
- [ ] `context/PROJECT_CONTEXT.md`
- [ ] `context/SECURITY_BASELINE.md`
- [ ] `context/DOMAIN_RULES.md`
- [ ] `docs/v2/payment-v1-retirement-plan.v2.md`

### Knowledge checks (must be explainable verbally)
- [ ] Can explain CRM JWT vs portal JWT claims (`sub`, `tid`, `roles`, `tv`).
- [ ] Can explain tenant isolation chain (JWT `tid` -> `TenantContext` -> repository filtering).
- [ ] Can explain why `hasRole('ADMIN')` is correct and `hasRole('ROLE_ADMIN')` is incorrect.
- [ ] Can explain separation between `/api/**` and `/api/portal/**`.
- [ ] Can explain payment v1 deprecation timeline and why new work targets v2 endpoints.

Practical checks:
- [ ] Decoded a JWT payload and identified `tid` + `tv`.
- [ ] Demonstrated an RBAC 403 with insufficient privileges.

Gate result:
- [ ] PASS Gate 2

---

## Gate 3 — Backend Contribution Readiness
### Technical checks
- [ ] Ran backend unit tests (`./mvnw -B -ntp test`).
- [ ] Ran backend integration tests (`./mvnw -B -ntp failsafe:integration-test failsafe:verify`).
- [ ] Traced one feature end-to-end (entity -> repo -> service -> controller -> IT).
- [ ] Identified at least one tenant-scoped query in real code.
- [ ] Reviewed Liquibase master and latest changesets.

### Rules mastery
- [ ] Understands additive-only migration rule.
- [ ] Understands DTO-only controller contract rule.
- [ ] Understands service-level ownership checks for AGENT constraints.

Gate result:
- [ ] PASS Gate 3

---

## Gate 4 — Frontend Contribution Readiness
### Technical checks
- [ ] Ran frontend tests in CI mode.
- [ ] Ran frontend production build.
- [ ] Located and explained auth interceptor behavior.
- [ ] Located and explained portal interceptor behavior.
- [ ] Confirmed API service paths are relative (no hardcoded backend host).

### Route and session model checks
- [ ] Can identify guarded CRM routes.
- [ ] Can identify guarded portal routes.
- [ ] Can explain `hlm_access_token` vs `hlm_portal_token` separation.

Gate result:
- [ ] PASS Gate 4

---

## Gate 5 — CI, Quality, and Operations
### CI understanding
- [ ] Can explain backend CI stages and failure implications.
- [ ] Can explain frontend CI stages and failure implications.
- [ ] Can explain Snyk workflow behavior and token dependency.
- [ ] Can explain secret-scan audit behavior.
- [ ] Can run `scripts/find-payment-v1-references.sh` and interpret the result.

### Error and troubleshooting literacy
- [ ] Triggered and inspected a `VALIDATION_ERROR` response envelope.
- [ ] Used runbook to diagnose one local issue.
- [ ] Can explain outbox retry pattern at high level.

Gate result:
- [ ] PASS Gate 5

---

## Gate 6 — First Contribution Delivered
### Contribution quality checks
- [ ] Feature branch created and scoped to one concern.
- [ ] Change includes tests aligned with modified behavior.
- [ ] Tenant/RBAC constraints preserved in changed paths.
- [ ] Docs/context updated where behavior/contracts changed.
- [ ] PR description includes verification commands and risk notes.

### Final sign-off
- [ ] Reviewer confirms contribution quality and autonomy level.

Gate result:
- [ ] PASS Gate 6

---

## Commands Quick Reference
```bash
# Backend
cd hlm-backend && ./mvnw -B -ntp test
cd hlm-backend && ./mvnw -B -ntp failsafe:integration-test failsafe:verify

# Frontend
cd hlm-frontend && npm test -- --watch=false --browsers=ChromeHeadless --progress=false
cd hlm-frontend && npm run build

# Smoke auth
TENANT_KEY=acme EMAIL=admin@acme.com PASSWORD='Admin123!' ./scripts/smoke-auth.sh
```

## Core References
- [05_DEV_GUIDE.md](05_DEV_GUIDE.md)
- [08_ONBOARDING_COURSE.md](08_ONBOARDING_COURSE.md)
- [../context/COMMANDS.md](../context/COMMANDS.md)
- [../context/CONVENTIONS.md](../context/CONVENTIONS.md)
