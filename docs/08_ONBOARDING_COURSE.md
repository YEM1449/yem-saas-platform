# 08 — Onboarding Course (5-Day Ramp-Up)

> Objective: move a new engineer from setup to first high-quality contribution in one week.
>
> Pedagogical model used in this course:
> - Learn (targeted reading)
> - Observe (inspect real code paths)
> - Apply (hands-on lab)
> - Validate (objective checks)
> - Reflect (what must be retained)

## Course Logistics
### Audience
- Backend, frontend, and full-stack engineers joining the project.

### Required tools
- Java 21
- Docker
- Node 18+ / npm 9+
- `curl` and `jq`

### Daily output expectation
At the end of each day, the engineer should produce:
- one short written summary of what was learned,
- one reproducible command/result trace,
- one concrete code reference (file + method) proving understanding.

---

## Day 0 — Environment and First Run
### Learning goal
Build and run the full stack locally and verify baseline access.

### Reading (30 min)
- `AGENTS.md`
- `docs/00_OVERVIEW.md`
- `docs/05_DEV_GUIDE.md`

### Hands-on lab
1. Start PostgreSQL:
```bash
docker run -d --name hlm-postgres \
  -e POSTGRES_DB=hlm \
  -e POSTGRES_USER=hlm_user \
  -e POSTGRES_PASSWORD=hlm_pwd \
  -p 5432:5432 \
  postgres:16-alpine
```

2. Configure environment:
```bash
cp .env.example .env
export $(grep -v '^#' .env | xargs)
```

3. Start backend:
```bash
cd hlm-backend
chmod +x mvnw
./mvnw spring-boot:run
```

4. Check backend health:
```bash
curl -i http://localhost:8080/actuator/health
```

5. Start frontend:
```bash
cd hlm-frontend
npm ci
npm start
```

6. Login using seeded account:
- tenant: `acme`
- email: `admin@acme.com`
- password: `Admin123!`

7. Run smoke test:
```bash
TENANT_KEY=acme EMAIL=admin@acme.com PASSWORD='Admin123!' ./scripts/smoke-auth.sh
```

### Validation criteria
- Backend health endpoint returns `200`.
- Frontend opens and authenticates successfully.
- Smoke script completes without auth failure.

### Retention checkpoint
Engineer can explain:
- why frontend proxy avoids CORS issues in local dev,
- why Liquibase runs automatically at startup,
- where environment variables are sourced from.

---

## Day 1 — Security, Tenant Isolation, and RBAC
### Learning goal
Understand request authentication, role enforcement, and tenant scoping.

### Reading (45 min)
- `docs/01_ARCHITECTURE.md`
- `context/SECURITY_BASELINE.md`
- `context/DOMAIN_RULES.md`

### Lab 1.1 — Obtain and inspect CRM JWT
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantKey":"acme","email":"admin@acme.com","password":"Admin123!"}' \
  | jq -r '.accessToken')
export TOKEN

python3 - <<'PY'
import base64, json, os
jwt = os.environ['TOKEN']
payload = jwt.split('.')[1]
payload += '=' * (-len(payload) % 4)
print(json.dumps(json.loads(base64.urlsafe_b64decode(payload)), indent=2))
PY
```

Expected key claims:
- `sub` (user UUID)
- `tid` (tenant UUID)
- `roles`
- `tv` (token version)

### Lab 1.2 — Validate identity endpoint
```bash
curl -s http://localhost:8080/auth/me \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### Lab 1.3 — RBAC proof (ADMIN vs AGENT)
```bash
AGENT_EMAIL="agent1+$(date +%s)@acme.com"
```

1. Create agent (admin-only endpoint):
```bash
curl -s -X POST http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$AGENT_EMAIL\",\"password\":\"Agent123!\",\"role\":\"ROLE_AGENT\"}" | jq .
```

2. Login as agent:
```bash
AGENT_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"tenantKey\":\"acme\",\"email\":\"$AGENT_EMAIL\",\"password\":\"Agent123!\"}" \
  | jq -r '.accessToken')
```

3. Try admin endpoint as agent (must fail):
```bash
curl -s -o /tmp/agent_admin_check.json -w "%{http_code}" \
  http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer $AGENT_TOKEN"
```
Expected HTTP: `403`.

### Validation criteria
- Engineer can identify where `TenantContext` is populated and cleared.
- Engineer can explain why portal tokens skip user security cache checks.
- Engineer demonstrates role mismatch behavior (`403`) in practice.

---

## Day 2 — Backend Feature Anatomy
### Learning goal
Trace a full backend feature across layers and test strategy.

### Reading (45 min)
- `docs/backend.md`
- `docs/api.md`
- `context/CONVENTIONS.md`

### Lab 2.1 — Run backend test suites
```bash
cd hlm-backend
./mvnw -B -ntp test
./mvnw -B -ntp failsafe:integration-test failsafe:verify
```

### Lab 2.2 — Trace `contact` feature end-to-end
Inspect and document findings from:
- `contact/domain/*`
- `contact/repo/ContactRepository`
- `contact/service/ContactService`
- `contact/api/ContactController`
- relevant `*IT` class under `src/test/java/.../contact`

Deliverable: short note answering:
1. where tenant scoping is enforced,
2. which methods are write-protected by role,
3. how controller contract maps to service logic.

### Lab 2.3 — Liquibase comprehension
- Locate latest changesets in:
  - `hlm-backend/src/main/resources/db/changelog/`
- Explain why applied changesets are immutable.

### Validation criteria
- Engineer correctly explains unit vs integration test responsibilities.
- Engineer identifies at least one tenant-safe repository query pattern.
- Engineer can describe migration sequencing in this repo.

---

## Day 3 — Frontend Architecture and API Integration
### Learning goal
Understand route architecture, auth interceptors, and API integration patterns.

### Reading (45 min)
- `docs/frontend.md`
- `docs/api-quickstart.md`
- `context/PROJECT_CONTEXT.md`

### Lab 3.1 — Route map
Inspect `hlm-frontend/src/app/app.routes.ts` and identify:
- public routes,
- CRM-protected routes,
- portal-protected routes.

### Lab 3.2 — Auth split
Inspect:
- `core/auth/auth.interceptor.ts`
- `portal/core/portal-interceptor.ts`
- `core/auth/auth.service.ts`
- `portal/core/portal-auth.service.ts`

Write a one-paragraph explanation of:
- why CRM and portal tokens must stay isolated,
- when each interceptor attaches a token.

### Lab 3.3 — Frontend quality checks
```bash
cd hlm-frontend
npm test -- --watch=false --browsers=ChromeHeadless --progress=false
npm run build
```

### Validation criteria
- Engineer can explain relative API path requirement.
- Engineer can locate where 401-triggered logout happens.
- Frontend tests/build run successfully.

---

## Day 4 — CI, Security Gates, and Operational Behavior
### Learning goal
Understand release pipeline, security controls, and runtime troubleshooting.

### Reading (45 min)
- `docs/07_RELEASE_AND_DEPLOY.md`
- `docs/runbook.md`
- `context/SECURITY_BASELINE.md`

### Lab 4.1 — Workflow inspection
Review `.github/workflows` and answer:
1. Which jobs block merge?
2. What runs when `SNYK_TOKEN` is missing?
3. Why `dependency-review.yml` and `codeql.yml` are absent?

### Lab 4.2 — Error contract verification
Trigger a validation error and inspect response envelope:
```bash
curl -s -X POST http://localhost:8080/api/contacts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}' | jq .
```

### Lab 4.3 — Outbox behavior comprehension
Inspect:
- `outbox/service/OutboundDispatcherService`
- `outbox/repo/OutboundMessageRepository`
- `OutboxIT`

Explain:
- `FOR UPDATE SKIP LOCKED` role,
- retry/backoff behavior,
- terminal failure state.

### Validation criteria
- Engineer can read and reason about CI failures quickly.
- Engineer can explain standardized API error structure.
- Engineer understands asynchronous dispatch model.

---

## Day 5 — Capstone Contribution
### Learning goal
Ship a scoped change with tests and documentation updates.

### Capstone task (recommended)
Implement a small, safe enhancement in an existing feature, including:
- backend changes,
- tests,
- documentation update.

Suggested workflow:
1. Create branch.
2. Implement change with tenant + RBAC safety.
3. Add/adjust tests (`*Test` and/or `*IT`).
4. Run validation commands.
5. Update impacted docs.
6. Open PR with clear evidence.

### Required PR evidence
- commands run + outputs summary,
- files changed grouped by purpose,
- risk notes and rollback approach,
- test coverage statement.

### Graduation criteria
Engineer is considered independently productive when they can:
- deliver one merged change without architectural or security regressions,
- correctly apply migration and testing conventions,
- explain tenant isolation, RBAC, and token model from memory.

---

## Weekly Competency Matrix (Quick Score)
| Competency | Day 0 | Day 1 | Day 2 | Day 3 | Day 4 | Day 5 |
|------------|-------|-------|-------|-------|-------|-------|
| Local setup autonomy | ✅ |  |  |  |  |  |
| Auth + tenant reasoning |  | ✅ |  |  |  |  |
| Backend feature fluency |  |  | ✅ |  |  |  |
| Frontend integration fluency |  |  |  | ✅ |  |  |
| CI/security operational literacy |  |  |  |  | ✅ |  |
| End-to-end delivery |  |  |  |  |  | ✅ |

## Recommended Follow-Up After Week 1
- Complete [`09_NEW_ENGINEER_CHECKLIST.md`](09_NEW_ENGINEER_CHECKLIST.md).
- Pair-review one security-sensitive PR.
- Own one bugfix in a commercial workflow (`deposit`, `contract`, `payments`, or `portal`).
