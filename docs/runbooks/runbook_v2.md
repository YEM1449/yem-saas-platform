# Operational Runbook — YEM SaaS Platform

| Field | Value |
|-------|-------|
| **Version** | 2.0 |
| **Date** | 2026-03-05 |
| **Audience** | SRE, DevOps, On-call Engineers, Backend Engineers |
| **Status** | Active |
| **Previous version** | [runbook.md](../archive/runbook_v1.md) |

---

## Table of Contents

1. [Overview](#1-overview)
2. [System Context](#2-system-context)
3. [Architecture Summary](#3-architecture-summary)
4. [Components and Services](#4-components-and-services)
5. [Environment Configuration](#5-environment-configuration)
6. [Deployment Procedures](#6-deployment-procedures)
7. [Monitoring and Observability](#7-monitoring-and-observability)
8. [Incident Response Procedures](#8-incident-response-procedures)
9. [Troubleshooting Guide](#9-troubleshooting-guide)
10. [Recovery and Rollback Procedures](#10-recovery-and-rollback-procedures)
11. [Maintenance Tasks](#11-maintenance-tasks)
12. [Known Issues and Limitations](#12-known-issues-and-limitations)
13. [Operational Best Practices](#13-operational-best-practices)
14. [References](#14-references)

---

## 1. Overview

**YEM SaaS Platform** is a multi-tenant SaaS CRM designed for real estate promotion and construction companies. It manages the full commercial lifecycle: projects, properties, contacts/prospects, deposits/reservations, sale contracts, payment schedules, outbound messaging, dashboards, and a buyer-facing client portal.

**Operational profile:**

| Property | Value |
|----------|-------|
| Backend | Spring Boot 3.5.8 — Java 21 — stateless |
| Frontend | Angular 19 SPA — served as static assets |
| Database | PostgreSQL 16+ — schema managed by Liquibase |
| Auth | Stateless JWT (HS256) — multi-tenant via `tid` claim |
| Messaging | Transactional outbox — at-least-once email/SMS delivery |
| Caching | Caffeine — node-local, TTL-based |
| Deployment | Manual — single-node — horizontal scaling requires shared media storage |

---

## 2. System Context

### 2.1 User Types

| Actor | Authentication | Access Scope |
|-------|---------------|-------------|
| Admin | CRM JWT (`ROLE_ADMIN`) | Full CRUD + user management + all tenant data |
| Manager | CRM JWT (`ROLE_MANAGER`) | Create/Update most resources + KPI dashboards |
| Agent | CRM JWT (`ROLE_AGENT`) | Read-only most; own deposits/contracts only |
| Property Buyer | Portal JWT (`ROLE_PORTAL`) | Read-only: own contracts via portal |

### 2.2 Tenant Model

- Every company is an isolated **tenant**, identified by a UUID.
- Tenant ID (`tid`) is embedded in every JWT and is the authoritative source.
- All data is row-scoped by `tenant_id`; cross-tenant access is architecturally impossible.
- A seeded development tenant exists: key=`acme`, ID=`11111111-1111-1111-1111-111111111111`.

### 2.3 Network Topology (Local Dev)

```
Browser (port 4200)
  └─► Angular Dev Server (proxy)
        └─► Spring Boot API (port 8080)
              ├─► PostgreSQL (port 5432)
              └─► SMTP / SMS providers (external)
```

---

## 3. Architecture Summary

### 3.1 Request Pipeline

```
Inbound HTTP Request
  │
  ├─► [PUBLIC] /auth/login, /api/portal/auth/*, /actuator/health, /swagger-ui/**
  │     └─► No authentication required
  │
  └─► [PROTECTED] all other paths
        └─► JwtAuthenticationFilter (OncePerRequestFilter)
              ├─► Decode & validate JWT (HS256, expiry)
              ├─► Extract: sub (userId/contactId), tid (tenantId), roles, tv
              ├─► ROLE_PORTAL → set principal = contactId (skip user security cache)
              ├─► CRM roles → validate tokenVersion via UserSecurityCacheService
              ├─► Set TenantContext (ThreadLocal: tenantId, userId)
              └─► Set Spring Security Authentication
                    │
                    └─► SecurityConfig route authorization
                          └─► @PreAuthorize method-level checks
                                └─► Controller → Service → Repository
                                      └─► (finally) TenantContext.clear()
```

### 3.2 JWT Token Model

| Claim | CRM JWT | Portal JWT |
|-------|---------|-----------|
| `sub` | userId (UUID) | contactId (UUID) |
| `tid` | tenantId (UUID) | tenantId (UUID) |
| `roles` | `ROLE_ADMIN` / `ROLE_MANAGER` / `ROLE_AGENT` | `ROLE_PORTAL` |
| `tv` | tokenVersion (int — for revocation) | **absent** |
| `iat` / `exp` | standard | standard (TTL fixed at 2h) |
| TTL default | 3600s (configurable) | 7200s |

### 3.3 Multi-Tenancy Enforcement Layers

```
1. JWT validation       → `tid` claim mandatory on every protected request
2. Filter               → TenantContext.setTenantId(tid) in JwtAuthenticationFilter
3. Service              → TenantContext.getTenantId() used in all data queries
4. Repository           → Every query filters on tenant_id
5. Test                 → CrossTenantIsolationIT verifies cross-tenant access returns 404
```

---

## 4. Components and Services

### 4.1 Backend — Spring Boot Application

| Property | Value |
|----------|-------|
| Port | 8080 |
| Health endpoint | `GET /actuator/health` |
| Info endpoint | `GET /actuator/info` |
| API docs | `GET /swagger-ui/index.html` |
| OpenAPI JSON | `GET /v3/api-docs` |
| Startup | Liquibase migrations run automatically |
| JVM recommended | `-Xms256m -Xmx512m -XX:+UseG1GC` |

**Backend packages (com.yem.hlm.backend):**

| Package | Role |
|---------|------|
| `auth/` | JWT, login, security config, filter |
| `tenant/` | Tenant entity, TenantContext (ThreadLocal) |
| `user/` | User management, RBAC roles |
| `contact/` | Contacts, prospects, ProspectDetail |
| `property/` | Properties, status machine |
| `project/` | Real estate projects |
| `deposit/` | Reservation deposits |
| `contract/` | Sale contracts, PDF generation |
| `commission/` | Commission rules and calculations |
| `dashboard/` | KPI, receivables, cash dashboards |
| `outbox/` | Transactional outbox (email/SMS) |
| `portal/` | Client portal: magic link + contract view |
| `payment/` | Payment schedule v1 **(deprecated, sunset 2026-12-31)** |
| `payments/` | Payment schedule v2 (preferred) |
| `media/` | Property file upload/download |
| `notification/` | In-app CRM notifications |
| `reminder/` | Scheduled reminders |
| `audit/` | Audit log |
| `common/` | Error envelope, shared utilities |

### 4.2 Frontend — Angular SPA

| Property | Value |
|----------|-------|
| Dev server | Port 4200 |
| Dev proxy target | `http://localhost:8080` |
| Routes proxied | `/auth`, `/api`, `/dashboard`, `/actuator` |
| Auth storage | `localStorage` key `hlm_access_token` |
| Portal storage | `localStorage` key `hlm_portal_token` |
| Production build | `npm run build` → `dist/` |

### 4.3 Database — PostgreSQL

| Property | Value |
|----------|-------|
| Version | 16+ recommended |
| Schema tool | Liquibase (auto-runs on backend startup) |
| DDL validation | Hibernate `ddl-auto: validate` |
| Tenant scoping | `tenant_id` column on all tenant-scoped tables |
| Changeset master | `db/changelog/db.changelog-master.yaml` |
| Changeset range | 001–027+ (sequential numbered files) |

**Key tables:**

| Table | Description |
|-------|-------------|
| `tenant` | Tenant registry |
| `app_user` | CRM users |
| `contact` | Contacts and prospects |
| `property` | Property catalog |
| `project` | Real estate projects |
| `deposit` | Reservation deposits |
| `sale_contract` | Sale contracts |
| `payment_schedule_item` | Payment schedule (v2) |
| `outbound_message` | Outbox messages queue |
| `portal_token` | Magic link tokens (SHA-256 stored) |
| `commission_rule` | Commission rules |
| `audit_log` | Commercial audit trail |

### 4.4 Caching — Caffeine

- **Type:** In-process, node-local (not distributed)
- **Config:** `CacheConfig` — each cache registered with independent TTL and max size
- **Key caches:**

| Cache Name | TTL | Purpose |
|-----------|-----|---------|
| `userSecurityCache` | configurable | Token version lookup (avoids per-request DB hit) |
| `commercialDashboard` | configurable | KPI summary cache |
| `receivablesDashboard` | 30s | Receivables aging buckets |
| `cashDashboard` | configurable | Cash dashboard |

- **Invalidation:** `userSecurityCache` is evicted on role change or user disable; others are TTL-only.

### 4.5 Outbox — Transactional Messaging

```
API writes OutboundMessage (status=PENDING) in same DB transaction
  └─► OutboxScheduler polls batch with FOR UPDATE SKIP LOCKED (every OUTBOX_POLL_INTERVAL_MS)
        └─► OutboundDispatcherService invokes:
              ├─► EmailSender.send(to, subject, body)
              └─► SmsSender.send(to, body)
                    ├─► Success → status = SENT
                    └─► Failure → retry with backoff: 1m, 5m, 30m
                          └─► After OUTBOX_MAX_RETRIES → status = FAILED (permanent)
```

- Default providers: `NoopEmailSender`, `NoopSmsSender` (log only — no real delivery in dev)
- Production: inject real `EmailSender` / `SmsSender` beans (SMTP, Twilio, etc.)

### 4.6 PDF Generation

- Library: **OpenHtmlToPDF** (`PdfRendererBuilder.useFastMode()`)
- Template engine: Thymeleaf (HTML → PDF)
- Rendering: **synchronous, in-memory** (`ByteArrayOutputStream`)
- Documents: reservation certificate, sale contract, payment call (Appel de Fonds)
- Memory note: each render holds full PDF bytes in heap; increase `-Xmx` if OOM observed

---

## 5. Environment Configuration

### 5.1 Required Environment Variables

| Variable | Example | Notes |
|----------|---------|-------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/hlm` | PostgreSQL JDBC URL |
| `DB_USER` | `hlm_user` | Database username |
| `DB_PASSWORD` | `hlm_pwd` | Database password |
| `JWT_SECRET` | *(64+ chars random)* | **Minimum 32 chars** — app refuses to start if shorter/blank |
| `JWT_TTL_SECONDS` | `3600` | Token lifetime; default 3600 |

### 5.2 Optional Environment Variables

| Variable | Default | Notes |
|----------|---------|-------|
| `MAIL_HOST` | — | SMTP server hostname |
| `MAIL_PORT` | `587` | SMTP port |
| `MAIL_USERNAME` | — | SMTP auth user |
| `MAIL_PASSWORD` | — | SMTP auth password |
| `MAIL_FROM` | — | From address for outgoing email |
| `OUTBOX_BATCH_SIZE` | `50` | Outbox batch size per poll |
| `OUTBOX_MAX_RETRIES` | `3` | Max retry attempts |
| `OUTBOX_POLL_INTERVAL_MS` | `30000` | Polling interval in ms |
| `MEDIA_STORAGE_DIR` | `./uploads` | Local media storage path |
| `MEDIA_MAX_FILE_SIZE` | `10485760` | Max upload in bytes (10 MB) |
| `CORS_ALLOWED_ORIGINS` | — | Comma-separated allowed origins |

### 5.3 Environment File

```bash
# Bootstrap from template
cp .env.example .env

# Export for shell sessions
export $(grep -v '^#' .env | xargs)
```

### 5.4 JWT Secret Generation

```bash
# Generate a secure secret (Linux/macOS)
openssl rand -base64 48
```

**Rules:**
- Minimum 32 characters.
- Application fails on startup with `JWT_SECRET` blank or too short (`@NotBlank` validation).
- Secret must match across all nodes if running more than one instance.
- Rotating the secret invalidates **all** active sessions immediately.

---

## 6. Deployment Procedures

### 6.1 Pre-Deployment Checklist

```
[ ] All CI jobs green on the PR (backend-ci, frontend-ci, snyk)
[ ] Integration tests pass (failsafe:integration-test failsafe:verify)
[ ] Liquibase changesets are additive and reviewed
[ ] Environment variables validated in target environment
[ ] MEDIA_STORAGE_DIR writable by the JVM process
[ ] JWT_SECRET set and ≥32 characters
[ ] SMTP/SMS config tested if using real providers
[ ] Health endpoint responding before traffic is routed
```

### 6.2 Backend Deployment

```bash
# 1. Build production JAR
cd hlm-backend
./mvnw -B -DskipTests package
# Output: target/hlm-backend-0.0.1-SNAPSHOT.jar

# 2. Run with environment variables
java $JAVA_OPTS \
  -Dspring.profiles.active=prod \
  -jar target/hlm-backend-0.0.1-SNAPSHOT.jar

# Recommended JAVA_OPTS for production
export JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -Dfile.encoding=UTF-8"

# 3. Verify startup
curl -f http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

**Startup sequence:**
1. Spring context loads → beans initialized
2. `JwtProperties` validated (`JWT_SECRET` present and ≥ 32 chars) — app aborts if invalid
3. Liquibase runs pending changesets against the database
4. Hibernate validates schema against entities (`ddl-auto: validate`) — app aborts if mismatch
5. Tomcat starts on port 8080
6. Scheduled jobs start (outbox poller, overdue deposit checker, reminder scheduler)

### 6.3 Frontend Deployment

```bash
# 1. Install dependencies
cd hlm-frontend && npm ci

# 2. Build for production
npm run build
# Output: dist/ directory

# 3. Serve dist/ with a web server (nginx, Apache, or CDN)
# Configure /auth, /api, /dashboard, /actuator to proxy to backend
```

**nginx proxy config example:**
```nginx
location /api/ {
    proxy_pass http://backend:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
location /auth/ {
    proxy_pass http://backend:8080;
}
```

### 6.4 Docker Quick Start (Local Dev)

```bash
# Start PostgreSQL
docker run -d --name hlm-postgres \
  -e POSTGRES_DB=hlm \
  -e POSTGRES_USER=hlm_user \
  -e POSTGRES_PASSWORD=hlm_pwd \
  -p 5432:5432 \
  postgres:16-alpine

# Verify database is ready
docker exec hlm-postgres pg_isready -U hlm_user -d hlm
```

---

## 7. Monitoring and Observability

### 7.1 Health Endpoints

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `GET /actuator/health` | None | Liveness / readiness probe |
| `GET /actuator/info` | None | Build + version info |
| `GET /actuator/health/db` | None | Database connectivity |
| `GET /actuator/health/diskSpace` | None | Disk space check |
| `GET /actuator/*` (others) | ROLE_ADMIN | Full metrics, env, etc. |

### 7.2 Structured Logging

- Format: **JSON** (Logstash Logback Encoder)
- Key log fields: `timestamp`, `level`, `logger`, `message`, `traceId` (if configured), `tenantId` (in service logs)
- Log levels by package:

| Package | Recommended Level |
|---------|------------------|
| `com.yem.hlm.backend` | `INFO` (production) / `DEBUG` (dev) |
| `org.springframework.security` | `WARN` |
| `org.hibernate.SQL` | `DEBUG` only for query tracing |
| `liquibase` | `INFO` |

**Important:** Never log `Authorization` header values, raw tokens, or passwords.

### 7.3 Key Operational Metrics to Monitor

| Metric / Signal | Threshold | Action |
|-----------------|-----------|--------|
| `/actuator/health` returns non-200 | Any | Immediate investigation |
| HTTP 5xx error rate spike | > 1% | Check logs for exception stack traces |
| HTTP 401 rate spike | Sustained > 5% | Possible JWT secret rotation or config issue |
| HTTP 403 rate spike | Sustained | Check RBAC config or client token claims |
| Outbox `FAILED` messages accumulating | Any | Check email/SMS provider connectivity |
| JVM heap near Xmx | > 80% | Increase -Xmx or investigate memory leak |
| DB connection pool exhausted | Any | Check HikariCP pool config + slow queries |
| Liquibase startup failure | Any | DO NOT restart without investigating — risk of data inconsistency |

### 7.4 Outbox Health Check

```bash
# Check for stuck PENDING messages (older than 1 hour)
psql $DB_URL -c "
SELECT status, count(*), max(created_at) as oldest
FROM outbound_message
WHERE created_at < now() - interval '1 hour'
GROUP BY status;
"

# Check FAILED messages
psql $DB_URL -c "
SELECT id, channel, recipient, error_message, retry_count, created_at
FROM outbound_message
WHERE status = 'FAILED'
ORDER BY created_at DESC
LIMIT 20;
"
```

### 7.5 Payment v1 Deprecation Telemetry

```bash
# Check v1 usage in logs (look for deprecation marker)
grep "payment_v1_endpoint_called" /var/log/hlm/application.log | tail -20

# Count v1 calls by endpoint (if Micrometer is configured)
# Metric: payment_v1_requests_total{endpoint="...", method="..."}
```

---

## 8. Incident Response Procedures

### 8.1 Severity Classification

| Severity | Description | Response Time |
|----------|-------------|--------------|
| P1 — Critical | Full service down, all users impacted | Immediate |
| P2 — High | Core feature broken, significant user impact | < 30 min |
| P3 — Medium | Feature degraded, workaround available | < 2 hours |
| P4 — Low | Minor issue, cosmetic | Next business day |

### 8.2 P1: Application Completely Down

**Symptoms:** `/actuator/health` returns non-200 or connection refused.

```bash
# Step 1: Verify process is running
ps aux | grep java

# Step 2: Check logs for fatal startup errors
journalctl -u hlm-backend -n 100 --no-pager
# OR if running from shell:
tail -100 /var/log/hlm/application.log

# Step 3: Common causes (check in order):
# a) JWT_SECRET missing or too short
grep "JWT_SECRET" /var/log/hlm/application.log | tail -5
# Look for: "JWT secret is too short" or "JWT_SECRET is missing"

# b) Database connection failure
grep "Could not obtain connection" /var/log/hlm/application.log | tail -5
# Fix: verify PostgreSQL is running and DB_URL/DB_USER/DB_PASSWORD are correct

# c) Liquibase migration failure
grep "liquibase" /var/log/hlm/application.log | grep -i "error\|fail" | tail -10
# Fix: see Section 9.6

# d) Hibernate schema validation failure
grep "SchemaManagementException\|Validate schema" /var/log/hlm/application.log | tail -5
# Fix: a migration is missing; check if the latest Liquibase changeset ran

# Step 4: Restart after fixing root cause
systemctl restart hlm-backend
# Wait 30s then verify:
curl -f http://localhost:8080/actuator/health
```

### 8.3 P2: Authentication Failures (Mass 401)

**Symptoms:** Multiple users reporting 401 errors; login endpoint returns 401.

```bash
# Step 1: Verify login endpoint is responding
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantKey":"acme","email":"admin@acme.com","password":"Admin123!"}'
# Expected: 200

# Step 2: If login fails, check credentials or JWT_SECRET
echo "JWT_SECRET length: ${#JWT_SECRET}"
# Must be ≥ 32 characters

# Step 3: If login succeeds but subsequent calls fail with 401 →
# Possible JWT_SECRET change (all existing tokens invalidated)
# Action: inform users to log in again; no data loss

# Step 4: Token version mismatch (user role was changed)
psql $DB_URL -c "SELECT id, email, token_version, enabled FROM app_user WHERE tenant_id='<tid>';"
# If tokenVersion was incremented, all tokens for that user are invalidated → user must re-login
```

### 8.4 P2: Database Unavailable

```bash
# Step 1: Check PostgreSQL status
pg_isready -h $DB_HOST -p 5432 -U $DB_USER

# Step 2: Check application DB pool logs
grep "HikariPool\|Connection\|could not connect" /var/log/hlm/application.log | tail -20

# Step 3: Test direct connection
psql $DB_URL -c "SELECT 1;"

# Step 4: If DB is down
# - If data loss is possible: DO NOT restart application — data in outbox may be in flight
# - Restore PostgreSQL → application will reconnect automatically (HikariCP retry)
# - Verify outbox messages after reconnect (see Section 7.4)
```

### 8.5 P3: Outbox Delivery Failure

**Symptoms:** Users not receiving emails/SMS; `FAILED` status in `outbound_message`.

```bash
# Step 1: Check failed messages
psql $DB_URL -c "
SELECT channel, error_message, retry_count, created_at
FROM outbound_message WHERE status='FAILED' ORDER BY created_at DESC LIMIT 10;"

# Step 2: Check provider connectivity
curl -v smtp://$MAIL_HOST:$MAIL_PORT

# Step 3: Manual retry (if provider is now available)
# Reset FAILED → PENDING to allow re-processing
psql $DB_URL -c "
UPDATE outbound_message
SET status='PENDING', retry_count=0, next_retry_at=now()
WHERE status='FAILED'
AND created_at > now() - interval '24 hours';"
# WARNING: This may cause duplicate delivery if message was partially sent.

# Step 4: Noop providers in dev
# If running with Noop providers, no emails are sent — check logs for
# "NoopEmailSender: would send email to ..." messages
```

### 8.6 P3: Slow Queries / Performance Degradation

```bash
# Step 1: Identify slow queries (requires pg_stat_statements extension)
psql $DB_URL -c "
SELECT query, calls, mean_exec_time, total_exec_time
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;"

# Step 2: Check connection pool saturation
# Look for "HikariPool ... timed out waiting for connection" in logs
grep "timed out waiting" /var/log/hlm/application.log | tail -10

# Step 3: Check cache hit rate
grep "Cache miss\|cacheable" /var/log/hlm/application.log | tail -20
# For dashboards: a cache miss on every request suggests TTL is too short

# Step 4: Clear application-level caches via actuator (admin only)
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"tenantKey":"acme","email":"admin@acme.com","password":"Admin123!"}' \
  | jq -r '.accessToken')
curl -X DELETE http://localhost:8080/actuator/caches/commercialDashboard \
  -H "Authorization: Bearer $TOKEN"
```

---

## 9. Troubleshooting Guide

### 9.1 Connection Refused on Port 8080

**Cause:** Backend is not running.

```bash
# Check if process is up
ps aux | grep hlm-backend
# or
ss -tlnp | grep 8080

# Restart
cd hlm-backend && ./mvnw spring-boot:run
# Wait for: "Tomcat started on port(s): 8080"
```

### 9.2 401 Unauthorized

| Sub-cause | Diagnosis | Fix |
|-----------|----------|-----|
| Missing `Authorization` header | Request logs show no header | Add `Authorization: Bearer <token>` |
| Token expired | JWT `exp` claim in the past | Re-login to get a fresh token |
| Malformed token | Signature validation failure in logs | Use the correct token format |
| Token version mismatch | `tv` claim ≠ `user.tokenVersion` | User must re-login; role/status was changed |
| Wrong JWT_SECRET | Signature invalid | Verify `JWT_SECRET` matches what was used to issue the token |
| Portal token on CRM route | ROLE_PORTAL on `/api/**` | Use CRM token for CRM endpoints |

### 9.3 403 Forbidden

| Sub-cause | Diagnosis | Fix |
|-----------|----------|-----|
| Insufficient role | JWT `roles` lacks required role | Use an account with the required role |
| `hasRole('ROLE_ADMIN')` in annotation | Double-prefix bug | Use `hasRole('ADMIN')` — Spring adds `ROLE_` |
| Agent trying write operation | Expected — read-only | Use Manager or Admin account |

### 9.4 CORS Preflight Failure

**Cause:** Calling backend directly from browser on a different origin.

```
Access to XMLHttpRequest at 'http://localhost:8080/api/...' from origin 'http://localhost:4200'
has been blocked by CORS policy
```

**Fix for dev:** Use the Angular dev server (port 4200) — the proxy handles CORS automatically.

**Fix for production:** Set `CORS_ALLOWED_ORIGINS` to include your frontend domain.

### 9.5 Liquibase Checksum Error

**Cause:** An already-applied changeset was edited.

```
ERROR: Validation failed: 1 change(s) have validation failures
  Checksum mismatch for changeset 'NNN_description.yaml::NNN::author'
```

**Fix:**
1. **Do NOT delete the changeset** from the database.
2. Revert the changeset file to its original content (check git: `git show HEAD:path/to/changeset.yaml`).
3. Add a new changeset for the intended fix.
4. Restart the application.

> **Golden rule**: Never edit a Liquibase changeset after it has been applied to any environment.

### 9.6 Hibernate Schema Validation Failure

**Cause:** An entity field exists in code but not in the database (migration was not applied).

```
ERROR: Schema-validation: missing column [xxx] in table [yyy]
```

**Fix:**
```bash
# Check pending changesets
grep -r "xxx" hlm-backend/src/main/resources/db/changelog/

# If a changeset was added but not applied:
# Restart the application — Liquibase will apply it on startup

# If the changeset is missing entirely:
# Create a new additive changeset adding the column
# Never add columns directly via SQL — always through Liquibase
```

### 9.7 Testcontainers / Docker Failure in IT Tests

```
Could not find a valid Docker environment
```

**Fix:**
```bash
# Verify Docker is running
docker info

# If permission issue
sudo usermod -aG docker $USER
# Log out and back in

# Run integration tests
cd hlm-backend && ./mvnw failsafe:integration-test failsafe:verify
```

### 9.8 JWT_SECRET Startup Failure

```
ERROR: JWT secret key is too short (actual: N, minimum: 32 characters)
```

**Fix:**
```bash
# Generate a valid secret
export JWT_SECRET=$(openssl rand -base64 48)
# Restart application
```

### 9.9 Outbox Messages Stuck as PENDING

```bash
# Check if scheduler is running
grep "OutboxScheduler" /var/log/hlm/application.log | tail -10

# If scheduler is disabled (test profile)
# application-test.yml: spring.task.scheduling.enabled=false
# Ensure production profile does not disable scheduling

# Manual trigger via dispatcher service (requires admin access or internal endpoint)
# OR restart the application to re-initialize the scheduler
```

### 9.10 Media Upload Failure

```
ERROR: MEDIA_STORAGE_DIR is not writable
```

**Fix:**
```bash
# Check directory exists and is writable
ls -la $MEDIA_STORAGE_DIR
chmod 755 $MEDIA_STORAGE_DIR
chown app-user:app-group $MEDIA_STORAGE_DIR
```

### 9.11 Portal Magic Link Not Working

| Symptom | Cause | Fix |
|---------|-------|-----|
| Link not received | `MAIL_HOST` not configured or Noop provider | Configure real SMTP or check logs for "NoopEmailSender" |
| Link expired | > 48 hours old | Request a new link |
| "Token already used" | Link clicked twice | Request a new link |
| 401 after clicking link | Token hash mismatch | Verify the full URL was not truncated by email client |

### 9.12 PDF Generation Failure

```
ERROR: ReservationPdfGenerationException: PDF rendering failed
```

**Fix:**
```bash
# Check for OOM
grep "OutOfMemoryError" /var/log/hlm/application.log | tail -5
# Fix: increase -Xmx (e.g., -Xmx1024m)

# Check for template rendering errors
grep "TemplateProcessingException\|ThymeleafException" /var/log/hlm/application.log | tail -10
# Fix: verify template exists at classpath:/templates/<name>.html

# Test PDF endpoint
curl -s -o /tmp/test.pdf -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/deposits/<deposit-id>/reservation.pdf
file /tmp/test.pdf
# Expected: PDF document, version ...
```

### 9.13 Spring Boot Startup Failure After `payment` + `payments` Merge

```
ConflictingBeanDefinitionException:
Annotation-specified bean name 'paymentScheduleController' ... conflicts with existing ...
```

**Cause:** both modules are loaded at the same time and some classes share the same simple name, so Spring generates duplicate default bean names.

**Known collisions:**
- `payment.api.PaymentScheduleController` vs `payments.api.PaymentScheduleController`
- `payment.service.PaymentScheduleService` vs `payments.service.PaymentScheduleService`
- `reminder.ReminderService` vs `payments.service.ReminderService`
- `reminder.ReminderScheduler` vs `payments.service.ReminderScheduler`

**Fix:** assign explicit bean names to one side (recommended: legacy v1 + payments reminder classes), for example:
- `@RestController("paymentV1ScheduleController")`
- `@Service("paymentV1ScheduleService")`
- `@Service("paymentsScheduleReminderService")`
- `@Component("paymentsScheduleReminderScheduler")`

**Verification:**
```bash
cd hlm-backend
./mvnw -DskipTests compile
./mvnw -DskipTests spring-boot:run
```
If this issue is fixed, Spring context initialization passes bean scanning and startup errors move to environment checks (DB connectivity, missing env vars, etc.).

---

## 10. Recovery and Rollback Procedures

### 10.1 JWT Secret Rotation

> **Impact:** All active sessions are immediately invalidated. All logged-in users must re-authenticate.

```bash
# Step 1: Generate new secret
NEW_SECRET=$(openssl rand -base64 48)
echo "New secret: $NEW_SECRET"

# Step 2: Update environment variable in target environment
# (Vault, k8s secret, systemd env file — depends on deployment)
export JWT_SECRET="$NEW_SECRET"

# Step 3: Restart the application
systemctl restart hlm-backend

# Step 4: Verify health
curl -f http://localhost:8080/actuator/health

# Step 5: Inform users — all must re-login
```

### 10.2 Rollback a Liquibase Migration

> Liquibase in this project uses `additive-only` changesets. Rollbacks must be manual.

```bash
# Option A: Revert data/schema with a new changeset
# Create a new NNN_rollback_xxx.yaml changeset that reverts the change
# (e.g., DROP COLUMN, DELETE data, etc.)

# Option B: Point-in-time restore from database backup
# 1. Stop the application (no writes during restore)
# 2. Restore database to backup
# 3. Ensure Liquibase changelog is consistent with restored state
# 4. Restart application
```

### 10.3 Application Rollback

```bash
# Deploy previous JAR version
java $JAVA_OPTS -jar hlm-backend-<previous-version>.jar

# Note: If new changesets were applied by the newer version,
# those must be manually reverted before running the older version
# (Hibernate validate will fail on unexpected columns)
```

### 10.4 Reset Stuck Outbox Messages

```bash
# Reset FAILED messages to PENDING for retry
psql $DB_URL -c "
UPDATE outbound_message
SET status = 'PENDING',
    retry_count = 0,
    next_retry_at = now(),
    error_message = NULL
WHERE status = 'FAILED'
AND id IN (SELECT id FROM outbound_message WHERE status='FAILED' LIMIT 100);
"
# WARNING: May cause duplicate delivery. Use only when provider was down and messages were not sent.
```

### 10.5 Unlock Stuck Portal Token

```bash
# If a token is corrupted or needs manual invalidation
psql $DB_URL -c "
UPDATE portal_token
SET used_at = now()
WHERE contact_id = '<contact-uuid>'
AND used_at IS NULL;
"
# This invalidates all active magic links for the contact
```

---

## 11. Maintenance Tasks

### 11.1 Adding a Liquibase Changeset (Regular Task)

```bash
# 1. Find the next sequence number
ls hlm-backend/src/main/resources/db/changelog/ | sort | tail -5

# 2. Create a new changeset file
# Example: 028_add_property_floor_count.yaml

# 3. Add include to master
vi hlm-backend/src/main/resources/db/changelog/db.changelog-master.yaml
# Add: - include: file: changelog/028_add_property_floor_count.yaml

# 4. Test locally
cd hlm-backend && ./mvnw spring-boot:run
# Verify Liquibase applies the changeset on startup

# 5. Run full test suite
./mvnw test && ./mvnw failsafe:integration-test failsafe:verify
```

### 11.2 Secret Rotation Schedule (Recommended)

| Secret | Rotation Frequency | Method |
|--------|-------------------|--------|
| `JWT_SECRET` | Every 90 days or after personnel change | Generate new, restart app, inform users |
| `DB_PASSWORD` | Every 90 days | Update DB + env var + restart |
| `SNYK_TOKEN` | On expiry or team member departure | GitHub Secrets UI |
| `MAIL_PASSWORD` | Per SMTP provider policy | Update env var + restart |

### 11.3 Database Maintenance

```bash
# Vacuum analyze (run during low-traffic windows)
psql $DB_URL -c "VACUUM ANALYZE;"

# Check table sizes
psql $DB_URL -c "
SELECT tablename, pg_size_pretty(pg_total_relation_size(tablename::regclass)) AS size
FROM pg_tables WHERE schemaname='public'
ORDER BY pg_total_relation_size(tablename::regclass) DESC;"

# Check for long-running queries
psql $DB_URL -c "
SELECT pid, now() - pg_stat_activity.query_start AS duration, query
FROM pg_stat_activity
WHERE (now() - pg_stat_activity.query_start) > interval '5 minutes';"
```

### 11.4 Outbox Cleanup

```bash
# Archive/delete old SENT messages (after retention period)
psql $DB_URL -c "
DELETE FROM outbound_message
WHERE status = 'SENT'
AND created_at < now() - interval '30 days';"

# Report on FAILED messages needing attention
psql $DB_URL -c "
SELECT channel, count(*), min(created_at), max(created_at)
FROM outbound_message
WHERE status = 'FAILED'
GROUP BY channel;"
```

### 11.5 Payment v1 Deprecation Monitoring

```bash
# Run migration scan to find frontend references
./scripts/find-payment-v1-references.sh

# Generate v1 usage report
./scripts/report-payment-v1-usage.sh

# Check v1 traffic markers in logs
grep "payment_v1_endpoint_called" /var/log/hlm/application.log \
  | awk '{print $NF}' | sort | uniq -c | sort -rn
```

> v1 payment endpoints are sunset on **2026-12-31**. See `docs/v2/payment-v1-retirement-plan.v2.md`.

### 11.6 Audit Log Review

```bash
# Check recent audit events for a tenant
psql $DB_URL -c "
SELECT event_type, user_id, created_at, details
FROM audit_log
WHERE tenant_id = '<tenant-uuid>'
ORDER BY created_at DESC
LIMIT 50;"
```

---

## 12. Known Issues and Limitations

| ID | Issue | Impact | Workaround / Plan |
|----|-------|--------|------------------|
| L-001 | Local media storage not suitable for multi-node | Can't scale horizontally without shared storage | Use `MEDIA_STORAGE_DIR` on a network mount, or implement cloud `MediaStorageService` bean |
| L-002 | PDF generation is synchronous, in-memory | Large PDFs may cause OOM | Increase `-Xmx`; future: async via outbox |
| L-003 | Payment v1 API (`payment/`) and v2 (`payments/`) coexist | Confusion about which to use | v1 is deprecated, sunset 2026-12-31; always target v2 |
| L-004 | Caffeine cache is node-local | In multi-node: cache inconsistency for `userSecurityCache` | Keep single-node or accept eventual invalidation lag |
| L-005 | No ESLint configured on frontend | TypeScript/style issues not caught in CI | Run `ng add @angular-eslint/schematics` |
| L-006 | Noop email/SMS providers in dev | No real messages sent | Configure SMTP/Twilio in production |
| L-007 | No automated release pipeline | Manual build + deploy required | Consider GitHub Actions release workflow |
| L-008 | GHAS not enabled | CodeQL + GitHub Dependency Review not available | Snyk covers SAST and OSS scanning |

---

## 13. Operational Best Practices

### 13.1 Security

- **Never log tokens, passwords, or `Authorization` header values.**
- JWT secret must be ≥ 32 characters; rotate on a schedule and after personnel changes.
- All tenant IDs must come from the JWT `tid` claim — never trust client-provided tenant IDs.
- Portal tokens (magic links) are single-use; never extend a used token manually.
- In production: disable `/swagger-ui/**` and `/v3/api-docs/**` or restrict to internal networks.

### 13.2 Database

- **Never edit applied Liquibase changesets.** Always add new, additive changesets.
- Always test new changesets locally before deploying to staging/production.
- Back up the database before applying migrations in production.
- After a failed Liquibase migration, investigate before restarting — a partial migration may leave the database in an inconsistent state.

### 13.3 Outbox Reliability

- Monitor `FAILED` messages daily; investigate `error_message` before manual reset.
- Resetting `FAILED` → `PENDING` may cause duplicate email/SMS delivery — communicate risk to affected users.
- Do not disable the outbox scheduler in production; it is controlled by `spring.task.scheduling.enabled`.

### 13.4 Payment v1 Migration

- New development must target `payments/` v2 endpoints only.
- Do not add new features to the `payment/` v1 package.
- Use `scripts/find-payment-v1-references.sh` to track migration progress.
- v1 sunset date: **2026-12-31**.

### 13.5 CI / Release

- Merge to `main` only when all CI jobs are green.
- After merge to `main`, Snyk `monitor` snapshots dependencies — verify in Snyk dashboard.
- Before each production release, run the full integration test suite locally.
- Snyk runs weekly (Monday 07:00 UTC) — review new vulnerability alerts promptly.

---

## 14. References

### Internal Documentation

| Document | Path | Purpose |
|----------|------|---------|
| Project Overview | `docs/00_OVERVIEW.md` | Mission, layout, domain glossary |
| Architecture | `docs/01_ARCHITECTURE.md` | C4 diagrams, request flow, package map |
| Developer Guide | `docs/05_DEV_GUIDE.md` | Local setup, testing, conventions |
| Release & Deploy | `docs/07_RELEASE_AND_DEPLOY.md` | CI workflows, deployment notes |
| Onboarding Course | `docs/08_ONBOARDING_COURSE.md` | 5-day learning course |
| Functional Spec | `docs/specs/Functional_Spec.md` | Business rules, roles, workflows |
| Technical Spec | `docs/specs/Technical_Spec.md` | Architecture, API conventions, data model |
| API Catalog | `docs/v2/api.v2.md` | All API endpoints |
| API Quickstart | `docs/v2/api-quickstart.v2.md` | curl examples |
| v2 Overview | `docs/v2/00_OVERVIEW.v2.md` | Current system overview |
| Payment v1 Retirement | `docs/v2/payment-v1-retirement-plan.v2.md` | Migration plan |
| Architecture Context | `context/ARCHITECTURE.md` | Compact LLM reference |
| Domain Rules | `context/DOMAIN_RULES.md` | Business rules reference |
| Security Baseline | `context/SECURITY_BASELINE.md` | Security controls |
| Conventions | `context/CONVENTIONS.md` | Code conventions |
| Commands | `context/COMMANDS.md` | All runnable commands |
| Open Points | `docs/_OPEN_POINTS.md` | Known gaps backlog |

### API Endpoints Quick Reference

| Action | Method | Path | Auth |
|--------|--------|------|------|
| Login | POST | `/auth/login` | None |
| Verify identity | GET | `/auth/me` | CRM JWT |
| Health | GET | `/actuator/health` | None |
| Swagger UI | GET | `/swagger-ui/index.html` | None (restrict in prod) |
| Request portal link | POST | `/api/portal/auth/request-link` | None |
| Verify portal token | GET | `/api/portal/auth/verify` | None |
| Portal contracts | GET | `/api/portal/contracts` | Portal JWT |

### Seed Account (Development Only)

| Field | Value |
|-------|-------|
| Tenant key | `acme` |
| Tenant ID | `11111111-1111-1111-1111-111111111111` |
| Admin email | `admin@acme.com` |
| Admin password | `Admin123!` |
| Admin user ID | `22222222-2222-2222-2222-222222222222` |
