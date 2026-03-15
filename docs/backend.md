# Backend Guide (Spring Boot)

## Tech stack
- **Language/runtime:** Java 21
- **Framework:** Spring Boot 3.5.x
- **Database:** PostgreSQL
- **Migrations:** Liquibase (YAML changesets)
- **Security:** Spring Security + JWT (HS256)

## Configuration

### Environment variables
| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/hlm` | JDBC URL for PostgreSQL |
| `DB_USER` | `hlm_user` | Database username |
| `DB_PASSWORD` | `hlm_pwd` | Database password |
| `JWT_SECRET` | *(required)* | HMAC secret used to sign JWTs |
| `JWT_TTL_SECONDS` | `3600` | Token TTL in seconds |
| `SSL_ENABLED` | `false` | Enable embedded Tomcat TLS (dev/staging) |
| `SERVER_PORT` | `8080` | HTTP or HTTPS port (use 8443 with TLS) |
| `SSL_KEYSTORE_PATH` | (none) | Path to PKCS12 keystore |
| `SSL_KEYSTORE_PASSWORD` | (none) | Keystore password |
| `FORWARD_HEADERS_STRATEGY` | `NONE` | Set to `FRAMEWORK` behind Nginx/LB |
| `EMAIL_HOST` | (none) | SMTP host — blank = Noop (log-only) |
| `EMAIL_PORT` | `587` | SMTP port |
| `EMAIL_USER` | (none) | SMTP username |
| `EMAIL_PASSWORD` | (none) | SMTP password |
| `EMAIL_FROM` | `noreply@hlm.local` | Sender address |
| `TWILIO_ACCOUNT_SID` | (none) | Twilio SID — blank = Noop (log-only) |
| `TWILIO_AUTH_TOKEN` | (none) | Twilio auth token |
| `TWILIO_FROM` | (none) | Twilio sender phone number |
| `PORTAL_BASE_URL` | `http://localhost:4200` | Must be `https://` in production |
| `PORTAL_CLEANUP_CRON` | `0 0 3 * * *` | Cron for portal token cleanup |
| `REDIS_ENABLED` | `false` | `true` = Redis cache, `false` = Caffeine |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | *(blank)* | Redis auth password |
| `MEDIA_OBJECT_STORAGE_ENABLED` | `false` | `true` activates S3-compatible object storage |
| `MEDIA_OBJECT_STORAGE_ENDPOINT` | *(blank)* | Provider URL. Blank = AWS S3 auto-resolve. OVH example: `https://s3.gra.perf.cloud.ovh.net` |
| `MEDIA_OBJECT_STORAGE_REGION` | `eu-west-1` | Region code (e.g. `gra`, `fr-par`, `eu-west-1`) |
| `MEDIA_OBJECT_STORAGE_BUCKET` | `hlm-media` | Bucket / container name |
| `MEDIA_OBJECT_STORAGE_ACCESS_KEY` | *(none)* | Provider S3 access key |
| `MEDIA_OBJECT_STORAGE_SECRET_KEY` | *(none)* | Provider S3 secret key |
| `GDPR_RETENTION_DAYS` | `1825` | Days before soft-deleted contacts are anonymized |
| `DATA_RETENTION_CRON` | `0 0 2 * * *` | Daily GDPR retention sweep cron |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318` | OTLP trace exporter endpoint |
| `OTEL_SAMPLE_RATE` | `1.0` | Trace sampling rate (0.0–1.0) |

The backend reads these values from `application.yml` and fails fast if `JWT_SECRET` is blank.

### Service ports
- **HTTP API:** `http://localhost:8080`
- **Actuator health:** `GET /actuator/health`

## Authentication & tenant isolation

1. **Login:** `POST /auth/login` validates tenant + user and returns a JWT.
2. **JWT claims:** `sub` (user ID), `tid` (tenant ID), and `roles` (list of role strings).
3. **Request filter:** `JwtAuthenticationFilter` validates the token, sets Spring Security authentication, and stores the tenant in `TenantContext`.
4. **Repository scoping:** repositories query data by `tenant_id` to guarantee isolation.

## RBAC rules
- **Roles:** `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_AGENT`.
- **Usage:** controllers/services typically enforce permissions via `@PreAuthorize`.
- **Defaults:** when no roles are present, the system treats the user as `ROLE_AGENT`.

## Error contract
All endpoints return a consistent JSON shape on error, backed by `ErrorResponse` and `ErrorCode`:

```json
{
  "timestamp": "2026-02-09T12:00:00.000+00:00",
  "status": 401,
  "error": "Unauthorized",
  "code": "UNAUTHORIZED",
  "message": "Full authentication is required",
  "path": "/api/properties",
  "fieldErrors": null
}
```

## Database & migrations

- **Liquibase master:** `hlm-backend/src/main/resources/db/changelog/db.changelog-master.yaml`
- **Changesets:** stored in `db/changelog/changes/` with numeric prefixes.
- **Schema validation:** Hibernate is configured with `ddl-auto: validate`; schema changes must come from Liquibase.

## Local development

### Run
```bash
cd hlm-backend
./mvnw spring-boot:run
```

### Tests
```bash
cd hlm-backend
./mvnw test
./mvnw failsafe:integration-test failsafe:verify
```

Integration tests require Docker for Testcontainers.

## Email / SMS Providers

**Email:** `SmtpEmailSender` activates when `EMAIL_HOST` is set (via `spring.mail.*` bridge).
Falls back to `NoopEmailSender` (logs only) when `EMAIL_HOST` is blank (dev default).

**SMS:** `TwilioSmsSender` activates when `TWILIO_ACCOUNT_SID` is set.
Falls back to `NoopSmsSender` (logs only) when not configured.

Portal magic-link email uses `EmailSender` directly (not via outbox) from `PortalAuthService`.

## GDPR / Data Protection

The platform implements RGPD and Moroccan Law 09-08 controls:

- **Consent fields** on `Contact`: `consent_given`, `consent_date`, `consent_method`, `processing_basis` (Liquibase changeset 029)
- **Data Subject Rights API** at `/api/gdpr/**`: export, anonymize (erasure), rectify, privacy notice
- **Automated retention sweep** via `DataRetentionScheduler` (daily 02:00, configurable)
- **Privacy notice** loaded from `classpath:gdpr/privacy-notice.txt` at startup

See [docs/gdpr.md](gdpr.md) for the full compliance guide.

## Cache Architecture

| Mode | Active When | Bean |
|---|---|---|
| Caffeine (in-process) | `REDIS_ENABLED=false` (default) | `CacheConfig` |
| Redis (distributed) | `REDIS_ENABLED=true` | `RedisCacheConfig` |

Both modes expose identical cache names. Redis is required for multi-instance deployments.

## Media Storage Architecture

| Mode | Active When | Bean |
|---|---|---|
| Local filesystem | `MEDIA_OBJECT_STORAGE_ENABLED=false` (default) | `LocalFileMediaStorage` |
| S3-compatible object storage | `MEDIA_OBJECT_STORAGE_ENABLED=true` | `ObjectStorageMediaStorage` |

Both modes implement the `MediaStorageService` interface. `ObjectStorageMediaStorage` uses the S3 object storage
protocol with mandatory path-style addressing (`pathStyleAccessEnabled=true`), ensuring compatibility with all
providers. Supported: OVH Object Storage, Scaleway, Hetzner, Cloudflare R2, MinIO (self-hosted), and AWS S3.
The bucket is created automatically on startup if it does not exist.
See [object-storage.md](object-storage.md) for provider-specific setup.

## API references
- Full endpoint catalog: [api.md](api.md)
- Curl walkthrough: [api-quickstart.md](api-quickstart.md)
- GDPR compliance: [gdpr.md](gdpr.md)
- Docker & container infra: [docker.md](docker.md)
- Object storage (OVH, Scaleway, Hetzner, MinIO, AWS S3): [object-storage.md](object-storage.md)
