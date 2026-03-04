# 07 — Release and Deploy

## CI Pipeline Overview

All workflows are in [`.github/workflows/`](../.github/workflows/). Each is path-scoped to avoid unnecessary runs.

### Workflow Summary

| Workflow | Trigger | Jobs | Gate |
|----------|---------|------|------|
| `backend-ci.yml` | push/PR on `hlm-backend/**` | Unit tests → Package → Integration tests | Blocks merge on failure |
| `frontend-ci.yml` | push/PR on `hlm-frontend/**` | Tests (headless) → Build | Blocks merge on failure |
| `snyk.yml` | push/PR (code changes) | OSS dep scan + Code SAST | Blocks on HIGH+ vulns (if SNYK_TOKEN set) |
| `dependency-review.yml` | PR on pom.xml/package.json | GitHub dep review | Blocks on HIGH severity |
| `codeql.yml` | push/PR on backend/frontend | CodeQL SAST (Java + TypeScript) | Posts to Security tab |
| `secret-scan.yml` | push/PR on backend/frontend | Pattern-based secret audit | Audit-only (never fails build) |

### Backend CI (`backend-ci.yml`)

```
Job: unit-and-package  (timeout: 25m)
  1. Checkout
  2. Setup Java 21 (Temurin) + Maven cache
  3. Run unit tests: ./mvnw -B -ntp test
  4. Package: ./mvnw -B -ntp -DskipTests package
  5. Upload surefire-reports artifact

Job: integration-test  (timeout: 30m, needs: unit-and-package)
  1. Checkout
  2. Setup Java 21 + Maven cache
  3. Verify Docker (for Testcontainers)
  4. Run ITs: ./mvnw -B -ntp failsafe:integration-test
  5. Upload failsafe-reports artifact
```

> [OPEN POINT OP-001] Consider adding `failsafe:verify` after `failsafe:integration-test` to ensure IT failures actually fail the job.

### Frontend CI (`frontend-ci.yml`)

```
Job: test-and-build  (timeout: 20m)
  1. Checkout
  2. Setup Node 18 + npm cache
  3. npm ci
  4. Tests: npm test -- --watch=false --browsers=ChromeHeadless --code-coverage
  5. Build: npm run build
  6. Upload coverage artifact
```

> [OPEN POINT OP-006] No ESLint/lint gate. Consider adding if `@angular-eslint` is configured.

### Snyk (`snyk.yml`)

```
Job: open-source  (timeout: 20m, requires SNYK_TOKEN)
  - Scans backend Maven POM + frontend package.json
  - Threshold: --severity-threshold=high
  - On push to main: snyk monitor (snapshots to Snyk dashboard)

Job: code  (timeout: 20m, requires SNYK_TOKEN)
  - Snyk Code SAST
  - Exports SARIF → uploads to GitHub Security tab

Job: missing-token
  - Emits a warning if SNYK_TOKEN is not configured (no failure)
```

Required secrets: `SNYK_TOKEN` (required), `SNYK_ORG` (optional).

> [OPEN POINT OP-004] No scheduled cron scan. New CVEs between code changes would not be caught automatically. Consider a weekly schedule trigger.

### Dependency Review (`dependency-review.yml`)

- Runs on PRs that modify `pom.xml`, `package.json`, or `package-lock.json`.
- Uses `actions/dependency-review-action@v4`.
- Fails on HIGH severity dependency additions.

### CodeQL (`codeql.yml`)

- Matrix: `java-kotlin` + `javascript-typescript`
- Runs `security-and-quality` query suite
- Results posted to GitHub Security → Code scanning alerts

### Secret Scan (`secret-scan.yml`)

- Lightweight grep-based scan for common secret patterns (AWS keys, GH tokens, API keys, RSA keys)
- **Audit-only**: always exits 0. Findings uploaded as artifact.
- For enforcement-grade secret scanning, enable GitHub Advanced Security.

## GitHub Actions Security

| Control | Status |
|---------|--------|
| Workflow-level `permissions: contents: read` | ✅ All workflows |
| Job-level `security-events: write` (CodeQL, Snyk code) | ✅ Scoped per-job |
| `actions: read` (CodeQL) | ✅ Scoped per-job |
| `pull-requests: read` (dep review) | ✅ Scoped per-job |
| Snyk token guard (`if: secrets.SNYK_TOKEN != ''`) | ✅ Present |
| `concurrency` with `cancel-in-progress: true` | ✅ All workflows |
| `actions/checkout@v4` | ✅ Pinned major |
| `actions/setup-java@v4` | ✅ Pinned major |
| `actions/setup-node@v4` | ✅ Pinned major |
| `actions/upload-artifact@v4` | ✅ Pinned major |
| `actions/dependency-review-action@v4` | ✅ Pinned major |
| `github/codeql-action/init@v3` | ✅ Pinned major |
| Snyk CLI pinned to major (`snyk@1`) | ✅ Present |
| `timeout-minutes` on all jobs | ✅ Present |

## Caching Strategy

| Workflow | Cache |
|----------|-------|
| backend-ci (unit + IT) | Maven: `actions/setup-java cache: maven` |
| frontend-ci | npm: `actions/setup-node cache: npm` |
| snyk (open-source job) | Maven + npm (both cached) |
| codeql (java-kotlin) | Maven |

## Required Secrets

| Secret | Required | Used In |
|--------|----------|---------|
| `SNYK_TOKEN` | Yes (for Snyk) | `snyk.yml` |
| `SNYK_ORG` | Optional | `snyk.yml` |
| `GITHUB_TOKEN` | Auto-provided | All workflows |

## Release Process

> No automated release pipeline is currently configured. PRs merge to `main` via GitHub. Deployment is manual.

**Recommended release steps:**
1. Ensure all CI jobs are green on the PR.
2. Merge to `main` — Snyk monitor snapshots are captured automatically.
3. Build the production JAR: `cd hlm-backend && ./mvnw -B -DskipTests package`.
4. Build the Angular bundle: `cd hlm-frontend && npm run build`.
5. Deploy JAR + static assets to your target environment.
6. Liquibase runs migrations automatically on backend startup.

## .snyk Policy File

Location: `.snyk` (repo root)

```yaml
version: v1.25.0
ignore: {}
patch: {}
```

No ignores are currently configured. To ignore a vulnerability:
```yaml
ignore:
  SNYK-JAVA-XXXX-12345:
    - '*':
        reason: "False positive — patched in runtime environment"
        expires: '2026-12-31T00:00:00.000Z'
```

Always document the reason and set an expiry date for ignores.
