# Getting Started For Engineers

This guide is the recommended day-one path for engineers joining the repository.

## 1. What You Are Working On

The platform is a multi-societe real-estate CRM with:

- a Spring Boot backend
- an Angular frontend
- a buyer portal
- superadmin governance features
- PostgreSQL-backed business workflows for sales, collections, and compliance

Before writing code, read:

1. [../../context/ARCHITECTURE.md](../../context/ARCHITECTURE.md)
2. [../../context/MODULES.md](../../context/MODULES.md)
3. [../../spec/functional-spec.md](../../spec/functional-spec.md)

## 2. Local Prerequisites

| Tool | Recommended version |
| --- | --- |
| Docker + Compose v2 | current |
| Java | 21 |
| Node.js | 20+ |
| npm | 10+ |

## 3. Fastest Working Setup

```bash
cp .env.example .env
docker compose up -d --wait --wait-timeout 180
```

Verify:

```bash
curl -s http://localhost:8080/actuator/health
curl -I http://localhost
```

## 4. When To Run Services Manually

### Backend only

```bash
cd hlm-backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Frontend only

```bash
cd hlm-frontend
npm ci
npm start
```

Use this mode when iterating heavily on Angular or backend code without restarting the full compose stack.

## 5. Seed Accounts

| Account | Role |
| --- | --- |
| `superadmin@yourcompany.com` / `YourSecure2026!` | `SUPER_ADMIN` |
| `admin@acme.com` / `Admin123!Secure` | `ROLE_ADMIN` |
| `manager@demo.ma` / `Manager123!Sec` | `ROLE_MANAGER` |
| `agent@demo.ma` / `Agent123!Secure` | `ROLE_AGENT` |

## 6. Key Behaviors To Understand Early

### Multi-societe login

- some users may receive a societe selection step after `/auth/login`
- final staff sessions are cookie-based
- the intermediate step uses a short-lived partial bearer token

### Separate buyer sessions

- buyers do not authenticate like staff users
- portal routes use `hlm_portal_auth`

### Sales model

- `vente` is the commercial deal workflow
- `contract` is the formal contract + payment schedule workflow

## 7. First Commands To Run

```bash
cd hlm-backend && ./mvnw test
cd hlm-frontend && npm test -- --watch=false
cd hlm-frontend && npm run build
```

If Docker is available for Testcontainers:

```bash
cd hlm-backend && ./mvnw failsafe:integration-test failsafe:verify
```

## 8. Day-One Reading Order

1. [../../context/ARCHITECTURE.md](../../context/ARCHITECTURE.md)
2. [backend-deep-dive.md](backend-deep-dive.md)
3. [frontend-deep-dive.md](frontend-deep-dive.md)
4. [database.md](database.md)
5. [testing.md](testing.md)

## 9. First Contribution Rules

- keep changes societe-safe
- update Liquibase for schema changes
- update docs when workflow or auth behavior changes
- run the narrowest useful test set before widening

## 10. Common First-Week Pitfalls

- forgetting that some routes are cookie-driven rather than token-in-body driven
- reading old `tenant` names in migrations as if they were the current runtime model
- treating `vente` and `contract` as duplicates instead of complementary flows
- adding schema changes without a matching Liquibase changeset
- adding IT tests with class-level `@Transactional`
