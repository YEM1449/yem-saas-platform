# Engineer Onboarding Course

This course takes a new backend engineer from zero understanding to full production-readiness on the YEM SaaS Platform. It is structured as 19 sequential modules, each covering one architectural domain.

## How to Use This Course

Read each module in order. Each module:
- States learning objectives
- Explains the concept in the context of this codebase
- Points to the relevant source files
- Includes a hands-on exercise to verify understanding

Estimated time: 2–3 hours total for all modules.

---

## Module Index

| # | Module | Topic |
|---|--------|-------|
| 01 | [Multi-Tenancy](01-multi-tenancy.md) | How tenant isolation is enforced |
| 02 | [JWT Authentication](02-jwt-authentication.md) | Token generation, validation, and claims |
| 03 | [Token Revocation](03-token-revocation.md) | Token version + UserSecurityCache pattern |
| 04 | [RBAC](04-rbac.md) | Roles, @PreAuthorize, permission matrix |
| 05 | [Filter Chain](05-filter-chain.md) | Security filter order and request lifecycle |
| 06 | [Domain Layer](06-domain-layer.md) | Entities, repositories, services, controllers |
| 07 | [Liquibase Migrations](07-liquibase-migrations.md) | Additive-only changeset strategy |
| 08 | [Error Handling](08-error-handling.md) | ErrorCode, GlobalExceptionHandler, ErrorResponse |
| 09 | [Caching](09-caching.md) | Caffeine vs Redis, @Cacheable, cache names |
| 10 | [Outbox Pattern](10-outbox-pattern.md) | Transactional email/SMS with SKIP LOCKED |
| 11 | [Contact State Machine](11-contact-state-machine.md) | ContactStatus transitions and enforcement |
| 12 | [Property Lifecycle](12-property-lifecycle.md) | PropertyStatus, soft delete |
| 13 | [Sales Pipeline](13-sales-pipeline.md) | Reservation → Deposit → Contract → Payment |
| 14 | [GDPR Compliance](14-gdpr-compliance.md) | Export, rectify, anonymize, retention |
| 15 | [Client Portal](15-client-portal.md) | Magic link auth, ROLE_PORTAL, portal JWT |
| 16 | [PDF Generation](16-pdf-generation.md) | openhtmltopdf + Thymeleaf patterns |
| 17 | [Object Storage](17-object-storage.md) | LocalFile vs S3-compatible, MediaStorage interface |
| 18 | [Rate Limiting and Lockout](18-rate-limiting-and-lockout.md) | Bucket4j, login buckets, account lockout |
| 19 | [Observability and OTel](19-observability-otel.md) | Health, correlation IDs, OTLP tracing |
