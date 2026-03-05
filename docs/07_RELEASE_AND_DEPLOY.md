# 07 — Release and Deploy

## CI Pipeline Overview

All workflows are in [`.github/workflows/`](../.github/workflows/). Each is path-scoped to avoid unnecessary runs.

### Workflow Summary

| Workflow | Trigger | Jobs | Gate |
|----------|---------|------|------|
| `backend-ci.yml` | push/PR on `hlm-backend/**` | Unit tests → Package → Integration tests | Blocks merge on failure |
| `frontend-ci.yml` | push/PR on `hlm-frontend/**` | Tests (headless) → Build | Blocks merge on failure |
| `snyk.yml` | push/PR + weekly Monday 07:00 UTC | OSS dep scan + Code SAST | Blocks on HIGH+ vulns (if SNYK_TOKEN set) |
| `secret-scan.yml` | push/PR on backend/frontend | Pattern-based secret audit | Audit-only (never fails build) |

> **Note — GHAS workflows removed**: Both `codeql.yml` and `dependency-review.yml` were removed because GitHub Advanced Security (GHAS) is not enabled on this private repository and cannot run without it.
> - **SAST replacement**: Snyk Code (`snyk.yml` code job) provides equivalent SAST coverage.
> - **Dependency review replacement**: Snyk OSS (`snyk.yml` open-source job) scans `pom.xml` and `package.json` for known vulnerabilities on every push/PR.
> - **To restore**: Enable GHAS in repository Settings → Security → Code scanning, then restore the removed workflows from git history.

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

Weekly Snyk cron scan is enabled: Monday 07:00 UTC.

### Removed GHAS Workflows

- `dependency-review.yml` removed (requires GHAS + Dependency Graph on private repositories).
- `codeql.yml` removed (requires GHAS on private repositories).
- Current replacements: Snyk OSS + Snyk Code in `snyk.yml`.

### Secret Scan (`secret-scan.yml`)

- Lightweight grep-based scan for common secret patterns (AWS keys, GH tokens, API keys, RSA keys)
- **Audit-only**: always exits 0. Findings uploaded as artifact.
- For enforcement-grade secret scanning, enable GitHub Advanced Security.

## GitHub Actions Security

| Control | Status |
|---------|--------|
| Workflow-level `permissions: contents: read` | ✅ All workflows |
| Job-level `security-events: write` (Snyk Code SARIF upload) | ✅ Scoped per-job |
| Snyk token guard (`if: secrets.SNYK_TOKEN != ''`) | ✅ Present |
| `concurrency` with `cancel-in-progress: true` | ✅ All workflows |
| `actions/checkout@v4` | ✅ Pinned major |
| `actions/setup-java@v4` | ✅ Pinned major |
| `actions/setup-node@v4` | ✅ Pinned major |
| `actions/upload-artifact@v4` | ✅ Pinned major |
| Snyk CLI pinned to major (`snyk@1`) | ✅ Present |
| `timeout-minutes` on all jobs | ✅ Present |

## Caching Strategy

| Workflow | Cache |
|----------|-------|
| backend-ci (unit + IT) | Maven: `actions/setup-java cache: maven` |
| frontend-ci | npm: `actions/setup-node cache: npm` |
| snyk (open-source job) | Maven + npm (both cached) |

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

## Production Deployment Notes

### Media Storage (OP-007)

The `media/` package uses `LocalFileMediaStorage` by default (files written to `MEDIA_STORAGE_DIR`, default `./uploads`). This is **not suitable for horizontal scaling or cloud deployments**.

**Cloud swap**: `MediaStorageService` is an interface. Provide a `@Primary @Bean` implementing it:

```java
// Example: swap to S3 without touching existing code
@Primary
@Bean
public MediaStorageService s3MediaStorageService(AmazonS3 s3, ...) {
    return new S3MediaStorageService(s3, bucketName);
}
```

| Deployment | `MEDIA_STORAGE_DIR` | Notes |
|------------|---------------------|-------|
| Single-node / dev | `./uploads` (default) | Files on local disk |
| Docker | Mount a volume | `MEDIA_STORAGE_DIR=/data/uploads` |
| Cloud (S3/GCS) | N/A — swap bean | Implement `MediaStorageService` for your provider |

Required env vars for local storage:
```bash
MEDIA_STORAGE_DIR=/var/hlm/uploads   # writable directory for the JVM process
MEDIA_MAX_FILE_SIZE=10485760         # bytes; default 10 MB
```

### PDF Generation Memory (OP-008)

PDF generation (`DocumentGenerationService`) uses OpenHtmlToPDF with `PdfRendererBuilder.useFastMode()`. Rendering is **synchronous and in-memory** (`ByteArrayOutputStream`). Each PDF call holds the full document bytes in heap during rendering.

**Recommended JVM settings for production** (add to startup script or Dockerfile):
```bash
JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
# Increase Xmx if PDF generation causes OOM (e.g., large contracts with many pages)
```

**Observable symptoms of PDF OOM**:
- `java.lang.OutOfMemoryError: Java heap space` in logs during `POST .../pdf`
- Long GC pauses before PDF endpoint responses

**Future consideration**: For high-traffic PDF generation, offload to an async worker (outbox pattern: queue a PDF job, return job ID, deliver via email when ready).

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
