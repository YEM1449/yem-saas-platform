# Runbook

## Local run (backend)

1. Export required env vars (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, optional `JWT_TTL_SECONDS`).
   Or copy `.env.example` → `.env` and source it.
2. Start PostgreSQL (local or container).
3. Run the service:
   ```bash
   cd hlm-backend
   chmod +x mvnw
   ./mvnw spring-boot:run
   ```
4. Verify:
   ```bash
   curl -i http://localhost:8080/actuator/health
   # Expected: 200 {"status":"UP"}
   ```

## Tenant model

- Every JWT carries a `tid` (tenant ID) claim.
- `JwtAuthenticationFilter` extracts `tid` and stores it in `TenantContext` (ThreadLocal).
- All repositories are tenant-scoped — queries filter by `TenantContext.getTenantId()`.
- `TenantContext` is cleared automatically at end of each request.
- Seeded tenant: key `acme`, id `11111111-1111-1111-1111-111111111111`.

### Tenant = Societe; Group dashboards

- **Tenant == Societe**: each tenant maps 1:1 to a legal entity (societe).
- **Consolidation via Groups**: a "Group" concept sits above tenants for Direction-level KPIs.
- Group dashboards return **aggregates only** -- no cross-tenant entity listing.
- MVP: a tenant belongs to **max 1 group** (nullable `group_id` FK on `tenant`).
- Group membership is a separate mapping (`group_membership`), not tied to per-tenant `app_user`.
- Cross-tenant raw entity access remains prohibited.
- See [ADR-001](adr/ADR-001-tenant-is-societe-group-dashboards.md) for full rationale.

## RBAC conventions

| Role          | Abilities                             |
|---------------|---------------------------------------|
| `ROLE_ADMIN`  | Full CRUD on all resources            |
| `ROLE_MANAGER`| Create, Read, Update (no Delete)      |
| `ROLE_AGENT`  | Read-only                             |

- Annotations use `hasRole('ADMIN')` / `hasAnyRole('ADMIN','MANAGER')` — Spring adds the `ROLE_` prefix automatically.
- JWT `roles` claim contains full enum names: `["ROLE_ADMIN"]`.
- `JwtAuthenticationFilter` maps each role to `SimpleGrantedAuthority`.

## ErrorResponse contract

All API errors return a consistent JSON shape (see `common/error/ErrorResponse.java`):

```json
{
  "timestamp": "2026-02-09T12:00:00.000+00:00",
  "status": 401,
  "error": "Unauthorized",
  "code": "UNAUTHORIZED",
  "message": "Full authentication is required",
  "path": "/api/properties",
  "correlationId": null,
  "fieldErrors": null
}
```

Key fields:
- `code` — stable `ErrorCode` enum value (e.g. `UNAUTHORIZED`, `FORBIDDEN`, `VALIDATION_ERROR`, `NOT_FOUND`).
- `fieldErrors` — present only for 400 validation errors, array of `{ field, message }`.
- `correlationId` — reserved for future use.

## Troubleshooting

### Connection refused / backend not reachable

- **Symptom:** `curl: (7) Failed to connect to localhost port 8080: Connection refused`
- **Cause:** backend is not running, or is on a different port.
- **Fix:**
  1. Verify the backend is started: check logs for `Tomcat started on port(s): 8080`.
  2. Test: `curl -i http://localhost:8080/actuator/health`
  3. If using a non-default port, check `server.port` in `application.yml`.

### 401 Unauthorized
- **Causes:** missing/expired token, invalid signature, missing `tid` claim.
- **Fix:** re-authenticate via `POST /auth/login`, ensure `JWT_SECRET` matches the signing secret.
- **Frontend note:** check that the `Authorization: Bearer <token>` header is sent on every request (no extra spaces, no missing `Bearer` prefix).

### 403 Forbidden
- **Causes:** user's role lacks permission for the endpoint, or tenant mismatch.
- **Fix:** verify user role in JWT `roles` claim and tenant `tid` claim.
- **RBAC quick check:** decode the JWT at [jwt.io](https://jwt.io) and confirm `roles` contains the required role.

### CORS preflight failures

- **Symptom:** browser blocks requests on `OPTIONS`; console shows `Access-Control-Allow-Origin` missing.
- **Config file:** `hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/CorsConfig.java`
- **Currently allowed dev origins:**
  - `http://localhost:5173` (Vite)
  - `http://localhost:3000` (CRA / Next.js dev)
- **How CORS is wired:**
  - `SecurityConfig.java` enables CORS: `.cors(Customizer.withDefaults())` — picks up the `CorsConfigurationSource` bean from `CorsConfig`.
  - `SecurityConfig.java` permits all `OPTIONS` requests: `.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()`.
  - Allowed methods: `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
  - Allowed headers: `*` (all).
  - Exposed headers: `Location`.
  - Credentials: `false`.

**Test preflight manually:**
```bash
curl -i -X OPTIONS "http://localhost:8080/api/properties" \
  -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: authorization,content-type"
```
Expected: `200` with `Access-Control-Allow-Origin: http://localhost:5173`.

If your frontend runs on a different port, add it to `CorsConfig.allowedOrigins`.

### Liquibase validation errors
- **Causes:** manual schema edits or out-of-sync changesets.
- **Fix:** never edit applied changesets; add new changesets and rerun.
- **Common symptom:** `Validation Failed: X changesets check sum` — means someone modified an already-applied changeset.

### Hibernate ddl-auto validate errors
- **Symptom:** `SchemaManagementException` on startup — entity mapping doesn't match DB schema.
- **Cause:** `application.yml` sets `ddl-auto: validate` — Hibernate checks mappings against the actual schema but does not alter it. Schema changes come from Liquibase only.
- **Fix:** create a new Liquibase changeset that adds/alters the column/table.

### Testcontainers / Docker issues
- **Symptoms:** tests hang, `Connection refused`, or container startup failures.
- **Quick check:** `docker info` — if this fails, Docker isn't running.
- **Checks:**
  - Docker daemon is running and accessible.
  - CI/host has enough memory for PostgreSQL container.
  - Testcontainers uses a singleton PostgreSQL container via `IntegrationTestBase`.
  - On WSL2, ensure Docker Desktop integration is enabled.

### App fails to start: `JWT_SECRET` missing
- **Cause:** `JwtProperties` has `@Validated` + `@NotBlank` — no fallback.
- **Fix:** set `JWT_SECRET` env var (≥32 characters for HS256).

## Operational notes

- **Secret rotation:** rotate `JWT_SECRET` during maintenance and invalidate old tokens by expiring TTLs.
- **No fallback secrets:** app fails fast if `JWT_SECRET` is missing — this is intentional.
- **Database:** Liquibase runs on startup (`ddl-auto: validate` ensures schema matches entities).
