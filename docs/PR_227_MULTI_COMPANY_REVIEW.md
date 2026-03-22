# PR #227 Multi-Company Review

This document reviews the multi-company migration by comparing `main` (`c3bda24`) to `Epic/change-multitenantTomultiCompany` (`1bc66de`).

The analysis is based on the PR diff, not just the current post-PR code.

## 1. Executive Summary

PR #227 is a large migration from a single-tenant-per-user model to a multi-company model built around:

- `societe`
- `app_user`
- `app_user_societe`
- JWT `sid` company scoping
- `SocieteContext` request context

The migration intent is sound:

- one user can belong to multiple societes
- company role moves from `app_user.role` to `app_user_societe.role`
- `SUPER_ADMIN` becomes a platform-level role
- most business tables switch from `tenant_id` to `societe_id`

However, the reviewed branch is much broader than that migration alone.

- Diff size: 396 files changed
- Added: 127
- Modified: 175
- Deleted: 94
- Backend files touched: 213
- Frontend files touched: 37
- Docs files touched: 121

The biggest concerns introduced or left unresolved by the PR are:

- multi-company login is not end-to-end complete in the PR branch frontend
- `/auth/switch-societe` can mint a fresh JWT without re-checking revocation state
- migration changeset `036` only repoints one FK path before deleting duplicate `app_user` rows
- company isolation still depends heavily on manual query discipline, while the old static guard test was removed

## 2. Change Analysis

### 2.1 Data Model

Previous behavior:

- `app_user` belonged to exactly one `tenant`
- business data used `tenant_id`
- login required `tenantKey`
- role lived on `app_user.role`

New behavior introduced by the PR:

- `tenant` is replaced by `societe`
- `app_user` becomes a platform identity keyed by global email
- `app_user_societe` becomes the membership table carrying per-societe role and active state
- business data switches from `tenant_id` to `societe_id`
- login uses email/password only and may require a second company-selection step

Likely intent:

- support one user across multiple companies
- separate platform role from company role
- keep the shared-database architecture while renaming scope from tenant to societe

### 2.2 Backend Packages

Removed:

- old `tenant` package and tenant-specific tests

Added:

- `societe` package with context, repository, service, controller, membership model, and super-admin guard

Modified heavily:

- `auth`
- `contact`
- `project`
- `property`
- `deposit`
- `contract`
- `dashboard`
- `payments`
- `portal`
- `user`

### 2.3 Liquibase

The PR adds changesets `031` through `038`.

Key migration steps:

- create `societe`
- create `app_user_societe`
- rename `tenant_id` to `societe_id` across business tables
- migrate user membership data
- add `societe.key`
- deduplicate `app_user.email`
- add indexes and version columns

### 2.4 Frontend

The Angular login form is changed to remove `tenantKey`, but the PR branch does not fully implement the backend's new two-step login contract for multi-societe users.

### 2.5 Test Surface

Added:

- `CrossSocieteIsolationIT`

Deleted:

- `CrossTenantIsolationIT`
- `TenantScopedRepoGuardTest`
- tenant controller/context tests

Regression in test coverage:

- the old guard that scanned for unsafe repository usage was removed
- no replacement test was added for partial-token login or `/auth/switch-societe`

## 3. Multi-Company Implementation Review

### 3.1 How company context is introduced

- `AuthService.login()` resolves active `app_user_societe` memberships
- `JwtProvider` writes company scope into JWT claim `sid`
- `JwtAuthenticationFilter` reads `sid` and writes it to `SocieteContext`
- controllers and services read `SocieteContext` and pass `societeId` into repository methods

### 3.2 Where company role lives

- platform role: `app_user.platform_role`
- company role: `app_user_societe.role`

### 3.3 How company scope is enforced

Mostly application-side, by convention:

- services call `SocieteContext.getSocieteId()`
- repositories expose `findBySocieteId...` and custom `WHERE ... societeId = :societeId` queries

What the PR does not add:

- centralized automatic scoping for all repositories
- a replacement static guard against unsafe unscoped repository calls
- database-level RLS for the migration itself

### 3.4 Spoofing risk

The company context is not taken from request headers or body for normal API calls. It comes from a signed JWT claim, which is good.

The weak point is not direct spoofing. The weak point is trust in token-derived company context without consistently re-validating user and membership state on every special auth path.

## 4. Issues And Risks

### 🔴 Critical

1. Multi-company login is broken end to end in the PR branch.
   The backend can return `requiresSocieteSelection=true` and a short-lived partial token, but the PR branch frontend still treats login as a one-step flow and redirects directly into CRM.

2. `/auth/switch-societe` can bypass revocation and disabled-user checks.
   The method validates signature and membership, then mints a new JWT, but it does not verify the presented token against current token version and it does not reject disabled users before issuing the new token.

3. Changeset `036-deduplicate-app-user-email.yaml` is incomplete for real migrated data.
   It rewires `app_user_societe.user_id` before deleting duplicate `app_user` rows, but other tables still have FKs to `app_user`, including deposit, notification, sale contract, and outbound message.

### 🟠 Medium

1. Company isolation remains manual and easy to forget.
   The PR removes the old repository guard test and relies on developers consistently passing `societeId` in services and repositories.

2. Some validation still checks user existence globally instead of membership in the active societe.
   Example pattern: dashboard agent validation uses `userRepository.findById(...)` instead of a societe-scoped membership check.

3. Null-scope handling is inconsistent.
   Several controllers and services read `SocieteContext.getSocieteId()` directly and pass it down without a single shared fail-fast guard.

4. Migration fallback groups null-tenant records into one default societe.
   Changeset `033` uses `COALESCE(tenant_id, '00000000-0000-0000-0000-000000000001')`. Whether that is acceptable business behavior depends on data quality and needs clarification.

5. PR scope is too broad for safe review.
   The same branch includes unrelated docs, GDPR, storage, Redis, and pipeline work, which increases merge and regression risk.

### 🟢 Low

1. `SocieteContext.setRole()` exists in the PR branch but is not populated by the JWT filter.

2. Terminology drift remains in comments and test names.

3. The migration branch adds `CrossSocieteIsolationIT`, but it still focuses mainly on read isolation and does not cover enough mutation and auth edge cases.

## 5. Root Cause Analysis

### 5.1 Broken multi-societe login

Pattern:

- backend contract changed
- frontend request model changed
- frontend response handling did not fully change with it

Propagation:

- backend returns partial token
- JWT filter intentionally refuses partial tokens for normal API routes
- frontend stores or treats that token as a usable session
- multi-membership users fail immediately after login

### 5.2 Token re-issuance bypass in `/auth/switch-societe`

Pattern:

- endpoint is `permitAll`
- token is parsed inside service instead of flowing through the normal request-auth path
- service checks membership but not revocation state

Propagation:

- a still-signed old token or partial token is presented
- service resolves `userId` from JWT subject
- service re-issues a fresh full JWT without verifying current enabled/token-version state

### 5.3 Incomplete email dedup migration

Pattern:

- changeset assumes `app_user_societe` is the only reference needing rewrite
- delete step removes duplicate `app_user` rows globally

Propagation:

- duplicate-email users may still be referenced from other tables
- delete can fail on FK constraints or require manual pre-cleaning
- production migration becomes data-dependent and fragile

## 6. Non-Breaking Fix Strategy

1. Finish the login contract end to end.
   Keep the backend contract, update the frontend to branch between:
   - single-societe login: store full token and enter CRM
   - multi-societe login: store partial token in memory only, show societe picker, then call `/auth/switch-societe`

2. Harden `/auth/switch-societe`.
   Reuse the same enabled and token-version checks already enforced by the JWT filter before minting a new token.

3. Replace the deleted repository guard with a societe-aware version.
   Keep the current architecture, but add a static test that blocks risky bare `findById()` calls on societe-owned aggregates.

4. Introduce a shared fail-fast helper for scope.
   One helper should read `SocieteContext`, reject null scope, and be reused by services/controllers that require an active societe.

5. Fix the migration path for duplicate users.
   If follow-up schema work is needed, use a new Liquibase changeset after the existing sequence and repoint every FK that can reference `app_user` before deleting duplicates.

6. Narrow future PRs.
   Split auth migration, data migration, docs, and unrelated infra changes into separate review units.

## 7. Code-Level Improvement Patterns

### 7.1 Safe company scoping

Before:

```java
UUID societeId = SocieteContext.getSocieteId();
return repository.findById(id);
```

After:

```java
UUID societeId = societeAccess.requireSocieteId();
return repository.findBySocieteIdAndId(societeId, id)
        .orElseThrow(() -> new NotFoundException(id));
```

### 7.2 Safe auth re-issuance

Before:

```java
Jwt jwt = jwtProvider.parse(rawToken);
User user = userRepository.findById(userId).orElseThrow(...);
return jwtProvider.generate(userId, societeId, role, user.getTokenVersion());
```

After:

```java
Jwt jwt = jwtProvider.parse(rawToken);
UUID userId = extractUserId(jwt);
UserSecurityInfo secInfo = userSecurityCacheService.getSecurityInfo(userId);
if (secInfo == null || !secInfo.enabled() || secInfo.tokenVersion() != extractTokenVersion(jwt)) {
    throw unauthorized();
}
User user = userRepository.findById(userId).orElseThrow(...);
if (!user.isEnabled()) {
    throw unauthorized();
}
return LoginResponse.bearer(jwtProvider.generate(userId, societeId, role, user.getTokenVersion()), ttl);
```

### 7.3 Membership-scoped validation

Before:

```java
userRepository.findById(requestedAgentId).orElseThrow(...);
```

After:

```java
appUserSocieteRepository.findByIdUserIdAndIdSocieteId(requestedAgentId, societeId)
        .filter(AppUserSociete::isActif)
        .orElseThrow(() -> new UserNotFoundException(requestedAgentId));
```

## 8. Documentation Update

### Developer rules

- Always derive active company scope from the authenticated JWT, never from request payload.
- Always fail fast when a CRM endpoint requires `societeId` and the context is missing.
- Always validate membership in the active societe before operating on users or user-owned actions.
- Always increment token version when role, enabled state, or membership access changes.

### Do

- use `findBySocieteId...` repository methods
- keep super-admin logic separate from CRM company logic
- add isolation tests for both reads and writes
- document when a token is partial versus full

### Do not

- use bare `findById()` for societe-owned aggregates unless the parent was already scoped
- treat `/auth/switch-societe` as a lightweight convenience endpoint
- silently group ambiguous legacy data into a default societe without business sign-off
- mix major data-model migration work with unrelated infrastructure and documentation churn

## 9. Validation Scenarios

Priority scenarios for this migration:

1. Single-membership login returns a full JWT with `sid`.
2. Multi-membership login returns a partial token and no CRM access until societe selection completes.
3. `/auth/switch-societe` rejects:
   - disabled users
   - stale token-version users
   - inactive memberships
   - societes outside membership
4. Cross-societe reads return `404` or empty results consistently.
5. Cross-societe writes and updates are rejected consistently.
6. Role changes and membership removal revoke old JWTs immediately.
7. Super-admin endpoints work without `sid`, but CRM endpoints still require company scope.
8. Migration dry-run with duplicate emails and historical references in deposit, notification, contract, and outbound tables succeeds without manual cleanup.

## 10. Recommended Merge Gate

Before merging a branch shaped like PR #227:

- fix the multi-societe frontend login flow
- harden `/auth/switch-societe`
- repair or replace changeset `036`
- restore a societe-aware repository guard test
- add integration coverage for partial-token selection and company-scoped mutations
