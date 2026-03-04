# _OPEN_POINTS.md — Open Points Log

_Updated: 2026-03-04_

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
**Resolution**: Kept audit-only per recommendation. Pattern-based grep has false-positive risk; findings are uploaded as artifacts for manual review. GitHub Advanced Security native secret scanning is the enforcement-grade alternative when GHAS is enabled.

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
`dependency-review.yml` retained with `continue-on-error: true` — becomes a hard gate when GHAS is enabled.  
CI workflow table in `docs/07_RELEASE_AND_DEPLOY.md` updated.  
CI security gates in `context/SECURITY_BASELINE.md` updated.

---

## OPEN

### [OP-002] 📋 DOCUMENTED (no code change) — payment/ vs payments/ Packages
**Context**: Two packages with distinct but overlapping concerns:
- `payment/` — **v1 model**: PaymentSchedule (tranches), PaymentCall (Appel de Fonds PDF), payment recording. API: `/api/contracts/{id}/payment-schedule`, `/api/payment-calls`.
- `payments/` — **v2 model**: PaymentScheduleItem workflow (issue→send→cancel), Call-for-Funds PDF+reminders, CashDashboard. API: `/api/contracts/{id}/schedule`, `/api/schedule-items`, `/api/dashboard/commercial/cash`.

**Resolution chosen**: Option 2 — document distinct responsibilities (both serve active routes; merge requires significant refactoring risk with no immediate user-visible benefit).

**Documentation updated**: `docs/01_ARCHITECTURE.md`, `context/ARCHITECTURE.md`.

**Remaining risk**: A new engineer may find two `PaymentScheduleController` classes confusing. Recommend:
1. Add a `@Deprecated` annotation to `payment/api/PaymentScheduleController` if `payments/` fully supersedes it.
2. Or ensure the API docs (`docs/api.md`) clearly distinguish the two endpoints.

**Owner**: Backend team — when a dedicated refactoring sprint is planned.

