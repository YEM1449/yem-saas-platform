# 08 — Onboarding Course: YEM SaaS Platform

> **Goal**: Get a new engineer from zero to productive in 5 days, with hands-on labs grounded in real code.
>
> **Audience**: Backend (Java/Spring), Frontend (Angular), or Fullstack engineers.
>
> **Prerequisites**: Java 21 installed, Docker installed, Node 18+, npm 9+, a PostgreSQL client.

---

## Day 0 — Environment Setup

### Objectives
- Clone the repo and get both backend and frontend running locally.
- Successfully log in with the seeded admin account.
- Understand the repo structure.

### Steps

**1. Clone and inspect the repo:**
```bash
git clone <repo-url>
cd yem-saas-platform
ls -la
# Explore: AGENTS.md, CLAUDE.md, docs/, context/, hlm-backend/, hlm-frontend/
```

**2. Read the key context files (15 min):**
- `CLAUDE.md` — quick rules
- `context/PROJECT_CONTEXT.md` — what this is
- `docs/00_OVERVIEW.md` — repo layout + domain glossary

**3. Start the database:**
```bash
docker run -d --name hlm-postgres \
  -e POSTGRES_DB=hlm -e POSTGRES_USER=hlm_user -e POSTGRES_PASSWORD=hlm_pwd \
  -p 5432:5432 postgres:16-alpine
```

**4. Configure environment:**
```bash
cp .env.example .env
export $(grep -v '^#' .env | xargs)
```

**5. Start the backend:**
```bash
cd hlm-backend && chmod +x mvnw && ./mvnw spring-boot:run
# Wait for: "Started HlmBackendApplication in X seconds"
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

**6. Start the frontend:**
```bash
cd hlm-frontend && npm ci && npm start
# Open http://localhost:4200
# Login: tenant=acme, email=admin@acme.com, password=Admin123!
```

### Verification
- Browser shows the properties list at `/app/properties`.
- Run: `TENANT_KEY=acme EMAIL=admin@acme.com PASSWORD='Admin123!' ./scripts/smoke-auth.sh`

### What You Learned
- The repo layout and two-module structure (backend + frontend).
- How Liquibase auto-migrates the schema on startup.
- How the dev proxy works (`proxy.conf.json` → no CORS in dev).

---

## Day 1 — Architecture & Core Concepts

### Objectives
- Understand multi-tenancy and JWT authentication.
- Read the security filter chain.
- Understand RBAC.

### Reading (30 min)
- `docs/01_ARCHITECTURE.md` — full request pipeline diagram
- `context/DOMAIN_RULES.md` — domain rules
- `context/SECURITY_BASELINE.md` — JWT claims, RBAC

### Lab 1.1 — Inspect the JWT

**Goal**: Understand what's inside a JWT.

```bash
# 1. Login and capture token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantKey":"acme","email":"admin@acme.com","password":"Admin123!"}' \
  | jq -r '.accessToken')

# 2. Decode the payload (base64 decode the middle part)
echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq .
```

Expected output: `sub` (userId), `tid` (tenantId), `roles` (["ROLE_ADMIN"]), `tv` (tokenVersion), `exp`.

**Questions to answer**:
- What is the `tid` claim?
- What is `tv` used for?
- How long is the token valid? (check `exp - iat`)

### Lab 1.2 — Trace a Request Through the Filter

**Goal**: Read the security filter code.

1. Open [JwtAuthenticationFilter.java](../hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java).
2. Find where `TenantContext` is set.
3. Find where `tv` (tokenVersion) is validated.
4. Find the portal branch (ROLE_PORTAL handling).

**Expected finding**: The portal branch skips `UserSecurityCacheService` because contactId ≠ userId.

### Lab 1.3 — Role-Based Access Control Test

**Goal**: See RBAC in action.

```bash
TOKEN_ADMIN=... # get admin token

# Create an agent user (admin only)
curl -s -X POST http://localhost:8080/api/users \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H "Content-Type: application/json" \
  -d '{"email":"agent@acme.com","password":"Agent123!","role":"ROLE_AGENT","firstName":"Test","lastName":"Agent"}'

# Login as agent
TOKEN_AGENT=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantKey":"acme","email":"agent@acme.com","password":"Agent123!"}' \
  | jq -r '.accessToken')

# Try admin-only endpoint as agent → should get 403
curl -s -o /dev/null -w "%{http_code}" \
  http://localhost:8080/api/users \
  -H "Authorization: Bearer $TOKEN_AGENT"
# Expected: 403
```

### What You Learned
- The JWT structure and tenant isolation via `tid`.
- How `JwtAuthenticationFilter` populates `TenantContext` and Spring Security.
- RBAC enforcement via `@PreAuthorize`.

---

## Day 2 — Hands-On Backend

### Objectives
- Run the test suite.
- Read a complete feature (end-to-end: entity → repo → service → controller → IT test).
- Understand Liquibase migrations.

### Lab 2.1 — Run Tests

```bash
cd hlm-backend

# Unit tests (no Docker needed)
./mvnw test
# Expected: ~36 tests, all green

# Integration tests (Docker required)
./mvnw failsafe:integration-test
# Expected: all IT tests green (Testcontainers spins up PostgreSQL)
```

### Lab 2.2 — Read a Complete Feature: `contact/`

**Goal**: Trace a full feature from DB to API.

1. **Liquibase migration**: find the contact table changeset in `db/changelog/` — look for `contact` or `contacts`.
2. **Entity**: read [Contact.java](../hlm-backend/src/main/java/com/yem/hlm/backend/contact/domain/Contact.java).
3. **Repository**: read [ContactRepository.java](../hlm-backend/src/main/java/com/yem/hlm/backend/contact/repo/ContactRepository.java) — note how `tenantId` is always a filter.
4. **Service**: read [ContactService.java](../hlm-backend/src/main/java/com/yem/hlm/backend/contact/service/ContactService.java) — find where `TenantContext.getTenantId()` is called.
5. **Controller**: read [ContactController.java](../hlm-backend/src/main/java/com/yem/hlm/backend/contact/api/ContactController.java) — find `@PreAuthorize` annotations.
6. **IT test**: read [ContactControllerIT.java](../hlm-backend/src/test/java/com/yem/hlm/backend/contact/) — see how `@IntegrationTest` and JWT generation work.

**Questions**:
- Where is `tenantId` injected in the repository query?
- What role is required to create a contact?
- How is the JWT generated in the IT test?

### Lab 2.3 — Inspect Liquibase Migrations

```bash
# List all changesets
ls hlm-backend/src/main/resources/db/changelog/

# Find the portal_token changeset (Phase 4)
cat hlm-backend/src/main/resources/db/changelog/025_portal_token.yaml
```

**What to note**: How changesets add tables with `createTable`, add columns with `addColumn`, and use `addNotNullConstraint`. Never edit these once applied.

### Lab 2.4 — Add a Test (stretch goal)

**Goal**: Write a new unit test for a service method.

1. Pick any service class (e.g., `CommissionRuleService`).
2. Write a test for one method using Mockito.
3. Run: `./mvnw test -Dtest=YourNewTest`

### What You Learned
- How to run unit and integration tests.
- The full backend feature anatomy (entity → repo → service → controller → test).
- How Liquibase manages schema evolution.

---

## Day 3 — Hands-On Frontend

### Objectives
- Understand Angular route structure.
- Read the auth flow (interceptors, guards).
- Make a small change and verify it.

### Lab 3.1 — Explore Route Structure

```bash
# Find all route definitions
grep -r "path:" hlm-frontend/src/app/app.routes.ts
grep -r "loadComponent\|loadChildren" hlm-frontend/src/app/app.routes.ts
```

**Questions**:
- Which routes are public vs guarded?
- Where are portal routes defined?

### Lab 3.2 — Read the Auth Interceptor

1. Open [auth.interceptor.ts](../hlm-frontend/src/app/core/auth/auth.interceptor.ts).
2. Find where the JWT is attached to requests.
3. Find where 401 responses trigger a logout.

Then open the portal interceptor. How does it differ from the CRM interceptor?

### Lab 3.3 — Frontend Feature: Properties

1. Open [properties.component.ts](../hlm-frontend/src/app/features/properties/).
2. Find where `PropertyService` is called.
3. Open [property.service.ts](../hlm-frontend/src/app/features/properties/) — find the API call path. Is it relative or absolute?

### Lab 3.4 — Run Frontend Tests

```bash
cd hlm-frontend
npm test -- --watch=false --browsers=ChromeHeadless --progress=false
```

### What You Learned
- Angular 19 standalone component and lazy route architecture.
- How the auth interceptor automatically attaches JWTs.
- How relative API paths + the dev proxy enable seamless development.

---

## Day 4 — Quality, Security, and CI

### Objectives
- Understand the CI pipeline.
- Read security and error handling patterns.
- Learn to investigate failures.

### Reading (20 min)
- `docs/07_RELEASE_AND_DEPLOY.md` — CI pipeline overview
- `context/SECURITY_BASELINE.md` — all security controls

### Lab 4.1 — Read the CI Workflows

Open each workflow in `.github/workflows/`:
1. `backend-ci.yml` — what jobs run? In what order?
2. `snyk.yml` — what happens if `SNYK_TOKEN` is not set?
3. `dependency-review.yml` — when does this trigger?
4. `codeql.yml` — what languages are analyzed?

**Questions**:
- Can integration tests run without unit tests passing first?
- What severity threshold does Snyk use to fail a build?
- What is the purpose of `concurrency: cancel-in-progress: true`?

### Lab 4.2 — Read Error Handling

1. Open [GlobalExceptionHandler.java](../hlm-backend/src/main/java/com/yem/hlm/backend/common/error/GlobalExceptionHandler.java).
2. Read [ErrorCode.java](../hlm-backend/src/main/java/com/yem/hlm/backend/common/error/ErrorCode.java).
3. Trigger an error via curl:
```bash
# Missing required field → validation error
curl -s -X POST http://localhost:8080/api/contacts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}'
# Read the ErrorResponse envelope
```

### Lab 4.3 — Outbox Pattern

1. Open [OutboundDispatcherService.java](../hlm-backend/src/main/java/com/yem/hlm/backend/outbox/service/OutboundDispatcherService.java).
2. Find the `FOR UPDATE SKIP LOCKED` query.
3. Trace the retry logic and exponential backoff array `{1L, 5L, 30L}`.
4. Read [OutboxIT.java](../hlm-backend/src/test/java/com/yem/hlm/backend/outbox/) to see how the dispatcher is tested.

### What You Learned
- The full CI pipeline and its security gates.
- How error handling produces consistent JSON envelopes.
- The transactional outbox pattern and how retries work.

---

## Day 5 — End-to-End Feature + Review

### Objectives
- Implement a small, real change end-to-end.
- Practice the full workflow: branch → code → test → PR.

### Lab 5.1 — Full Workflow Practice

**Exercise**: Add a new read-only endpoint that returns the count of contacts for the current tenant.

Steps:
1. Create branch: `git checkout -b feature/contact-count`
2. Add a `countByTenantId` method to `ContactRepository`.
3. Add a `getContactCount()` method in `ContactService`.
4. Add `GET /api/contacts/count` endpoint in `ContactController` (returns `{ "count": N }`).
5. Add an IT test in `ContactControllerIT` for all three roles (admin, manager, agent).
6. Run: `./mvnw test && ./mvnw failsafe:integration-test`
7. Create a PR.

**Checklist before opening PR:**
- [ ] All existing tests still green
- [ ] New IT test covers RBAC (admin/manager/agent can all read)
- [ ] Endpoint uses `TenantContext.getTenantId()` (not hardcoded)
- [ ] Response is a DTO (not an entity)
- [ ] No sensitive data logged

---

## Debugging Playbook

| Symptom | Where to Look | Fix |
|---------|---------------|-----|
| 401 on every request | JwtAuthenticationFilter logs | Check JWT format, expiry, secret |
| 403 on valid user | `@PreAuthorize` annotation | Check role name (no ROLE_ prefix) |
| `TenantContext` returns null | Missing filter call or wrong profile | Check filter is in filter chain; use correct test profile |
| Testcontainers fails | Docker daemon | `docker info` → start Docker |
| Liquibase checksum error | Changeset was edited after apply | Revert changeset; add new changeset for fix |
| IT test picks wrong config | Profile not set | `@ActiveProfiles("test")` or use `@IntegrationTest` |
| Frontend 404 on API | Wrong path or proxy not running | Use relative path `/api/...`; run `npm start` not `ng serve --no-proxy` |
| CORS in browser | Direct access to :8080 | Use port 4200 with proxy |
| Cache returns stale data | Caffeine TTL | Wait for TTL; for test, use `@CacheEvict` or restart |

## Glossary

| Term | Definition |
|------|-----------|
| `tid` | Tenant ID — UUID in JWT claim identifying the tenant |
| `tv` | Token Version — integer for JWT revocation |
| `TenantContext` | ThreadLocal holding tenantId + userId for the current request |
| `@IntegrationTest` | Custom annotation combining `@SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("test")` |
| `*IT` | Integration Test — runs with Maven Failsafe + Testcontainers |
| Outbox | Transactional outbox pattern for reliable async email/SMS |
| Portal | Client-facing login system for property buyers (ROLE_PORTAL) |
| Magic link | One-time URL sent via email for portal authentication |
| `ROLE_PORTAL` | JWT role for portal clients (buyers); contactId as sub, no tv claim |
| Changeset | Liquibase migration unit — immutable once applied to any environment |
| `ErrorCode` | Enum of business error codes used in `ErrorResponse` envelope |
| `ProspectDetail` | Entity extending Contact with prospect-specific fields (source, stage) |
| `CommissionRule` | Rule defining how commission is calculated for a project or tenant |
| `PortalJwtProvider` | Generates portal-specific JWTs (different from `JwtProvider`) |
| `FOR UPDATE SKIP LOCKED` | PostgreSQL advisory lock used by outbox to avoid duplicate processing |
