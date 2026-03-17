# Architectural Decisions

Key decisions made in the YEM SaaS Platform codebase and the rationale behind each one, derived from code patterns and configuration.

## Table of Contents

1. [Additive-Only Liquibase Migrations](#1-additive-only-liquibase-migrations)
2. [TenantContext as ThreadLocal](#2-tenantcontext-as-threadlocal)
3. [Transactional Outbox Pattern for Email/SMS](#3-transactional-outbox-pattern-for-emailsms)
4. [Two Cache Backends: Caffeine and Redis](#4-two-cache-backends-caffeine-and-redis)
5. [ROLE_PORTAL Separate from CRM Roles](#5-role_portal-separate-from-crm-roles)
6. [LocalFileMediaStorage as Default](#6-localfilemediastorage-as-default)
7. [Payment v1 Tables Dropped](#7-payment-v1-tables-dropped)
8. [GDPR Erasure is Anonymization, Not Hard Delete](#8-gdpr-erasure-is-anonymization-not-hard-delete)
9. [Soft Delete for Properties](#9-soft-delete-for-properties)
10. [@ConditionalOnExpression for SMTP Activation](#10-conditionalOnexpression-for-smtp-activation)
11. [JPQL CAST Pattern for Nullable LocalDateTime Params](#11-jpql-cast-pattern-for-nullable-localdatetime-params)
12. [Token Version for Instant Revocation](#12-token-version-for-instant-revocation)

---

## 1. Additive-Only Liquibase Migrations

**Decision:** Liquibase changesets are immutable once committed. New column additions, table creations, and index additions are done in new changesets. Existing applied changesets are never edited.

**Rationale:**
- Liquibase checksums each applied changeset. Editing an applied changeset causes a checksum mismatch on the next startup, which crashes the application.
- Immutable changesets provide a reliable audit trail of how the schema evolved.
- Rolling back changes is safer via a new changeset (e.g., `DROP COLUMN`) than editing history.

**Evidence:** `db.changelog-master.yaml` has 30 sequential changesets (001–030), each in its own file. Changeset `030-update-seed-password.yaml` updates the seed data password rather than modifying changeset `002-seed-tenant-owner.yaml`.

---

## 2. TenantContext as ThreadLocal

**Decision:** Tenant and user identity are stored in a static `ThreadLocal<UUID>` pair (`TenantContext`), not in a request-scoped Spring bean.

**Rationale:**
- Spring's synchronous servlet model (one thread per request) makes ThreadLocal a safe, zero-overhead store for request-scoped state.
- A ThreadLocal utility class (`static void setTenantId(UUID)`) is accessible from any class — entities, services, repositories — without constructor injection.
- Request-scoped beans would require all callees to declare a dependency; ThreadLocal is invisible to the call chain.
- The risk of thread pool leakage is handled by `TenantContext.clear()` in the `finally` block of `JwtAuthenticationFilter`.

**Evidence:** `TenantContext.java` uses two `ThreadLocal<UUID>` fields with explicit `clear()` method called in filter's `finally` block.

---

## 3. Transactional Outbox Pattern for Email/SMS

**Decision:** Email and SMS messages are not sent directly in the business transaction. Instead they are written to the `outbound_message` table, and a background scheduler dispatches them.

**Rationale:**
- Direct email send in a transaction has a fatal flaw: if the SMTP call succeeds but the DB transaction rolls back (e.g., due to an exception after the email send), the user receives an email for a cancelled operation.
- The outbox guarantees "at-least-once" delivery: the message is only sent after the transaction commits (the row exists in the DB). If the send fails, exponential backoff retries it.
- `FOR UPDATE SKIP LOCKED` allows multiple application instances to process the outbox concurrently without double-sending.

**Evidence:** `OutboundDispatcherService` uses a native query with `FOR UPDATE SKIP LOCKED`. `OutboxMessageService` creates `outbound_message` rows inside the caller's `@Transactional` context. Magic-link emails in `PortalAuthService` call `EmailSender.send()` directly because they have no DB transaction to protect.

---

## 4. Two Cache Backends: Caffeine and Redis

**Decision:** In-process Caffeine is the default cache. Redis is an opt-in replacement controlled by `REDIS_ENABLED=true`.

**Rationale:**
- Caffeine has zero network overhead and is sufficient for single-instance deployments (local dev, staging, small production).
- Redis is required when running multiple application instances behind a load balancer — Caffeine caches are local to each JVM, so two instances would have inconsistent `userSecurityCache` (token revocation visibility). Redis is a shared cache across all instances.
- The switch is transparent: the same `@Cacheable` annotations and cache names work with both backends.

**Evidence:** `CacheConfig.java` registers Caffeine caches. `RedisCacheConfig.java` (activated by `@ConditionalOnProperty("app.redis.enabled")`) registers the same cache names in Redis with `GenericJackson2JsonRedisSerializer`.

---

## 5. ROLE_PORTAL Separate from CRM Roles

**Decision:** Client portal users get `ROLE_PORTAL` in their JWT, not `ROLE_AGENT`. The portal principal is a `contactId`, not a `userId`.

**Rationale:**
- Portal users are property buyers, not CRM employees. Granting them `ROLE_AGENT` would give them access to the entire CRM `/api/**` namespace.
- Using a distinct role makes the `SecurityConfig` rule clear and easy to audit: `/api/portal/**` → `ROLE_PORTAL`, `/api/**` → CRM roles.
- Portal tokens must bypass `UserSecurityCacheService` because portal principals are contacts, not `app_user` rows. The filter detects `ROLE_PORTAL` and skips the revocation check.

**Evidence:** `SecurityConfig.java` lines 73–76. `JwtAuthenticationFilter.isPortalToken()` checks for `ROLE_PORTAL` authority.

---

## 6. LocalFileMediaStorage as Default

**Decision:** Property media is stored on the local filesystem (`./uploads` by default). S3-compatible storage is opt-in via `MEDIA_OBJECT_STORAGE_ENABLED=true`.

**Rationale:**
- Local storage has no external dependencies, making local development frictionless.
- The `MediaStorage` interface (`upload`, `download`, `delete`) abstracts the backend. Switching from local to S3 is a single env var change.
- In the Docker Compose setup, the backend writes to `/tmp/hlm-uploads` (always writable by the non-root `hlm` user).
- In production with MinIO or cloud S3, the same code path activates `ObjectStorageMediaStorage`.

**Evidence:** `LocalFileMediaStorage` is `@Primary` / default bean; `ObjectStorageMediaStorage` has `@ConditionalOnProperty("app.media.object-storage.enabled")`.

---

## 7. Payment v1 Tables Dropped

**Decision:** The legacy v1 payment tables (`payment`, `payment_tranche`, `payment_call`, `payment_schedule`) were removed in changeset `028-drop-v1-payment-tables`.

**Rationale:**
- The v1 payment model was a simple flat structure that did not support partial payments or per-item state tracking.
- v2 (`payment_schedule_item` + `schedule_payment`) adds proper item-level state machines, partial payment support, and reminder idempotency guards.
- Since the tables were new (no production data had been migrated), dropping them was safe and cleaner than maintaining two models.

**Note:** Despite the drop, the changeset is itself additive in that it uses a `DROP TABLE` which is forward-only. The "additive-only" rule means changesets are never edited — a new changeset (028) performs the drop.

---

## 8. GDPR Erasure is Anonymization, Not Hard Delete

**Decision:** `DELETE /api/gdpr/contacts/{id}/anonymize` zeroes PII fields on the `contact` row (and related detail rows) but does not physically delete the row.

**Rationale:**
- Hard deleting a contact would violate foreign key constraints on `deposit.contact_id`, `sale_contract.buyer_contact_id`, and other tables.
- Financial records must be retained for legal/accounting reasons even after a GDPR erasure request.
- Anonymization satisfies the GDPR "right to erasure" by making re-identification impossible, while preserving the structural integrity of financial audit trails.
- The `anonymized_at` timestamp provides a verifiable record that the erasure was processed.
- Erasure is blocked when SIGNED contracts exist (the contract is still legally binding; the buyer identity cannot be erased while the contract is active).

**Evidence:** `AnonymizationService.anonymize()`. `GdprErasureBlockedException` is thrown when `SaleContractStatus.SIGNED` contracts exist.

---

## 9. Soft Delete for Properties

**Decision:** `DELETE /api/properties/{id}` sets `deleted_at = now()` and `status = DELETED` rather than physically removing the row.

**Rationale:**
- Properties are referenced by deposits, reservations, contracts, media, and contact interests. Hard delete would require cascading or FK violations.
- Deleted properties may still be referenced in historical data (audits, contracts).
- All list queries filter `WHERE deleted_at IS NULL` to hide deleted properties from the UI.

**Evidence:** `PropertyService.softDelete()` and `PropertyRepository` queries with `deleted_at IS NULL` conditions.

---

## 10. @ConditionalOnExpression for SMTP Activation

**Decision:** `SmtpEmailSender` uses `@ConditionalOnExpression("!'${app.email.host:}'.isBlank()")` instead of `@ConditionalOnProperty`.

**Rationale:**
- `@ConditionalOnProperty` treats an empty string (`""`) as "the property is present and has a value", which would activate `SmtpEmailSender` even when `EMAIL_HOST=` is left blank in the `.env` file.
- An empty `EMAIL_HOST` means no SMTP is configured. Activating the SMTP sender with an empty host would cause auth failures.
- `@ConditionalOnExpression` with `.isBlank()` correctly treats empty strings as "not configured".

**Evidence:** `SmtpEmailSender.java` annotation. Documented in `pitfall_conditional_on_property_empty_string.md`.

---

## 11. JPQL CAST Pattern for Nullable LocalDateTime Params

**Decision:** JPQL queries with nullable `LocalDateTime` parameters use `CAST(:param AS LocalDateTime) IS NULL` instead of `:param IS NULL`.

**Rationale:**
- PostgreSQL's type inference system cannot infer the type of a null literal in a JPQL parameter. `:param IS NULL` generates SQL that PostgreSQL rejects with a type inference error.
- `CAST(:param AS LocalDateTime) IS NULL` forces Hibernate to emit a typed NULL, which PostgreSQL handles correctly.

**Evidence:** Documented in `pitfall_jpql_null_localdatetime.md`. Applied in `ContractRepository`, `DepositRepository`, and `CommercialAuditRepository` queries.

---

## 12. Token Version for Instant Revocation

**Decision:** JWTs carry a `tv` (token version) integer claim matching the `token_version` column on `app_user`. On every request, the filter compares the claim to the DB value (via Caffeine/Redis cache).

**Rationale:**
- Standard JWTs are stateless and cannot be revoked before expiry. When an admin disables a compromised account or changes a user's role, the existing tokens must be invalidated.
- Adding a server-side revocation store converts the JWT from purely stateless to "bounded stateful" — the revocation check adds one cache lookup (sub-millisecond with Caffeine, ~1–2 ms with Redis) per request.
- Incrementing `token_version` on disable or role change is simpler than maintaining a per-token denylist (which would grow unboundedly).
- Cache TTL of 60 seconds means revocation propagates within 60 seconds — acceptable for the use cases here.

**Evidence:** `AdminUserService.setEnabled()` and `AdminUserService.changeRole()` call `user.incrementTokenVersion()`. `JwtAuthenticationFilter` checks `secInfo.tokenVersion() != tokenTv`.
