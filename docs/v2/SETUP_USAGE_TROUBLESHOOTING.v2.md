# Setup, Usage, and Troubleshooting v2

## 1. Setup
### Prerequisites
- Java 21
- Docker
- Node 18+ / npm 9+
- PostgreSQL reachable instance (or local container)

### Environment setup
```bash
cp .env.example .env
export $(grep -v '^#' .env | xargs)
```

### Start services
```bash
cd hlm-backend && ./mvnw spring-boot:run
cd hlm-frontend && npm ci && npm start
```

## 2. Core Usage Patterns
### Backend test loop
```bash
cd hlm-backend
./mvnw -B -ntp test
./mvnw -B -ntp failsafe:integration-test failsafe:verify
```

### Frontend test loop
```bash
cd hlm-frontend
npm test -- --watch=false --browsers=ChromeHeadless --progress=false
npm run build
```

### API smoke usage
Use [api-quickstart.v2.md](api-quickstart.v2.md) for end-to-end execution.

## 3. Troubleshooting Matrix
| Problem | Probable Cause | Resolution |
|---------|----------------|------------|
| `401` on protected endpoint | missing/expired token | re-login and resend Bearer token |
| `403` on write endpoint | insufficient role | verify caller role and endpoint RBAC |
| deposit create fails | property not `ACTIVE` | update property status before deposit |
| contract create fails | missing `agentId` (admin/manager path) | provide `agentId` from `/auth/me` |
| portal verify fails | token expired/used | request a new magic link |
| integration tests fail quickly | Docker unavailable | check `docker info` and daemon status |
| Liquibase checksum error | edited applied migration | revert edit; create additive changeset |
| frontend API 404 | wrong absolute URL or proxy bypass | use relative `/api/*` paths |

## 4. Operational Best Practices
1. Keep all schema changes in new Liquibase files.
2. Treat all IDs as tenant-scoped.
3. Prefer payment v2 endpoints for new integrations.
4. Keep docs updated in same PR as behavior changes.
5. Use targeted tests first, full suite before merge.

## 5. References
- [API Quickstart v2](api-quickstart.v2.md)
- [API Catalog v2](api.v2.md)
- [Onboarding v2](08_ONBOARDING_COURSE.v2.md)
