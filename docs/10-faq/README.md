# Frequently Asked Questions

**Updated**: 2026-03-25

---

## Getting Started

**Q: What is the minimum I need to run the stack locally?**

Docker + Compose v2. Copy `.env.example` to `.env`, set `JWT_SECRET` (32+ chars), then
`docker compose up -d`. No local Java or Node required for Mode A (full Docker Compose).
See [07-deployment/local-setup.md](../07-deployment/local-setup.md).

**Q: What's the difference between Docker Compose mode and mixed-mode setup?**

- **Full Docker Compose**: all services (backend + frontend + infra) run in containers.
  Easiest to start but slower iteration cycle for code changes.
- **Mixed mode**: Postgres/Redis/MinIO in Docker, backend + frontend on host with
  `./mvnw spring-boot:run` and `npm start`. Gives hot-reload and faster feedback.

**Q: What seed accounts are available out of the box?**

| Email | Password | Role |
| --- | --- | --- |
| `admin@acme.com` | `Admin123!Secure` | `ROLE_ADMIN` (societe: acme) |
| `superadmin@yourcompany.com` | `YourSecure2026!` | `SUPER_ADMIN` |

Change these in any non-disposable environment.

**Q: How do I bootstrap the first SUPER_ADMIN in a fresh production deployment?**

Set `APP_BOOTSTRAP_ENABLED=true`, `APP_BOOTSTRAP_EMAIL`, and `APP_BOOTSTRAP_PASSWORD` in
`.env`, start the backend, then remove those three variables and restart. The bootstrap is
idempotent — it will not create a duplicate if the email already exists.

---

## Authentication & Authorization

**Q: Why does login return `requiresSocieteSelection: true`?**

The user belongs to multiple sociétés. The backend returns a short-lived partial token (300s)
and a list of available sociétés. The client must call:

```
POST /auth/switch-societe
Authorization: Bearer <partial-token>
{ "societeId": "<uuid>" }
```

This returns a full scoped JWT. Note: the current Angular login UI does not yet implement this
selection step — it assumes single-societe users.

**Q: How does token revocation work?**

Each JWT carries a `tv` (token version) claim. `JwtAuthenticationFilter` compares it to the
cached value from `UserSecurityCacheService`. Events that increment `token_version` and thus
invalidate existing tokens: role change, user disable, password reset, certain membership
changes. Revocation is near-immediate, bounded only by the cache TTL.

**Q: What is a partial token?**

A JWT with `"partial": true` and a 300-second TTL. It is **only** valid on
`POST /auth/switch-societe`. Any other API call returns 401. After societe selection,
the backend issues a full scoped token.

**Q: How does the buyer portal magic link work?**

1. Buyer calls `POST /api/portal/auth/request-link` with their email and the societe key.
2. Backend rate-limits the request, generates a random 32-byte token, stores only its
   SHA-256 hash, and sends the raw token in an email link.
3. Buyer clicks the link → `GET /api/portal/auth/verify?token=...`.
4. Backend verifies the hash, marks the token as used (one-time), and issues a
   `ROLE_PORTAL` JWT valid for 2 hours.

Security properties: only the hash stored (raw token never persisted), 48-hour TTL,
one-time use, rate-limited.

**Q: Can a ROLE_ADMIN invite another ROLE_ADMIN?**

No. Only `SUPER_ADMIN` can assign the `ADMIN` role to a company member. A company `ADMIN`
can only assign `MANAGER` or `AGENT`. Enforced by `SocieteRoleValidator`.

---

## Multi-Société Architecture

**Q: What is a societe?**

A company entity (`societe` table). It represents a real-estate agency or operator on the
platform. Each societe has its own members (`app_user_societe`), data (contacts, properties,
etc.), quotas, and branding.

**Q: How is data isolated between sociétés?**

Three complementary layers:
1. **Application**: `SocieteContext` (ThreadLocal) + `requireSocieteId()` in every service
   + `WHERE societe_id = ?` in every repository query.
2. **AOP**: `RlsContextAspect` fires inside each transaction and sets the PostgreSQL
   transaction-local variable `app.current_societe_id`.
3. **Database**: PostgreSQL Row-Level Security (RLS) policies on all 13 domain tables enforce
   that only rows matching the session's `societe_id` are visible.

**Q: What is `SocieteContext` and where is it set?**

`SocieteContext` is a class of five `ThreadLocal` fields (societeId, userId, role, superAdmin,
impersonatedBy). It is set by `JwtAuthenticationFilter` after JWT validation and cleared in
its `finally` block. Services read it via `SocieteContextHelper.requireSocieteId()`.

**Q: What is the nil-UUID (`00000000-…`) sentinel?**

RLS policies allow rows to bypass the societe filter when the session variable is set to the
nil UUID `00000000-0000-0000-0000-000000000000`. This lets system schedulers (which run
without a user request context) query across all sociétés.
`SocieteContextHelper.runAsSystem(societeId, ...)` temporarily sets this sentinel.

**Q: How do schedulers run across all sociétés safely?**

Schedulers call `SocieteContextHelper.runAsSystem(nil-UUID, () -> ...)` to bypass RLS, then
iterate over all active societe IDs and call services with explicit `societeId` parameters.
`ShedLock` prevents duplicate execution across instances (`@SchedulerLock` on
`OutboundDispatcherScheduler.poll()`).

---

## Development

**Q: What test convention does this project use?**

- `*Test.java` → unit tests → run via `mvn test` (Surefire)
- `*IT.java` → integration tests → run via `mvn failsafe:integration-test` (Failsafe, needs Docker for Testcontainers)
- `mvn verify` runs both.
- E2E: Playwright in `hlm-frontend/e2e/`, run via `npx playwright test`.

**Q: Why can't I use `@Transactional` on IT test classes?**

`AuditEventListener` uses `Propagation.REQUIRES_NEW`, which opens a separate DB connection.
If the test wraps everything in a transaction, the `REQUIRES_NEW` connection can't see the
uncommitted test data → FK violation (500 instead of 201). Instead, use unique email UIDs in
`@BeforeEach` for test isolation, not transaction rollback.

**Q: What is the next available Liquibase changeset number?**

**053**. Changesets 001–052 are applied. All changesets are additive-only — never modify an
existing one after deployment.

**Q: What's the difference between `/api/users` and `/api/mon-espace/utilisateurs`?**

- `/api/users` → `AdminUserController` — platform-level user CRUD (create users, manage roles).
  **Not** `/api/admin/users` — that prefix is SUPER_ADMIN-only in `SecurityConfig`.
- `/api/mon-espace/utilisateurs` → `UserManagementController` — HR/membership surface for
  managing company members, including invitation rate limiting and quota enforcement.

**Q: How do I add a new domain module?**

See [03-backend/README.md](../03-backend/README.md) for the step-by-step guide. The short
version: Liquibase changeset → JPA entity (with `societe_id`) → repository (with societe
filter) → service (`requireSocieteId()` first) → DTOs → controller with `@Tag` annotation →
unit test → IT test (no `@Transactional` on test class).

---

## Operations

**Q: The backend container is unhealthy — how do I diagnose it?**

```bash
docker compose logs hlm-backend --tail=100
```

Look for:
1. Liquibase errors (`relation "X" does not exist` → changeset references a dropped table)
2. `JWT_SECRET @NotBlank` → missing env var
3. Database connection refused → Postgres not ready; start infra first
4. Java heap OOM → increase `JAVA_OPTS` memory

**Q: Messages are queued but not delivered — what should I check?**

1. Is `EMAIL_HOST` configured? Without it, `NoopEmailSender` is active (logs only).
2. Is `OutboundDispatcherService` running? Check logs for `[outbox]` entries.
3. Are there SMTP authentication errors in logs?
4. Check `outbound_message` table: `SELECT status, error_message FROM outbound_message WHERE status = 'FAILED' LIMIT 10;`

**Q: A portal magic link is expired or says "already used" — what to do?**

- Already used: one-time tokens cannot be reissued; the buyer must request a new link via
  `POST /api/portal/auth/request-link`.
- Expired: tokens expire after 48 hours; same — request a new link.
- Verify `PORTAL_BASE_URL` is set to your actual domain — a wrong URL means the link in the
  email points to the wrong server.

**Q: How do I reset the database in development?**

```bash
docker compose down -v   # removes volumes (ALL data lost)
docker compose up -d     # starts fresh; Liquibase re-applies all changesets
```

---

## Security

**Q: Why was `opentelemetry-exporter-otlp` removed from `pom.xml`?**

It spawns `BatchSpanProcessor_WorkerThread-1` and `BatchLogRecordProcessor_WorkerThread-1`
background threads that never stop on JVM shutdown. This caused warnings in smoke tests and
prevents a clean shutdown. The `micrometer-tracing-bridge-otel` bridge (which provides the
Micrometer integration) is still on the classpath — only the OTLP exporter itself was
removed. Add it back when an OTel collector endpoint is actively configured and managed.

**Q: Which tables have PostgreSQL RLS enabled?**

All 13 domain tables (Wave 4, changeset 051):
`contact`, `property`, `project`, `property_reservation`, `sale_contract`, `deposit`,
`commission_rule`, `task`, `document`, `notification`, `property_media`,
`payment_schedule_item`, `schedule_payment`, `schedule_item_reminder`

Infrastructure tables (`outbound_message`, `portal_token`, `app_user`, `societe`,
`app_user_societe`, `shedlock`) are excluded — no per-request societe filter needed on them.

**Q: What does the nil-UUID bypass do in RLS policies?**

When `app.current_societe_id` is set to `00000000-0000-0000-0000-000000000000`, the RLS
policy's `USING` clause short-circuits and allows all rows to be visible. This is the system
bypass used by schedulers via `SocieteContextHelper.runAsSystem()`. It is not a security hole
because:
- Only the application's DB user can set this session variable
- RLS is `FORCE ROW LEVEL SECURITY` — table owners are not exempt
- The nil-UUID is never issued as a real societe ID
