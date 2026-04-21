# Module 19: Observability, Logs, Metrics, And Tracing

## Why This Matters

Without observability, a complex multi-surface system becomes guesswork to operate.

## Learning Goals

- understand the available health and runtime signals
- understand what logs, metrics, and tracing each contribute
- connect observability to debugging and operations

## Current Building Blocks

- actuator health and info endpoints
- structured logging
- Prometheus metrics export
- optional OTLP tracing export

## Files To Study

- [../../hlm-backend/src/main/resources/application.yml](../../hlm-backend/src/main/resources/application.yml)
- [../../hlm-backend/src/main/resources/logback-spring.xml](../../hlm-backend/src/main/resources/logback-spring.xml)
- [../guides/engineer/ci-cd.md](../guides/engineer/ci-cd.md)
- [../guides/engineer/troubleshooting.md](../guides/engineer/troubleshooting.md)

## Practical Questions Observability Should Answer

- is the backend healthy?
- are schedulers running?
- are messages being dispatched?
- are dashboards slow because of data volume, auth, or storage?
- is a failing path limited to CRM, portal, or both?

## Exercise

Create a simple debugging plan for a report that says "buyers can log in, but portal contract pages are slow."
