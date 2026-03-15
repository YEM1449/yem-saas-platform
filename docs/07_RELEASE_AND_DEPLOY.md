# 07 — Release and Deploy

## CI Pipeline Overview

All workflows are in [`.github/workflows/`](../.github/workflows/). Each is path-scoped to avoid unnecessary runs.

### Workflow Summary

| Workflow | Trigger | Jobs | Gate |
|----------|---------|------|------|
| `backend-ci.yml` | push/PR on `hlm-backend/**` | Unit tests → Package → Integration tests | Blocks merge on failure |
| `frontend-ci.yml` | push/PR on `hlm-frontend/**` | Tests (headless) → Build | Blocks merge on failure |
| `docker-build.yml` | push/PR on backend/frontend/compose | Build images → Push to ghcr.io → Compose smoke test (PR) | Blocks on build or smoke failure |
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
  4. Run ITs: ./mvnw -B -ntp failsafe:integration-test failsafe:verify
  5. Upload failsafe-reports artifact
```

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

ESLint configured via `@angular-eslint`. Lint runs in `frontend-ci.yml` before build step (`npm run lint`).

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
- Optional enforcement mode: set repository variable `SECRET_SCAN_ENFORCE=true` to fail the workflow when findings exist.
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

**Container-based release (recommended — Sprint 4 onwards):**

1. Ensure all CI jobs are green on the PR.
2. Merge to `main` — `docker-build.yml` automatically builds and pushes images to `ghcr.io`.
3. Tag the release: `git tag v1.2.3 && git push origin v1.2.3` — versioned images published.
4. On the target host:
   ```bash
   IMAGE_TAG=v1.2.3 docker compose \
     -f docker-compose.yml \
     -f docker-compose.prod.yml \
     up -d
   ```
5. Liquibase runs migrations automatically on backend startup.
6. Run smoke test: `./scripts/smoke-stack.sh --backend-url https://your-server`.

**JAR-based release (legacy / non-Docker):**
1. Ensure all CI jobs are green on the PR.
2. Merge to `main`.
3. Build the production JAR: `cd hlm-backend && ./mvnw -B -DskipTests package`.
4. Build the Angular bundle: `cd hlm-frontend && npm run build`.
5. Deploy JAR + static assets to your target environment.

## Production Deployment Notes

### Media Storage (OP-007)

The `media/` package uses `LocalFileMediaStorage` by default. Enable S3-compatible object storage for production:

```env
MEDIA_OBJECT_STORAGE_ENABLED=true
MEDIA_OBJECT_STORAGE_ENDPOINT=                   # blank = AWS S3; set for OVH/Scaleway/Hetzner/MinIO
MEDIA_OBJECT_STORAGE_ACCESS_KEY=your-access-key
MEDIA_OBJECT_STORAGE_SECRET_KEY=your-secret-key
MEDIA_OBJECT_STORAGE_BUCKET=hlm-media
MEDIA_OBJECT_STORAGE_REGION=eu-west-1
```

`ObjectStorageMediaStorage` creates the bucket automatically on startup if it does not exist.
See [object-storage.md](object-storage.md) for provider-specific setup (OVH, Scaleway, Hetzner, Cloudflare R2, MinIO, AWS S3).

| Deployment | Storage Mode | Notes |
|------------|-------------|-------|
| Single-node / dev | `MEDIA_OBJECT_STORAGE_ENABLED=false` (local disk) | `MEDIA_STORAGE_DIR=./uploads` |
| Docker (single node) | Mount a volume | `MEDIA_STORAGE_DIR=/data/uploads` |
| Docker (multi-instance) | `MEDIA_OBJECT_STORAGE_ENABLED=true` + MinIO container | MinIO in `docker-compose.yml` |
| OVH / Scaleway / Hetzner | `MEDIA_OBJECT_STORAGE_ENABLED=true` + provider endpoint | See `object-storage.md` |
| AWS S3 | `MEDIA_OBJECT_STORAGE_ENABLED=true`, leave `ENDPOINT` blank | SDK auto-resolves |

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

## Production Deployment Checklist

### HTTPS / TLS
- [ ] Nginx installed and `nginx/nginx.conf` deployed (replace `app.example.com` with real domain)
- [ ] Let's Encrypt certificate obtained: `certbot certonly --standalone -d <DOMAIN>`
- [ ] `PORTAL_BASE_URL` set to `https://<DOMAIN>`
- [ ] `FORWARD_HEADERS_STRATEGY=FRAMEWORK` in production environment
- [ ] Port 8080 bound to `127.0.0.1` only (not exposed to internet)
- [ ] Port 443 open in firewall
- [ ] HSTS preload verified: `curl -I https://<DOMAIN>/actuator/health | grep Strict`

### Email (SMTP)
- [ ] `EMAIL_HOST`, `EMAIL_USER`, `EMAIL_PASSWORD`, `EMAIL_FROM` set
- [ ] Send a test portal magic link and verify receipt

### SMS (Twilio)
- [ ] `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM` set
- [ ] Send a test SMS via `POST /api/messages`

### Redis (distributed cache — multi-instance)
- [ ] `REDIS_ENABLED=true` set in production `.env`
- [ ] `REDIS_PASSWORD` set to a strong password
- [ ] Redis container or managed Redis endpoint reachable from backend

### Object Storage (S3-compatible — OVH / Scaleway / Hetzner / MinIO)
- [ ] `MEDIA_OBJECT_STORAGE_ENABLED=true` set in production `.env`
- [ ] `MEDIA_OBJECT_STORAGE_ACCESS_KEY`, `MEDIA_OBJECT_STORAGE_SECRET_KEY`, `MEDIA_OBJECT_STORAGE_BUCKET` set
- [ ] Provider endpoint set (`MEDIA_OBJECT_STORAGE_ENDPOINT`) — leave blank only for AWS S3
- [ ] Bucket created (or `ObjectStorageMediaStorage` will create it on startup for MinIO)

### GDPR / Data Protection
- [ ] `privacy-notice.txt` updated: `[NOM DE LA SOCIÉTÉ]` and `[EMAIL DPO]` replaced
- [ ] `DataRetentionScheduler` running: check log at 02:00 for `[RETENTION]`
- [ ] DPO contact reachable at configured email

### Scheduled tasks
- [ ] Verify `PortalTokenCleanupScheduler` running: check log at 03:00 for `[PORTAL-CLEANUP]`
- [ ] Verify overdue payment scheduler running: check log at 06:00
- [ ] Verify GDPR retention sweep running: check log at 02:00 for `[RETENTION]`

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
