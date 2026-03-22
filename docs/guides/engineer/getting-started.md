# Getting Started For Engineers

This guide gets the current stack running locally.

## 1. Prerequisites

| Tool | Version |
| --- | --- |
| Docker + Compose v2 | recent |
| Java | 21 |
| Node.js | 20+ |
| npm | 10+ recommended |

## 2. Start The Stack

```bash
cp .env.example .env
docker compose up -d
```

Verify backend health:

```bash
curl -s http://localhost:8080/actuator/health
```

## 3. Seed Login

Admin login:

```bash
curl -s http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@demo.ma","password":"Admin123!Secure"}'
```

Other seeded users:

- `manager@demo.ma / Manager123!Sec`
- `agent@demo.ma / Agent123!Secure`
- `superadmin@yourcompany.com / YourSecure2026!`

## 4. Frontend

Dockerized frontend:

- `http://localhost`

Local Angular dev mode:

```bash
cd hlm-frontend
npm ci
npm start
```

Then open:

- `http://localhost:4200`

## 5. Backend Dev Mode

```bash
cd hlm-backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Useful local profile behavior:

- local CORS defaults are relaxed for common local origins
- the app still uses the real schema and auth flow

## 6. Important Current Auth Behavior

The backend may return two login shapes:

- full bearer token
- partial token with `requiresSocieteSelection=true`

That second path is used when a user belongs to more than one societe. In that case the client must call:

```bash
POST /auth/switch-societe
Authorization: Bearer <partial token>
```

with body:

```json
{
  "societeId": "uuid"
}
```

## 7. Useful Local Commands

```bash
cd hlm-backend && ./mvnw test
cd hlm-backend && ./mvnw verify
cd hlm-frontend && npm test -- --watch=false
cd hlm-frontend && npm run build
```

## 8. First Files To Read

- [../../context/ARCHITECTURE.md](../../context/ARCHITECTURE.md)
- [../../context/DATA_MODEL.md](../../context/DATA_MODEL.md)
- [../../context/SECURITY_BASELINE.md](../../context/SECURITY_BASELINE.md)
- [../../spec/api-reference.md](../../spec/api-reference.md)

## 9. Known Setup Gotchas

- `JWT_SECRET` must be set outside the local profile path.
- The backend supports multi-societe login, but the current Angular login UI does not yet implement a societe selection screen.
- `POST /tenants` is not part of the active onboarding flow even though the security configuration still permits it.
