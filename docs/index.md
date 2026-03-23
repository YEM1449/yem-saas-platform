# YEM SaaS Platform Documentation

This documentation set is the maintained source of truth for the current codebase as of `2026-03-22`.

The platform is a multi-societe real-estate CRM built around:

- a Spring Boot backend in [hlm-backend](/home/yem/CRM-HLM/yem-saas-platform/hlm-backend)
- an Angular CRM and client portal in [hlm-frontend](/home/yem/CRM-HLM/yem-saas-platform/hlm-frontend)
- a PostgreSQL schema managed by Liquibase changesets `001` through `050`

Important:

- The implemented runtime model is `societe` + `app_user_societe`, not the older `tenant` terminology still present in some legacy learning material.
- When a guide conflicts with the documents below, trust the `Context`, `Specifications`, and `Runbook` sections first.

## Start Here

| Document | Audience | Purpose |
| --- | --- | --- |
| [../README.md](../README.md) | Everyone | Repository overview, quickstart, default credentials, doc map |
| [guides/engineer/getting-started.md](guides/engineer/getting-started.md) | Engineers | Local setup, first login, dev workflow |
| [runbook-operations.md](runbook-operations.md) | Operators | Deployment, bootstrap, smoke checks, troubleshooting |

## Context

These documents explain what the running system is and how it is structured.

| Document | Purpose |
| --- | --- |
| [context/ARCHITECTURE.md](context/ARCHITECTURE.md) | Runtime architecture, request flow, async jobs, frontend surfaces |
| [context/DATA_MODEL.md](context/DATA_MODEL.md) | Inferred domain model, key tables, relationships, constraints |
| [context/MODULES.md](context/MODULES.md) | Backend and frontend module responsibilities |
| [context/SECURITY_BASELINE.md](context/SECURITY_BASELINE.md) | AuthN, authZ, revocation, rate limits, lockout, portal security, RLS |
| [context/DECISIONS.md](context/DECISIONS.md) | Important design choices visible in the codebase |

## Specifications

These documents reconstruct the current product behavior from code and tests.

| Document | Purpose |
| --- | --- |
| [spec/functional-spec.md](spec/functional-spec.md) | Business workflows, actors, state transitions, enforced rules |
| [spec/technical-spec.md](spec/technical-spec.md) | Stack, configuration, deployment model, integration points |
| [spec/api-reference.md](spec/api-reference.md) | Endpoint catalog grouped by module, with auth and payload guidance |
| [spec/requirements-spec.md](spec/requirements-spec.md) | Functional and non-functional requirements inferred from code |

## Operations

| Document | Purpose |
| --- | --- |
| [runbook-operations.md](runbook-operations.md) | Operating the platform day to day |
| [../scripts/POSTMAN_GUIDE.md](../scripts/POSTMAN_GUIDE.md) | Manual API testing notes |

## Supporting Guides

These guides are still useful, but they are secondary to the source-of-truth documents above.

| Document | Purpose |
| --- | --- |
| [guides/engineer/getting-started.md](guides/engineer/getting-started.md) | Day-one setup |
| [guides/engineer/docker.md](guides/engineer/docker.md) | Docker and compose workflows |
| [guides/engineer/ci-cd.md](guides/engineer/ci-cd.md) | CI and image pipeline overview |
| [guides/engineer/object-storage.md](guides/engineer/object-storage.md) | Media storage backends |
| [guides/engineer/https-tls.md](guides/engineer/https-tls.md) | TLS and reverse-proxy setup |
| [guides/engineer/troubleshooting.md](guides/engineer/troubleshooting.md) | Symptom-based troubleshooting |

## Legacy Learning Material

The following folders contain educational material that predates the current `societe`-centric implementation in places:

- [course](course)
- several deep-dive guides under [guides/engineer](guides/engineer)

Treat them as historical training aids unless they explicitly state they were refreshed for the current model.
