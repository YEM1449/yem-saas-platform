# Troubleshooting Guide

Symptom → Cause → Fix. Organized by failure domain.

---

## Backend Startup Issues

### JWT_SECRET NotBlank exception at startup

**Symptom**: Application fails to start with `ConstraintViolationException: JWT_SECRET must not be blank` or similar.

**Cause**: `JwtProperties` uses `@Validated` + `@NotBlank`. The application performs fail-fast validation on startup.

**Fix**: Set a 32-character or longer secret:
```env
JWT_SECRET=your-super-secret-key-at-least-32-chars-long
```
In `.env` for Docker Compose; in CI workflow `env:` block for GitHub Actions.

---

### Liquibase migration fails with "relation X does not exist"

**Symptom**: `LiquibaseException: relation "some_table" does not exist` during startup.

**Cause**: A changeset attempts to reference a table that was dropped in an earlier changeset (e.g., the v1 `payment` tables were removed; changeset references them).

**Fix**: Check the changeset history in `db.changelog-master.yaml`. Dropped tables cannot be altered. Create a new additive changeset targeting the active table names instead.

---

### Backend crash-loops in Docker Compose

**Symptom**: `docker compose ps` shows `hlm-backend` as unhealthy after repeated restart attempts.

**Cause**: Most commonly: Liquibase migration failure, or the backend started before PostgreSQL was ready.

**Fix**:
```bash
docker compose logs hlm-backend --tail=50
```
Look for the root exception. Ensure PostgreSQL is healthy before backend starts:
```yaml
depends_on:
  postgres:
    condition: service_healthy
```

---

### ExceptionInInitializerError / Could not find valid Docker environment

**Symptom**: Integration tests fail on startup with `ExceptionInInitializerError` or `Could not find valid Docker environment`.

**Cause**: Testcontainers cannot locate the Docker socket. On WSL2 with Docker Desktop, the socket path differs from the standard `/var/run/docker.sock`.

**Fix** (local WSL2):
```bash
export DOCKER_HOST=unix:///mnt/wsl/docker-desktop/shared-sockets/host-services/docker.proxy.sock
```
This is set automatically on `ubuntu-latest` GitHub Actions runners.

---

## Authentication Issues

### Login returns 401

**Symptom**: `POST /auth/login` returns `401 Unauthorized`.

**Possible causes and fixes**:

1. Wrong credentials — verify email and password. Seed password is `Admin123!Secure` (not the old `Admin123!`).
2. Account locked — check `app_user.locked_until`. Run: `SELECT locked_until FROM app_user WHERE email = 'user@example.com';`
3. No active societe membership — `AuthService.login()` requires at least one active `app_user_societe` record. Check: `SELECT * FROM app_user_societe WHERE user_id = '<uuid>' AND actif = true;`

---

### Login returns requiresSocieteSelection: true

**Symptom**: Login responds with HTTP 200 but body includes `"requiresSocieteSelection": true`.

**Cause**: The user has multiple active societe memberships. A partial token is returned instead of a full JWT.

**Fix**: The client must call `POST /auth/switch-societe` with `{societeId}` and the partial token. The backend then writes the final `hlm_auth` cookie-backed session for the selected societe.

---

### API returns 401 after successful login (partial token used on wrong route)

**Symptom**: Requests to `/api/**` (non-portal) return 401 immediately after login.

**Cause**: The partial token (`partial=true`, no `sid`) is being used on a CRM route that requires a full token with `sid`.

**Fix**: Complete the societe selection flow: `POST /auth/switch-societe` → receive full token → use full token for subsequent API calls.

---

### API returns 401 after role change or password reset

**Symptom**: Previously valid token starts returning 401.

**Cause**: `app_user.token_version` was incremented (role change, disable, password reset, membership change). The old JWT `tv` claim is now below the stored version.

**Fix**: User must re-login to obtain a new token with the current `token_version`.

---

### LoginRateLimitIT always uses email "acme" instead of the test email

**Symptom**: Rate-limit tests fail because all requests hit the same rate-limit bucket regardless of the email used.

**Cause**: `String.formatted()` silently drops extra arguments:
```java
// WRONG: tenant is used as the email value; email is silently dropped
String body = "{\"email\": \"%s\"}".formatted(tenant, email);
```

**Fix**: Count `%s` placeholders and ensure they match the number of arguments exactly:
```java
String body = "{\"email\": \"%s\", \"tenantKey\": \"%s\"}".formatted(email, tenant);
```

---

## Integration Test Issues

### FK violation in IT test (500 instead of expected 201)

**Symptom**: IT tests that create resources return HTTP 500 instead of 201. Stack trace shows FK violation from `AuditEventListener`.

**Cause**: `@Transactional` is present on the IT test class. `AuditEventListener` uses `Propagation.REQUIRES_NEW`, which opens a new database connection. That new connection cannot see the uncommitted test transaction data, causing FK failures.

**Fix**: Remove `@Transactional` from the test class. Prevent data collisions with UID-suffixed emails instead of rollback:
```java
@BeforeEach
void setUp() {
    uid = UUID.randomUUID().toString().substring(0, 8);
    String email = "testuser+" + uid + "@acme.com";
}
```

---

### uk_user_email constraint violation in @BeforeEach

**Symptom**: Second test method in a class fails with `uk_user_email` unique constraint violation.

**Cause**: Hardcoded emails in `@BeforeEach` are reused on each invocation. Without rollback, the first test's user record persists and the second test tries to insert with the same email.

**Fix**: Generate a unique `uid` per `@BeforeEach` invocation and append it to all email addresses.

---

### 401 on data setup calls in IT tests

**Symptom**: Setup calls (`POST /api/projects`, `POST /api/properties`) return 401.

**Cause**: Using the wrong bearer token for setup (e.g., agent token instead of admin token).

**Fix**: Always use `adminBearer` for all data setup calls. Only switch to the role under test for the actual operation being tested:
```java
// Setup with adminBearer
mvc.perform(post("/api/projects").header("Authorization", "Bearer " + adminBearer)...);

// Test the actual operation with agentBearer
mvc.perform(get("/api/projects").header("Authorization", "Bearer " + agentBearer)...)
   .andExpect(status().isOk()); // or isForbidden() for a negative test
```

---

### 404 on admin operation targeting a test user

**Symptom**: `PUT /api/users/{userId}/role` or similar returns 404 even though the user was created.

**Cause**: `AdminUserService.findUserInSociete()` calls `appUserSocieteRepository.findByIdUserIdAndIdSocieteId(userId, societeId)` and throws `UserNotFoundException` (404) when no membership record exists. Creating the user via `userRepository.save(user)` alone is not sufficient.

**Fix**: Also create the `AppUserSociete` membership record:
```java
userRepository.save(user);
appUserSocieteRepository.save(
    new AppUserSociete(new AppUserSocieteId(user.getId(), societeId), "AGENT")
);
```

---

### IT test uses wrong ErrorResponse field

**Symptom**: `jsonPath("$.errorCode").value("QUOTA_UTILISATEURS_ATTEINT")` fails; path not found.

**Cause**: The `ErrorResponse` record serializes the `ErrorCode code` field as `$.code`, not `$.errorCode`.

**Fix**:
```java
// Wrong
.andExpect(jsonPath("$.errorCode").value("QUOTA_UTILISATEURS_ATTEINT"))

// Correct
.andExpect(jsonPath("$.code").value("QUOTA_UTILISATEURS_ATTEINT"))
```

---

## Frontend / Angular Issues

### npm ci fails with chokidar conflict

**Symptom**: `npm ci` fails with unresolvable peer dependency or `chokidar` version conflict.

**Cause**: `@angular-eslint/*` version does not match the `@angular/cli` minor version. Mismatched versions pull conflicting `chokidar` transitive dependencies.

**Fix**: All `@angular-eslint/*` packages must match the `@angular/cli` minor version. For `@angular/cli@19.2.x`:
```json
"@angular-eslint/builder": "^19.2.0",
"@angular-eslint/eslint-plugin": "^19.2.0",
"@angular-eslint/eslint-plugin-template": "^19.2.0"
```
Update all `@angular-eslint/*` packages to the same minor version in the same commit.

---

### Jasmine spy TypeError: Cannot redefine property

**Symptom**: Jasmine unit test throws `TypeError: Cannot redefine property: url` (or similar property name).

**Cause**: `jasmine.createSpyObj(name, methods, { prop: val })` creates the property with `configurable: false`. Calling `Object.defineProperty` to override it throws `TypeError`.

**Fix**: Do not pass properties to `createSpyObj`. Use a closure variable:
```typescript
// Wrong
const routerSpy = jasmine.createSpyObj('Router', ['navigate'], { url: '/initial' });
Object.defineProperty(routerSpy, 'url', { get: () => currentUrl }); // throws

// Correct
let currentUrl = '/initial';
const routerSpy = jasmine.createSpyObj('Router', ['navigate']);
Object.defineProperty(routerSpy, 'url', { get: () => currentUrl, configurable: true });
```

---

### E2E: wrong button clicked / unexpected navigation

**Symptom**: Playwright E2E test clicks the wrong button (e.g., "Annuler" instead of "Créer").

**Cause**: The selector `button[type="submit"]` matches all buttons without an explicit `type` attribute (HTML default is `type="submit"`). Playwright returns the first match in DOM order, which is often "Cancel" before "Save".

**Fix**: Add `data-testid` to all interactive elements and use it as the primary selector:
```typescript
// Wrong
await page.click('button:has-text("Créer"), button[type="submit"]');

// Correct
await page.click('[data-testid="save-button"]');
```

---

### ChromeHeadlessCI crashes in unit tests

**Symptom**: `npm test` fails in CI with Chrome sandbox errors.

**Cause**: `karma.conf.js` is missing the `ChromeHeadlessCI` custom launcher with `--no-sandbox` flags.

**Fix**:
```javascript
customLaunchers: {
  ChromeHeadlessCI: {
    base: 'ChromeHeadless',
    flags: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage']
  }
},
browsers: ['ChromeHeadlessCI']
```

---

### Angular guard spec fails with injection context error

**Symptom**: Guard unit test fails with `EnvironmentInjector` or injection context error.

**Cause**: Functional guards require an injection context to resolve `inject()` calls.

**Fix**: Use `TestBed.runInInjectionContext`:
```typescript
it('should block unauthenticated access', () => {
  const result = TestBed.runInInjectionContext(() =>
    authGuard(activatedRouteSnapshot, routerStateSnapshot)
  );
  expect(result).toBeFalse();
});
```

---

## Docker / Infrastructure Issues

### nginx: host not found in upstream at startup

**Symptom**: nginx container starts and immediately crashes with `[emerg] host not found in upstream "hlm-backend"`.

**Cause**: nginx resolves `proxy_pass http://hlm-backend/` at config-load time. If Docker DNS hasn't registered the `hlm-backend` container when nginx starts, DNS resolution fails.

**Fix**: Use the Docker embedded resolver with a `$backend` variable:
```nginx
resolver 127.0.0.11 valid=30s;
set $backend http://hlm-backend:8080;
location /api/ {
    proxy_pass $backend;
}
```
Using a variable defers DNS resolution to request time instead of config-load time.

---

### Backend unhealthy after Docker Compose up

**Symptom**: `docker compose up --wait` times out with backend still unhealthy.

**Cause**: Liquibase migration failure. The backend container is running but crashing or stuck.

**Fix**:
```bash
docker compose logs hlm-backend --tail=100
```
Look for Liquibase or database connection errors. Fix the migration issue and restart:
```bash
docker compose restart hlm-backend
```

---

### Backend threads leaking on shutdown

**Symptom**: Application shutdown takes >30 seconds; thread dump shows `BatchSpanProcessor` threads still running.

**Cause**: `opentelemetry-exporter-otlp` is on the classpath. It creates a `BatchSpanProcessor` background thread that does not clean up properly on context close.

**Fix**: Remove `opentelemetry-exporter-otlp` from `pom.xml`. Keep only `micrometer-tracing-bridge-otel`. Use the `OTEL_ENABLED=true` + `OTEL_EXPORTER_OTLP_ENDPOINT` approach which uses the Micrometer bridge without the leaking exporter.

---

## Business Logic Issues

### Messages not delivered (email/SMS)

**Symptom**: Outbox messages remain in `PENDING` status; no emails or SMS delivered.

**Causes and fixes**:

1. `EMAIL_HOST` not configured → `NoopEmailSender` active → messages logged but not sent. Set `EMAIL_HOST` to a real SMTP server.
2. Outbox dispatcher not polling → check `OutboundDispatcherService` logs; ensure `spring.task.scheduling.enabled` is not `false` in the active profile.
3. Credentials wrong → check `SmtpEmailSender` for `MailAuthenticationException` in logs.
4. `next_retry_at` in the future → messages are in backoff; check `SELECT status, next_retry_at, retry_count FROM outbound_message ORDER BY created_at DESC;`

---

### Portal magic-link not working

**Symptom**: Portal magic link returns an error on verification.

**Possible causes**:

| Cause | Fix |
|---|---|
| Token already used | Token is one-time use; request a new link |
| Token expired | 48-hour TTL elapsed; request a new link |
| Wrong `PORTAL_BASE_URL` | The link in the email points to the wrong host; set `PORTAL_BASE_URL` to the correct public URL |
| `EMAIL_HOST` not set | Magic-link email was never sent (NoopEmailSender) |

---

### Property create fails with validation error for VILLA

**Symptom**: `POST /api/properties` returns 400 with validation errors for VILLA type properties.

**Cause**: `PropertyType.VILLA` requires all four fields: `surfaceAreaSqm`, `landAreaSqm`, `bedrooms`, `bathrooms`. Any null field triggers a 400.

**Required fields by type**:

| PropertyType | Required fields |
|---|---|
| `VILLA` | `surfaceAreaSqm`, `landAreaSqm`, `bedrooms`, `bathrooms` |
| `APPARTEMENT` | `surfaceAreaSqm`, `bedrooms`, `bathrooms`, `floorNumber` |

Note: `APARTMENT` is not a valid enum value. Use `APPARTEMENT`.

---

### Role assignment fails with 500 (CHECK constraint violation)

**Symptom**: Admin attempts to assign a role and receives HTTP 500. Database logs show `chk_societe_role` constraint violation.

**Cause**: `AppUserSociete.role` is being set to a value with the `ROLE_` prefix (e.g., `ROLE_ADMIN`) instead of the short form (`ADMIN`).

**Fix**: Ensure `AdminUserService.toSocieteRole()` strips the prefix before persisting. Verify no code path passes the full `ROLE_` prefixed value to `AppUserSociete.setRole()`.

---

### Quota exceeded error (409)

**Symptom**: `POST /api/mon-espace/utilisateurs/invitations` or `POST /api/users` returns 409 with `QUOTA_UTILISATEURS_ATTEINT`.

**Cause**: The societe has reached its `max_utilisateurs` limit. `QuotaService.enforceUserQuota(societeId)` compares `Societe.maxUtilisateurs` against `AppUserSocieteRepository.countBySocieteIdAndActifTrue()`.

**Fix options**:
1. Deactivate inactive members (`AppUserSociete.actif = false`) to free up quota.
2. Have a `SUPER_ADMIN` increase `societe.max_utilisateurs` via `PUT /api/admin/societes/{id}`.

---

## CI Pipeline Issues

### Backend CI fails on integration-test job but unit tests pass

**Symptom**: `unit-and-package` job succeeds; `integration-test` job fails.

**Cause**: Integration tests require Docker (Testcontainers). The `integration-test` job in `backend-ci.yml` uses Docker-in-Docker or a Docker-enabled runner; the `unit-and-package` job does not.

**Fix**: Verify the `integration-test` job specifies a runner with Docker available (`ubuntu-latest` has Docker). Check `DOCKER_HOST` environment variable is set if using a custom runner.

---

### E2E: login rate-limit races

**Symptom**: E2E tests fail intermittently with 429 responses on the login step.

**Cause**: Playwright is running with more than one worker. Parallel workers all attempt login simultaneously, hitting the rate-limit threshold.

**Fix**: `playwright.config.ts` must have `workers: 1`. This is non-negotiable for this test suite.

---

### E2E: wrong seed credentials

**Symptom**: E2E login test fails with 401 using `admin@acme.com / Admin123!`.

**Cause**: The password was updated in changeset 030. The old `Admin123!` (9 chars) fails the `StrongPassword` validator (minimum 12 chars). The correct password is `Admin123!Secure`.

Old `superadmin@hlm.io / SuperSecret123!` credentials are also wrong — replaced in changeset 046.

**Current seed credentials**:

| Account | Email | Password |
|---|---|---|
| CRM Admin | `admin@acme.com` | `Admin123!Secure` |
| Super Admin | `superadmin@yourcompany.com` | `YourSecure2026!` |
