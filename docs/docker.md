# Docker & Container Infrastructure

This guide covers running the YEM SaaS Platform in Docker for local development and production.

---

## Services

| Service | Image | Port | Purpose |
|---|---|---|---|
| `postgres` | postgres:16-alpine | 5432 | Primary database |
| `redis` | redis:7-alpine | 6379 | Distributed cache (prod) |
| `minio` | minio/minio | 9000/9001 | S3-compatible object storage |
| `hlm-backend` | Built locally or from registry | 8080 | Spring Boot API |
| `hlm-frontend` | Built locally or from registry | 80 | Angular + Nginx |
| `mailhog` | mailhog/mailhog (dev only) | 1025/8025 | Email capture |

---

## Local Development

### Prerequisites

- Docker 24+ with Docker Compose v2
- `docker compose version` should show `v2.x`

### Quick Start

```bash
# 1. Copy and fill in secrets
cp .env.example .env
# Edit .env — at minimum set JWT_SECRET

# 2. Build and start (docker-compose.override.yml applied automatically)
docker compose up -d

# 3. Watch logs
docker compose logs -f hlm-backend

# 4. Verify
curl http://localhost:8080/actuator/health
```

The override file (`docker-compose.override.yml`) adds:
- **Mailhog** on port 8025 (web UI) / 1025 (SMTP) for capturing outbound emails
- **JDWP remote debug** on port 5005 for the backend
- `REDIS_ENABLED=false` by default — uses Caffeine in-process cache

### Angular Live Reload

For frontend development with Hot Module Replacement, skip the Docker frontend container and run Angular CLI directly:

```bash
# Comment out or remove the hlm-frontend service in docker-compose.override.yml, then:
cd hlm-frontend && npm start
# Angular dev server on http://localhost:4200 proxies API calls to :8080
```

### Email Testing (Mailhog)

1. Start the stack — Mailhog starts automatically in the override.
2. Open http://localhost:8025 to see captured emails.
3. The backend is configured to send via `mailhog:1025` (no auth, no TLS).

---

## Production Deployment

```bash
# Pull pre-built images and start with production hardening
docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  up -d
```

### Production `.env` Requirements

```env
JWT_SECRET=<min 32 chars random string>
POSTGRES_PASSWORD=<strong password>
REDIS_PASSWORD=<strong password>
IMAGE_TAG=v1.2.3       # must match the tag pushed to registry
REGISTRY=ghcr.io/yem1449
```

### Production Differences (docker-compose.prod.yml)

- Postgres and Redis ports are **not exposed** externally
- Resource `limits` and `reservations` are set per service
- `REDIS_ENABLED=true` — distributed cache active
- `MEDIA_OBJECT_STORAGE_ENABLED=true` — media stored in MinIO (or swap endpoint for OVH/Scaleway)
- `FORWARD_HEADERS_STRATEGY=FRAMEWORK` — trust Nginx X-Forwarded headers
- Images are pulled from registry (`build: !reset null`)

---

## Building Images

### Manually

```bash
# Backend
docker build -t hlm-backend:local ./hlm-backend

# Frontend
docker build -t hlm-frontend:local ./hlm-frontend
```

### Via GitHub Actions

The `docker-build.yml` workflow:
1. Triggers on push to `main`, any `v*.*.*` tag, or PRs touching backend/frontend.
2. Builds both images with layer caching (GHA cache).
3. Pushes to `ghcr.io/yem1449/hlm-backend` and `hlm-frontend` on main/tags.
4. Runs a Compose smoke test on PRs.

---

## Distributed Cache (Redis)

By default the backend uses **Caffeine** (in-process cache) — no Redis dependency.

Enable Redis for multi-instance deployments:

```env
REDIS_ENABLED=true
REDIS_HOST=redis          # or your managed Redis host
REDIS_PORT=6379
REDIS_PASSWORD=secret
```

Cache names and TTLs are identical in both modes:

| Cache | TTL |
|---|---|
| `userSecurity` | 60 s |
| `commercialDashboardSummary` | 30 s |
| `cashDashboard` | 60 s |
| `receivablesDashboard` | 30 s |

---

## Object Storage (S3-compatible protocol)

By default, property media is stored on the local filesystem (`./uploads`).

Enable S3-compatible object storage by setting `MEDIA_OBJECT_STORAGE_ENABLED=true` and pointing to a provider:

```env
# MinIO (included in docker-compose.yml — path-style addressing required, already enabled)
MEDIA_OBJECT_STORAGE_ENABLED=true
MEDIA_OBJECT_STORAGE_ENDPOINT=http://minio:9000
MEDIA_OBJECT_STORAGE_REGION=us-east-1
MEDIA_OBJECT_STORAGE_BUCKET=hlm-media
MEDIA_OBJECT_STORAGE_ACCESS_KEY=minioadmin
MEDIA_OBJECT_STORAGE_SECRET_KEY=minioadmin
```

The same configuration pattern works for OVH, Scaleway, Hetzner, and other providers —
just change the endpoint URL and region. See [object-storage.md](object-storage.md) for
provider-specific instructions and exact endpoint values.

MinIO console (when using docker-compose): http://localhost:9001 (credentials from `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` in `.env`).

| Deployment | Storage Mode | Notes |
|---|---|---|
| Local dev | `MEDIA_OBJECT_STORAGE_ENABLED=false` (local disk) | `MEDIA_STORAGE_DIR=./uploads` |
| Docker (single node) | Mount a volume | `MEDIA_STORAGE_DIR=/data/uploads` |
| Docker (multi-instance) | `MEDIA_OBJECT_STORAGE_ENABLED=true` + MinIO container | Wired in `docker-compose.yml` |
| OVH / Scaleway / Hetzner | `MEDIA_OBJECT_STORAGE_ENABLED=true` + provider endpoint | See `object-storage.md` |
| AWS S3 | `MEDIA_OBJECT_STORAGE_ENABLED=true`, leave `ENDPOINT` blank | SDK auto-resolves |

---

## Observability (OpenTelemetry)

The backend exports traces via OTLP HTTP (port 4318).

### Local OTel Collector

```bash
docker run --rm -p 4317:4317 -p 4318:4318 \
  -v $(pwd)/otel-collector-config.yml:/etc/otelcol/config.yaml \
  otel/opentelemetry-collector-contrib:latest
```

### Configuration

```env
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318  # or your managed backend
OTEL_SAMPLE_RATE=1.0   # 0.0–1.0 (1.0 = trace everything, lower in high-traffic prod)
```

Traces are exported to any OTLP-compatible backend: Jaeger, Grafana Tempo, Honeycomb, Datadog, New Relic.

---

## Smoke Test

Verify a running stack:

```bash
./scripts/smoke-stack.sh
# Defaults: backend=localhost:8080, frontend=localhost, tenant=acme, admin@acme.com
```

Custom endpoints:

```bash
./scripts/smoke-stack.sh \
  --backend-url  http://your-server:8080 \
  --frontend-url https://your-server \
  --email admin@acme.com \
  --password 'YourPassword!'
```

---

## Useful Commands

```bash
# View all container status
docker compose ps

# Follow backend logs
docker compose logs -f hlm-backend

# Open a psql shell in the DB container
docker compose exec postgres psql -U hlm_user -d hlm

# Connect to Redis CLI
docker compose exec redis redis-cli

# Hard reset (remove volumes — destroys all data)
docker compose down -v

# Rebuild a single service
docker compose build hlm-backend && docker compose up -d hlm-backend
```
