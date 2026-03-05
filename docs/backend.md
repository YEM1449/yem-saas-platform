# Backend Guide (Spring Boot)

## Tech stack
- **Language/runtime:** Java 21
- **Framework:** Spring Boot 3.5.x
- **Database:** PostgreSQL
- **Migrations:** Liquibase (YAML changesets)
- **Security:** Spring Security + JWT (HS256)

## Configuration

### Environment variables
| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/hlm` | JDBC URL for PostgreSQL |
| `DB_USER` | `hlm_user` | Database username |
| `DB_PASSWORD` | `hlm_pwd` | Database password |
| `JWT_SECRET` | *(required)* | HMAC secret used to sign JWTs |
| `JWT_TTL_SECONDS` | `3600` | Token TTL in seconds |

The backend reads these values from `application.yml` and fails fast if `JWT_SECRET` is blank.

### Service ports
- **HTTP API:** `http://localhost:8080`
- **Actuator health:** `GET /actuator/health`

## Authentication & tenant isolation

1. **Login:** `POST /auth/login` validates tenant + user and returns a JWT.
2. **JWT claims:** `sub` (user ID), `tid` (tenant ID), and `roles` (list of role strings).
3. **Request filter:** `JwtAuthenticationFilter` validates the token, sets Spring Security authentication, and stores the tenant in `TenantContext`.
4. **Repository scoping:** repositories query data by `tenant_id` to guarantee isolation.

## RBAC rules
- **Roles:** `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_AGENT`.
- **Usage:** controllers/services typically enforce permissions via `@PreAuthorize`.
- **Defaults:** when no roles are present, the system treats the user as `ROLE_AGENT`.

## Error contract
All endpoints return a consistent JSON shape on error, backed by `ErrorResponse` and `ErrorCode`:

```json
{
  "timestamp": "2026-02-09T12:00:00.000+00:00",
  "status": 401,
  "error": "Unauthorized",
  "code": "UNAUTHORIZED",
  "message": "Full authentication is required",
  "path": "/api/properties",
  "fieldErrors": null
}
```

## Database & migrations

- **Liquibase master:** `hlm-backend/src/main/resources/db/changelog/db.changelog-master.yaml`
- **Changesets:** stored in `db/changelog/changes/` with numeric prefixes.
- **Schema validation:** Hibernate is configured with `ddl-auto: validate`; schema changes must come from Liquibase.

## Local development

### Run
```bash
cd hlm-backend
./mvnw spring-boot:run
```

### Tests
```bash
cd hlm-backend
./mvnw test
./mvnw failsafe:integration-test failsafe:verify
```

Integration tests require Docker for Testcontainers.

## API references
- Full endpoint catalog: [api.md](api.md)
- Curl walkthrough: [api-quickstart.md](api-quickstart.md)
- Operational troubleshooting: [runbook.md](runbook.md)
