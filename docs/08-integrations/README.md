# Integrations Guide

External services, optional integrations, and activation patterns.

---

## 1. Overview Table

| Integration | Required | Activation | Purpose |
|---|---|---|---|
| PostgreSQL 16 | Yes | Always | Primary database; RLS, advisory locks |
| Redis | No | `REDIS_ENABLED=true` | Distributed cache; token-version revocation |
| SMTP Email | No | `EMAIL_PROVIDER=smtp` + `EMAIL_HOST` non-blank | Transactional outbox + portal magic links (classic SMTP) |
| Brevo HTTP Email | No | `EMAIL_PROVIDER=brevo-http` + `BREVO_API_KEY` non-blank | Transactional email via HTTPS API (use when SMTP ports are blocked) |
| Twilio SMS | No | `TWILIO_ACCOUNT_SID` set | SMS notifications via outbox |
| MinIO / S3 | No | `MEDIA_OBJECT_STORAGE_ENABLED=true` | Media and document object storage |
| OTel Collector | No | `OTEL_ENABLED=true` + `OTEL_EXPORTER_OTLP_ENDPOINT` | Distributed tracing and Prometheus metrics |

---

## 2. Email Integration

### Provider Switch

Two real providers are bundled. Choose one with the `EMAIL_PROVIDER` env var; only one `EmailSender` bean activates at any time.

| `EMAIL_PROVIDER` | Implementation | Transport | When to use |
|---|---|---|---|
| `smtp` *(default)* | `SmtpEmailSender` | SMTP/STARTTLS (port 587) or implicit SSL (465) | Standard SMTP relays (SendGrid, Mailgun, Gmail, Brevo SMTP, …) |
| `brevo-http` | `BrevoHttpEmailSender` | HTTPS `POST https://api.brevo.com/v3/smtp/email` | When outbound SMTP ports (25/465/587) are blocked by the host/firewall |

If the selected provider's required config is missing (`EMAIL_HOST` blank for `smtp`, `BREVO_API_KEY` blank for `brevo-http`), `NoopEmailSender` takes over as a safe fallback — it only logs messages to the application log and does not deliver anything.

### Option A — SMTP relay

```env
EMAIL_PROVIDER=smtp
EMAIL_HOST=smtp-relay.brevo.com
EMAIL_PORT=587
EMAIL_USER=your_login
EMAIL_PASSWORD=your_smtp_key
EMAIL_FROM=noreply@example.com
EMAIL_FROM_NAME=HLM CRM

# Transport mode — STARTTLS on 587 is the default
EMAIL_SSL_ENABLE=false
EMAIL_STARTTLS_ENABLE=true
EMAIL_STARTTLS_REQUIRED=true

# Optional timeouts (ms)
EMAIL_CONNECT_TIMEOUT_MS=10000
EMAIL_READ_TIMEOUT_MS=10000
EMAIL_WRITE_TIMEOUT_MS=10000
```

Set `EMAIL_SSL_ENABLE=true` and `EMAIL_PORT=465` only if your provider mandates implicit SSL. **Port 465 is commonly blocked outbound in containerised / cloud environments — prefer 587 whenever possible.**

`SmtpEmailSender` activates via:

```java
@ConditionalOnExpression(
    "'${app.email.provider:smtp}'.equalsIgnoreCase('smtp') && !'${app.email.host:}'.isBlank()"
)
```

**Critical**: `@ConditionalOnProperty` must NOT be used here. When `EMAIL_HOST` is unset, the `${app.email.host:}` YAML expression defaults to an empty string `""`. `@ConditionalOnProperty` treats `""` as "present" and would incorrectly activate the SMTP sender, causing authentication failures on every startup.

### Option B — Brevo HTTPS API

Use this when the host/firewall blocks SMTP ports. It talks to Brevo over plain HTTPS (port 443), which is almost always allowed.

```env
EMAIL_PROVIDER=brevo-http
BREVO_API_KEY=xkeysib-...
BREVO_BASE_URL=https://api.brevo.com   # optional, defaults to this
EMAIL_FROM=noreply@example.com
EMAIL_FROM_NAME=HLM CRM
```

Generate an API key at <https://app.brevo.com/settings/keys/api>. `BrevoHttpEmailSender` activates via:

```java
@ConditionalOnExpression(
    "'${app.email.provider:}'.equalsIgnoreCase('brevo-http') && !'${app.email.brevo.api-key:}'.isBlank()"
)
```

The sender uses Spring's `RestClient` to `POST /v3/smtp/email` with the Brevo payload shape `{sender:{email,name?}, to:[{email}], subject, htmlContent|textContent}`. HTML vs plain-text is auto-detected: if the body matches `<tag>` anywhere it is sent as `htmlContent`, otherwise as `textContent`.

Errors are wrapped as `RuntimeException` so the outbox dispatcher retries them with exponential backoff like any other send failure.

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
| `EMAIL_PROVIDER` | `smtp` | Email backend selector: `smtp`, `brevo-http` (anything else → Noop) |
| `EMAIL_HOST` | `` (blank) | SMTP host; blank + `EMAIL_PROVIDER=smtp` → NoopEmailSender |
| `EMAIL_PORT` | `587` | SMTP port (587 STARTTLS recommended; 465 implicit SSL often blocked) |
| `EMAIL_USER` | — | SMTP authentication username |
| `EMAIL_PASSWORD` | — | SMTP authentication password |
| `EMAIL_SSL_ENABLE` | `false` | Enable implicit SSL (set `true` only if using port 465) |
| `EMAIL_STARTTLS_ENABLE` | `true` | Enable STARTTLS upgrade on plain SMTP connection |
| `EMAIL_STARTTLS_REQUIRED` | `true` | Fail if STARTTLS upgrade not offered by server |
| `EMAIL_CONNECT_TIMEOUT_MS` | `10000` | SMTP connection timeout (ms) |
| `EMAIL_READ_TIMEOUT_MS` | `10000` | SMTP read timeout (ms) |
| `EMAIL_WRITE_TIMEOUT_MS` | `10000` | SMTP write timeout (ms) |
| `BREVO_API_KEY` | — | Brevo API key; blank + `EMAIL_PROVIDER=brevo-http` → NoopEmailSender |
| `BREVO_BASE_URL` | `https://api.brevo.com` | Brevo API base URL (override only for testing) |
| `EMAIL_FROM` | — | From address for outgoing email |
| `EMAIL_FROM_NAME` | `` (blank) | Optional display name for the sender (e.g., "HLM CRM") |
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
