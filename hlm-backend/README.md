# HLM Backend

The backend is a Spring Boot 3.5 application that exposes the CRM API, the superadmin API, and the buyer portal API.

## Responsibilities

- authenticate staff users and portal buyers
- enforce societe isolation and role-based access control
- manage CRM aggregates such as contacts, properties, reservations, deposits, ventes, contracts, payments, tasks, and documents
- run schedulers for reminders, retention, token cleanup, and outbox dispatch
- generate PDFs and integrate with storage, email, SMS, and observability backends

## Structure

The codebase is domain-oriented. Most modules follow this shape:

```text
<module>/
  api/
  domain/
  repo/
  service/
```

Cross-cutting packages include:

- `auth` for session issuance, cookies, filters, revocation, and lockout
- `common` for errors, validation, rate limiting, shared DTOs, and utility code
- `societe` for multi-societe context, membership, impersonation, and RLS hooks

## Run Locally

```bash
cd hlm-backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Verify

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/swagger-ui.html
```

## Important Runtime Notes

- staff auth is cookie-based through `hlm_auth`
- multi-societe staff users may receive a partial token before final societe selection
- portal auth is separate and uses `hlm_portal_auth`
- schema changes are managed only through Liquibase
- RLS relies on `RlsContextAspect` running inside an open transaction

## Read Next

- [../docs/context/ARCHITECTURE.md](../docs/context/ARCHITECTURE.md)
- [../docs/context/SECURITY_BASELINE.md](../docs/context/SECURITY_BASELINE.md)
- [../docs/spec/technical-spec.md](../docs/spec/technical-spec.md)
- [../docs/guides/engineer/backend-deep-dive.md](../docs/guides/engineer/backend-deep-dive.md)
