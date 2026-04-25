# YEM SaaS Platform Learning Path

This course turns the repository into a structured training program for engineers, advanced interns, and students.

## 1. What This Course Is For

Use the modules to learn:

- how a multi-societe SaaS is modeled
- how auth, roles, and cookies work in a real product
- how a sales pipeline becomes code, schema, and UI
- how compliance, storage, and async delivery affect architecture

## 2. How To Study

For each module:

1. read the concept section
2. open the listed files
3. trace the implementation in code
4. connect it back to the user workflow
5. complete the suggested exercise

## 3. Recommended Order

### Foundations

1. [01-multi-tenancy.md](01-multi-tenancy.md)
2. [02-jwt-authentication.md](02-jwt-authentication.md)
3. [03-token-revocation.md](03-token-revocation.md)
4. [04-rbac.md](04-rbac.md)
5. [05-filter-chain.md](05-filter-chain.md)
6. [06-domain-layer.md](06-domain-layer.md)
7. [07-liquibase-migrations.md](07-liquibase-migrations.md)
8. [08-error-handling.md](08-error-handling.md)
9. [09-caching.md](09-caching.md)
10. [10-outbox-pattern.md](10-outbox-pattern.md)

### Business Domain

11. [11-contact-state-machine.md](11-contact-state-machine.md)
12. [12-property-lifecycle.md](12-property-lifecycle.md)
13. [13-sales-pipeline.md](13-sales-pipeline.md)
14. [14-gdpr-compliance.md](14-gdpr-compliance.md)
15. [15-client-portal.md](15-client-portal.md)

### Platform Infrastructure

16. [16-pdf-generation.md](16-pdf-generation.md)
17. [17-object-storage.md](17-object-storage.md)
18. [18-rate-limiting-and-lockout.md](18-rate-limiting-and-lockout.md)
19. [19-observability-otel.md](19-observability-otel.md)
20. [20-3d-visualiseur.md](20-3d-visualiseur.md)

## 4. Companion Documents

- architecture: [../context/ARCHITECTURE.md](../context/ARCHITECTURE.md)
- data model: [../context/DATA_MODEL.md](../context/DATA_MODEL.md)
- specifications: [../spec/functional-spec.md](../spec/functional-spec.md)
- engineer onboarding: [../guides/engineer/getting-started.md](../guides/engineer/getting-started.md)
- user understanding: [../guides/user/overview.md](../guides/user/overview.md)

## 5. Learning Outcomes

By the end of the course you should be able to:

- explain why the platform uses a shared-schema multi-societe architecture
- trace a request from browser to database
- implement a new feature without breaking scoping or auth
- understand how sales, contracts, payments, and portal access fit together
- understand how a Three.js viewer integrates with Angular and the live data model
- make documentation and code evolve together
