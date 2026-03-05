# 09 — New Engineer Checklist v2

Use this as a formal readiness gate before independent delivery.

## Engineer Metadata
- Engineer:
- Role:
- Start Date:
- Mentor:
- Completion Date:

## Gate A — Access and Tooling
- [ ] Repo access granted
- [ ] Java 21 installed (`java -version`)
- [ ] Docker available (`docker info`)
- [ ] Node 18+ and npm 9+ installed
- [ ] `.env` configured

## Gate B — Runtime Validation
- [ ] Backend starts successfully
- [ ] Frontend starts successfully
- [ ] Seed login succeeds
- [ ] Auth smoke script passes

## Gate C — Architecture and Security Understanding
- [ ] Can explain CRM JWT vs portal JWT
- [ ] Can explain tenant isolation path
- [ ] Can explain role annotation conventions
- [ ] Demonstrated a controlled RBAC failure (`403`)

## Gate D — Backend Contribution Readiness
- [ ] Unit tests executed
- [ ] Integration tests executed
- [ ] One feature traced end-to-end in code
- [ ] Liquibase immutability rule understood

## Gate E — Frontend Contribution Readiness
- [ ] Frontend tests run in CI mode
- [ ] Frontend production build succeeds
- [ ] Route + interceptor model understood
- [ ] Relative API path rule validated

## Gate F — CI and Operations
- [ ] CI workflow stages understood
- [ ] Error envelope structure understood
- [ ] Outbox retry semantics understood

## Gate G — First Contribution Delivered
- [ ] Scoped branch and focused change
- [ ] Test coverage added/updated
- [ ] Docs updated for behavior change
- [ ] PR reviewed and accepted

## Final Sign-off
- [ ] Mentor sign-off complete
- [ ] Engineer can deliver independently on low-risk feature changes

## Commands
```bash
cd hlm-backend && ./mvnw -B -ntp test
cd hlm-backend && ./mvnw -B -ntp failsafe:integration-test failsafe:verify
cd hlm-frontend && npm test -- --watch=false --browsers=ChromeHeadless --progress=false
cd hlm-frontend && npm run build
```
