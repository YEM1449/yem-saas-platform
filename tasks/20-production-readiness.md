# Task 20 — Production Readiness Checklist & Deployment

## Priority: HIGH (after frontend complete)
## Effort: 2 hours

## Overview

Final verification before deploying to production. This task covers configuration hardening, environment preparation, and deployment execution.

## Pre-Deployment Checklist

### Security

- [ ] `JWT_SECRET` is a cryptographically random string (min 32 chars): `openssl rand -base64 48`
- [ ] `CORS_ALLOWED_ORIGINS` set to exact production frontend URL (no localhost)
- [ ] `FORWARD_HEADERS_STRATEGY=FRAMEWORK` set (behind reverse proxy)
- [ ] Swagger UI disabled in production (verify `SwaggerProductionIT` passes)
- [ ] Database password is strong and unique
- [ ] Redis password set (if Redis enabled)
- [ ] `SSL_ENABLED=true` or TLS terminated at load balancer
- [ ] SUPER_ADMIN account has a strong password (changeset 046 seeds one — rotate it!)
- [ ] `.env` file is NOT committed to git

### Database

- [ ] All 50 Liquibase changesets applied successfully
- [ ] RLS policies active on `contact` and `property` tables (changeset 050)
- [ ] Indexes exist for all `societe_id` columns (changeset 044, 047)
- [ ] Connection pool sized correctly (default: max 20, min 5)
- [ ] Backups configured (pg_dump or managed provider snapshots)

### Application

- [ ] `spring.jpa.hibernate.ddl-auto=validate` (never `update` or `create` in production)
- [ ] Logging level set appropriately (INFO, not DEBUG)
- [ ] OTEL tracing enabled if monitoring is set up
- [ ] Schedulers enabled: `SPRING_TASK_SCHEDULING_ENABLED=true`
- [ ] Email/SMS configured (or NoopSender used with awareness)
- [ ] `GDPR_RETENTION_DAYS` set to company policy (default: 1825 = 5 years)

### Frontend

- [ ] `environment.production.ts` has correct `apiUrl`
- [ ] Production build passes: `npm run build -- --configuration=production`
- [ ] No hardcoded localhost URLs in service files
- [ ] Nginx config serves Angular routes correctly (try-files)

### CI/CD

- [ ] All CI workflows pass on the branch
- [ ] Docker images build successfully
- [ ] Health checks pass in Docker Compose

## Free-Tier Deployment Options

| Component | Provider | Free Tier |
|-----------|----------|-----------|
| PostgreSQL | Neon.tech | 0.5 GB, autoscaling |
| PostgreSQL (alt) | Supabase | 500 MB |
| Redis | Upstash | 10K commands/day |
| Object Storage | Cloudflare R2 | 10 GB, no egress fees |
| Backend | Railway.app | 500 hours/month |
| Backend (alt) | Render.com | 750 hours/month |
| Frontend | Cloudflare Pages | Unlimited |
| Frontend (alt) | Vercel | 100 GB bandwidth |
| DNS + TLS | Cloudflare | Free plan |

## Deployment Steps

### Option A: Docker Compose on a VPS (simplest)

```bash
# 1. Clone and configure
git clone https://github.com/YEM1449/yem-saas-platform.git
cd yem-saas-platform
cp .env.example .env
# Edit .env with production values

# 2. Build and start
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build

# 3. Verify
curl -sf http://localhost:8080/actuator/health
curl -sf http://localhost/
```

### Option B: Split services (free tier)

```bash
# 1. Deploy PostgreSQL to Neon.tech
#    Get connection string: postgresql://user:pass@host/db

# 2. Deploy Redis to Upstash  
#    Get connection string: redis://default:pass@host:port

# 3. Deploy backend to Railway
#    Set env vars: DB_URL, JWT_SECRET, REDIS_HOST, etc.
#    Deploy from Dockerfile

# 4. Deploy frontend to Cloudflare Pages
#    Build command: npm run build -- --configuration=production
#    Output directory: dist/hlm-frontend/browser
```

## Post-Deployment Validation

```bash
# 1. Health check
curl https://api.yourdomain.com/actuator/health

# 2. Login test
curl -X POST https://api.yourdomain.com/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@yourdomain.com","password":"YourPassword"}'

# 3. Frontend loads
curl -s https://app.yourdomain.com/ | grep "hlm-frontend"

# 4. Cross-société isolation (create data in société A, verify invisible from société B)

# 5. Run smoke script
./scripts/smoke-auth.sh
./scripts/smoke-rbac.sh
```

## Monitoring (Post-Deploy)

- [ ] Set up Grafana Cloud free tier for metrics/traces (if OTEL enabled)
- [ ] Set up Sentry free tier for error tracking (frontend + backend)
- [ ] Set up UptimeRobot free tier for uptime monitoring on `/actuator/health`
- [ ] Configure backup schedule for PostgreSQL (daily)

## Acceptance Criteria

- [ ] All checklist items verified
- [ ] Application accessible via HTTPS
- [ ] Login → CRM features → logout flow works
- [ ] SuperAdmin → société management works
- [ ] Portal → client access works
- [ ] No errors in logs during first 24 hours
