# Integrations Guide

External services, optional integrations, and activation patterns.

---

## 1. Overview Table

| Integration | Required | Activation | Purpose |
|---|---|---|---|
| PostgreSQL 16 | Yes | Always | Primary database; RLS, advisory locks |
| Redis | No | `REDIS_ENABLED=true` | Distributed cache; token-version revocation |
| SMTP Email | No | `EMAIL_HOST` non-blank | Transactional outbox + portal magic links |
| Twilio SMS | No | `TWILIO_ACCOUNT_SID` set | SMS notifications via outbox |
| MinIO / S3 | No | `MEDIA_OBJECT_STORAGE_ENABLED=true` | Media and document object storage |
| OTel Collector | No | `OTEL_ENABLED=true` + `OTEL_EXPORTER_OTLP_ENDPOINT` | Distributed tracing and Prometheus metrics |

---

## 2. Email Integration

### Default Behavior

Without any email configuration, `NoopEmailSender` is active. It logs message details to the application log but does not deliver emails.

### Enabling SMTP

Set the `EMAIL_HOST` environment variable to a non-blank value:

```env
EMAIL_HOST=smtp.example.com
EMAIL_PORT=587
EMAIL_USERNAME=user@example.com
EMAIL_PASSWORD=secret
EMAIL_FROM=noreply@example.com
```

`SmtpEmailSender` activates via:

```java
@ConditionalOnExpression("!'${app.email.host:}'.isBlank()")
```

**Critical**: `@ConditionalOnProperty` must NOT be used here. When `EMAIL_HOST` is unset, the `${app.email.host:}` YAML expression defaults to an empty string `""`. `@ConditionalOnProperty` treats `""` as "present" and would incorrectly activate the SMTP sender, causing authentication failures on every startup.

### Two Email Delivery Flows

| Flow | Used by | Mechanism |
|---|---|---|
| Direct send | Portal magic-link (`PortalAuthService`) | `EmailSender.send(to, subject, body)` called synchronously |
| Outbox | CRM notifications, contract events | `outbound_message` table → `OutboundDispatcherService.runDispatch()` |

The portal uses direct send because magic-link delivery is a public endpoint — there is no `User` FK available for the outbox's audit relationship.

### Outbox Configuration

| Property | Env Var | Default | Description |
|---|---|---|---|
| `app.outbox.batch-size` | `OUTBOX_BATCH_SIZE` | `50` | Messages processed per poll cycle |
| `app.outbox.max-retries` | `OUTBOX_MAX_RETRIES` | `3` | Permanent `FAILED` after this count |
| `app.outbox.polling-interval-ms` | `OUTBOX_POLL_INTERVAL_MS` | `5000` | Polling interval in milliseconds |

Retry backoff delays (minutes): `{1, 5, 30}`. After `max-retries` failures the message moves to permanent `FAILED` status.

The outbox scheduler is controlled by `@ConditionalOnProperty("spring.task.scheduling.enabled", matchIfMissing=true)`. Set `spring.task.scheduling.enabled=false` in `application-test.yml` to disable polling during integration tests.

---

## 3. Redis Integration

### Activation

```env
REDIS_ENABLED=true
REDIS_HOST=localhost
REDIS_PORT=6379
```

### Without Redis

When `REDIS_ENABLED` is not set or is `false`, Caffeine (in-process) cache is used. This works for single-instance deployments but does not share cache state across multiple application instances.

### With Redis

Redis is used for:

| Cache / Component | TTL | Purpose |
|---|---|---|
| `UserSecurityCacheService` | Per-entry eviction on version change | Token-version lookup for JWT revocation |
| `PROJECTS_CACHE` | 60 seconds | Project list queries; registered in `RedisCacheConfig` |
| `SOCIETES_CACHE` | 120 seconds | Societe list queries; registered in `RedisCacheConfig` |

**Note**: Before Wave 4, `PROJECTS_CACHE` and `SOCIETES_CACHE` were not registered in `RedisCacheConfig`. They silently fell back to Caffeine even when Redis was enabled. This was fixed by adding explicit cache registrations in `RedisCacheConfig.cacheConfigurations()`.

### Redis for Horizontal Scaling

With Redis active, multiple application replicas share:
- Token revocation state (any instance that increments `token_version` invalidates the cache for all instances)
- Project and societe cache (prevents cache stampedes)

---

## 4. Object Storage (MinIO / S3)

### Default Behavior

Without configuration, files (property media, documents) are stored on the local filesystem under `MEDIA_STORAGE_DIR` (default: `./media`).

### Enabling S3-Compatible Storage

```env
MEDIA_OBJECT_STORAGE_ENABLED=true
MEDIA_S3_ENDPOINT=http://minio:9000
MEDIA_S3_BUCKET=hlm-media
MEDIA_S3_ACCESS_KEY=minioadmin
MEDIA_S3_SECRET_KEY=minioadmin
MEDIA_S3_REGION=us-east-1
```

### MinIO as Local S3 Substitute

MinIO provides an S3-compatible API and is the recommended choice for local development and self-hosted deployments. Add it to `docker-compose.yml`:

```yaml
minio:
  image: minio/minio:latest
  command: server /data --console-address ":9001"
  ports:
    - "9000:9000"
    - "9001:9001"
  environment:
    MINIO_ROOT_USER: minioadmin
    MINIO_ROOT_PASSWORD: minioadmin
```

### File Categories

| Module | Storage | Bucket prefix |
|---|---|---|
| `media` | Property images and attachments | `property-media/` |
| `document` | Generic entity attachments | `documents/` |

---

## 5. Twilio SMS

### Activation

```env
TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_AUTH_TOKEN=your_auth_token
TWILIO_FROM=+15551234567
```

### Default Behavior

Without `TWILIO_ACCOUNT_SID`, `NoopSmsSender` is active. It logs SMS details but does not send.

### Integration Pattern

`TwilioSmsSender` implements the `SmsSender` interface:

```java
public interface SmsSender {
    void send(String to, String body);
}
```

SMS messages flow through the same transactional outbox as email. The `outbound_message.channel` field distinguishes between `EMAIL` and `SMS` channels.

---

## 6. OTel Distributed Tracing

### Architecture

The tracing setup uses a bridge-only approach to avoid thread leaks:

| Dependency | Status | Purpose |
|---|---|---|
| `micrometer-tracing-bridge-otel` | Included | Bridges Micrometer spans to OTel API |
| `opentelemetry-exporter-otlp` | **Intentionally excluded** | Would create a `BatchSpanProcessor` background thread that leaks on application shutdown |

### Activation

```env
OTEL_ENABLED=true
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318
```

Without these variables the application starts normally with no-op tracing.

### Metrics Endpoint

Prometheus-compatible metrics are exposed at:

```
GET /actuator/prometheus
```

Configured in `application.yml`:

```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
```

### Correlation IDs

`CorrelationFilter` injects a `X-Correlation-Id` header on every response. Traces can be correlated across service boundaries using this header value.

### Docker Compose OTel Stack (Optional)

For local tracing with Jaeger:

```yaml
jaeger:
  image: jaegertracing/all-in-one:1.54
  ports:
    - "16686:16686"  # UI
    - "4318:4318"    # OTLP HTTP
  environment:
    COLLECTOR_OTLP_ENABLED: "true"
```

Set `OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4318` in the backend service environment.

---

## 7. Environment Variable Reference

Complete list of integration-related environment variables:

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | — | JDBC URL for PostgreSQL (required) |
| `DB_USERNAME` | — | Database username (required) |
| `DB_PASSWORD` | — | Database password (required) |
| `JWT_SECRET` | — | JWT signing secret, min 32 chars (required) |
| `REDIS_ENABLED` | `false` | Enable Redis for distributed caching |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `EMAIL_HOST` | `` (blank) | SMTP host; blank = NoopEmailSender |
| `EMAIL_PORT` | `587` | SMTP port |
| `EMAIL_USERNAME` | — | SMTP authentication username |
| `EMAIL_PASSWORD` | — | SMTP authentication password |
| `EMAIL_FROM` | — | From address for outgoing email |
| `TWILIO_ACCOUNT_SID` | — | Twilio account SID; absent = NoopSmsSender |
| `TWILIO_AUTH_TOKEN` | — | Twilio auth token |
| `TWILIO_FROM` | — | Twilio sender phone number |
| `MEDIA_OBJECT_STORAGE_ENABLED` | `false` | Enable S3/MinIO storage |
| `MEDIA_S3_ENDPOINT` | — | S3 endpoint URL |
| `MEDIA_S3_BUCKET` | — | S3 bucket name |
| `MEDIA_S3_ACCESS_KEY` | — | S3 access key |
| `MEDIA_S3_SECRET_KEY` | — | S3 secret key |
| `MEDIA_STORAGE_DIR` | `./media` | Local filesystem fallback directory |
| `OTEL_ENABLED` | `false` | Enable OTel tracing export |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | — | OTel collector endpoint |
| `PORTAL_BASE_URL` | — | Base URL for portal magic links (e.g., `https://app.example.com/portal`) |
| `CORS_ALLOWED_ORIGINS` | — | Comma-separated list of allowed CORS origins |
| `OUTBOX_BATCH_SIZE` | `50` | Outbox dispatcher batch size |
| `OUTBOX_MAX_RETRIES` | `3` | Max outbox retry attempts before permanent FAILED |
| `OUTBOX_POLL_INTERVAL_MS` | `5000` | Outbox polling interval in milliseconds |
