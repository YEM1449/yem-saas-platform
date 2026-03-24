# Troubleshooting Guide — Engineer Guide

This guide covers the most common problems encountered during local development, CI, and production operation, with diagnosis steps and fixes.

## Table of Contents

1. [Application Startup Failures](#application-startup-failures)
2. [Authentication and JWT Issues](#authentication-and-jwt-issues)
3. [Database and Liquibase Issues](#database-and-liquibase-issues)
4. [CORS Errors](#cors-errors)
5. [Container and Docker Issues](#container-and-docker-issues)
6. [Test Failures](#test-failures)
7. [Performance Issues](#performance-issues)
8. [Common HTTP Error Responses](#common-http-error-responses)

---

## Application Startup Failures

### "JWT_SECRET must be set" / `@NotBlank` validation failure

**Symptom:**
```
Caused by: jakarta.validation.ConstraintViolationException: ...
  Field error in object 'jwtProperties' on field 'secret': must not be blank
```

**Cause:** `JWT_SECRET` environment variable is not set or is empty.

**Fix:**
1. Ensure `.env` exists at the project root.
2. Add `JWT_SECRET=my-very-long-dev-secret-replace-me-32chars` (minimum 32 characters).
3. Restart the application.

### "Port 8080 already in use"

**Symptom:**
```
Caused by: java.net.BindException: Address already in use
```

**Fix:**
```bash
# Find the process using port 8080
lsof -ti:8080

# Kill it
kill -9 $(lsof -ti:8080)

# Or change the backend port
SERVER_PORT=8081 ./mvnw spring-boot:run
```

### "Liquibase checksum mismatch"

**Symptom:**
```
Caused by: liquibase.exception.ValidationFailedException: Validation Failed:
     1 change sets check sum
     db/changelog/001-create-core-tables.yaml::001::author was: ...
```

**Cause:** A committed changeset was modified after it was applied to the database.

**Fix (development only — destroys all data):**
```bash
docker compose down -v
docker compose up -d
```

**Fix (never edit applied changesets):** Revert the changeset to its original content and create a new changeset for the intended change.

### "HikariPool-1 - Connection is not available"

**Symptom:**
```
HikariPool-1 - Connection is not available, request timed out after 30000ms
```

**Cause:** Database is not reachable or all pool connections are held by long-running queries.

**Fix:**
1. Verify PostgreSQL is running: `docker compose ps hlm-postgres`.
2. Check `DB_URL`, `DB_USER`, `DB_PASSWORD` in `.env`.
3. Check for long-running queries:
   ```sql
   SELECT pid, now() - pg_stat_activity.query_start AS duration, query
   FROM pg_stat_activity
   WHERE state = 'active' AND now() - pg_stat_activity.query_start > interval '30 seconds';
   ```

---

## Authentication and JWT Issues

### Login returns 401 "Bad credentials"

**Cause:** Wrong password, or BCrypt hash mismatch in seed data.

**Fix:**
- Correct seed password is `Admin123!Secure` (changed in changeset `030`).
- Old password `Admin123!` (9 chars) fails the `@StrongPassword` minimum-12-character rule.

### Login returns 401 "ACCOUNT_LOCKED"

**Cause:** More than `LOCKOUT_MAX_ATTEMPTS` (default 5) failed logins within the lockout window.

**Fix:**
```sql
UPDATE app_user SET failed_login_attempts = 0, locked_until = NULL
WHERE email = 'admin@acme.com';
```

Or wait `LOCKOUT_DURATION_MINUTES` (default 15 min) for the lockout to expire.

### 401 on every API call after login

**Cause possibilities:**
1. JWT has expired (`JWT_TTL_SECONDS` default is 3600 s).
2. Token version mismatch — user was disabled or role changed.
3. Wrong `Authorization: Bearer {token}` header format.
4. `JWT_SECRET` changed between token generation and validation.

**Diagnosis:**
```bash
# Decode the JWT (base64 decode each part)
echo "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOi..." | cut -d. -f2 | base64 -d
```

Check `exp` claim (Unix timestamp). If expired, log in again.

### 403 Forbidden on an endpoint

**Cause:** User role does not have permission for the operation.

**Fix:** Check the `@PreAuthorize` annotation on the controller method. Ensure the user has the required role. To verify:
```sql
SELECT email, role FROM app_user WHERE email = 'user@example.com';
```

---

## Database and Liquibase Issues

### "relation does not exist"

**Symptom:**
```
org.postgresql.util.PSQLException: ERROR: relation "contact" does not exist
```

**Cause:** Liquibase has not applied the changeset that creates the table.

**Fix:**
```bash
docker compose logs hlm-backend | grep -i liquibase
```

Look for changeset application logs. If changesets are not applying, check:
1. Liquibase can reach the database.
2. `db.changelog-master.yaml` includes the new changeset file.
3. The changeset YAML syntax is valid.

### JPQL type inference error

**Symptom:**
```
org.postgresql.util.PSQLException: ERROR: could not determine data type of parameter $1
```

**Cause:** Nullable `LocalDateTime` parameter in a JPQL query using `:param IS NULL`.

**Fix:** Replace with `CAST(:param AS LocalDateTime) IS NULL`. See `pitfall_jpql_null_localdatetime.md`.

### "could not serialize access due to concurrent update"

**Symptom:**
```
org.postgresql.util.PSQLException: ERROR: could not serialize access due to concurrent update
```

**Cause:** Two concurrent transactions trying to update the same row (e.g., two agents creating a deposit on the same property).

**Fix:** `ReservationService.create()` uses a pessimistic write lock. Ensure deposit creation goes through the reservation check first. If seeing this in production, ensure all property status updates go through the service layer with proper locking.

---

## CORS Errors

### "Access to XMLHttpRequest blocked by CORS policy"

**Symptom in browser console:**
```
Access to XMLHttpRequest at 'http://localhost:8080/api/contacts' from origin
'http://localhost:4200' has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header
```

**Fix:**
1. Ensure `CORS_ALLOWED_ORIGINS` in `.env` includes the frontend origin.
2. For local dev, add BOTH: `http://localhost:4200,http://127.0.0.1:4200`.
3. The `application-local.yml` profile sets this automatically when using the `local` Spring profile.

**Important:** On Windows, browsers often use `127.0.0.1` even when typing `localhost` in the URL bar. Both origins must be in the allowlist.

### Preflight OPTIONS request failing

CORS preflight OPTIONS requests are automatically permitted for all paths in `SecurityConfig` (`permitAll` for `OPTIONS /**`). If OPTIONS is returning 403, verify that `SecurityConfig.securityFilterChain()` has the OPTIONS permitAll rule before the authenticated URL patterns.

---

## Container and Docker Issues

### Backend container exits immediately

**Diagnosis:**
```bash
docker logs hlm-backend
```

Common causes:
- **`JWT_SECRET` missing** — See startup failure section above.
- **Database not ready** — `depends_on: condition: service_healthy` should prevent this, but check `docker compose ps` to verify `hlm-postgres` is `healthy`.
- **Port conflict** — Port 8080 in use on the host.

### "Cannot connect to Docker" (Testcontainers)

**Symptom:**
```
Could not find a valid Docker environment
```

**Fix:**
1. Run `docker info` to confirm Docker is running.
2. On Windows with WSL2: ensure Docker Desktop has WSL integration enabled (Settings → Resources → WSL Integration).
3. If Docker Desktop uses a named pipe (`npipe:////./pipe/...`) rather than the standard Unix socket, export the WSL2 proxy socket:
   ```bash
   export DOCKER_HOST=unix:///mnt/wsl/docker-desktop/shared-sockets/host-services/docker.proxy.sock
   ```
4. On Linux: ensure your user is in the `docker` group: `sudo usermod -aG docker $USER` then re-login.

### MinIO container unhealthy

**Diagnosis:**
```bash
docker logs hlm-minio
```

**Fix:** MinIO requires a writable data volume. Ensure `minio_data` volume exists:
```bash
docker volume ls | grep minio
```

If not found, recreate with `docker compose up -d hlm-minio`.

---

## Test Failures

### "constructor does not match" in IT tests

**Symptom:**
```
no suitable constructor found for type ...
```

**Cause:** A domain entity constructor was changed (added a field) but IT test data builders were not updated.

**Fix:** Update the constructor call in the test to match the new signature. All constructor parameters must be provided.

### IT test leaves stale data (intermittent failures)

**Cause:** Tests share the same database and a previous test left data that affects the next test.

**Fix:**
1. **Do NOT use `@Transactional` on IT test classes.** `AuditEventListener` uses `Propagation.REQUIRES_NEW`, opening a separate DB connection that cannot see uncommitted data → FK violation (returns 500 instead of 201). This has burned us before.
2. Use unique email/name suffixes per test run:
   ```java
   String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
   String email = "admin-" + uid + "@test.local";
   ```
3. Delete specific entities in `@AfterEach` if uniqueness alone is not sufficient.

### "No qualifying bean of type 'SmtpEmailSender'"

**Cause:** `SmtpEmailSender` is not registered in the test context because `EMAIL_HOST` is blank. The `NoopEmailSender` is registered instead.

**This is expected behavior.** Tests should inject `EmailSender` (the interface), not `SmtpEmailSender` directly.

### Frontend test fails with "Angular CLI version mismatch"

**Fix:** Align `@angular/cli` and `@angular/core` versions in `package.json`:
```bash
cd hlm-frontend
npm install @angular/cli@19 @angular/core@19 --save-dev
npm install
```

---

## Performance Issues

### Slow dashboard queries

**Diagnosis:**
1. Enable SQL logging temporarily: set `show-sql: true` in `application.yml` (revert after diagnosis).
2. Check `EXPLAIN ANALYZE` output for missing indexes.
3. Verify dashboard cache is working: `GET /api/dashboard/commercial` should return fast on second call.

**Fix options:**
- Ensure composite indexes on `(tenant_id, project_id)` and `(tenant_id, status)` exist.
- Reduce cache TTL if freshness is required, or increase it for performance.
- Use the optional `agentId` / `projectId` params to scope queries rather than computing tenant-wide aggregates.

### HikariCP pool exhaustion

**Symptom:** Long connection wait times in logs.

**Fix:** Increase `maximum-pool-size` in `application.yml` (default 20). Monitor with `GET /actuator/metrics/hikaricp.connections.active`.

---

## Common HTTP Error Responses

| Status | Code | Typical Cause |
|--------|------|--------------|
| 400 | `VALIDATION_ERROR` | Request body fails bean validation |
| 401 | `UNAUTHORIZED` | Missing or expired JWT |
| 401 | `ACCOUNT_LOCKED` | Too many failed logins |
| 403 | `FORBIDDEN` | Insufficient role for operation |
| 404 | `*_NOT_FOUND` | Entity does not exist in this tenant |
| 409 | `*_ALREADY_EXISTS` | Duplicate email, property already sold, etc. |
| 409 | `GDPR_ERASURE_BLOCKED` | Signed contract prevents erasure |
| 422 | `INVALID_STATUS_TRANSITION` | State machine transition not allowed |
| 429 | `RATE_LIMIT_EXCEEDED` | Login or GDPR rate limit hit |
| 500 | `INTERNAL_ERROR` | Unhandled exception — check logs |

For 500 errors, the `X-Correlation-ID` header in the response can be used to search application logs:
```bash
docker compose logs hlm-backend | grep "{correlation-id}"
```
