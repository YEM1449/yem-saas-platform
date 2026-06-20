# Alerting & Observability (P4 / DA-026)

The app emits the failure signals; **routing them to a human is infra** (Prometheus +
Alertmanager → Slack/PagerDuty/email). This file documents the signals and ships ready-to-use
Prometheus rules so "failures are invisible until a user calls" stops being true.

## Signals the backend exposes

| Signal | Where | Scrape |
|---|---|---|
| Unhandled 5xx + tenant-isolation trips | `hlm.errors.unhandled` counter (tag `type`) — incremented in `GlobalExceptionHandler` | `/actuator/prometheus` → `hlm_errors_unhandled_total{type="…"}` |
| HTTP error rate (any 5xx) | Spring MVC server timer | `http_server_requests_seconds_count{status=~"5.."}` |
| Object storage (R2/S3) reachability | `ObjectStorageHealthIndicator` (EX-016) — contributes to aggregate `/actuator/health`, **not** to readiness | health detail; also `health` gauge if the health-metrics binder is on |
| Email provider wired & delivering | `EmailProviderHealthIndicator` — `delivering=false` when the Noop sender is active | `/actuator/health` detail |
| Scheduler double-fire prevented | ShedLock (EX-010/DA-025) on every DB sweep | n/a (correctness, not a metric) |

Prometheus scrape (already enabled via `management.endpoints.web.exposure.include: …,prometheus`):

```yaml
scrape_configs:
  - job_name: hlm-backend
    metrics_path: /actuator/prometheus
    static_configs: [{ targets: ["hlm-backend:8080"] }]
```

## Alert rules (`hlm-alerts.yml`)

```yaml
groups:
  - name: hlm-backend
    rules:
      # Any unhandled server error — DA-026: no longer wait for a user to call.
      - alert: HlmUnhandledErrors
        expr: increase(hlm_errors_unhandled_total[5m]) > 0
        for: 0m
        labels: { severity: warning }
        annotations:
          summary: "Unhandled server errors ({{ $labels.type }})"
          description: "{{ $value }} unhandled '{{ $labels.type }}' error(s) in the last 5m."

      # Tenant-isolation backstop tripped — P5: a société-scoped principal hit the DB with no
      # société. Should be ZERO in normal operation; non-zero = bug or attack. Page immediately.
      - alert: HlmTenantIsolationTripped
        expr: increase(hlm_errors_unhandled_total{type="TenantIsolationException"}[5m]) > 0
        for: 0m
        labels: { severity: critical }
        annotations:
          summary: "Tenant-isolation backstop tripped"
          description: "RlsContextAspect refused a société-less CRM request — investigate now."

      # Sustained 5xx rate.
      - alert: HlmHigh5xxRate
        expr: sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) > 0.2
        for: 5m
        labels: { severity: critical }
        annotations:
          summary: "Elevated 5xx rate"
          description: ">0.2 5xx/s for 5m."

      # Object storage unreachable (documents/3D/uploads broken) — EX-016. Requires the
      # health-as-metrics binder; otherwise alert on a blackbox probe of /actuator/health.
      - alert: HlmObjectStorageDown
        expr: health_component_status{component="objectStorageHealthIndicator"} == 0
        for: 2m
        labels: { severity: critical }
        annotations: { summary: "R2/S3 object storage DOWN" }
```

## Routing (the external half — left to infra)

Wire `hlm-alerts.yml` into Prometheus `rule_files`, and an Alertmanager receiver
(Slack/PagerDuty/email). A managed APM (Sentry/Datadog) can replace the error counter with full
stack traces; this repo intentionally stays vendor-neutral — the counter + ERROR logs are the
zero-dependency baseline. `error tracking + alerting` (DA-026) is "done" on the **emit** side here;
the **route-to-human** side is an ops deployment step.
