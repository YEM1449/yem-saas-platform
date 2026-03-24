# Architectural Decisions

These decisions are visible in the current codebase and materially shape maintenance work.

## 1. Multi-Societe Membership Instead of Single-Company Users

The platform uses:

- `app_user` for platform identity
- `societe` for company identity
- `app_user_societe` for role-bearing membership

Why it matters:

- one user can belong to multiple societes
- `SUPER_ADMIN` exists outside normal company membership
- login can require societe selection before issuing a full JWT

## 2. Stateless JWTs With Bounded Server-Side Revocation

The system deliberately avoids server sessions while still supporting forced logout and role changes.

Implementation pattern:

- short-lived JWT
- `tv` claim
- cache-backed `UserSecurityCacheService`

This is simpler than maintaining a per-token denylist and is already covered by integration tests.

## 3. Partial Tokens for Multi-Societe Login

The backend does not guess a company when a user has multiple memberships.

Instead it returns:

- a short-lived partial token
- `requiresSocieteSelection=true`
- the list of eligible societes

This is a meaningful product and security choice because downstream access control depends on a single active `sid`.

## 4. Application-Layer Isolation With Limited Database RLS

Primary isolation is implemented in services and repositories using `SocieteContext`.

RLS exists only as defense in depth for:

- `contact`
- `property`

This keeps the current code straightforward while still adding a database backstop for high-risk tables.

## 5. ThreadLocal Request Context

The application stores active request scope in `SocieteContext`, a static `ThreadLocal`.

Why the code chose this:

- easy access from deep service layers
- fits the synchronous servlet request model
- lightweight compared with propagating company scope through every method signature

Operational requirement:

- the context must always be cleared in `JwtAuthenticationFilter`

## 6. Transactional Outbox for Delivery Channels

Most email and SMS sending is modeled as database work first, delivery second.

Benefits:

- business transactions do not depend on SMTP or SMS availability
- messages are retried
- multiple app instances can safely process the same outbox table

Exception:

- portal magic-link emails are sent directly because the link must be generated immediately and the flow is not modeled as a larger business transaction

## 7. Soft Delete and Reversible State Over Hard Delete

High-value business records prefer state transitions over physical deletion.

Examples:

- properties are soft-deleted
- projects are archived
- reservations and deposits are canceled or expired
- contracts are canceled rather than removed

This preserves referential integrity and historical reporting.

## 8. Buyer Snapshots Captured at Contract Signature

Signed contracts store immutable buyer snapshot fields copied from the contact.

This separates:

- operational CRM data that may later be rectified or anonymized
- signed legal record data that must remain historically stable

## 9. Optimistic Locking on Mutable Administrative Records

Entities such as `User`, `Societe`, `Property`, `Contact`, and `SaleContract` carry version fields.

This is a clear choice toward:

- preventing silent lost updates
- surfacing `CONCURRENT_UPDATE` instead of overwriting changes

## 10. Local File Storage First, Object Storage Optional

The default media path is local filesystem storage.

Object storage is opt-in through configuration.

This keeps local development simple while still allowing S3-compatible deployments without changing application code.

## 11. Legacy Paths Are Kept Longer Than Legacy Concepts

The codebase still contains some transitional seams:

- old tenant-era migration history
- `CrossTenantAccessException` naming
- `/api/admin/users` alongside `/api/mon-espace/utilisateurs`
- `/api/societes/**` as a legacy alias

The current maintenance strategy is evolution over rewrite:

- keep compatibility where cheap
- route new work through `societe` terminology and the newer company-management surface
