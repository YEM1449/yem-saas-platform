# Architecture Reference — YEM SaaS Platform (HLM)

> Document version: 2026-03-25. Reflects Wave 4 (Production Hardening) state: changesets 001–052 applied, RLS phase 2 complete, ShedLock wired, Redis cache registered.

---

## 1. Executive Summary

The YEM SaaS Platform (codename HLM) is a multi-company real-estate CRM delivered as a Software-as-a-Service product. Its central architectural goal is **hard data isolation between subscriber companies (sociétés)** at every layer of the stack — from the HTTP filter that extracts the JWT `sid` claim, through the Spring AOP advice that projects the scope into the PostgreSQL session, down to Row-Level Security policies that make cross-société leakage structurally impossible at the database engine. Orthogonal concerns — distributed scheduling, asynchronous messaging, portal access for end-buyers, and platform-level super-administration — are all designed around the same isolation contract so that a single Spring Boot binary can safely serve an unbounded number of tenants sharing one PostgreSQL instance.

---

## 2. System Topology

```
╔══════════════════════════════════════════════════════════════════════════════════╗
║  CLIENT TIER                                                                     ║
║                                                                                  ║
║  ┌────────────────┐  ┌─────────────────────┐  ┌──────────────────────────────┐  ║
║  │  CRM Shell     │  │  Super-Admin Shell  │  │  Buyer Portal                │  ║
║  │  /app/*        │  │  /superadmin/*      │  │  /portal/*                   │  ║
║  │  Angular 19    │  │  Angular 19         │  │  Angular 19                  │  ║
║  │  ROLE_ADMIN/   │  │  ROLE_SUPER_ADMIN   │  │  ROLE_PORTAL                 │  ║
║  │  MANAGER/AGENT │  │                     │  │  (magic-link JWT)            │  ║
║  └───────┬────────┘  └──────────┬──────────┘  └──────────────┬───────────────┘  ║
╚══════════╪═══════════════════════╪══════════════════════════════╪════════════════╝
           │                       │                              │
           │  HTTPS / HTTP (dev)   │                              │
           ▼                       ▼                              ▼
╔══════════════════════════════════════════════════════════════════════════════════╗
║  REVERSE PROXY                                                                   ║
║                                                                                  ║
║  ┌──────────────────────────────────────────────────────────────────────────┐   ║
║  │  Nginx                                                                   │   ║
║  │  • Serves Angular SPA static assets (/app/**, /superadmin/**, /portal/**)│   ║
║  │  • Proxies /auth/**, /api/**, /actuator/** → hlm-backend:8080            │   ║
║  │  • resolver 127.0.0.11 valid=30s; set $backend http://hlm-backend:8080;  │   ║
║  │    (late DNS resolution prevents startup race with backend container)    │   ║
║  │  • (Dev) Angular ng serve on :4200 uses proxy.conf.json instead         │   ║
║  └──────────────────────────┬───────────────────────────────────────────────┘   ║
╚═════════════════════════════╪════════════════════════════════════════════════════╝
                              │
                              ▼
╔══════════════════════════════════════════════════════════════════════════════════╗
║  APPLICATION TIER                                                                ║
║                                                                                  ║
║  ┌──────────────────────────────────────────────────────────────────────────┐   ║
║  │  Spring Boot 3.5.8 / Java 21  (hlm-backend)                             │   ║
║  │                                                                          │   ║
║  │  JwtAuthenticationFilter  →  SecurityConfig  →  Controllers (23 modules)│   ║
║  │  RlsContextAspect  →  @Transactional  →  Repositories  →  JPA/Hibernate │   ║
║  │  SocieteContextTaskDecorator  →  @Async thread pool (hlm-async-*)       │   ║
║  │  ShedLock  →  @SchedulerLock schedulers (6 scheduled components)        │   ║
║  └──────────┬─────────────────┬──────────────────────┬───────────────────┘    ║
╚═════════════╪═════════════════╪══════════════════════╪════════════════════════╝
              │                 │                      │
              ▼                 ▼                      ▼
╔═════════════════╗  ╔══════════════════╗  ╔════════════════════════════════════╗
║  DATA TIER       ║  ║  CACHE TIER      ║  ║  EXTERNAL SERVICES (optional)     ║
║                  ║  ║                  ║  ║                                    ║
║  PostgreSQL 16   ║  ║  Caffeine        ║  ║  SMTP server      (EMAIL_HOST)    ║
║  • RLS on 14     ║  ║  (default,       ║  ║  Twilio / SMS     (TWILIO_*)      ║
║    domain tables ║  ║   in-process)    ║  ║  MinIO / S3       (MINIO_*)       ║
║  • shedlock      ║  ║                  ║  ║  OTel collector   (OTLP_ENDPOINT) ║
║    table (052)   ║  ║  Redis           ║  ║                                    ║
║  • Liquibase     ║  ║  (optional,      ║  ║  Prometheus/      (management.*   ║
║    changesets    ║  ║   REDIS_ENABLED= ║  ║  Grafana          actuator)       ║
║    001–052       ║  ║   true)          ║  ║                                    ║
╚═════════════════╝  ╚══════════════════╝  ╚════════════════════════════════════╝
```

**Dev vs. production proxy:** In development (`ng serve` on port 4200), `hlm-frontend/proxy.conf.json` proxies `/auth`, `/api`, `/dashboard`, and `/actuator` to `http://localhost:8080`. In production, Nginx serves the pre-built Angular bundle and forwards the same paths to the backend container.

---

## 3. Identity and Scope Model

### 3.1 Core Tables

| Table | Purpose | Key Columns |
|---|---|---|
| `app_user` | CRM staff identity; one row per human | `id UUID PK`, `email`, `password_hash`, `enabled`, `token_version INT`, `locked_until` |
| `societe` | Subscriber company (tenant) | `id UUID PK`, `key VARCHAR UNIQUE`, `nom`, `max_utilisateurs INT`, `actif BOOL` |
| `app_user_societe` | Many-to-many membership with per-société role | `(user_id, societe_id) PK`, `role VARCHAR CHECK(ADMIN\|MANAGER\|AGENT)`, `actif BOOL` |

### 3.2 Role Taxonomy

| Role | Scope | JWT `sid` claim | CRM access |
|---|---|---|---|
| `ROLE_SUPER_ADMIN` | Platform-wide | Absent (no société scope) | `/api/admin/**` only |
| `ROLE_ADMIN` | Per-société | Present | Full CRUD within société |
| `ROLE_MANAGER` | Per-société | Present | Create/Read/Update; no delete |
| `ROLE_AGENT` | Per-société | Present | Read-only via API |
| `ROLE_PORTAL` | Per-société | Present | `/api/portal/**` read-only; `sub` = contactId |

### 3.3 Role Storage vs. JWT Representation

`AppUserSociete.role` stores the **short form**: `ADMIN`, `MANAGER`, or `AGENT`. The `chk_societe_role` CHECK constraint enforces this. At JWT generation time, `AuthService.toJwtRole()` adds the `ROLE_` prefix (`ROLE_ADMIN`). `JwtAuthenticationFilter` wraps each role string in `SimpleGrantedAuthority`, which Spring Security reads verbatim. `@PreAuthorize` expressions use `hasRole('ADMIN')` — Spring adds the prefix again automatically, so the two transforms cancel out cleanly.

### 3.4 Multi-Société Membership

A single `app_user` may belong to multiple sociétés with different roles in each. The login flow (Section 4a) detects this and issues a **partial token** (no `sid` claim) prompting the client to call `POST /auth/switch-societe`. After société selection a full scoped token is issued. This is the supported multi-société branching path in both the backend and the current Angular login UI.

### 3.5 JWT Claim Map

| Claim | Type | Description |
|---|---|---|
| `sub` | String (UUID) | `app_user.id` for CRM tokens; `contact.id` for portal tokens |
| `sid` | String (UUID) | `societe.id`; absent for SUPER_ADMIN; present but set to contactId's societe for portal |
| `roles` | String[] | e.g. `["ROLE_ADMIN"]` |
| `tv` | Integer | `app_user.token_version`; absent for portal tokens |
| `imp` | String (UUID) | Impersonating SUPER_ADMIN's userId; present only on impersonation tokens |
| `partial` | Boolean | `true` on partial tokens issued before société selection |

---

## 4. Request Lifecycle — Step by Step

### 4a. CRM Login Flow

`POST /auth/login` — handled by `AuthController.login()` → `AuthService.login()`.

1. **Rate-limit check (IP + email identity).** `LoginRateLimiter.tryConsume(ip, email, email)` consults an in-memory Bucket4j counter. Exceeds threshold → `LoginRateLimitedException` → 429. This fires before any database access to deny credential-stuffing bots cheaply.

2. **Resolve user by email.** `UserRepository.findFirstByEmail(email)`. Uses `findFirst` rather than `findBy` to tolerate any pre-migration duplicate rows. Unknown email → `UnauthorizedException` → 401 (generic; no enumeration hint).

3. **Account lockout check.** `user.isLockedOut()` tests `lockedUntil > now()`. A configured number of consecutive failures sets `lockedUntil` via `LockoutProperties`. Locked → `AccountLockedException` → 423.

4. **Account disabled check.** `user.isEnabled()` must be true. Disabled → 401.

5. **BCrypt password verification.** `passwordEncoder.matches(password, user.getPasswordHash())`. On failure, the failed-attempt counter is incremented; if threshold crossed, `lockedUntil` is set and the `AuditEvent` is written via `SecurityAuditLogger`. Result → 401.

6. **Société membership lookup.** `AppUserSocieteRepository.findByIdUserIdAndActifTrue(userId)`. A user with no active membership cannot log in (returns 401). This is mandatory; direct `userRepository.save()` alone is insufficient for test setups.

7. **Multi-société branching.**
   - **Single membership:** A full JWT is generated immediately with `sid` = that societeId. `tokenVersion` (`tv` claim) is set from `user.getTokenVersion()`.
   - **Multiple memberships:** A partial token is issued (no `sid`, `partial=true`). The response body includes a `societes[]` list. The client must call `POST /auth/switch-societe` with a chosen `societeId` to receive a full scoped token. `AuthService.switchSociete()` validates the partial token, finds the requested membership, and issues a new full token.
   - **SUPER_ADMIN:** No `sid` or `tv` claim. Token carries `roles: ["ROLE_SUPER_ADMIN"]`.

8. **UserSecurityCache population.** `UserSecurityCacheService.invalidate(userId)` / cache is lazily re-populated on next request. Cache key = userId; value = `UserSecurityInfo(enabled, tokenVersion)`. TTL 60 s (Caffeine) or 60 s (Redis).

9. **Audit log.** `SecurityAuditLogger.logSuccessfulLogin(email, societeId, ip)` writes an `AuditEvent` record via `AuditEventListener` (Spring `@TransactionalEventListener`, `AFTER_COMMIT`, `Propagation.REQUIRES_NEW`).

10. **Response.** `LoginResponse` with `accessToken`, `expiresIn`, `role`, `societeId`, `societes[]` (for partial flow).

### 4b. CRM API Request Flow

Every authenticated `GET/POST/PUT/DELETE /api/**` call traverses this pipeline:

1. **`JwtAuthenticationFilter.doFilterInternal()` (OncePerRequestFilter).**
   - Extracts `Authorization: Bearer <token>` header.
   - Calls `JwtProvider.isValid(token)` (signature verification using `JwtDecoder` bean).
   - Rejects partial tokens (`jwtProvider.isPartialToken()`) for all paths except `/auth/switch-societe`.

2. **Token version check via `UserSecurityCacheService`.**
   - Extracts `tv` claim from JWT.
   - Calls `userSecurityCacheService.getSecurityInfo(userId)` (cache-aside: Redis if `REDIS_ENABLED=true`, else Caffeine).
   - If `secInfo == null` (cache miss) → loads from DB, caches result.
   - Validates: `secInfo.enabled() == true` AND `secInfo.tokenVersion() == tokenTv`. Mismatch (role changed, user disabled, forced logout) → skip auth context → Spring Security returns 401.
   - Purpose: enables **server-side revocation** without token blacklists. Incrementing `token_version` in the DB invalidates all live tokens within one cache TTL (60 s).

3. **`SocieteContext` population (ThreadLocal).**
   - For normal CRM tokens: `SocieteContext.setSocieteId(societeId)`, `setUserId(userId)`, `setRole(role)`.
   - For SUPER_ADMIN tokens: `SocieteContext.setSuperAdmin(true)` (no `setSocieteId` call — `sid` is absent).
   - For impersonation tokens: additionally `SocieteContext.setImpersonatedBy(impersonatedByUUID)`.
   - For portal tokens: `SocieteContext.setSocieteId(societeId)`, `setUserId(contactId)`, `setRole("ROLE_PORTAL")` — `UserSecurityCacheService` is skipped entirely.

4. **Spring Security authorization.**
   - `SecurityConfig` rule table evaluated in declaration order:
     - `/api/admin/**` → `hasRole("SUPER_ADMIN")`
     - `/api/portal/**` → `hasRole("PORTAL")`
     - `/api/**` → `hasAnyRole("ADMIN","MANAGER","AGENT")`
   - `@EnableMethodSecurity` allows `@PreAuthorize` at controller/service level for finer-grained checks (e.g., `hasRole('ADMIN')` on delete operations).

5. **`TransactionInterceptor` opens a database transaction.**
   - `TransactionOrderConfig` registers `@EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE - 10)`, which means `TransactionInterceptor` has AOP order `Integer.MAX_VALUE - 10`. This makes it the **outer** proxy around `RlsContextAspect` (order `MAX_VALUE - 1`). The transaction opens on the Hikari connection pool and is bound to the thread by `JpaTransactionManager` before the RLS aspect fires.

6. **`RlsContextAspect.setSocieteIdOnConnection()` fires (inner proxy, inside TX).**
   - Pointcut: `@within(org.springframework.transaction.annotation.Transactional) || @annotation(org.springframework.transaction.annotation.Transactional)`.
   - Reads `SocieteContext.getSocieteId()`.
   - If non-null: calls `SET LOCAL app.current_societe_id = '<uuid>'` via `JdbcTemplate.queryForObject("SELECT set_config('app.current_societe_id', ?, true)", ...)`.
   - If null (SUPER_ADMIN or scheduler): sets the **nil-UUID sentinel** `00000000-0000-0000-0000-000000000000`.
   - `is_local=true` scopes the GUC to the current transaction; it is automatically cleared on commit or rollback. No manual cleanup needed.
   - The `JdbcTemplate` reuses the Hikari connection already bound by `JpaTransactionManager` — this is the critical correctness property enabled by `TransactionOrderConfig`.

7. **Service layer calls `SocieteContextHelper.requireSocieteId()`.**
   - Returns `SocieteContext.getSocieteId()` or throws `CrossSocieteAccessException("Missing société context")` if null.
   - This is the **application-layer isolation gate**. Every service method that reads or writes société-scoped data calls this as its first line.

8. **Repository executes JPQL/native query with `societe_id = :societeId`.**
   - Every repository method that returns société-scoped data includes `WHERE societe_id = :societeId` as an explicit parameter.
   - This is the **second application-layer isolation gate** — redundant with the service check but eliminates the risk of a missing `requireSocieteId()` call silently returning cross-société data.

9. **PostgreSQL RLS policy validates the row.**
   - The RLS `USING` clause checks `current_setting('app.current_societe_id', true)`.
   - If set to the nil-UUID: all rows visible (system mode).
   - If set to a real UUID: only rows where `societe_id` matches.
   - If null/empty string: no rows visible (should never reach production; defense against missing filter).
   - This is the **database-layer isolation gate** — enforced by the engine regardless of application code.

10. **Response and cleanup.**
    - Transaction commits; `is_local=true` resets `app.current_societe_id` on the connection before it returns to the pool.
    - `JwtAuthenticationFilter.doFilterInternal()` `finally` block calls `SocieteContext.clear()` — clears all five ThreadLocal slots unconditionally, preventing leaks when the servlet container reuses the thread for the next request.

### 4c. Portal Magic-Link Flow

The buyer portal uses a passwordless email link. No `app_user` record exists for portal users — they are identified by `contact.id`.

1. **Client submits email + societeKey to `POST /api/portal/auth/request-link` (public endpoint).**

2. **`PortalAuthService.requestLink(email, societeKey)`:**
   - Resolves societe by `key`.
   - Looks up `Contact` by `email` and `societeId`. If not found, returns a generic 200 (no enumeration).
   - Generates 32 bytes from `SecureRandom`, encodes to URL-safe Base64 → raw token.
   - Computes `SHA-256` hex of raw token → stored hash.
   - Saves `PortalToken(contactId, societeId, hash, expiresAt=now+48h, used=false)` to `portal_token` table (changeset 025).
   - Calls `EmailSender.send(email, subject, body)` **directly** (not via outbox) — there is no `app_user` FK for the outbox module's `Message` entity in this flow.
   - Response includes `magicLinkUrl` for dev/test convenience.

3. **Contact clicks the link: `GET /api/portal/auth/verify?token=<rawToken>` (public endpoint).**

4. **`PortalAuthService.verifyToken(rawToken)`:**
   - Computes SHA-256 of `rawToken` → finds `PortalToken` by hash.
   - Validates: not expired (`expiresAt > now()`), not already used.
   - Marks `used = true`, saves (one-time use).
   - Calls `PortalJwtProvider.generate(contactId, societeId)` → 2-hour JWT with `roles=["ROLE_PORTAL"]`, `sub=contactId`, `sid=societeId`. Note: no `tv` claim.
   - Returns `PortalTokenVerifyResponse(accessToken, contactId, societeId)`.

5. **Frontend relies on the `hlm_portal_auth` httpOnly cookie.** `PortalAuthService` and the portal interceptor use `withCredentials: true` for `/api/portal/` requests (except the public auth endpoints). On 401, `PortalAuthService.logout()` clears portal session state and redirects to `/portal/login`.

6. **`JwtAuthenticationFilter` detects `ROLE_PORTAL`** (via `isPortalToken()`) and skips `UserSecurityCacheService` — portal sessions are stateless (invalidated by TTL or one-time use). Sets `SocieteContext` with the contactId as `userId`.

7. **Portal controllers** (`PortalContractsController`, `PortalPaymentsController`, `PortalPropertyController`) extract `contactId` from `SecurityContextHolder.getAuthentication().getPrincipal()`. All queries scope to `(societeId, contactId)`. A request for another contact's data returns 404 (not 403) to avoid information disclosure.

8. **`PortalTokenCleanupScheduler.cleanup()`** runs daily at 03:00, calls `portalTokenRepository.deleteExpiredAndUsed(Instant.now())` inside `runAsSystem()`.

### 4d. Async @Async Flow

**Problem:** `SocieteContext` uses `ThreadLocal`. Spring's `@Async` executor submits `Runnable` tasks to a worker thread that has no ThreadLocals from the calling thread. Any `requireSocieteId()` call in the async method throws `CrossSocieteAccessException`.

**Solution: `SocieteContextTaskDecorator`** (`com.yem.hlm.backend.societe.async.SocieteContextTaskDecorator`).

How it works:
1. `AsyncConfig` creates a `ThreadPoolTaskExecutor` (core=4, max=16, queue=100, prefix `hlm-async-`) and registers `new SocieteContextTaskDecorator()` via `executor.setTaskDecorator(...)`.
2. All `@Async` beans use this executor by name (`taskExecutor`).
3. When a calling thread submits a task, `SocieteContextTaskDecorator.decorate(task)` is called on the **submitting thread**. It captures all five ThreadLocal values: `societeId`, `userId`, `role`, `superAdmin`, `impersonatedBy`.
4. The returned `Runnable` lambda, when executed on the **worker thread**, calls the five `SocieteContext.set*()` methods to restore the context snapshot, then calls `task.run()`, then calls `SocieteContext.clear()` in a `finally` block.
5. This `finally` clear is critical — pooled threads are reused, and a leaked ThreadLocal from task N would contaminate task N+1 with the wrong société.

The five captured fields cover all security-relevant context:
- `societeId` — needed by `requireSocieteId()` and RLS
- `userId` — needed by audit logging
- `role` — needed by `@PreAuthorize` checks in async methods
- `superAdmin` — needed by services that fork behavior for SUPER_ADMIN
- `impersonatedBy` — needed by audit logging for impersonation sessions

### 4e. Scheduler Flow

**System context (nil-UUID sentinel):**

Schedulers operate across all sociétés (e.g., expiring reservations for every company). They cannot have a société context. `SocieteContextHelper.runAsSystem(task)` provides the system mode entry point:

```
SocieteContext.setSystem()  // sets societeId=null, superAdmin=true
task.run()
SocieteContext.clear()      // in finally
```

With `societeId = null`, `RlsContextAspect.setSocieteIdOnConnection()` sets `app.current_societe_id = '00000000-0000-0000-0000-000000000000'`. All 14 RLS policies treat this nil-UUID as a bypass sentinel, making all rows visible. JPA queries in schedulers still use explicit `WHERE societe_id = ?` where applicable (e.g., iterating per-société), relying on the application layer rather than RLS for per-société filtering within the sweep.

**ShedLock — distributed exclusive execution:**

In a horizontally-scaled deployment multiple backend instances share one PostgreSQL database. Without coordination, every scheduler would fire on every instance simultaneously, causing duplicate email sends, double expiry processing, etc.

`ShedLockConfig` (`com.yem.hlm.backend.common.lock.ShedLockConfig`) registers a `JdbcTemplateLockProvider` backed by the `shedlock` table (changeset 052). Configuration: `@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")`, `.usingDbTime()` (uses DB server clock to avoid clock-skew between nodes).

`OutboundDispatcherScheduler.poll()` is annotated:
```java
@SchedulerLock(name = "outbox_dispatcher", lockAtMostFor = "PT1M", lockAtLeastFor = "PT200MS")
```

Semantics:
- `lockAtMostFor = "PT1M"`: if the holding node crashes mid-execution, the lock releases after 1 minute, allowing another node to proceed.
- `lockAtLeastFor = "PT200MS"`: prevents rapid double-fire if the method completes in under 200 ms.

ShedLock uses a PostgreSQL UPDATE with `lock_until < NOW()` to acquire; only one node wins. The loser skips that execution cycle entirely (no queue, no retry).

---

## 5. Multi-Société Isolation — Defense in Depth

Three independent layers enforce isolation. A bug in any one layer is caught by the others.

### Layer 1: Application Layer

**Mechanism:** `SocieteContextHelper.requireSocieteId()` + explicit `WHERE societe_id = ?` in every repository query.

**Where enforced:** Service classes call `requireSocieteId()` as the first statement of any method that reads or writes société data. This throws `CrossSocieteAccessException` (HTTP 403) if the ThreadLocal is not set — catching mis-routed requests, scheduler calls that forgot `runAsSystem()`, or unit tests with missing setup.

Every `@Query` and derived-method in JPA repositories includes `societe_id = :societeId` as an explicit parameter received from the service layer. This means even if `requireSocieteId()` were somehow bypassed, the query would fail with a missing parameter or return an empty result set.

**Why two checks:** The `requireSocieteId()` call provides an early-fail with a clear error. The repository `WHERE` clause provides a second gate that protects against any future refactor that adds a service method without the guard call.

### Layer 2: AOP Layer

**Mechanism:** `RlsContextAspect` sets `app.current_societe_id` on the active database connection before every `@Transactional` method body runs.

**Where enforced:** AOP pointcut `@within || @annotation` on `@Transactional` — fires for every Spring-managed transactional invocation regardless of which module, which service, or which caller initiated it.

**Why this is not the primary gate:** AOP advice can be disabled (e.g., by calling a method directly without going through the proxy). The application-layer checks in Layer 1 are therefore not redundant — they are the primary mechanism. AOP is defense-in-depth.

**Critical ordering requirement:** See Section 6.

### Layer 3: Database Layer (PostgreSQL RLS)

**Mechanism:** PostgreSQL Row-Level Security policies on all 14 domain tables, enforced by the database engine.

**Policy logic (identical on all 14 tables):**

```sql
CREATE POLICY societe_isolation_<table> ON <table>
  USING (
    current_setting('app.current_societe_id', true) = '00000000-0000-0000-0000-000000000000'
    OR (
      current_setting('app.current_societe_id', true) IS NOT NULL
      AND current_setting('app.current_societe_id', true) <> ''
      AND societe_id = current_setting('app.current_societe_id', true)::uuid
    )
  )
  WITH CHECK ( /* same expression */ );
```

Three branches:
- **Nil-UUID** (`00000000-0000-0000-0000-000000000000`): all rows visible — system/scheduler bypass.
- **Real UUID**: only rows where `societe_id` matches the session variable.
- **NULL or empty string**: no rows — fail-safe for connections that never set the variable (e.g., a raw `psql` session, Liquibase migrations).

**Tables with RLS (changeset 051):**
`contact`, `property`, `project`, `property_reservation`, `sale_contract`, `deposit`, `commission_rule`, `task`, `document`, `notification`, `property_media`, `payment_schedule_item`, `schedule_payment`, `schedule_item_reminder`

**Tables without RLS (by design):**
`outbound_message`, `portal_token`, `app_user`, `societe`, `app_user_societe`, `shedlock` — infrastructure/platform tables that are not société-scoped in the same sense, or that schedulers access without needing per-société filtering enforced at the DB layer.

**Why not rely solely on RLS:** RLS is bypassed for the PostgreSQL superuser role. Liquibase migrations and certain DBA operations run as a privileged role. Application isolation must not depend on RLS being active during those windows. Layer 1 (application) and the `WHERE societe_id` clauses handle those cases.

---

## 6. AOP Transaction Ordering (CRITICAL)

### The Problem

Spring Boot auto-configures `@EnableTransactionManagement` at `Ordered.LOWEST_PRECEDENCE` (`Integer.MAX_VALUE`). In Spring AOP, a **lower order number** means **higher priority**, which means **outer proxy**, which means the `@Before` advice fires **first** (before the method) and the `@After` fires **last** (after the method).

`RlsContextAspect` was annotated `@Order(Ordered.LOWEST_PRECEDENCE - 1)` = `MAX_VALUE - 1`. Since `MAX_VALUE - 1 < MAX_VALUE`, the RLS aspect had **lower order number** than the transaction interceptor. Therefore it was the **outer** proxy:

```
[BROKEN]
RlsContextAspect @Before   ← fires here (no TX open yet)
  TransactionInterceptor   ← opens TX here
    service method body
  TransactionInterceptor   ← commits/rolls back TX
RlsContextAspect @After    ← fires here (TX already closed)
```

The `JdbcTemplate.queryForObject("SELECT set_config(...)")` in the `@Before` advice ran on a **standalone connection** (not transaction-bound). PostgreSQL committed that SET immediately when the connection was returned to the pool. The Hibernate connection opened by the transaction interceptor never saw the variable. Every RLS policy saw `current_setting('app.current_societe_id', true)` as empty string and blocked all rows — returning 0 results for every query.

### The Fix

`TransactionOrderConfig` (`com.yem.hlm.backend.auth.config.TransactionOrderConfig`) replaces Spring Boot's auto-configured transaction management configuration:

```java
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement(proxyTargetClass = true, order = Ordered.LOWEST_PRECEDENCE - 10)
public class TransactionOrderConfig {}
```

This works because `TransactionAutoConfiguration.EnableTransactionManagementConfiguration` is `@ConditionalOnMissingBean(AbstractTransactionManagementConfiguration.class)`. By declaring our own `@EnableTransactionManagement` we suppress the auto-configuration. The TX interceptor now has order `MAX_VALUE - 10`. The RLS aspect has order `MAX_VALUE - 1`. Since `MAX_VALUE - 10 < MAX_VALUE - 1`, the TX interceptor is now the **outer** proxy:

```
[CORRECT]
TransactionInterceptor @Before   ← opens TX, binds connection to thread
  RlsContextAspect @Before       ← JdbcTemplate reuses bound connection, SET LOCAL fires
    service method body          ← Hibernate reuses same connection, sees the GUC
  RlsContextAspect @After
TransactionInterceptor @After    ← commits TX; is_local=true resets GUC automatically
```

### The Rule

**Order number lower = more outer = `@Before` fires earlier = `@After` fires later.**

To add a new AOP aspect that must run inside an active transaction, give it an order number **greater than** `Ordered.LOWEST_PRECEDENCE - 10` (i.e., `MAX_VALUE - 9` or higher). To add an aspect that must wrap the transaction (e.g., a distributed trace span), give it an order number **less than** `MAX_VALUE - 10`.

---

## 7. Backend Package Structure

Base package: `com.yem.hlm.backend`

All modules follow the four-layer sub-package pattern:
- `module/api/` — `@RestController` classes + DTO records
- `module/domain/` — JPA `@Entity` classes + enums
- `module/repo/` — Spring Data JPA `@Repository` interfaces
- `module/service/` — `@Service` business logic classes

### Module Inventory

| Module | Package suffix | Responsibility |
|---|---|---|
| `audit` | `audit` | Immutable `AuditEvent` records; `CommercialAuditController` reads event history |
| `auth` | `auth` | Login, JWT generation/validation, rate limiting, lockout, `UserSecurityCacheService`, async/cache/TX config |
| `commission` | `commission` | `CommissionRule` entity; formula engine (`agreedPrice × rate/100 + fixedAmount`); project-specific rule overrides tenant default |
| `common` | `common` | Cross-cutting: `ErrorResponse`, `ErrorCode`, `ShedLockConfig`, rate limiter, CORS config, OpenAPI config |
| `contact` | `contact` | CRM contacts (prospect/client lifecycle), `ProspectDetail`, `ClientDetail`, contact interests, GDPR-aware anonymization hooks |
| `contract` | `contract` | `SaleContract` entity; contract lifecycle (DRAFT→SIGNED→CANCELLED); PDF generation via openhtmltopdf |
| `dashboard` | `dashboard` | Commercial KPI aggregation: `CommercialDashboardService` (16 JPQL queries), receivables aging buckets, prospect source funnel |
| `deposit` | `deposit` | `Deposit` entity; reservation-to-deposit conversion; PDF generation; `DepositWorkflowScheduler` (hourly) |
| `document` | `document` | Cross-entity file attachments; `Document` entity references arbitrary entity type + id |
| `gdpr` | `gdpr` | `AnonymizationService`; `DataRetentionScheduler` (daily 02:00); GDPR Art. 5(1)(e) sweep; erasure blocked by SIGNED contracts |
| `media` | `media` | `PropertyMedia` entity; MinIO/S3 object storage integration; upload size/type enforcement |
| `notification` | `notification` | In-app CRM bell notifications (`Notification` entity); distinct from `outbox` (transactional email/SMS) |
| `outbox` | `outbox` | Transactional outbox pattern for email and SMS; `OutboundMessage` entity (changeset 017); `EmailSender`/`SmsSender` provider interfaces; `OutboundDispatcherScheduler` + ShedLock |
| `payments` | `payments` | Payment schedule (`PaymentScheduleItem`), tranches, payment calls; `ReminderScheduler` (daily 07:00); v2 only (v1 tables dropped by changeset 028) |
| `portal` | `portal` | Buyer portal: magic-link auth (`PortalAuthService`), `PortalJwtProvider`, read-only contract/payment/property views; `PortalTokenCleanupScheduler` (daily 03:00) |
| `project` | `project` | `Project` entity; project KPIs; `ACTIVE`/`ARCHIVED` status; pessimistic lock on property assignment |
| `property` | `property` | `Property` entity; `PropertyType` (`APPARTEMENT`/`VILLA`/etc.); commercial workflow (AVAILABLE→RESERVED→SOLD); property dashboard |
| `reminder` | `reminder` | CRM task reminders (`ReminderScheduler`); distinct from payment reminders in `payments` module |
| `reservation` | `reservation` | `PropertyReservation` entity; pessimistic write lock on property during creation; `ReservationExpiryScheduler` (hourly) |
| `societe` | `societe` | `Societe` entity; `AppUserSociete` membership; `SocieteContext` (ThreadLocal); `RlsContextAspect`; `SocieteContextHelper`; `SocieteContextTaskDecorator` |
| `task` | `task` | CRM tasks (`Task` entity); `GET /api/tasks` defaults to current user's assigned tasks |
| `user` | `user` | `AppUser` entity; `AdminUserController` at `/api/users`; profile endpoint (`UserProfileController` at `/api/me`); `QuotaService` |
| `usermanagement` | `usermanagement` | Invitation flow (`InvitationService`); rate-limited invite endpoint; `UserManagementController` at `/api/mon-espace/utilisateurs` |

### Cross-Cutting Modules

- **`auth`**: owns all security infrastructure (JWT, filters, configs) and is depended on by every other module indirectly.
- **`common`**: provides `ErrorResponse`/`ErrorCode` (used by all controllers), `ShedLockConfig`, `RateLimiterService`, and OpenAPI config.
- **`societe`**: provides `SocieteContext`, `SocieteContextHelper`, `RlsContextAspect`, and `SocieteContextTaskDecorator` — the isolation contract that all other modules depend on.
- **`admin`** (package `com.yem.hlm.backend.admin`): SUPER_ADMIN platform management endpoints under `/api/admin/**`; societe CRUD for platform operators.

---

## 8. Frontend Architecture

### 8.1 Route Trees

The Angular 19 SPA (`hlm-frontend/`) uses standalone components and lazy-loaded routes split across three isolated route trees:

| Route prefix | Shell component | Guard | Audience |
|---|---|---|---|
| `/app/*` | `ShellComponent` | `authGuard` | CRM staff (ADMIN/MANAGER/AGENT) |
| `/superadmin/*` | `SuperadminShellComponent` | `superadminGuard` | Platform operators (SUPER_ADMIN) |
| `/portal/*` | `PortalShellComponent` | `portalGuard` | Buyers (ROLE_PORTAL, magic-link) |

Public routes (no guard): `/login`, `/activation`, `/portal/login`.

### 8.2 Guards

All guards are Angular 19 functional guards invoked via `canActivate`.

| Guard | File | Logic |
|---|---|---|
| `authGuard` | `core/auth/auth.guard.ts` | Validates the CRM session through `AuthService.verifySession()`; redirects to `/login` when the `hlm_auth` cookie-backed session is invalid |
| `adminGuard` | `core/auth/admin.guard.ts` | Validates the current CRM user profile and requires `ROLE_ADMIN`; used on `/app/admin/users` and template routes |
| `superadminGuard` | `core/auth/superadmin.guard.ts` | Validates the current CRM user profile and requires `ROLE_SUPER_ADMIN`; guards the entire `/superadmin/*` tree |
| `portalGuard` | `portal/core/portal-auth.guard.ts` | Validates the buyer session through `PortalAuthService.validateSession()`; redirects to `/portal/login` when the `hlm_portal_auth` cookie-backed session is invalid |

### 8.3 HTTP Interceptors

Two functional interceptors are registered in `app.config.ts` via `provideHttpClient(withInterceptors([...]))`:

**`authInterceptor`** (`core/auth/auth.interceptor.ts`):
- does not attach a CRM bearer token for normal SPA traffic because the final session lives in the `hlm_auth` httpOnly cookie
- still allows the dedicated societe-switch flow to send the short-lived partial token explicitly from the service layer
- on 401 from protected CRM endpoints: clears cached session state and redirects through the auth service

**`portalInterceptor`** (`portal/core/portal.interceptor.ts`):
- relies on `withCredentials: true` so the `hlm_portal_auth` cookie is sent to portal endpoints
- does not mix portal session behavior into CRM routes
- on 401 from a portal API endpoint: clears portal session state and redirects to `/portal/login`

The two interceptors are independent — CRM token never goes to portal endpoints; portal token never goes to CRM endpoints.

### 8.4 Session Storage

| Key | Storage | Content |
|---|---|---|
| `hlm_auth` | httpOnly cookie | final JWT for CRM staff and SUPER_ADMIN |
| `hlm_portal_auth` | httpOnly cookie scoped to `/api/portal` | final buyer portal JWT |
| partial societe token | in-memory request flow only | short-lived token used only for `/auth/switch-societe` |

### 8.5 Local Development Proxy

`hlm-frontend/proxy.conf.json` forwards the following paths from Angular's `ng serve` (port 4200) to `http://localhost:8080`:

| Proxied path | Purpose |
|---|---|
| `/auth/**` | Login, switch-société, invitation endpoints |
| `/api/**` | All CRM, admin, and portal API endpoints |
| `/dashboard/**` | (Legacy path retained for backward compat) |
| `/actuator/**` | Health checks, metrics during development |

In production this proxy is replaced by Nginx (see Section 2).

---

## 9. Asynchronous Components

All six scheduled components use `SocieteContextHelper.runAsSystem()` to run in system mode (nil-UUID sentinel). All are gated by `@ConditionalOnProperty("spring.task.scheduling.enabled", matchIfMissing = true|false)` to allow clean test profiles.

| Component | Class | Trigger | Behavior | ShedLock | SocieteContext mode |
|---|---|---|---|---|---|
| `OutboundDispatcherScheduler` | `outbox.scheduler` | `@Scheduled(fixedDelay = ${app.outbox.polling-interval-ms:5000})` | Polls `outbound_message` table (SKIP LOCKED), dispatches up to `batch-size` messages via `EmailSender`/`SmsSender`; exponential backoff (`{1,5,30}` min); permanent FAILED after `max-retries` | Yes: `name="outbox_dispatcher"`, `lockAtMostFor="PT1M"` | `runAsSystem()` |
| `DepositWorkflowScheduler` | `deposit.scheduler` | `@Scheduled(cron = "0 0 * * * *")` (hourly) | Calls `DepositService.runHourlyWorkflow(Duration.ofHours(24))`; transitions stale deposits | No | `runAsSystem()` |
| `ReservationExpiryScheduler` | `reservation.service` | `@Scheduled(cron = "${app.reservation.expiry-cron:0 0 * * * *}")` (hourly) | Calls `ReservationService.runExpiryCheck()`; sets ACTIVE → EXPIRED for past-`expiryDate` reservations | No | `runAsSystem()` |
| `ReminderScheduler` (payments) | `payments.service` | `@Scheduled(cron = "${app.payments.reminder-cron:0 0 7 * * *}")` (daily 07:00) | Calls `ReminderService.processAll()`; sends payment call due-date reminders via outbox | No | `runAsSystem()` |
| `PortalTokenCleanupScheduler` | `portal.scheduler` | `@Scheduled(cron = "${app.portal.cleanup-cron:0 0 3 * * *}")` (daily 03:00) | Calls `portalTokenRepository.deleteExpiredAndUsed(Instant.now())`; removes stale `portal_token` rows | No | `runAsSystem()` |
| `DataRetentionScheduler` | `gdpr.scheduler` | `@Scheduled(cron = "${app.gdpr.retention-cron:0 0 2 * * *}")` (daily 02:00) | GDPR Art. 5(1)(e): anonymizes soft-deleted contacts older than `defaultRetentionDays` (default 1825 = 5 years) per société; skips contacts blocked by SIGNED contracts (logs WARN) | No | `runAsSystem()` |

**Note:** `DataRetentionScheduler` is gated by `@ConditionalOnProperty("spring.task.scheduling.enabled")` without `matchIfMissing = true` — it is therefore disabled by default when the property is absent entirely, unlike the other schedulers. Explicitly set `spring.task.scheduling.enabled=true` in production.

---

## 10. Caching Architecture

### 10.1 Caffeine (Default)

Active when `app.redis.enabled` is absent or `false`. `CacheConfig` registers named Caffeine caches via `registerCustomCache()`. Each cache has an independent TTL and maximum entry count. Caffeine is in-process and does not survive backend restarts or share state between instances.

### 10.2 Redis (Optional)

Activated by `app.redis.enabled=true` (env: `REDIS_ENABLED=true`). `RedisCacheConfig` (`com.yem.hlm.backend.auth.config.RedisCacheConfig`) is annotated `@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")` and declares a `CacheManager` bean that supersedes the Caffeine `CacheManager`. All cache values are serialized as JSON via `GenericJackson2JsonRedisSerializer`.

Connection: `spring.data.redis.host` (default `localhost`), `spring.data.redis.port` (default `6379`), `spring.data.redis.password`. Configured via env vars `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`.

### 10.3 Named Cache Registry

All caches defined in `CacheConfig` and overridden with the same TTLs in `RedisCacheConfig`:

| Cache name constant | TTL (Caffeine) | TTL (Redis) | Contents |
|---|---|---|---|
| `USER_SECURITY_CACHE` | 60 s | 60 s | `UserSecurityInfo(enabled, tokenVersion)` keyed by `userId` |
| `COMMERCIAL_DASHBOARD_CACHE` | 30 s | 30 s | `CommercialDashboardSummaryDTO` keyed by `(societeId, agentId, period)` |
| `CASH_DASHBOARD_CACHE` | 60 s | 60 s | Cash flow dashboard aggregates keyed by `(societeId, period)` |
| `RECEIVABLES_DASHBOARD_CACHE` | 30 s | 30 s | Receivables aging buckets keyed by `(societeId)` |
| `PROJECTS_CACHE` | 60 s | 60 s | Project list keyed by `societeId` |
| `SOCIETES_CACHE` | 120 s | 120 s | Societe list keyed by platform-level query (SUPER_ADMIN) |

`RedisCacheManager` is built `.transactionAware()` — cache writes are deferred until the enclosing Spring transaction commits, preventing dirty cache entries when a transaction rolls back.

### 10.4 Cache Invalidation

`UserSecurityCacheService.invalidate(userId)` is called on role change, user disable, and forced logout. This removes the `USER_SECURITY_CACHE` entry for that user. The next request will reload from DB and re-cache. Stale tokens are therefore honoured for at most one cache TTL (60 s) after the invalidating event.

Dashboard caches use time-based expiry only (no explicit invalidation). The 30–120 s TTL is acceptable for analytical KPIs; real-time data is not a requirement.

---

## 11. Known Architecture Gaps

The following issues are confirmed by code inspection. They do not represent security vulnerabilities in deployed configurations but are worthy of tracking for future hardening.

### 11.1 Orphan `POST /tenants` Permission

`SecurityConfig` (line 79) contains:
```java
.requestMatchers(HttpMethod.POST, "/tenants").permitAll()
```
This path was used by an early bootstrapping flow. No `TenantsController` at `/tenants` exists in the current codebase. The `permitAll` rule is harmless (Spring Security will 404), but it is misleading and should be removed when the security configuration is next touched.

### 11.2 SUPER_ADMIN Token Has No `sid` Claim

SUPER_ADMIN JWTs omit the `sid` claim entirely. `JwtAuthenticationFilter` handles this by branching on `isSuperAdmin` and calling `SocieteContext.setSuperAdmin(true)` without setting `societeId`. Any service method called by a SUPER_ADMIN request that calls `requireSocieteId()` will throw `CrossSocieteAccessException`. Portal services (e.g., `PortalContractService`) must read `contactId` from `SecurityContextHolder.getAuthentication().getPrincipal()` and the `societeId` from path parameters or JWT `sid` claim, not from `SocieteContext`. This is correctly implemented in the portal module but must be observed by any future service method reachable via SUPER_ADMIN routes.

### 11.3 Multi-Société Selection Requires End-To-End Testing When Auth Changes

`AuthService.login()` on the backend issues a partial token and returns a `societes[]` list for users with multiple memberships. The Angular `LoginComponent` now supports the société-selection branch and calls `POST /auth/switch-societe` before entering the CRM. This path is more fragile than single-membership login because it spans backend auth, frontend UI state, and cookie issuance, so it should be regression-tested whenever auth code changes.

### 11.4 Societe Suspension Fields Not Enforced at Request Time

`Societe` has an `actif` boolean column (changeset 031). `AppUserSocieteRepository.findByIdUserIdAndActifTrue()` already filters for active memberships at login time. However, if a societe is deactivated (`actif = false`) after a user has already obtained a valid JWT, subsequent API requests will still succeed — `JwtAuthenticationFilter` does not re-check `societe.actif` on every request, and `UserSecurityCacheService` caches only `AppUser.enabled` + `tokenVersion`, not `Societe.actif`. Enforcing suspension at request time would require either adding a `societe.actif` check to `UserSecurityCacheService` (cache + DB) or using `RlsContextAspect` to block queries when `societe.actif = false` (requires schema-level function). Neither is currently implemented.
