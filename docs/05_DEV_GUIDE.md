# 05 — Developer Guide

> Audience: engineers contributing backend, frontend, or full-stack changes.
>
> Goal: provide a precise, repeatable path from local setup to production-quality PR.

Canonical command source: [`../context/COMMANDS.md`](../context/COMMANDS.md)

## 1. Outcomes
After following this guide, you should be able to:
- run backend and frontend locally,
- execute unit and integration tests with confidence,
- implement features while preserving tenant isolation + RBAC,
- ship PRs that pass CI and documentation quality gates.

## 2. Prerequisites
| Tool | Required Version | Verify |
|------|------------------|--------|
| Java | 21 | `java -version` |
| Docker | running daemon | `docker info` |
| Node.js | 18+ | `node -v` |
| npm | 9+ | `npm -v` |
| PostgreSQL client | optional but recommended | `psql --version` |

Notes:
- Docker is required for backend integration tests (Testcontainers).
- Backend expects a reachable PostgreSQL instance for local runtime.

## 3. One-Time Local Setup
### 3.1 Configure environment variables

The `local` Spring profile provides safe defaults for all required variables, so **no env setup is required to start locally**.

To override defaults (e.g. a real JWT secret, different DB), copy and source the env file:
```bash
cp .env.example .env
# Edit .env as needed, then source it:
set -a && source .env && set +a
```

> **Note:** Do NOT use `export $(grep -v '^#' .env | xargs)` — cron expressions in `.env` contain spaces and will break that command. Use `set -a && source .env && set +a` instead.

Local defaults provided by `application-local.yml`:
| Variable | Default |
|----------|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/hlm` |
| `DB_USER` | `hlm_user` |
| `DB_PASSWORD` | `hlm_pwd` |
| `JWT_SECRET` | `local-dev-secret-do-not-use-in-production-min32` |

> **Production:** All four variables above **must** be set via env or secrets manager. Never deploy with the default JWT secret.

### 3.2 Start local PostgreSQL (recommended)
```bash
docker run -d --name hlm-postgres \
  -e POSTGRES_DB=hlm \
  -e POSTGRES_USER=hlm_user \
  -e POSTGRES_PASSWORD=hlm_pwd \
  -p 5432:5432 \
  postgres:16-alpine
```

If a container named `hlm-postgres` already exists: `docker start hlm-postgres`

### 3.3 Start backend
```bash
cd hlm-backend
chmod +x mvnw
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

> **Important:** Always pass `-Dspring-boot.run.profiles=local`. Without it, datasource defaults are not loaded and the app will fail to connect to the DB.

> **Port conflict:** If you see `Port 8080 was already in use`, stop the existing process first:
> ```bash
> fuser -k 8080/tcp
> ```

Health check:
```bash
curl http://localhost:8080/actuator/health
```
Expected: `{"status":"UP"}`.

### 3.4 Start frontend
```bash
cd hlm-frontend
npm ci
npm start
```
Open `http://localhost:4200`.

Seed login:
- tenant: `acme`
- email: `admin@acme.com`
- password: `Admin123!`

## 4. Daily Development Loops
### 4.1 Backend-only loop
```bash
cd hlm-backend
./mvnw -B -ntp test
./mvnw -B -ntp -DskipTests compile
```
Use targeted test runs while iterating:
```bash
./mvnw -B -ntp -Dtest=ClassNameTest test
./mvnw -B -ntp failsafe:integration-test failsafe:verify -Dit.test=ClassNameIT
```

### 4.2 Frontend-only loop
```bash
cd hlm-frontend
npm start
npm test -- --watch=false --browsers=ChromeHeadless --progress=false
npm run build
```

### 4.3 Full-stack loop
1. Backend running on `:8080`.
2. Frontend running on `:4200` with proxy enabled.
3. Verify auth flow with smoke script:
```bash
TENANT_KEY=acme EMAIL=admin@acme.com PASSWORD='Admin123!' ./scripts/smoke-auth.sh
```

## 5. Testing Strategy (When to Run What)
| Change Type | Minimum Local Validation |
|-------------|--------------------------|
| Backend service logic | targeted `*Test` + relevant module tests |
| Backend API / RBAC / tenant behavior | relevant `*IT` via Failsafe |
| Liquibase migration | app startup + relevant IT path |
| Frontend component/service | unit tests + production build |
| Cross-layer feature | backend tests + frontend tests + end-to-end manual smoke |

### Canonical backend test commands
```bash
cd hlm-backend
./mvnw -B -ntp test
./mvnw -B -ntp failsafe:integration-test failsafe:verify
./mvnw -B -ntp verify
```

## 6. Backend Feature Implementation Playbook
Use this sequence to avoid regressions.

1. Model change
- Add new Liquibase changeset (`NNN_description.yaml`).
- Include it in `db.changelog-master.yaml`.
- Never modify applied changesets.

2. Domain + repository
- Add/adjust entity fields.
- Add tenant-scoped repository methods.

3. Service layer
- Read tenant from `TenantContext.getTenantId()`.
- Enforce RBAC assumptions and business guards.
- Throw domain exceptions mapped to `ErrorCode`.

4. API layer
- Expose DTO-based request/response contracts.
- Use `@PreAuthorize` where endpoint-level role constraints are required.

5. Tests
- Unit tests for business logic.
- Integration tests for RBAC, tenant isolation, and API contract behavior.

6. Documentation
- Update docs/context if behavior, command, or endpoint contracts changed.

## 7. Frontend Feature Implementation Playbook
1. Add/adjust typed models and service API calls (relative paths only).
2. Update component and route behavior.
3. Keep CRM and portal auth concerns separate:
- CRM token: `hlm_access_token`
- Portal token: `hlm_portal_token`
4. Validate:
```bash
cd hlm-frontend
npm test -- --watch=false --browsers=ChromeHeadless --progress=false
npm run build
```

## 8. Security and Multi-Tenancy Guardrails
Before merging backend changes, verify:
- No code trusts tenant ID from request payload/path when tenant can be derived from JWT context.
- `hasRole('ADMIN')` style expressions are used (no `ROLE_` prefix in expression).
- Cross-tenant access attempts return protected outcomes (404/403 depending pattern).
- Sensitive endpoints have role restrictions and ownership checks where required.

Reference:
- [`../context/SECURITY_BASELINE.md`](../context/SECURITY_BASELINE.md)
- [`../context/DOMAIN_RULES.md`](../context/DOMAIN_RULES.md)

## 9. Liquibase Rules (Strict)
Do:
- add new numbered changesets,
- keep backward-compatible additive migrations,
- ensure change is idempotent in the migration lifecycle.

Do not:
- edit previously applied changesets,
- rely on Hibernate auto-DDL for schema evolution.

## 10. PR Readiness Checklist
Before opening a PR:
- [ ] relevant tests pass locally,
- [ ] tenant isolation and RBAC preserved,
- [ ] no hardcoded backend URLs in frontend code,
- [ ] migrations are additive-only,
- [ ] docs/context updated for changed behavior,
- [ ] no secrets added.

## 11. Payment v2 Only
The `payment/` v1 package has been fully deleted (Epic/sec-improvement). All payment work must target `payments/` (v2):
- Backend: `payments/api`, `payments/service`, `payments/repo`, `payments/domain`
- Frontend route: `/contracts/:contractId/payments` → `features/contracts/payment-schedule.component`
- Portal: `/api/portal/contracts/{contractId}/payment-schedule` returns `List<PaymentScheduleItemResponse>` (v2)

Reference:
- [v2/payment-v1-retirement-plan.v2.md](v2/payment-v1-retirement-plan.v2.md)

## 12. Troubleshooting Matrix
| Symptom | Likely Cause | Action |
|---------|--------------|--------|
| `Connection refused` on `:8080` | backend not running | check backend logs and health endpoint |
| startup fails with JWT error | missing/short `JWT_SECRET` | `export $(grep -v '^#' .env \| xargs)` or set `JWT_SECRET` >= 32 chars |
| Liquibase parse/YAML error | merge conflict markers in changelog file | resolve `<<<<<<<` markers in the affected `changes/*.yaml` and keep the HEAD version |
| Liquibase `relation does not exist` | FK target table missing from dependent changeset | verify preceding changeset created the referenced table; use no-op placeholder if table is retired |
| 401 after login | bad token, expiry, or revocation | re-login and re-check token use |
| 403 on API | insufficient role | verify endpoint RBAC + caller role |
| integration tests fail early | Docker unavailable | run `docker info`, then retry |
| Liquibase checksum error | edited applied changeset | revert edit, create new changeset |
| frontend API 404/CORS | proxy bypass or wrong URL | use relative `/api/*` on `:4200` app |

## 13. Useful References
- Local walkthrough: [local-dev.md](local-dev.md)
- Architecture: [01_ARCHITECTURE.md](01_ARCHITECTURE.md)
- API first calls: [api-quickstart.md](api-quickstart.md)
- Coding conventions: [../context/CONVENTIONS.md](../context/CONVENTIONS.md)
- New engineer path: [08_ONBOARDING_COURSE.md](08_ONBOARDING_COURSE.md)
