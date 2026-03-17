# YEM SaaS Platform — Documentation Index

This is the master index for all documentation of the YEM SaaS Platform (HLM CRM). The platform is a multi-tenant real-estate CRM built on Spring Boot 3.5 / Angular 19, targeting property agencies in Morocco. Use the audience sections below to navigate to the right starting point.

## Table of Contents

1. [For Engineers](#for-engineers)
2. [For Users](#for-users)
3. [For Learners](#for-learners)
4. [Specifications](#specifications)
5. [Context and Architecture](#context-and-architecture)

---

## For Engineers

These guides assume you are comfortable with Java / Spring Boot and Angular.

| File | Description |
|------|-------------|
| [guides/engineer/getting-started.md](guides/engineer/getting-started.md) | From zero to running in 15 minutes: prerequisites, clone, first login |
| [guides/engineer/backend-deep-dive.md](guides/engineer/backend-deep-dive.md) | Package structure, adding features, multi-tenancy patterns, pitfalls |
| [guides/engineer/frontend-deep-dive.md](guides/engineer/frontend-deep-dive.md) | Angular 19 standalone components, routing, auth interceptor, portal interceptor |
| [guides/engineer/database.md](guides/engineer/database.md) | Liquibase changelog rules, changeset naming, additive-only strategy |
| [guides/engineer/testing.md](guides/engineer/testing.md) | Unit tests, integration tests, Testcontainers, bearer token patterns |
| [guides/engineer/docker.md](guides/engineer/docker.md) | Compose stacks, services, volumes, health checks, rebuild workflow |
| [guides/engineer/ci-cd.md](guides/engineer/ci-cd.md) | backend-ci, frontend-ci, docker-build workflows explained |
| [guides/engineer/object-storage.md](guides/engineer/object-storage.md) | Local vs S3-compatible media storage, switching providers |
| [guides/engineer/https-tls.md](guides/engineer/https-tls.md) | Embedded TLS, Nginx reverse proxy, TlsRedirectConfig |
| [guides/engineer/gdpr-compliance.md](guides/engineer/gdpr-compliance.md) | GDPR / Law 09-08 implementation: consent, anonymization, data export |
| [guides/engineer/troubleshooting.md](guides/engineer/troubleshooting.md) | Symptom-based troubleshooting for the most common issues |

---

## For Users

These guides use plain language and avoid technical jargon.

| File | Description |
|------|-------------|
| [guides/user/overview.md](guides/user/overview.md) | What the platform does, the four user roles, key concepts |
| [guides/user/getting-started-user.md](guides/user/getting-started-user.md) | First login, changing password, navigating the dashboard |
| [guides/user/contacts.md](guides/user/contacts.md) | Managing prospects and clients, consent, GDPR rights |
| [guides/user/properties.md](guides/user/properties.md) | Property catalog, all 9 types, media uploads, CSV import |
| [guides/user/sales-pipeline.md](guides/user/sales-pipeline.md) | Prospect to deposit to reservation to signed contract |
| [guides/user/dashboard.md](guides/user/dashboard.md) | Reading commercial KPIs, filtering by agent and date |
| [guides/user/client-portal.md](guides/user/client-portal.md) | Magic link, viewing contracts and payment schedules |
| [guides/user/notifications.md](guides/user/notifications.md) | In-app notifications, email/SMS reminders |
| [guides/user/gdpr-rights.md](guides/user/gdpr-rights.md) | What data is stored, how to request export or deletion |

---

## For Learners

These modules teach the architectural concepts used in this codebase, from first principles.

| File | Description |
|------|-------------|
| [course/README.md](course/README.md) | Course overview, learning path, estimated time per module |
| [course/01-multi-tenancy.md](course/01-multi-tenancy.md) | TenantContext ThreadLocal, JWT tid claim, tenant-scoped JPA queries |
| [course/02-jwt-authentication.md](course/02-jwt-authentication.md) | JWT anatomy, claims, HS256 signing, JwtProvider, token revocation |
| [course/03-rbac.md](course/03-rbac.md) | Spring Security roles, @PreAuthorize patterns, ROLE_PORTAL |
| [course/04-spring-security-filters.md](course/04-spring-security-filters.md) | SecurityConfig filter chain, JwtAuthenticationFilter, CORS |
| [course/05-rate-limiting.md](course/05-rate-limiting.md) | Bucket4j token buckets, per-IP and per-identity limits, LoginRateLimiter |
| [course/06-account-lockout.md](course/06-account-lockout.md) | Failed login counter, lockout duration, auto-reset logic |
| [course/07-liquibase.md](course/07-liquibase.md) | Changelog, changesets, additive-only strategy, preconditions |
| [course/08-jpa-hibernate.md](course/08-jpa-hibernate.md) | JPA entity patterns, Spring Data, JPQL, N+1, tenant isolation |
| [course/09-outbox-pattern.md](course/09-outbox-pattern.md) | Transactional outbox, SKIP LOCKED, exponential backoff, providers |
| [course/10-caching-caffeine.md](course/10-caching-caffeine.md) | In-process Caffeine cache, CacheConfig, all cache names and TTLs |
| [course/11-caching-redis.md](course/11-caching-redis.md) | Redis distributed cache, when to use vs Caffeine, configuration |
| [course/12-health-actuator.md](course/12-health-actuator.md) | Actuator endpoints, health indicators, Docker health check timing |
| [course/13-docker-containers.md](course/13-docker-containers.md) | Multi-stage Dockerfile, layertools, non-root user, health check |
| [course/14-docker-compose.md](course/14-docker-compose.md) | Compose file merge, depends_on, healthcheck timing pitfalls |
| [course/15-ci-cd-github-actions.md](course/15-ci-cd-github-actions.md) | GHA workflows, job dependencies, GHCR push, smoke test |
| [course/16-object-storage-s3.md](course/16-object-storage-s3.md) | S3-compatible storage, MinIO, AWS SDK v2, path-style access |
| [course/17-angular-architecture.md](course/17-angular-architecture.md) | Angular 19 standalone, interceptors, guards, route structure |
| [course/18-gdpr-law0908.md](course/18-gdpr-law0908.md) | GDPR / Moroccan Law 09-08, consent, anonymization, privacy notice |
| [course/19-observability-otel.md](course/19-observability-otel.md) | OpenTelemetry, OTLP exporter, Micrometer tracing, Actuator |

---

## Specifications

Technical and functional specifications.

| File | Description |
|------|-------------|
| [spec/functional-spec.md](spec/functional-spec.md) | Numbered requirements per module, state machines, implementation status |
| [spec/technical-spec.md](spec/technical-spec.md) | Component diagram, full tech stack, multi-tenancy, caching, container architecture |
| [spec/api-reference.md](spec/api-reference.md) | Every endpoint: method, path, roles, request/response bodies, error codes |

---

## Context and Architecture

Internal architecture documents for engineers maintaining this codebase.

| File | Description |
|------|-------------|
| [context/ARCHITECTURE.md](context/ARCHITECTURE.md) | Full architecture overview, request lifecycle, Mermaid component diagram |
| [context/DATA_MODEL.md](context/DATA_MODEL.md) | Every DB table, all columns, indexes, FK constraints, ER diagram |
| [context/SECURITY_BASELINE.md](context/SECURITY_BASELINE.md) | All security mechanisms: JWT, rate limiting, lockout, CORS, TLS, GDPR |
| [context/MODULES.md](context/MODULES.md) | One section per domain package: key classes, endpoints, business rules |
| [context/DECISIONS.md](context/DECISIONS.md) | Architectural decisions and rationale derived from code patterns |
