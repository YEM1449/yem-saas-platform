# YEM SaaS Platform Documentation

This documentation set is organized around one rule:

- `context/` explains what the codebase implements today.
- `spec/` explains what the platform is expected to do and why.
- `guides/` teaches people how to build, operate, and use it.
- `course/` turns the repository into a structured learning path for newcomers and students.

Legacy duplicate user guides and deprecated AI context files were removed so there is a single canonical path through the documentation.

## Start Here

| Reader | First document | Follow-up |
| --- | --- | --- |
| Engineer joining the repo | [guides/engineer/getting-started.md](guides/engineer/getting-started.md) | [context/ARCHITECTURE.md](context/ARCHITECTURE.md) |
| Product / operations stakeholder | [spec/requirements-spec.md](spec/requirements-spec.md) | [spec/functional-spec.md](spec/functional-spec.md) |
| End user | [guides/user/overview.md](guides/user/overview.md) | [guides/user/getting-started-user.md](guides/user/getting-started-user.md) |
| Student / trainee | [course/README.md](course/README.md) | [course/01-multi-tenancy.md](course/01-multi-tenancy.md) |

## Source Of Truth: Context

| Document | Purpose |
| --- | --- |
| [context/ARCHITECTURE.md](context/ARCHITECTURE.md) | runtime topology, auth flows, request lifecycle, deployment shapes |
| [context/DATA_MODEL.md](context/DATA_MODEL.md) | aggregates, entity relationships, state models, persistence boundaries |
| [context/MODULES.md](context/MODULES.md) | backend modules, frontend feature areas, ownership map |
| [context/SECURITY_BASELINE.md](context/SECURITY_BASELINE.md) | trust boundaries, cookies, revocation, rate limiting, RLS, auditability |
| [context/DECISIONS.md](context/DECISIONS.md) | high-value design decisions and their consequences |
| [context/api-map.md](context/api-map.md) | route and endpoint map by surface and domain |
| [context/known-issues.md](context/known-issues.md) | current limitations, debt, and operational caveats |

## Product Specifications

| Document | Purpose |
| --- | --- |
| [spec/requirements-spec.md](spec/requirements-spec.md) | personas, goals, functional and non-functional requirements |
| [spec/functional-spec.md](spec/functional-spec.md) | end-to-end workflows, role permissions, business rules, state transitions |
| [spec/technical-spec.md](spec/technical-spec.md) | stack, deployment model, configuration, integrations, testing, security controls |
| [spec/api-reference.md](spec/api-reference.md) | API contract by route family, auth expectations, request / response patterns |

## Engineer Guides

| Guide | Focus |
| --- | --- |
| [guides/engineer/getting-started.md](guides/engineer/getting-started.md) | first-day setup and verification |
| [guides/engineer/backend-deep-dive.md](guides/engineer/backend-deep-dive.md) | Spring Boot architecture and coding patterns |
| [guides/engineer/frontend-deep-dive.md](guides/engineer/frontend-deep-dive.md) | Angular surfaces, session model, and route structure |
| [guides/engineer/database.md](guides/engineer/database.md) | schema evolution, RLS, indexing, and persistence rules |
| [guides/engineer/testing.md](guides/engineer/testing.md) | unit, integration, and E2E strategy |
| [guides/engineer/ci-cd.md](guides/engineer/ci-cd.md) | GitHub Actions pipeline and release expectations |
| [guides/engineer/docker.md](guides/engineer/docker.md) | local compose workflows and container responsibilities |
| [guides/engineer/https-tls.md](guides/engineer/https-tls.md) | reverse proxy, secure cookies, and transport security |
| [guides/engineer/object-storage.md](guides/engineer/object-storage.md) | local disk vs S3-compatible media/document storage |
| [guides/engineer/gdpr-compliance.md](guides/engineer/gdpr-compliance.md) | privacy requirements and technical implementation |
| [guides/engineer/troubleshooting.md](guides/engineer/troubleshooting.md) | practical debugging playbook |

## User Guides

| Guide | Audience |
| --- | --- |
| [guides/user/overview.md](guides/user/overview.md) | everyone using the platform |
| [guides/user/getting-started-user.md](guides/user/getting-started-user.md) | first-time staff users |
| [guides/user/dashboard.md](guides/user/dashboard.md) | commercial, cash, and receivables dashboards |
| [guides/user/contacts.md](guides/user/contacts.md) | contact qualification and customer records |
| [guides/user/properties.md](guides/user/properties.md) | project, immeuble, tranche, and property management |
| [guides/user/sales-pipeline.md](guides/user/sales-pipeline.md) | reservations, deposits, ventes, contracts, and schedules |
| [guides/user/notifications.md](guides/user/notifications.md) | tasks, notifications, messages, and audit activity |
| [guides/user/administration.md](guides/user/administration.md) | users, templates, superadmin, and governance workflows |
| [guides/user/client-portal.md](guides/user/client-portal.md) | buyer portal and invitation flow |
| [guides/user/gdpr-rights.md](guides/user/gdpr-rights.md) | privacy operations and data subject handling |

## Learning Path

The course material is not just reference text. Each module explains a concept, points to concrete files in the repository, and suggests exercises.

| Module | Topic |
| --- | --- |
| [course/01-multi-tenancy.md](course/01-multi-tenancy.md) | societe isolation and RLS |
| [course/02-jwt-authentication.md](course/02-jwt-authentication.md) | auth, cookies, and session issuance |
| [course/03-token-revocation.md](course/03-token-revocation.md) | revocation, cache, and membership switching |
| [course/04-rbac.md](course/04-rbac.md) | platform roles, societe roles, and route authorization |
| [course/05-filter-chain.md](course/05-filter-chain.md) | Spring Security request lifecycle |
| [course/06-domain-layer.md](course/06-domain-layer.md) | controller-service-repository patterns |
| [course/07-liquibase-migrations.md](course/07-liquibase-migrations.md) | schema evolution workflow |
| [course/08-error-handling.md](course/08-error-handling.md) | validation and error contracts |
| [course/09-caching.md](course/09-caching.md) | Caffeine, Redis, and dashboard caching |
| [course/10-outbox-pattern.md](course/10-outbox-pattern.md) | reliable async messaging |
| [course/11-contact-state-machine.md](course/11-contact-state-machine.md) | contact lifecycle |
| [course/12-property-lifecycle.md](course/12-property-lifecycle.md) | inventory model and property statuses |
| [course/13-sales-pipeline.md](course/13-sales-pipeline.md) | reservation-to-sale workflows |
| [course/14-gdpr-compliance.md](course/14-gdpr-compliance.md) | privacy-by-design in the platform |
| [course/15-client-portal.md](course/15-client-portal.md) | buyer portal architecture |
| [course/16-pdf-generation.md](course/16-pdf-generation.md) | document rendering and template flows |
| [course/17-object-storage.md](course/17-object-storage.md) | media and document storage abstractions |
| [course/18-rate-limiting-and-lockout.md](course/18-rate-limiting-and-lockout.md) | abuse resistance |
| [course/19-observability-otel.md](course/19-observability-otel.md) | logging, metrics, tracing, and diagnostics |

## Architecture Decisions

Immutable ADRs remain in [adr/](adr/). Read them after the context documents if you want historical design rationale.
