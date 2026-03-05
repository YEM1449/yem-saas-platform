# 09 — New Engineer Checklist

## Pre-Arrival (access requests)
- [ ] GitHub repo access granted
- [ ] Docker Hub account (for pulling postgres:16-alpine)
- [ ] Snyk org access (if participating in security reviews)

## Day 0 — Setup
- [ ] Java 21 (Temurin) installed — `java -version`
- [ ] Docker installed and running — `docker info`
- [ ] Node 18+ installed — `node -v`
- [ ] npm 9+ installed — `npm -v`
- [ ] PostgreSQL client available (psql or DBeaver/pgAdmin)
- [ ] Repo cloned
- [ ] `.env` file created from `.env.example`
- [ ] PostgreSQL Docker container running
- [ ] Backend starts: `http://localhost:8080/actuator/health` returns `{"status":"UP"}`
- [ ] Frontend starts: `http://localhost:4200` redirects to `/login`
- [ ] First login successful: `acme` / `admin@acme.com` / `Admin123!`
- [ ] Smoke test passes: `./scripts/smoke-auth.sh`

## Day 1 — Architecture Reading
- [ ] Read `AGENTS.md` (canonical instructions)
- [ ] Read `context/PROJECT_CONTEXT.md`
- [ ] Read `docs/00_OVERVIEW.md`
- [ ] Read `docs/01_ARCHITECTURE.md`
- [ ] Read `context/DOMAIN_RULES.md`
- [ ] Read `context/SECURITY_BASELINE.md`
- [ ] Decoded a JWT token manually (Lab 1.1)
- [ ] Traced request through `JwtAuthenticationFilter` (Lab 1.2)
- [ ] Verified RBAC with agent vs admin token (Lab 1.3)

## Day 2 — Backend Proficiency
- [ ] Unit tests green: `./mvnw test`
- [ ] Integration tests green: `./mvnw failsafe:integration-test failsafe:verify`
- [ ] Read full `contact/` feature (entity → repo → service → controller → test)
- [ ] Identified where `TenantContext.getTenantId()` is called in a service
- [ ] Explained Liquibase changeset immutability rule
- [ ] Read an IT test and understood JWT generation pattern

## Day 3 — Frontend Proficiency
- [ ] Explored Angular route structure
- [ ] Read `auth.interceptor.ts` and explained the 401 logout flow
- [ ] Read `portal.interceptor.ts` and explained the difference from auth interceptor
- [ ] Located relative API path in a service (e.g., `property.service.ts`)
- [ ] Frontend tests green: `npm test -- --watch=false --browsers=ChromeHeadless`

## Day 4 — Quality & Security
- [ ] Read `docs/07_RELEASE_AND_DEPLOY.md` and explained all 4 CI workflows
- [ ] Explained what happens when SNYK_TOKEN is missing
- [ ] Read `GlobalExceptionHandler.java` and triggered a validation error via curl
- [ ] Traced outbox retry logic (exponential backoff array)
- [ ] Read at least one IT test for the outbox

## Day 5 — First Contribution
- [ ] Created a feature branch
- [ ] Implemented the contact count endpoint (Lab 5.1)
- [ ] All tests pass (unit + IT)
- [ ] IT test covers all three CRM roles
- [ ] PR opened with clear description
- [ ] PR reviewed by a senior engineer

## Ongoing Knowledge Checks

After 2 weeks, you should be able to:
- [ ] Add a new Liquibase changeset without help
- [ ] Write a new IT test with RBAC coverage
- [ ] Explain the portal vs CRM JWT difference from memory
- [ ] Debug a failing IT test (wrong profile, missing annotation, etc.)
- [ ] Locate any feature code by navigating the package structure

## Key Files to Bookmark

| File | Why |
|------|-----|
| `AGENTS.md` | Canonical instructions — read first when blocked |
| `context/COMMANDS.md` | All commands — never guess |
| `context/CONVENTIONS.md` | Code patterns — match existing style |
| `context/DOMAIN_RULES.md` | Business rules — check before implementing |
| `docs/01_ARCHITECTURE.md` | Architecture diagrams |
| `docs/07_RELEASE_AND_DEPLOY.md` | CI pipeline reference |
| `.env.example` | Required environment variables |
| `db/changelog/db.changelog-master.yaml` | Liquibase master (find next changeset number here) |
