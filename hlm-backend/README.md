# HLM Backend

Spring Boot backend for the YEM SaaS Platform. Provides multi-tenant REST APIs secured with JWT and role-based access control.

## Quickstart

```bash
cd hlm-backend
cp ../.env.example ../.env
export $(grep -v '^#' ../.env | xargs)
./mvnw spring-boot:run
```

Health check:
```bash
curl -i http://localhost:8080/actuator/health
```

## Documentation

- Root overview: [../docs/overview.md](../docs/overview.md)
- Backend guide: [../docs/backend.md](../docs/backend.md)
- API catalog: [../docs/api.md](../docs/api.md)
- Runbook: [../docs/runbook.md](../docs/runbook.md)
