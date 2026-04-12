# Production Deployment Guide

**Audience**: infrastructure engineers and platform operators
**Updated**: 2026-03-25

---

## 1. Prerequisites

| Requirement | Notes |
| --- | --- |
| Docker 24+ and Compose v2 | Required |
| PostgreSQL 16 instance | Can be the compose-managed one or external managed DB |
| Reverse proxy with TLS | Nginx or Traefik; Let's Encrypt for certificate |
| Domain name | For CORS, FRONTEND_BASE_URL, PORTAL_BASE_URL |
| SMTP credentials | Optional — fall back to no-op without them |

---

## 2. Critical Variables for Production

These variables **must** differ from their defaults in any non-disposable environment:

| Variable | Production requirement |
| --- | --- |
| `JWT_SECRET` | Unique, 32+ random chars; generate with `openssl rand -base64 48` |
| `DB_URL` | Point to your production PostgreSQL instance |
| `DB_USER` | Use a dedicated DB user (not `postgres`) |
| `DB_PASSWORD` | Strong unique password |
| `CORS_ALLOWED_ORIGINS` | Your actual domain only (e.g. `https://app.yourcompany.com`) |
| `FRONTEND_BASE_URL` | Your actual CRM domain |
| `PORTAL_BASE_URL` | Your actual portal domain |
| `EMAIL_PROVIDER` | `smtp` or `brevo-http` — pick the transport (defaults to `smtp`) |
| `EMAIL_HOST` | SMTP hostname (required when `EMAIL_PROVIDER=smtp`) |
| `BREVO_API_KEY` | Brevo API key (required when `EMAIL_PROVIDER=brevo-http`) |
| `REDIS_ENABLED=true` + `REDIS_HOST` | Required for multi-instance deployments |

---

## 3. Step-by-Step: Single-Host Docker Compose

### Step 1 — Environment file

```bash
cp .env.example .env
```

Edit `.env`:

```bash
# Security (mandatory)
JWT_SECRET=$(openssl rand -base64 48)
DB_URL=jdbc:postgresql://hlm-postgres:5432/hlm
DB_USER=hlm_user
DB_PASSWORD=$(openssl rand -base64 24)

# URLs (use your actual domain)
CORS_ALLOWED_ORIGINS=https://app.yourcompany.com
FRONTEND_BASE_URL=https://app.yourcompany.com
PORTAL_BASE_URL=https://portal.yourcompany.com

# Email — option A: classic SMTP relay (default)
EMAIL_PROVIDER=smtp
EMAIL_HOST=smtp.yourprovider.com
EMAIL_PORT=587
EMAIL_USER=apikey
EMAIL_PASSWORD=your-smtp-password
EMAIL_FROM=noreply@yourcompany.com
EMAIL_FROM_NAME=Your Company

# Email — option B: Brevo HTTPS API (use when outbound SMTP is blocked)
# EMAIL_PROVIDER=brevo-http
# BREVO_API_KEY=xkeysib-...
# EMAIL_FROM=noreply@yourcompany.com
# EMAIL_FROM_NAME=Your Company

# Redis (for multi-instance)
REDIS_ENABLED=true
REDIS_HOST=hlm-redis
REDIS_PORT=6379
```

### Step 2 — Start infrastructure

```bash
# Start Postgres, Redis, and MinIO first
docker compose up -d postgres redis minio

# Wait for Postgres to be healthy
docker compose ps
# Wait until hlm-postgres shows "(healthy)"
```

### Step 3 — Start backend

```bash
docker compose up -d hlm-backend

# Poll health (backend runs Liquibase migrations on first start)
for i in $(seq 1 30); do
  STATUS=$(curl -s http://localhost:8080/actuator/health | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null)
  [ "$STATUS" = "UP" ] && echo "Backend healthy!" && break
  echo "Attempt $i/30: $STATUS — waiting 10s..."
  sleep 10
done
```

> Liquibase applies all 52 changesets on first startup. This can take 60–90 seconds.
> Docker health check is configured with `start_period: 90s, retries: 10`.

### Step 4 — Start frontend

```bash
docker compose up -d hlm-frontend
```

### Step 5 — Smoke verification

```bash
./scripts/smoke-auth.sh
./scripts/smoke-rbac.sh
./scripts/smoke-stack.sh
```

---

## 4. First SUPER_ADMIN Bootstrap

On a fresh deployment with no seed data, create the first platform operator:

```bash
# Stop the running backend first
docker compose stop hlm-backend

# Set bootstrap variables in .env temporarily
echo "APP_BOOTSTRAP_ENABLED=true" >> .env
echo "APP_BOOTSTRAP_EMAIL=superadmin@yourcompany.com" >> .env
echo "APP_BOOTSTRAP_PASSWORD=YourSecure2026!" >> .env

# Restart backend — it will create the SUPER_ADMIN and exit bootstrap
docker compose up -d hlm-backend
# Watch logs
docker compose logs -f hlm-backend | grep -E "bootstrap|SUPER_ADMIN|error" -i

# After success: remove bootstrap variables from .env
# Edit .env and remove the three APP_BOOTSTRAP_* lines
docker compose restart hlm-backend
```

Verify:
```bash
curl -s http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"superadmin@yourcompany.com","password":"YourSecure2026!"}'
```

---

## 5. Reverse Proxy — Nginx Example

```nginx
# /etc/nginx/sites-enabled/yem-platform
server {
    listen 443 ssl http2;
    server_name app.yourcompany.com;

    ssl_certificate     /etc/letsencrypt/live/app.yourcompany.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/app.yourcompany.com/privkey.pem;

    # Proxy everything to the Angular/Nginx frontend container
    location / {
        proxy_pass         http://127.0.0.1:80;
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
        proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
    }
}

server {
    listen 80;
    server_name app.yourcompany.com;
    return 301 https://$host$request_uri;
}
```

The Angular container (port 80) proxies `/auth`, `/api`, `/actuator` to the backend container
via Docker internal DNS. See `nginx/` directory for the container-level Nginx config.

---

## 6. Health Checks and Monitoring

```bash
# Backend health
curl -s https://app.yourcompany.com/actuator/health

# Container status
docker compose ps

# Backend logs (last 200 lines)
docker compose logs --tail=200 hlm-backend

# Frontend logs
docker compose logs --tail=50 hlm-frontend

# Database — Liquibase status
docker exec -it hlm-postgres psql -U hlm_user -d hlm \
  -c "SELECT id, author, dateexecuted FROM databasechangelog ORDER BY orderexecuted DESC LIMIT 5;"

# Outbox queue depth
docker exec -it hlm-postgres psql -U hlm_user -d hlm \
  -c "SELECT status, COUNT(*) FROM outbound_message GROUP BY status;"
```

---

## 7. Release Validation Checklist

After every deployment, verify:

- [ ] `GET /actuator/health` returns `{ "status": "UP" }`
- [ ] Frontend serves the CRM login page at your domain
- [ ] CRM login succeeds: `admin@acme.com` / `Admin123!Secure` (or your seed credentials)
- [ ] SUPER_ADMIN login succeeds: `superadmin@yourcompany.com`
- [ ] Swagger UI accessible at `/swagger-ui.html` (if enabled in this environment)
- [ ] Outbox dispatcher is processing: check `outbound_message` status counts
- [ ] Smoke scripts pass: `./scripts/smoke-auth.sh`, `smoke-rbac.sh`, `smoke-stack.sh`
- [ ] Portal magic-link flow works end-to-end (if SMTP configured)

---

## 8. Security Hardening Checklist

Before go-live:

- [ ] `JWT_SECRET` is unique and 32+ chars — never reuse the development secret
- [ ] Default seed passwords changed (`admin@acme.com`, `superadmin@yourcompany.com`)
- [ ] `CORS_ALLOWED_ORIGINS` contains **only** your production domain(s)
- [ ] TLS enabled via reverse proxy — no plaintext HTTP in production
- [ ] Redis secured with `REDIS_PASSWORD` if exposed on a shared network
- [ ] `DB_PASSWORD` is strong and unique — dedicated database user
- [ ] `EMAIL_PASSWORD` is an application-specific password, not your main account
- [ ] `APP_BOOTSTRAP_*` variables removed from `.env` after first use
- [ ] `.env` file permissions restricted: `chmod 600 .env`
- [ ] Swagger UI disabled or access-restricted in production (`springdoc.api-docs.enabled=false`)

---

## 9. Database Backup

```bash
# Dump (run from host or CI)
docker exec hlm-postgres pg_dump -U hlm_user -d hlm | gzip > hlm_$(date +%Y%m%d_%H%M).sql.gz

# Restore
gunzip -c hlm_backup.sql.gz | docker exec -i hlm-postgres psql -U hlm_user -d hlm
```

---

## 10. Rollback

Liquibase changesets are **additive-only** — rollback to a previous application version
is safe only if the new changesets have not been applied yet. If changesets were applied,
restore from a database backup.

```bash
# Rolling back application version (no new changesets applied)
docker compose stop hlm-backend
docker compose up -d hlm-backend --no-recreate
# Or pull and deploy a previous image tag
```
