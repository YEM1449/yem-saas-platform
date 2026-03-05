# CI Audit And Recommendations

## Scope
- Audited workflows under `.github/workflows/`:
  - `backend-ci.yml`
  - `frontend-ci.yml`
  - `snyk.yml`
- Added security workflows:
  - `dependency-review.yml`
  - `codeql.yml`
  - `secret-scan.yml`

## Findings And Changes

### 1) Backend CI
- Finding:
  - Backend workflow executed tests and integration tests, but lacked explicit package validation.
  - Maven command flags were not optimized for deterministic CI logs (`-ntp` missing).
  - Test reports were uploaded only on failure.
- Change:
  - Added `workflow_dispatch` for manual execution.
  - Renamed main job to `unit-and-package`; it now runs:
    - `./mvnw -B -ntp test`
    - `./mvnw -B -ntp -DskipTests package`
  - Kept integration tests as a dedicated job with Testcontainers support.
  - Uploads Surefire and Failsafe reports with `if: always()` for better diagnostics.
  - Kept least privilege `contents: read`.

### 2) Frontend CI
- Finding:
  - Frontend workflow only validated build; no automated test/coverage signal.
- Change:
  - Added `workflow_dispatch`.
  - Consolidated into a deterministic `test-and-build` job:
    - `npm ci`
    - `npm test -- --watch=false --browsers=ChromeHeadless --code-coverage --progress=false`
    - `npm run build`
  - Uploads `coverage/` as artifact when present.
  - Kept lockfile-safe install and npm cache via `actions/setup-node`.

### 3) Snyk Workflow Hardening
- Finding:
  - Third-party action reference used `@master`.
  - Workflow required broad `security-events: write` at top-level.
  - PRs from forks would fail when `SNYK_TOKEN` is unavailable.
- Change:
  - Replaced action-based setup with CLI install pinned to major: `npm install -g snyk@1`.
  - Restricted `security-events: write` to Snyk Code job only (SARIF upload).
  - Added conditional job execution based on `SNYK_TOKEN` presence.
  - Added explicit warning job when token is missing.
  - Kept `snyk monitor` only on `push` to `main`.

### 4) Dependency Risk Control
- Finding:
  - No PR-time dependency policy gate.
- Change:
  - Added `dependency-review.yml` using `actions/dependency-review-action@v4`.
  - Runs only for dependency manifest/lockfile changes.
  - Fails on high-severity dependency issues.

### 5) Code Scanning
- Finding:
  - No first-party static analysis workflow in GitHub Security.
- Change:
  - Added `codeql.yml` for:
    - `java-kotlin` (backend compile step included)
    - `javascript-typescript` (frontend/source analysis)
  - Least privilege job permissions:
    - `actions: read`
    - `contents: read`
    - `security-events: write`

### 6) Secret Leak Prevention (Audit-Only)
- Finding:
  - No automated secret pattern checks in CI.
- Change:
  - Added `secret-scan.yml` audit workflow.
  - Runs a lightweight regex-based scan using `grep` for common credential/token patterns.
  - Uploads findings as artifact and emits warnings.
  - Non-blocking by design (`exit 0`) to avoid false-positive PR failures during baseline phase.

## Before/After Flow (High-Level)

### Before
- Backend: unit tests -> integration tests.
- Frontend: build only.
- Security: Snyk workflow only; setup via `@master`; no dependency review; no CodeQL; no secret-leak audit.

### After
- Backend CI:
  - Unit tests + package validation
  - Integration tests
  - Always-on report artifacts
- Frontend CI:
  - Unit tests (CI/headless) + coverage artifact
  - Production build validation
- Security CI:
  - Snyk OSS + Snyk Code (hardened permissions and fork-safe behavior)
  - PR dependency review gate
  - CodeQL analysis for Java + JS/TS
  - Audit-only secret pattern scanning

## Security Hardening Checklist Applied
- [x] Least privilege `GITHUB_TOKEN` at workflow/job levels.
- [x] No `pull_request_target` usage.
- [x] Path-scoped triggers to reduce irrelevant execution.
- [x] `concurrency` with `cancel-in-progress` to avoid stale runs.
- [x] Third-party `@master` usage removed from Snyk setup path.
- [x] Secrets not printed; Snyk jobs skipped safely when token missing.
- [x] Added dependency-risk gate for PRs.
- [x] Added first-party static analysis workflow (CodeQL).
- [x] Added non-blocking secret pattern scan workflow with artifact output.

## Local Equivalents (Same Checks)

### Backend
```bash
cd hlm-backend
chmod +x mvnw
./mvnw -B -ntp test
./mvnw -B -ntp -DskipTests package
./mvnw -B -ntp failsafe:integration-test
```

### Frontend
```bash
cd hlm-frontend
npm ci
npm test -- --watch=false --browsers=ChromeHeadless --code-coverage --progress=false
npm run build
```

### Snyk
```bash
npm install --global snyk@1
export SNYK_TOKEN="<your-token>"
# Optional:
export SNYK_ORG="<your-org-id-or-slug>"

snyk test --file=hlm-backend/pom.xml --package-manager=maven --severity-threshold=high
snyk test --file=hlm-frontend/package.json --package-manager=npm --severity-threshold=high
snyk code test
```

## [OPEN POINT] Items
- [OPEN POINT] Frontend lint/typecheck:
  - `hlm-frontend/package.json` currently has no `lint` script.
  - Decision needed: add ESLint configuration and include `npm run lint` in CI.
- [OPEN POINT] Backend coverage publishing:
  - `hlm-backend/pom.xml` does not currently configure JaCoCo reporting.
  - Decision needed: add JaCoCo plugin and publish XML/HTML coverage artifacts.
- [OPEN POINT] Secret scanning enforcement:
  - Current scan is intentionally lightweight and audit-only.
  - Decision needed: replace/augment with dedicated scanner (e.g., gitleaks) and move to blocking mode after baseline cleanup.
- [OPEN POINT] Branch protection:
  - Ensure required checks include:
    - Backend CI
    - Frontend CI
    - Dependency Review
    - CodeQL
    - Snyk Security (if token configured)
