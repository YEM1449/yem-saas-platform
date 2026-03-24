# ADR 004 — Optimistic Locking with @Version on Mutable Entities

**Date:** 2026-03-22
**Authors:** YEM Platform Team

---

## Status

Accepted

---

## Context

The platform is a multi-user SaaS. Multiple users — or multiple browser tabs of the same user — may read and then attempt to update the same resource concurrently. Without a concurrency control mechanism, a classic lost-update problem occurs: User A reads a record, User B reads the same record, User A saves their changes, User B overwrites them with a stale version.

Two standard approaches exist:

1. **Pessimistic locking** — acquire a database-level lock when reading. Prevents concurrent reads. Suitable for short, high-contention operations (e.g., the reservation feature uses `SELECT FOR UPDATE` via `findByTenantIdAndIdForUpdate()` to prevent double-booking). Not suitable for user-facing edit forms where the lock would be held across multiple HTTP requests.

2. **Optimistic locking** — no lock is acquired on read. Instead, a `version` column is incremented on every write. If two concurrent saves conflict, the database constraint rejects the second write with an `OptimisticLockException`.

For user-facing edit operations (modifying a company profile, changing a user's role, editing a member's profile), the time between reading a record and submitting an update can be many seconds or minutes. Pessimistic locking over this span would degrade performance and risk deadlocks. Optimistic locking is appropriate.

---

## Decision

Mutable entities that are exposed to concurrent user-facing updates carry a `@Version Long version` field managed by JPA/Hibernate.

**Entities with `@Version`:**

- `Societe` — company profile; updated by SUPER_ADMIN and (in future) company settings flows.
- `User` (`app_user`) — member profile and role; updated by ADMIN and by role-change / member-removal operations.

**API contract:**

Requests that mutate these entities must include the current `version` value in the request body. The service layer validates that the submitted version matches the entity's current version before applying changes.

Example (`ModifierUtilisateurRequest`, `ChangerRoleRequest`, `RetirerUtilisateurRequest`):

```json
{
  "nouveauRole": "MANAGER",
  "version": 3
}
```

If the version does not match, the service throws `BusinessRuleException(CONCURRENT_UPDATE)`, which maps to HTTP `409`:

```json
{
  "status": 409,
  "code": "CONCURRENT_UPDATE",
  "message": "Ce profil a été modifié entre-temps. Rechargez et réessayez."
}
```

The client is expected to reload the resource, show the current state to the user, and allow them to re-submit with the updated version.

**Hibernate automatic increment:**

Hibernate increments the `version` column atomically using an `UPDATE … WHERE version = :expected` statement. If the row was updated by another transaction between the read and the write, Hibernate's row count comes back as 0 and it throws `OptimisticLockException`. The service-layer version check is an application-level pre-check to provide a clean error message before Hibernate's exception surfaces.

---

## Consequences

**Positive:**
- No read locks. Multiple users can read and start editing the same record simultaneously without blocking each other.
- Clear, deterministic conflict detection: exactly one save wins; the other receives a descriptive 409.
- The `version` value is surfaced to the frontend in every GET response, making it straightforward for the Angular client to pass it back on writes.
- Correct for high-latency user-facing forms where locks would be held too long.

**Negative:**
- The client must always include the current `version` in mutation requests. Missing `version` is a `400 VALIDATION_ERROR`. API consumers that omit `version` will see failures until they comply.
- The 409 conflict requires the user to re-read and re-apply their changes, which may be frustrating if conflicts are frequent (unlikely in typical company-admin workflows).
- Hibernate's own `OptimisticLockException` can still surface if the application-level check passes but a concurrent database-level write races in between. This is handled by the global exception handler mapping `OptimisticLockException` → `409 CONCURRENT_UPDATE`.

---

## Alternatives Considered

**Pessimistic locking for all updates:** Rejected. Holding a database lock for the duration of a user's edit session (seconds to minutes) is not viable for a web application. Pessimistic locking is retained only for the reservation feature where the critical section is a single transaction (property availability check + reservation creation).

**Last-write-wins (no version check):** Rejected. In a multi-user admin tool, silent overwrites erode data integrity and are difficult to debug after the fact.

**Event sourcing / CRDT:** Considered for future conflict resolution (merging concurrent changes instead of rejecting one). Deferred — the current operation set (role assignment, profile edits) does not need field-level merge semantics. The 409 + reload approach is sufficient.
