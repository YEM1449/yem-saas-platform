# HLM Backend

Spring Boot backend for the YEM SaaS Platform.

## Responsibilities

The backend currently provides:

- CRM and portal authentication
- societe and membership administration
- project, property, contact, reservation, deposit, contract, payment, task, document, commission, audit, and dashboard APIs
- schedulers for reminders, expirations, retention, and outbox delivery

## Run Locally

```bash
cd hlm-backend
./mvnw spring-boot:run
```

Or with the local profile:

```bash
cd hlm-backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Verify

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@demo.ma","password":"Admin123!Secure"}'
```

## Important Runtime Notes

- CRM login is email/password only.
- Multi-societe users may receive `requiresSocieteSelection=true` and must call `/auth/switch-societe`.
- `SUPER_ADMIN` tokens are platform-level and may omit `sid`.
- Portal authentication is separate and lives under `/api/portal/*`.

## Main Entry Points

| Area | Path |
| --- | --- |
| Auth | `/auth/*` |
| CRM API | `/api/*` |
| Portal API | `/api/portal/*` |
| Super-admin API | `/api/admin/societes/*` |
| Actuator | `/actuator/*` |

## Further Reading

- [../docs/context/ARCHITECTURE.md](../docs/context/ARCHITECTURE.md)
- [../docs/context/SECURITY_BASELINE.md](../docs/context/SECURITY_BASELINE.md)
- [../docs/spec/api-reference.md](../docs/spec/api-reference.md)
- [../docs/runbook-operations.md](../docs/runbook-operations.md)
