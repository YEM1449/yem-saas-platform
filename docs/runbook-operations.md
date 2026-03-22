# Operations Runbook

Audience: platform operators, on-call engineers, technical leads

## 1. Deployment Modes

### Docker Compose

Recommended for local validation and single-host environments.

```bash
cp .env.example .env
docker compose up -d
docker compose ps
docker compose logs -f hlm-backend
```

Production-style overlay:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### Mixed local development

Infrastructure in Docker, apps on host:

```bash
docker compose up -d postgres redis minio
cd hlm-backend && ./mvnw spring-boot:run
cd hlm-frontend && npm ci && npm start
```

## 2. Required Environment Variables

At minimum, set:

| Variable | Purpose |
| --- | --- |
| `JWT_SECRET` | required signing secret |
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USER` | database user |
| `DB_PASSWORD` | database password |

Frequently adjusted optional variables:

| Area | Variables |
| --- | --- |
| CORS | `CORS_ALLOWED_ORIGINS` |
| Redis | `REDIS_ENABLED`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` |
| Email | `EMAIL_HOST`, `EMAIL_PORT`, `EMAIL_USER`, `EMAIL_PASSWORD`, `EMAIL_FROM` |
| SMS | `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM` |
| Portal | `PORTAL_BASE_URL` |
| Frontend links | `FRONTEND_BASE_URL` |
| Media storage | `MEDIA_STORAGE_DIR`, `MEDIA_OBJECT_STORAGE_*` |
| Scheduling | `REMINDER_*`, `PAYMENTS_OVERDUE_CRON`, `PORTAL_CLEANUP_CRON`, `DATA_RETENTION_CRON` |
| Observability | `OTEL_ENABLED`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_SAMPLE_RATE` |

## 3. First-Time Platform Bootstrap

The first `SUPER_ADMIN` can be created by startup bootstrap.

```bash
APP_BOOTSTRAP_ENABLED=true \
APP_BOOTSTRAP_EMAIL=superadmin@yourcompany.com \
APP_BOOTSTRAP_PASSWORD='YourSecure2026!' \
cd hlm-backend && ./mvnw spring-boot:run
```

After a successful bootstrap:

1. remove the three `APP_BOOTSTRAP_*` variables
2. restart normally
3. verify login through `/auth/login`

Important:

- this bootstrap is code-backed
- the older `POST /tenants` path is not a current operational onboarding flow

## 4. Default Seed Accounts

Current Liquibase seed data provides:

| Account | Password | Intended use |
| --- | --- | --- |
| `admin@demo.ma` | `Admin123!Secure` | company admin |
| `manager@demo.ma` | `Manager123!Sec` | company manager |
| `agent@demo.ma` | `Agent123!Secure` | company agent |
| `superadmin@yourcompany.com` | `YourSecure2026!` | local-development super admin |

Change these credentials outside disposable environments.

## 5. Health Checks

Primary endpoints:

- `GET /actuator/health`
- `GET /actuator/info`

Examples:

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/actuator/info
```

Useful container checks:

```bash
docker compose ps
docker compose logs --tail=200 hlm-backend
docker compose logs --tail=200 hlm-frontend
```

## 6. Operational Smoke Checks

The repo contains smoke assets under [scripts](/home/yem/CRM-HLM/yem-saas-platform/scripts).

Recommended sequence:

```bash
./scripts/smoke-auth.sh
./scripts/smoke-rbac.sh
./scripts/smoke-stack.sh
```

If a user belongs to multiple societes, expect `/auth/login` to require an explicit `/auth/switch-societe` step before normal CRM API access.

## 7. Routine Administrative Operations

### Manage societes

Use `SUPER_ADMIN` routes:

- `GET /api/admin/societes`
- `POST /api/admin/societes`
- `PUT /api/admin/societes/{id}`
- `POST /api/admin/societes/{id}/desactiver`
- `POST /api/admin/societes/{id}/reactiver`
- `POST /api/admin/societes/{id}/impersonate/{userId}`

### Manage company members

Use:

- `GET /api/mon-espace/utilisateurs`
- `POST /api/mon-espace/utilisateurs`
- `PATCH /api/mon-espace/utilisateurs/{userId}`
- `PATCH /api/mon-espace/utilisateurs/{userId}/role`
- `DELETE /api/mon-espace/utilisateurs/{userId}`

### GDPR operations

Contacts:

- `GET /api/gdpr/contacts/{contactId}/export`
- `DELETE /api/gdpr/contacts/{contactId}/anonymize`

Users:

- `GET /api/mon-espace/utilisateurs/{userId}/export-donnees`
- `DELETE /api/mon-espace/utilisateurs/{userId}/anonymiser`

## 8. Debugging Guide

### Login succeeds but business API returns 401

Check for:

- expired or malformed bearer token
- token revocation after role change, password reset, or disable
- partial token used against a route other than `/auth/switch-societe`

### Login works for some users but not multi-societe users

Expected backend behavior:

- `/auth/login` may return `requiresSocieteSelection=true`
- client must call `POST /auth/switch-societe`

Current codebase note:

- the backend fully supports this flow
- the current Angular login experience does not yet implement that selection UX

### Company appears deactivated but users still access CRM

The `societe` lifecycle fields exist, but request-time suspension enforcement was not found in the current login and domain flow. Treat deactivation as an administrative state, not as a guaranteed access block, until enforcement is added.

### Resource quotas do not trip

Current enforcement confirmed from code:

- user quota is enforced when adding a member

Current enforcement not confirmed:

- property quota
- contact quota
- project quota

### Messages are not being delivered

Check:

- `outbound_message` rows are being created
- `EMAIL_HOST` or Twilio credentials are configured
- `OutboundDispatcherService` is running
- provider-specific failures in backend logs

### Portal link does not work

Check:

- `PORTAL_BASE_URL`
- portal token not already used
- token not older than 48 hours
- backend `/api/portal/auth/verify` response

## 9. Database Operations

Useful commands:

```bash
docker exec -it hlm-postgres psql -U hlm_user -d hlm
```

Inside `psql`:

```sql
\dt
SELECT id, author, dateexecuted FROM databasechangelog ORDER BY orderexecuted;
SELECT COUNT(*) FROM outbound_message;
SELECT COUNT(*) FROM portal_token;
```

Development reset:

```bash
docker compose down -v
docker compose up -d
```

## 10. Release Validation Checklist

- backend health is `UP`
- frontend serves the CRM shell
- CRM login works for demo users
- super-admin login works
- outbox dispatch is functioning or intentionally disabled
- Swagger/OpenAPI is enabled in the target environment if manual API validation is required
- smoke scripts match the current seed credentials and auth contract
