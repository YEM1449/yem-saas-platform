# Getting Started — Engineer Guide

This guide takes you from zero to a fully running local development environment in under 15 minutes. You will clone the repository, configure environment variables, start the stack with Docker Compose, and log in for the first time.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Clone the Repository](#clone-the-repository)
3. [Environment Setup](#environment-setup)
4. [Start with Docker Compose](#start-with-docker-compose)
5. [First Login](#first-login)
6. [Verify Health](#verify-health)
7. [Run Backend Locally (Without Docker)](#run-backend-locally-without-docker)
8. [Run Frontend Locally](#run-frontend-locally)
9. [Common First-Run Problems](#common-first-run-problems)

---

## Prerequisites

| Tool | Minimum Version | Install |
|------|----------------|---------|
| Docker Desktop (or Docker Engine + Compose V2) | 24+ | https://docs.docker.com/get-docker/ |
| Java 21 (Temurin recommended) | 21 | https://adoptium.net/ |
| Maven | Bundled via `./mvnw` | — |
| Node.js | 20+ | https://nodejs.org/ |
| npm | 10+ | Bundled with Node.js |
| git | Any | — |

Confirm your Docker is running:
```bash
docker info
```

---

## Clone the Repository

```bash
git clone https://github.com/yem1449/yem-saas-platform.git
cd yem-saas-platform
```

---

## Environment Setup

Copy the example environment file and fill in the required values:

```bash
cp .env.example .env
```

Open `.env` and set at minimum:

```bash
# Required: must be at least 32 characters
JWT_SECRET=my-very-long-dev-secret-replace-me-32chars

# Database (defaults match docker-compose.yml — no change needed for local dev)
DB_URL=jdbc:postgresql://localhost:5432/hlm
DB_USER=hlm_user
DB_PASSWORD=hlm_pwd
POSTGRES_DB=hlm
POSTGRES_USER=hlm_user
POSTGRES_PASSWORD=hlm_pwd
```

All other env vars have sensible defaults. Leave `EMAIL_HOST`, `TWILIO_ACCOUNT_SID`, `REDIS_ENABLED`, and `MEDIA_OBJECT_STORAGE_ENABLED` blank or `false` for local dev — the no-op providers are used automatically.

---

## Start with Docker Compose

Build and start all services:

```bash
docker compose up --build -d
```

This starts:
- `hlm-postgres` — PostgreSQL 16 on port 5432
- `hlm-redis` — Redis 7 on port 6379
- `hlm-minio` — MinIO S3-compatible storage on port 9000 (console: 9001)
- `hlm-backend` — Spring Boot on port 8080
- `hlm-frontend` — Nginx + Angular SPA on port 80

Watch startup progress:
```bash
docker compose logs -f hlm-backend
```

The backend is ready when you see:
```
Started HlmBackendApplication in X.XXX seconds
```

It can take 60–90 seconds on first run because Liquibase applies 30 changesets.

---

## First Login

### Check health first

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

### Log in with the seed admin account

```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@acme.com","password":"Admin123!Secure"}' | jq .
```

Expected response:
```json
{
  "token": "eyJhbGc...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "userId": "22222222-2222-2222-2222-222222222222",
  "tenantId": "11111111-1111-1111-1111-111111111111",
  "role": "ROLE_ADMIN",
  "email": "admin@acme.com"
}
```

Seed tenant: `acme` (id: `11111111-1111-1111-1111-111111111111`)
Seed user: `admin@acme.com` / `Admin123!Secure` (ROLE_ADMIN)

Store the token for subsequent API calls:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@acme.com","password":"Admin123!Secure"}' | jq -r .token)
```

Test an authenticated endpoint:
```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/projects | jq .
```

### Open the frontend

Navigate to `http://localhost/` (port 80) or `http://localhost:80/` in your browser. Log in with `admin@acme.com` / `Admin123!Secure`.

---

## Verify Health

```bash
# Backend health
curl http://localhost:8080/actuator/health

# Frontend (served by Nginx)
curl -I http://localhost/

# Check all containers are running
docker compose ps
```

---

## Run Backend Locally (Without Docker)

If you want to run the Spring Boot application directly on your host (faster reload):

1. Start only the infrastructure containers:
```bash
docker compose up -d postgres redis
```

2. Activate the `local` profile which provides a non-secret JWT key and relaxed CORS:
```bash
cd hlm-backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile (`application-local.yml`) sets:
- `JWT_SECRET` to a dev default so you don't need it in your env
- CORS to allow `localhost:4200`, `127.0.0.1:4200`, `localhost:5173`, `localhost:3000`
- `MAIL_HEALTH_ENABLED=false`

Run unit tests:
```bash
./mvnw test
```

Run integration tests (requires Docker for Testcontainers):
```bash
./mvnw failsafe:integration-test
```

---

## Run Frontend Locally

```bash
cd hlm-frontend
npm install
npm start
```

The Angular dev server starts on `http://localhost:4200`. The `proxy.conf.json` forwards all `/auth`, `/api`, `/dashboard`, and `/actuator` requests to the backend at `http://localhost:8080`.

```bash
# Build production bundle
npm run build
```

---

## Common First-Run Problems

### "JWT_SECRET must be set"

The application fails to start because `JWT_SECRET` is not in your environment. Ensure `.env` has `JWT_SECRET=<at least 32 characters>` and you are running from the project root where the `.env` file is located.

### "Liquibase checksum mismatch"

This happens if you manually edited an already-applied Liquibase changeset. To fix: drop and recreate the DB (development only):

```bash
docker compose down -v  # removes the postgres_data volume
docker compose up -d
```

### Backend container exits immediately

Check logs:
```bash
docker logs hlm-backend
```

Common causes:
- `JWT_SECRET` missing from `.env`
- Database not yet healthy when the backend starts (Compose `depends_on: condition: service_healthy` should prevent this, but timing can vary)
- Port 8080 already in use

### CORS errors in the browser

If you see CORS errors in the browser console when the frontend calls the backend:
- Ensure `CORS_ALLOWED_ORIGINS` in `.env` includes both `http://localhost:4200` and `http://127.0.0.1:4200`.
- This is pre-configured in `application-local.yml` when using the `local` profile.

### "Login fails with Admin123!"

The seed password was updated in changeset `030-update-seed-password.yaml`. The correct password is **`Admin123!Secure`** (15 characters). The old `Admin123!` does not meet the minimum 12-character `@StrongPassword` requirement.

### Testcontainers fails ("Cannot connect to Docker")

Integration tests use Testcontainers which requires Docker. Run `docker info` to confirm Docker is running. On Windows with WSL2, ensure the Docker Desktop WSL integration is enabled.
