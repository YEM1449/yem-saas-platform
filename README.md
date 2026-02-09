# YEM SaaS Platform

Multi-tenant CRM platform for real estate promotion and construction teams. The backend is a Spring Boot service that enforces tenant isolation via JWT claim `tid` and a request-scoped `TenantContext`. Each tenant owns its own data, and access is restricted by tenant-aware repositories and RBAC roles.

## Quickstart (backend)

**Requirements**
- Java 21 (see `hlm-backend/pom.xml`)
- Docker (for integration tests with Testcontainers)

**Run locally**
```bash
cd hlm-backend
./mvnw spring-boot:run
```

**Profiles**
- Default profile uses PostgreSQL and Liquibase validation.
- Test profiles are configured in `hlm-backend/src/test/resources`.

## Environment variables (placeholders only)
- `DB_URL=<DB_URL>`
- `DB_USER=<DB_USER>`
- `DB_PASSWORD=<DB_PASSWORD>`
- `JWT_SECRET=<JWT_SECRET>` (required, HS256)
- `JWT_TTL_SECONDS=<JWT_TTL_SECONDS>`

## Tests
```bash
cd hlm-backend
./mvnw test
```

## Documentation
- Project docs: [docs/index.md](docs/index.md)
- AI Context Pack: [docs/ai/quick-context.md](docs/ai/quick-context.md) | [docs/ai/deep-context.md](docs/ai/deep-context.md) | [docs/ai/prompt-playbook.md](docs/ai/prompt-playbook.md)
- Working agreement for agents: [GPT.md](GPT.md)
