# Known Issues & Resolved Bugs

Tracks production-affecting issues, their root cause, and resolution status.  
Format: **[RESOLVED]** / **[OPEN]** / **[WONTFIX]**

---

## RESOLVED

### DNS_NXDOMAIN — Invitation emails bouncing
- **Symptom**: New user invitation emails fail with DNS resolution error.
- **Root cause**: `FRONTEND_BASE_URL` defaulted to `yem-hlm.pages.dev` (non-existent domain). The correct Cloudflare Workers URL is `yem-hlm.youssouf-mehdi.workers.dev`.
- **Fix**: Updated `application.yml` default for both `FRONTEND_BASE_URL` and `PORTAL_BASE_URL` to `workers.dev`.
- **Commit**: Wave 8 infrastructure fix.

### Activation link 404 after clicking
- **Symptom**: Clicking the activation link in the email leads to a 404 or blank page.
- **Root cause**: Frontend activation route read token from **URL path** (`/activation/:token`) but the component actually read from **query param** (`?token=`). Angular's router could not match the path.
- **Fix**: E2E tests corrected to use `/activation?token=...`. Confirmed the component always used query params.
- **Commit**: Wave 10 E2E fix.

### `POST /auth/invitation/{token}/activer` — Account not activated
- **Symptom**: Activation form submission returns success but user cannot login.
- **Root cause**: `@TransactionalEventListener` on the email sending listener used `BEFORE_COMMIT` phase. The portal token was not yet committed when the listener fired, causing FK violation or stale read on subsequent login.
- **Fix**: Changed all email listeners to `@TransactionalEventListener(TransactionPhase.AFTER_COMMIT)`.
- **Commit**: Wave 4 production hardening.

### Login rate-limit — All requests attributed to wrong email
- **Symptom**: `LoginRateLimitIT` failed; all login attempts blocked after first attempt regardless of email.
- **Root cause**: `String.formatted()` with template `"email": "%s"` and two arguments `.formatted(societeKey, email)` used `societeKey` as the email value (extra arg silently ignored).
- **Fix**: Corrected format string to include both placeholders.
- **Commit**: IT test bug fix (Phase 8 CI hardening).

### `@Transactional` on IT test classes — FK violation
- **Symptom**: Integration tests fail with FK constraint violation (500) instead of expected 201.
- **Root cause**: `AuditEventListener` uses `Propagation.REQUIRES_NEW`, opening a separate DB connection that cannot see uncommitted data from the test's outer transaction.
- **Fix**: Removed `@Transactional` from all IT test classes. Use unique email UIDs per test.
- **Status**: Documented in CLAUDE.md pitfalls. All IT tests fixed.

### nginx — `host not found in upstream` at container startup
- **Symptom**: `hlm-frontend` container fails to start; nginx logs `[emerg] host not found in upstream "hlm-backend"`.
- **Root cause**: `proxy_pass http://hlm-backend/` resolves DNS at config-load time. If the backend container isn't registered in Docker DNS yet, nginx crashes.
- **Fix**: Added `resolver 127.0.0.11 valid=30s; set $backend http://hlm-backend:8080;` and `proxy_pass $backend;` to nginx config.
- **Commit**: INF-2 (Phase 8).

### Playwright E2E — `page.request` calls hit Python SPA server (501) in CI
- **Symptom**: E2E tests using `page.request.post('/api/...')` receive 501 or HTML response in CI.
- **Root cause**: Playwright's `baseURL` is `http://localhost:4200` (Python static server). The Python server only handles GET. In CI the backend is at port 8080 and no proxy exists.
- **Fix**: Added `PLAYWRIGHT_API_BASE=http://localhost:8080` env var in CI workflow. All `page.request` calls in specs now use `${API_BASE}/api/...`.
- **Commit**: Wave 10 E2E CI fix.

### Playwright E2E — activation.spec.ts, pipeline.spec.ts not executed
- **Symptom**: `npx playwright test --list` showed only 15 tests (5 files); `activation.spec.ts` and `pipeline.spec.ts` were silently skipped.
- **Root cause**: `playwright.config.ts` projects only matched `auth|superadmin` and `contacts|tasks` patterns.
- **Fix**: Added `activation-tests`, `pipeline-tests`, and `portal-tests` projects to config.
- **Commit**: Wave 10 P2 fix.

### R2 EU object storage — 403 on media upload
- **Symptom**: Media upload to Cloudflare R2 EU bucket returns 403 Forbidden.
- **Root cause**: Using global R2 endpoint (`<account>.r2.cloudflarestorage.com`) for EU bucket. EU buckets require the regional endpoint.
- **Fix**: Set `MEDIA_OBJECT_STORAGE_ENDPOINT=https://<account-id>.eu.r2.cloudflarestorage.com` in production environment.
- **Status**: Documented in `application.yml` comments and CLAUDE.md infrastructure notes.

### Angular `chokidar` conflict — `npm ci` fails in Docker
- **Symptom**: `npm ci` during Docker image build fails with conflicting peer dependencies for `chokidar`.
- **Root cause**: `@angular/cli@19.2.x` and `@angular-eslint/*@19.0.x` pull different `chokidar` versions.
- **Fix**: Aligned all `@angular-eslint/*` packages to `^19.2.0` to match `@angular/cli`.
- **Commit**: CI-6 (Phase 8).

### Jasmine 5.x spy property — `TypeError: Cannot redefine property`
- **Symptom**: Angular unit test fails with `TypeError: Cannot redefine property: authToken`.
- **Root cause**: `jasmine.createSpyObj(name, methods, { prop: val })` sets `configurable: false` on properties. Subsequent `Object.defineProperty` call throws.
- **Fix**: Use a mutable closure variable (`let authToken = '...'`) and pass only methods array to `createSpyObj`.
- **Commit**: CI-7 (Phase 8). Documented in CLAUDE.md.

### StrongPassword validator — test passwords too short
- **Symptom**: IT tests fail with 400 on password fields.
- **Root cause**: `StrongPassword` requires `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z\d]).{12,}$`. Test passwords like `Admin123!` (9 chars) did not meet minimum length.
- **Fix**: Updated all IT test passwords to 12+ chars compliant values.
- **Commit**: Phase 8 CI fixes. New compliant passwords documented in CLAUDE.md.

---

## OPEN

### Portal — no "send another link" after session expiry
- **Symptom**: Buyer whose 2h portal session has expired sees a blank or error page rather than a clear "your session expired, request a new link" message.
- **Workaround**: Navigate to `/portal/login` directly.
- **Priority**: Low (buyer can still get a new link manually).

### Outbox dispatcher — occasional duplicate emails in multi-node setup
- **Symptom**: In rare cases where two nodes start simultaneously, the outbox dispatcher may poll the same message before ShedLock kicks in.
- **Root cause**: `lockAtLeastFor = "PT0.2S"` is very short; window exists between row selection and lock acquisition.
- **Mitigation**: ShedLock `lockAtLeastFor` is conservative. The outbox uses `FOR UPDATE SKIP LOCKED` which prevents double-processing at DB level.
- **Priority**: Very low (mitigated by SKIP LOCKED at DB layer).

---

## WONTFIX

### OpenAPI docs accessible to authenticated CRM users
- **Rationale**: Swagger UI is gated behind CRM roles (not public). Internal staff access is acceptable for a B2B SaaS. Public exposure would be a security issue — but that is not the case.

### HSTS not set in local dev profile
- **Rationale**: HSTS on `http://localhost` causes browser issues. Production profile enables HSTS via Spring Security headers. Local dev intentionally excluded.
