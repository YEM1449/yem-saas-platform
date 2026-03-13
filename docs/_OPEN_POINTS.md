# _OPEN_POINTS.md — Open Points Log

_Updated: 2026-03-05_

---

## RESOLVED

### [OP-001] ✅ RESOLVED — Integration Test CI — Failsafe verify
**Resolution**: Added `failsafe:verify` to `backend-ci.yml` integration-test job.  
`./mvnw -B -ntp failsafe:integration-test failsafe:verify`  
IT failures now correctly fail the CI job.

### [OP-003] ✅ ACCEPTED — Snyk Code `--severity-threshold` not supported
**Resolution**: Left as-is per recommendation. `snyk code test` exits non-zero on error; SARIF upload delivers findings to GitHub Security tab regardless of threshold. No action needed.

### [OP-004] ✅ RESOLVED — Scheduled Snyk Scan
**Resolution**: Added weekly cron trigger to `snyk.yml`: `0 7 * * 1` (Monday 07:00 UTC).  
New CVEs between code pushes are now caught automatically.

### [OP-005] ✅ ACCEPTED — Secret Scan Audit-Only Mode
**Resolution**: Kept audit-only by default per recommendation (pattern-based grep has false-positive risk). Added an optional enforcement switch (`SECRET_SCAN_ENFORCE=true`) to fail CI when findings exist. GitHub Advanced Security native secret scanning remains the enforcement-grade target when GHAS is enabled.

### [OP-006] ✅ DOCUMENTED — Frontend Lint Gate Missing
**Resolution**: `@angular-eslint` is not configured in `angular.json` or `package.json`. A lint CI step cannot be added until ESLint is set up. Setup steps documented in `docs/05_DEV_GUIDE.md`:
```bash
cd hlm-frontend && ng add @angular-eslint/schematics
```
Once configured, add `npm run lint` to `frontend-ci.yml` before the test step.

### [OP-007] ✅ DOCUMENTED — Media Storage Local vs Cloud
**Resolution**: `MediaStorageService` is already an interface with `LocalFileMediaStorage` as the default. The Javadoc on the interface explicitly states "Production swap: provide an `@Primary` S3-backed bean." Cloud swap pattern documented in:
- `docs/07_RELEASE_AND_DEPLOY.md` — Production Deployment Notes → Media Storage
- `context/ARCHITECTURE.md` — media/ Storage Architecture

Env vars: `MEDIA_STORAGE_DIR` (default `./uploads`), `MEDIA_MAX_FILE_SIZE` (default 10 MB).

### [OP-008] ✅ DOCUMENTED — PDF Generation Memory
**Resolution**: `DocumentGenerationService` already uses `useFastMode()`. Memory concern documented:
- `docs/07_RELEASE_AND_DEPLOY.md` — Production Deployment Notes → PDF Generation Memory
- `context/ARCHITECTURE.md` — PDF Generation section
Recommended JVM: `-Xmx512m`. Async PDF (future backlog) documented.

### [OP-009] ✅ RESOLVED — GitHub Advanced Security — CodeQL + Dependency Review
**Resolution**: `codeql.yml` removed — Snyk Code (`snyk.yml` code job) provides equivalent SAST without requiring GHAS.  
`dependency-review.yml` removed — the action cannot run without GHAS on this private repository and only consumed CI minutes with `continue-on-error: true`. Snyk OSS (`snyk.yml` open-source job) covers dependency vulnerability scanning.  
CI workflow table in `docs/07_RELEASE_AND_DEPLOY.md` updated.  
CI security gates in `context/SECURITY_BASELINE.md` updated.

### [OP-002] ✅ RESOLVED — payment/ vs payments/ Packages
**Context**: Two packages with overlapping payment responsibilities:
- `payment/` (v1): `/api/contracts/{id}/payment-schedule`, `/api/payment-calls`
- `payments/` (v2): `/api/contracts/{id}/schedule`, `/api/schedule-items`, cash dashboard

**Resolution**:
- Kept both routes temporarily for backward compatibility.
- Marked both `payment/api/PaymentScheduleController` and `payment/api/PaymentCallController` as `@Deprecated`.
- Added deprecation response headers (`Deprecation`, `Sunset`, `Warning`, `Link`) on all v1 payment endpoints to push clients toward v2.
- Added v1 usage telemetry for controlled migration:
  - metric `payment_v1_requests_total` (endpoint/method tags),
  - log marker `payment_v1_endpoint_called`.
- Added migration tooling:
  - `scripts/find-payment-v1-references.sh`
  - `scripts/report-payment-v1-usage.sh`
- Published complete runbook and communication templates in `docs/v2/payment-v1-retirement-plan.v2.md`.
- Updated architecture/context/API docs to reflect retirement execution plan.
