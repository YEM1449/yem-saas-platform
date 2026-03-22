# Operations Runbook — YEM SaaS Platform

**Audience:** DevOps, on-call engineers, platform administrators
**Stack:** Spring Boot 3.5.8 · Java 21 · PostgreSQL 16 · Redis 7 · Angular 19 · MinIO

---

## Table of Contents

1. [Starting the Platform](#1-starting-the-platform)
2. [Environment Variables](#2-environment-variables)
3. [First-Time Setup](#3-first-time-setup)
4. [Managing Companies (SUPER_ADMIN)](#4-managing-companies)
5. [Managing Company Members](#5-managing-company-members)
6. [RGPD Operations](#6-rgpd-operations)
7. [Common Errors](#7-common-errors)
8. [Health Checks](#8-health-checks)
9. [Database Operations](#9-database-operations)
10. [Monitoring](#10-monitoring)

---

## 1. Starting the Platform

### Option A — Docker Compose (recommended)

```bash
# Copy and fill in secrets
cp .env.example .env
# Edit .env — at minimum set POSTGRES_PASSWORD and JWT_SECRET

# Start all services (PostgreSQL, Redis, MinIO, backend, frontend)
docker compose up -d

# Check all services are healthy
docker compose ps

# Tail backend logs
docker compose logs -f hlm-backend
```

For production, overlay the hardening file:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### Option B — Local Development Mode

**Prerequisites:** Java 21, Node 20+, Docker (for Testcontainers), a running PostgreSQL instance.

```bash
# Terminal 1 — Start infrastructure only
docker compose up -d postgres redis

# Terminal 2 — Backend
export DB_URL=jdbc:postgresql://localhost:5432/hlm
export DB_USER=hlm_user
export DB_PASSWORD=hlm_pwd
export JWT_SECRET=<generate-a-32+-char-random-string>
cd hlm-backend
./mvnw spring-boot:run

# Terminal 3 — Frontend (proxies /auth and /api to :8080)
cd hlm-frontend
npm ci
npm start
```

The backend starts on `http://localhost:8080`. The frontend starts on `http://localhost:4200`.

### Stopping

```bash
docker compose down          # stops but preserves volumes
docker compose down -v       # stops and deletes volumes (data loss)
```

---

## 2. Environment Variables

All secrets must be provided as environment variables; they are never stored in source code.

### Required

| Variable | Example | Notes |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/hlm` | JDBC connection string |
| `DB_USER` | `hlm_user` | Database username |
| `DB_PASSWORD` | *(secret)* | Database password |
| `JWT_SECRET` | *(≥32 random chars)* | HS256 signing key. App refuses to start if blank (`@NotBlank`) |

### Optional — JWT

| Variable | Default | Notes |
|---|---|---|
| `JWT_TTL_SECONDS` | `3600` | Access token lifetime in seconds |

### Optional — Email (SMTP)

When `EMAIL_HOST` is blank, a no-op sender is used (emails are logged, not sent).

| Variable | Default | Notes |
|---|---|---|
| `EMAIL_HOST` | *(blank)* | SMTP server hostname. Leave blank to disable SMTP |
| `EMAIL_PORT` | `587` | SMTP port (STARTTLS) |
| `EMAIL_USER` | *(blank)* | SMTP authentication username |
| `EMAIL_PASSWORD` | *(blank)* | SMTP authentication password |
| `EMAIL_FROM` | `noreply@example.com` | Sender address |

### Optional — SMS (Twilio)

When `TWILIO_ACCOUNT_SID` is blank, a no-op sender is used.

| Variable | Default | Notes |
|---|---|---|
| `TWILIO_ACCOUNT_SID` | *(blank)* | Twilio Account SID |
| `TWILIO_AUTH_TOKEN` | *(blank)* | Twilio Auth Token |
| `TWILIO_FROM` | *(blank)* | Originating phone number |

### Optional — Redis

| Variable | Default | Notes |
|---|---|---|
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | *(blank)* | Redis password (leave blank if none) |
| `REDIS_ENABLED` | `false` | `true` for distributed Caffeine replacement in production |

### Optional — Object Storage (MinIO / S3-compatible)

| Variable | Default | Notes |
|---|---|---|
| `MEDIA_OBJECT_STORAGE_ENABLED` | `false` | `true` to use S3-compatible backend |
| `MEDIA_OBJECT_STORAGE_ENDPOINT` | *(blank)* | Leave blank for AWS S3 (auto-resolved). Set for MinIO/OVH/Scaleway/etc. |
| `MEDIA_OBJECT_STORAGE_BUCKET` | `hlm-media` | S3 bucket name |
| `MEDIA_OBJECT_STORAGE_REGION` | `eu-west-1` | Bucket region |
| `MEDIA_OBJECT_STORAGE_ACCESS_KEY` | *(blank)* | Access key |
| `MEDIA_OBJECT_STORAGE_SECRET_KEY` | *(blank)* | Secret key |
| `MEDIA_STORAGE_DIR` | `./uploads` | Local filesystem fallback when object storage is disabled |

### Optional — Bootstrap (first deploy only)

| Variable | Default | Notes |
|---|---|---|
| `APP_BOOTSTRAP_ENABLED` | `false` | Set to `true` on first deploy only. Remove after use. |
| `APP_BOOTSTRAP_EMAIL` | *(blank)* | Email for the first SUPER_ADMIN account |
| `APP_BOOTSTRAP_PASSWORD` | *(blank)* | Password (min 12 chars, upper+lower+digit+special) |

### Optional — CORS

| Variable | Default | Notes |
|---|---|---|
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200,http://127.0.0.1:4200` | Comma-separated allowed origins. Set to your exact frontend domain in production. |

### Optional — Portal

| Variable | Default | Notes |
|---|---|---|
| `PORTAL_BASE_URL` | `http://localhost:4200` | Base URL for magic link emails |
| `FRONTEND_BASE_URL` | `http://localhost:4200` | Base URL for invitation emails |

### Optional — GDPR / Scheduler

| Variable | Default | Notes |
|---|---|---|
| `GDPR_RETENTION_DAYS` | `1825` | Contact data retention in days (5 years) |
| `DATA_RETENTION_CRON` | `0 0 2 * * *` | Cron for daily GDPR sweep (02:00) |
| `REMINDER_CRON` | `0 0 8 * * *` | Cron for daily reminders (08:00) |
| `PAYMENTS_OVERDUE_CRON` | `0 0 6 * * *` | Cron for marking overdue calls (06:00) |

### Optional — Rate Limiting

| Variable | Default | Notes |
|---|---|---|
| `RATE_LIMIT_LOGIN_IP_MAX` | `20` | Max login attempts per IP per window |
| `RATE_LIMIT_LOGIN_KEY_MAX` | `10` | Max login attempts per identity per window |
| `RATE_LIMIT_LOGIN_WINDOW_SECONDS` | `60` | Rate limit window in seconds |
| `LOCKOUT_MAX_ATTEMPTS` | `5` | Failed attempts before account lockout |
| `LOCKOUT_DURATION_MINUTES` | `15` | Account lockout duration |

---

## 3. First-Time Setup

### 3.1 Bootstrap the First SUPER_ADMIN

The SUPER_ADMIN account is created via the `SuperAdminBootstrapService`, a `CommandLineRunner` that runs once at startup when `APP_BOOTSTRAP_ENABLED=true`. It is idempotent (safe to run twice).

```bash
APP_BOOTSTRAP_ENABLED=true \
APP_BOOTSTRAP_EMAIL=superadmin@yourcompany.com \
APP_BOOTSTRAP_PASSWORD='YourSecure2026!' \
./mvnw spring-boot:run
```

Password requirements: minimum 12 characters, at least one uppercase letter, one lowercase letter, one digit, and one special character. The password must not contain the email local-part.

After the backend logs `[BOOTSTRAP] SUPER_ADMIN bootstrapped successfully`, stop the server, **remove** the bootstrap environment variables, and restart normally.

```bash
# Verify — login as SUPER_ADMIN (no sid claim required)
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"superadmin@yourcompany.com","password":"YourSecure2026!"}'
```

A successful login returns an `accessToken`. SUPER_ADMIN tokens contain `"roles":["ROLE_SUPER_ADMIN"]` and no `sid` claim.

### 3.2 Verify SUPER_ADMIN Access

```bash
TOKEN=<accessToken from above>

# List all companies (should return empty page on a fresh install)
curl -s http://localhost:8080/api/admin/societes \
  -H "Authorization: Bearer $TOKEN"
```

---

## 4. Managing Companies

All company-management endpoints are under `/api/admin/societes/**` and require `ROLE_SUPER_ADMIN`. They are also double-guarded by the `@RequiresSuperAdmin` AOP aspect (`SuperAdminAspect`), which checks `SocieteContext.isSuperAdmin()`.

### 4.1 Create a Company

```bash
curl -s -X POST http://localhost:8080/api/admin/societes \
  -H "Authorization: Bearer $SA_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "nom": "Acme Immobilier",
    "pays": "MA"
  }'
```

Response: `201 Created` with the full `SocieteDetailDto` including the generated `id`.

### 4.2 List Companies (paginated, filterable)

```bash
# All companies
curl -s "http://localhost:8080/api/admin/societes?size=20&sort=nom" \
  -H "Authorization: Bearer $SA_TOKEN"

# Filter by country and subscription plan
curl -s "http://localhost:8080/api/admin/societes?pays=MA&planAbonnement=PRO" \
  -H "Authorization: Bearer $SA_TOKEN"

# Filter active/inactive
curl -s "http://localhost:8080/api/admin/societes?actif=false" \
  -H "Authorization: Bearer $SA_TOKEN"
```

### 4.3 Get Company Details and Stats

```bash
SOCIETE_ID=<uuid>

curl -s "http://localhost:8080/api/admin/societes/$SOCIETE_ID" \
  -H "Authorization: Bearer $SA_TOKEN"

curl -s "http://localhost:8080/api/admin/societes/$SOCIETE_ID/stats" \
  -H "Authorization: Bearer $SA_TOKEN"

curl -s "http://localhost:8080/api/admin/societes/$SOCIETE_ID/compliance" \
  -H "Authorization: Bearer $SA_TOKEN"
```

### 4.4 Update a Company

```bash
curl -s -X PUT "http://localhost:8080/api/admin/societes/$SOCIETE_ID" \
  -H "Authorization: Bearer $SA_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "nom": "Acme Immobilier SA",
    "planAbonnement": "PRO",
    "maxUtilisateurs": 25,
    "version": 0
  }'
```

The `version` field enables optimistic locking. If the company was modified concurrently, the request returns `409 CONCURRENT_UPDATE`.

### 4.5 Suspend / Reactivate a Company

Suspending a company bumps `tokenVersion` for all members, atomically revoking all active JWTs.

```bash
# Suspend
curl -s -X POST "http://localhost:8080/api/admin/societes/$SOCIETE_ID/desactiver" \
  -H "Authorization: Bearer $SA_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"raison": "Non-payment of subscription"}'

# Reactivate
curl -s -X POST "http://localhost:8080/api/admin/societes/$SOCIETE_ID/reactiver" \
  -H "Authorization: Bearer $SA_TOKEN"
```

### 4.6 Impersonate a Company User (SA-7)

Issues a short-lived token (1 hour TTL) carrying the target user's role and an `imp` claim for audit traceability. All operations performed under this token are traceable to the originating SUPER_ADMIN.

```bash
USER_ID=<target-user-uuid>

curl -s -X POST "http://localhost:8080/api/admin/societes/$SOCIETE_ID/impersonate/$USER_ID" \
  -H "Authorization: Bearer $SA_TOKEN"
```

Response: `{ "accessToken": "...", "impersonatedUserId": "...", "societeId": "..." }`

---

## 5. Managing Company Members

Company user management is under `/api/mon-espace/utilisateurs/**`. The caller must be authenticated with a société-scoped JWT (has `sid` claim).

### 5.1 Permission Matrix

| Action | SUPER_ADMIN | ADMIN | MANAGER | AGENT |
|---|---|---|---|---|
| List members | YES | YES | YES | NO |
| View member detail | YES | YES | YES | NO |
| Invite member | YES | YES (MANAGER/AGENT only) | NO | NO |
| Re-send invitation | YES | YES | NO | NO |
| Edit member profile | YES | YES | NO | NO |
| Change member role to ADMIN | YES | **NO** (403 ROLE_ESCALATION_FORBIDDEN) | NO | NO |
| Change member role to MANAGER/AGENT | YES | YES | NO | NO |
| Remove member | YES | YES | NO | NO |
| Unblock locked account | YES | YES | NO | NO |
| Export personal data (RGPD Art. 15) | YES | YES | YES | NO |
| Anonymise user (RGPD Art. 17) | YES | YES | NO | NO |

**Key rule:** A company-level ADMIN cannot assign the ADMIN role — only a SUPER_ADMIN can. This is enforced by `SocieteRoleValidator.validateAssignableRole()`, called both in `UserManagementController` (API layer) and `UserManagementService` (service layer, defense-in-depth).

**Last-admin protection:** The last ADMIN of a société cannot be demoted or removed. Attempt returns `409 DERNIER_ADMIN`.

### 5.2 List Members

```bash
# Requires société-scoped JWT (ADMIN or MANAGER)
curl -s "http://localhost:8080/api/mon-espace/utilisateurs?size=20" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Filter by role and status
curl -s "http://localhost:8080/api/mon-espace/utilisateurs?role=AGENT&actif=true" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### 5.3 Invite a New Member

```bash
curl -s -X POST http://localhost:8080/api/mon-espace/utilisateurs \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "newagent@company.ma",
    "role": "AGENT",
    "prenom": "Fatima",
    "nomFamille": "Benali"
  }'
```

An invitation email is sent. The user clicks the link, sets a password, and gains access. The invitation link expires after 48 hours (# TODO verify expiry duration in `InvitationService`).

### 5.4 Change a Member's Role

The request body must include the current `version` value (optimistic locking).

```bash
curl -s -X PATCH "http://localhost:8080/api/mon-espace/utilisateurs/$USER_ID/role" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "nouveauRole": "MANAGER",
    "raison": "Promoted to manager",
    "version": 2
  }'
```

On success, the user's `tokenVersion` is incremented, revoking all existing JWTs for that user.

### 5.5 Remove a Member

```bash
curl -s -X DELETE "http://localhost:8080/api/mon-espace/utilisateurs/$USER_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "raison": "Left the company",
    "version": 2
  }'
```

The membership is soft-deleted (`actif=false`). The user's `tokenVersion` is incremented, revoking all active JWTs.

### 5.6 Unblock a Locked Account

Accounts are automatically locked after `LOCKOUT_MAX_ATTEMPTS` failed login attempts (default 5). Admins can unblock manually.

```bash
curl -s -X POST "http://localhost:8080/api/mon-espace/utilisateurs/$USER_ID/debloquer" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

---

## 6. RGPD Operations

### 6.1 Export Personal Data (Art. 15 / Art. 20)

Returns the user's personal data in JSON. The `notes_admin` field is intentionally excluded (it is internal organisational data, not personal data belonging to the data subject).

```bash
curl -s "http://localhost:8080/api/mon-espace/utilisateurs/$USER_ID/export-donnees" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Response includes: id, email, prenom, nomFamille, telephone, poste, langueInterface, CGU consent data, last login, and société memberships.

### 6.2 Anonymise a User (Art. 17)

Nulls personal fields (prenom, nomFamille, telephone, poste, photoUrl, langueInterface, CGU consent), disables the account, and deactivates all société memberships. The email is preserved to maintain uniqueness constraints.

```bash
curl -s -X DELETE "http://localhost:8080/api/mon-espace/utilisateurs/$USER_ID/anonymiser" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Returns `204 No Content` on success. An `UserAnonymizedEvent` is published for audit.

### 6.3 RGPD Compliance Score

Each société has a computed compliance score (0–100) available to SUPER_ADMIN:

```bash
curl -s "http://localhost:8080/api/admin/societes/$SOCIETE_ID/compliance" \
  -H "Authorization: Bearer $SA_TOKEN"
```

Score is computed from: company name (+20), DPO email (+20), registered address (+10), CNDP/CNIL number (+30), DPO name (+10), legal basis (+10).

### 6.4 Automated Data Retention

A scheduled job runs daily at 02:00 (configurable via `DATA_RETENTION_CRON`) to sweep contact data older than `GDPR_RETENTION_DAYS` (default 1825 days / 5 years). The scheduler is disabled in the `test` Spring profile.

---

## 7. Common Errors

| HTTP | Error Code | Cause | Fix |
|---|---|---|---|
| 401 | `INVALID_OR_MISSING_TOKEN` | JWT absent, malformed, or expired | Re-authenticate to obtain a fresh token |
| 401 | `TOKEN_INVALIDATED` | Token version revoked (role change, membership removal, or société suspension) | Re-authenticate |
| 401 | `ACCOUNT_LOCKED` | Too many failed login attempts (default: 5 in 15 min) | Wait for lockout to expire or have an ADMIN call `POST /api/mon-espace/utilisateurs/{id}/debloquer` |
| 401 | `NO_SOCIETE_ACCESS` | User authenticated but has no active société membership | Ensure an `AppUserSociete` record exists for the user. An ADMIN must invite or re-add the user. |
| 403 | `SUPER_ADMIN_REQUIRED` | Called a `/api/admin/**` endpoint without `ROLE_SUPER_ADMIN` | Use a SUPER_ADMIN token |
| 403 | `ROLE_ESCALATION_FORBIDDEN` | Company-level ADMIN attempted to assign `ADMIN` role | Only SUPER_ADMIN can assign ADMIN role. Contact platform admin. |
| 403 | `INSUFFICIENT_ROLE` | Caller's role does not meet the required minimum | Use a token with a higher role, or have an ADMIN perform the action |
| 403 | `SOCIETE_SUSPENDED` | Société is suspended | SUPER_ADMIN must call `POST /api/admin/societes/{id}/reactiver` |
| 404 | `SOCIETE_NOT_FOUND` | Société UUID does not exist | Verify the UUID is correct |
| 404 | `MEMBRE_NON_TROUVE` | User is not an active member of the société | Invite or re-add the user |
| 409 | `DERNIER_ADMIN` | Attempt to remove or demote the last ADMIN | Promote another member to ADMIN first, then demote/remove |
| 409 | `CONCURRENT_UPDATE` | Optimistic lock conflict — `version` in request body is stale | Fetch the latest resource, update `version`, and retry |
| 409 | `USER_ALREADY_IN_SOCIETE` | Invitation sent to a user who is already an active member | No action needed; user already has access |
| 409 | `QUOTA_UTILISATEURS_ATTEINT` | Société has reached its `max_utilisateurs` limit | SUPER_ADMIN must increase the quota via `PUT /api/admin/societes/{id}` |
| 410 | `INVITATION_EXPIREE` | Invitation link expired or already used | ADMIN must call `POST /api/mon-espace/utilisateurs/{id}/reinviter` |
| 429 | `LOGIN_RATE_LIMITED` | Too many login attempts from this IP or for this identity | Wait and retry. Default window: 60 s, max 20 attempts per IP / 10 per identity. |
| 500 | `INTERNAL_ERROR` | Unhandled server exception | Check backend logs. Report to engineering with the `timestamp` from the error response. |

---

## 8. Health Checks

### Backend Health Endpoint

```bash
# Public — no authentication required
curl -s http://localhost:8080/actuator/health
# Expected: {"status":"UP"}

# Info endpoint
curl -s http://localhost:8080/actuator/info
```

The health endpoint checks: database connectivity, disk space, and (if enabled) mail server ping. Mail health is disabled by default (`MAIL_HEALTH_ENABLED=false`).

### Docker Compose Health Status

```bash
docker compose ps
# All services should show "healthy" status

# Check a specific service
docker compose ps hlm-backend
```

### Database Health

```bash
# Direct Postgres check (inside container)
docker exec hlm-postgres pg_isready -U hlm_user -d hlm

# Or via psql
docker exec -it hlm-postgres psql -U hlm_user -d hlm -c "SELECT version();"
```

### Redis Health

```bash
docker exec hlm-redis redis-cli ping
# Expected: PONG
```

---

## 9. Database Operations

### Running Liquibase Migrations

Migrations run automatically on startup. To run them manually (without starting the full application):

```bash
cd hlm-backend
./mvnw liquibase:update   # TODO verify — confirm this goal works with the project POM
```

Changesets are in `hlm-backend/src/main/resources/db/changelog/changes/`. They are **additive only** — never edit an already-applied changeset.

### Backup (PostgreSQL)

```bash
# Dump
docker exec hlm-postgres pg_dump -U hlm_user hlm > backup_$(date +%Y%m%d_%H%M%S).sql

# Restore
docker exec -i hlm-postgres psql -U hlm_user hlm < backup_YYYYMMDD_HHMMSS.sql
```

### Common Queries

```sql
-- List all societes and their status
SELECT id, nom, actif, plan_abonnement FROM societe ORDER BY nom;

-- Count active members per societe
SELECT s.nom, COUNT(aus.id_user_id) AS members
FROM societe s
JOIN app_user_societe aus ON aus.id_societe_id = s.id AND aus.actif = true
GROUP BY s.nom ORDER BY members DESC;

-- Check pending invitations
SELECT u.email, aus.date_ajout
FROM app_user u
JOIN app_user_societe aus ON aus.id_user_id = u.id
WHERE u.invitation_expire_at > now() AND u.enabled = false
ORDER BY aus.date_ajout DESC;

-- Find locked accounts
SELECT email, compte_bloque_at FROM app_user WHERE compte_bloque = true;
```

---

## 10. Monitoring

### OpenTelemetry (OTLP)

The backend ships with OpenTelemetry Java agent support. All exporters are disabled by default. To activate:

```bash
# In docker-compose or environment
OTEL_TRACES_EXPORTER=otlp
OTEL_METRICS_EXPORTER=otlp
OTEL_LOGS_EXPORTER=otlp
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
OTEL_SERVICE_NAME=hlm-backend
```

A sample `otel-collector-config.yml` is provided at the repo root. Pair with a Jaeger/Tempo backend for trace visualization.

### Application Logs

```bash
# Live backend logs
docker compose logs -f hlm-backend

# Filter for errors
docker compose logs hlm-backend 2>&1 | grep -E "ERROR|WARN"

# Security audit events (login, token revocation, impersonation)
docker compose logs hlm-backend 2>&1 | grep "\[SECURITY\]"

# Bootstrap events
docker compose logs hlm-backend 2>&1 | grep "\[BOOTSTRAP\]"
```

### Key Log Patterns

| Pattern | Meaning |
|---|---|
| `[BOOTSTRAP] SUPER_ADMIN bootstrapped` | First SUPER_ADMIN created successfully |
| `[BOOTSTRAP] Skipped: ... already a SUPER_ADMIN` | Idempotent re-run, no changes made |
| `Started HlmBackendApplication` | Application started successfully |
| `Tomcat started on port(s): 8080` | HTTP listener ready |
| `HikariPool ... - Start completed` | Database connection pool ready |

### Metrics

Spring Boot Actuator exposes `health` and `info` endpoints. For full Prometheus metrics, add the Micrometer Prometheus dependency and expose the `prometheus` endpoint:

```bash
# TODO verify — Prometheus endpoint not confirmed active in this build
curl http://localhost:8080/actuator/prometheus
```

### Rate Limit Monitoring

Rate limit events are logged at WARN level with the caller's IP and identity. Monitor for `LOGIN_RATE_LIMITED` (429) spikes to detect brute-force attempts.
