# Module 19 — Observability and OpenTelemetry

## Learning Objectives

- Explain what each observability mechanism provides
- Configure OTLP tracing for a local Jaeger or Grafana Tempo instance
- Use correlation IDs to trace a request through the logs

---

## Observability Mechanisms

The platform implements three observability mechanisms:

| Mechanism | Implementation | Purpose |
|-----------|--------------|---------|
| Health checks | Spring Boot Actuator | Load balancer health; startup readiness |
| Correlation IDs | `RequestCorrelationFilter` | Log correlation across services |
| Distributed tracing | Micrometer + OTLP | End-to-end trace across services |

---

## Health Endpoint

`GET /actuator/health` is publicly accessible (no JWT required).

```json
{"status": "UP"}
```

Spring automatically includes health indicators for:
- Database (HikariCP ping)
- Redis (when enabled)
- Disk space

Mail health check is disabled by default (`MAIL_HEALTH_ENABLED=false`) to avoid CI failures when no SMTP is configured.

To use this endpoint in a load balancer health check or Docker healthcheck:
```yaml
healthcheck:
  test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health"]
  interval: 30s
  timeout: 10s
  retries: 3
```

---

## Correlation IDs

`RequestCorrelationFilter` assigns a unique ID to every request:

1. If the incoming request has `X-Correlation-ID` header → use it.
2. Otherwise → generate a UUID v4.
3. Store in `MDC.put("correlationId", id)`.
4. Write `X-Correlation-ID: {id}` to the response.
5. `finally`: `MDC.remove("correlationId")`.

Every log statement during the request automatically includes the correlation ID (via Logback MDC pattern):

```
2026-03-17T10:00:00.123 INFO  [correlationId=f47ac10b-58cc] ContactService - Contact created
```

To find all logs for a specific request:
```bash
docker compose logs hlm-backend | grep "f47ac10b-58cc"
```

To pass a correlation ID from a client:
```bash
curl -H "X-Correlation-ID: my-test-request-123" http://localhost:8080/api/contacts
```

---

## OpenTelemetry Tracing

### Activation

```bash
OTEL_ENABLED=true
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces
OTEL_SAMPLE_RATE=1.0   # 100% sampling
```

### How it works

Micrometer's OTLP exporter automatically instruments:
- All incoming HTTP requests
- JDBC queries
- Spring `@Transactional` spans
- Outgoing HTTP calls (if using Spring WebClient or RestTemplate)

Each trace is a tree of spans. A request to `GET /api/contacts` might produce:

```
GET /api/contacts [50ms]
  ├── JPA: SELECT FROM contact [30ms]
  │     └── HikariCP pool acquire [2ms]
  └── Cache: commercialDashboard lookup [0.1ms]
```

### Local Jaeger Setup

```yaml
# Add to docker-compose.yml for local tracing
jaeger:
  image: jaegertracing/all-in-one:latest
  ports:
    - "4318:4318"   # OTLP HTTP
    - "16686:16686" # Jaeger UI
```

Access the Jaeger UI at `http://localhost:16686`.

### Sampling

`OTEL_SAMPLE_RATE=1.0` exports every trace (100%). In high-traffic production environments, reduce to `0.1` (10%) to reduce telemetry volume and cost.

---

## Logback Configuration

The application uses SLF4J + Logback. MDC fields (`correlationId`, `tenantId`) are automatically included in log output when the pattern includes `%X{correlationId}`:

```xml
<pattern>%d{ISO8601} %level [correlationId=%X{correlationId}] %logger - %msg%n</pattern>
```

Structured logging (JSON) can be enabled for production by switching to `logstash-logback-encoder`.

---

## Source Files

| File | Purpose |
|------|---------|
| `common/filter/RequestCorrelationFilter.java` | Correlation ID injection |
| `src/main/resources/application.yml` (management section) | Actuator + OTLP config |
| `hlm-backend/pom.xml` | Micrometer OTLP dependency |

---

## Exercise

1. Make a request to `GET /api/contacts` (with a valid token).
2. Note the `X-Correlation-ID` header in the response.
3. Search the backend logs for that correlation ID.
4. Enable OTLP tracing with a local Jaeger instance.
5. Make the same request and view the trace in Jaeger UI at `http://localhost:16686`.
6. Identify the slowest span in the trace.
