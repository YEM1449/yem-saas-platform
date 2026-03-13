# 08 — Onboarding Course v2 (5 Days)

## Course Goal
Enable a new engineer to contribute safely and independently within one week.

## Pedagogy
Each day follows:
1. Learn (targeted reading)
2. Observe (real code references)
3. Apply (hands-on lab)
4. Validate (objective pass criteria)

## Day 0 — Local Environment and First Success
### Objectives
- Run backend + frontend locally
- Authenticate with seed account
- Execute smoke script

### Mandatory commands
```bash
cp .env.example .env
export $(grep -v '^#' .env | xargs)

cd hlm-backend && ./mvnw spring-boot:run
cd hlm-frontend && npm ci && npm start
TENANT_KEY=acme EMAIL=admin@acme.com PASSWORD='Admin123!' ./scripts/smoke-auth.sh
```

### Validation
- health endpoint returns `200`
- login succeeds
- smoke script completes

## Day 1 — Security and Tenant Isolation
### Objectives
- Understand JWT claims (`sub`, `tid`, `roles`, `tv`)
- Understand request filter path and role enforcement

### Labs
- Decode JWT payload
- Trace `JwtAuthenticationFilter`
- Demonstrate `403` using insufficient role

### Validation
- Engineer explains tenant isolation chain from JWT to repository filters.

## Day 2 — Backend Feature Fluency
### Objectives
- Read one full feature end-to-end
- Run unit and integration tests

### Labs
- Trace `contact` feature (`domain -> repo -> service -> controller -> IT`)
- Run:
```bash
cd hlm-backend
./mvnw -B -ntp test
./mvnw -B -ntp failsafe:integration-test failsafe:verify
```

### Validation
- Engineer identifies where tenant scope and RBAC are enforced.

## Day 3 — Frontend Integration Fluency
### Objectives
- Understand route guards and interceptors
- Verify relative API path usage

### Labs
- Inspect `app.routes.ts`
- Compare CRM and portal interceptors
- Run frontend tests/build

### Validation
- Engineer can explain why CRM and portal tokens must remain isolated.

## Day 4 — CI and Operational Literacy
### Objectives
- Understand pipeline gates and failure causes
- Understand error contract and outbox behavior

### Labs
- Read workflows under `.github/workflows`
- Trigger and inspect a `VALIDATION_ERROR`
- Trace outbox retry logic

### Validation
- Engineer can triage common CI failure patterns.

## Day 5 — Capstone Contribution
### Objectives
- Deliver one scoped PR with tests and docs updates

### Required PR quality bar
- tenant-safe logic
- role-safe endpoint behavior
- tests aligned to changed behavior
- docs/context updates included

## Reference Materials
- [Overview v2](00_OVERVIEW.v2.md)
- [API Quickstart v2](api-quickstart.v2.md)
- [Checklist v2](09_NEW_ENGINEER_CHECKLIST.v2.md)
