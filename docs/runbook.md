# Runbook

## Local run (backend)
1. Export required env vars (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, optional `JWT_TTL_SECONDS`).
2. Start PostgreSQL (local or container).
3. Run the service:
   - `cd hlm-backend`
   - `./mvnw spring-boot:run`

## Troubleshooting

### Testcontainers / Docker issues
- Symptoms: tests hang, `Connection refused`, or container startup failures.
- Checks:
  - Docker is running and accessible.
  - CI/host has enough memory for Postgres.
  - Testcontainers uses a singleton Postgres container (`IntegrationTestBase`).

### CORS preflight failures
- Symptoms: browser blocks requests on `OPTIONS`.
- Fix: ensure frontend origin is in `CorsConfig` allowed origins (e.g., localhost dev ports).

### 401 Unauthorized
- Causes: missing/expired token, invalid signature, missing `tid` claim.
- Fix: re-authenticate via `/auth/login`, ensure JWT_SECRET matches issuing secret.

### 403 Forbidden
- Causes: role missing or tenant mismatch.
- Fix: verify user role in JWT `roles` and tenant claim `tid`.

### Liquibase validation errors
- Causes: manual schema edits or out-of-sync changesets.
- Fix: never edit applied changesets; add new changesets and rerun.

## Operational notes
- **Secret rotation:** rotate `JWT_SECRET` during maintenance and invalidate old tokens by expiring TTLs.
- **No fallback secrets:** app should fail-fast if `JWT_SECRET` is missing.
