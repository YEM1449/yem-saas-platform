# Known Issues And Active Constraints

This file tracks current limitations and architectural caveats that maintainers should keep in view while working on the platform.

## Open Product And Engineering Issues

### 1. Two Sales Views Require Stronger User Education

- `vente` and `contract` both exist and are both visible in the UI
- this is intentional, but users can still confuse “commercial progression” with “formal contract record”
- mitigation: user documentation must explain when to work in `Ventes` versus `Contracts`

### 2. Two Member Management Surfaces Still Coexist

- `/api/users` and `/api/mon-espace/utilisateurs` overlap partially
- the richer flow is in `usermanagement`, but the older surface remains for compatibility and some UI uses
- risk: future permission changes can diverge if both controllers are not updated together

### 3. Legacy `tenant` Naming Persists In Schema History

- runtime code uses `societe`
- old Liquibase files, some index names, and a few historical comments still use `tenant`
- risk: newcomers may think the runtime model is mixed, when the business model is actually settled

### 4. Compose Starts Optional Infrastructure Even When Not Always Needed

- Redis and MinIO are convenient defaults in Docker Compose
- the application can run without them in some environments
- risk: new contributors may incorrectly assume those services are hard requirements for every deployment mode

### 5. Portal UX Still Depends On Regenerating A Magic Link After Session Expiry

- buyer sessions expire normally after the portal JWT TTL
- the recovery path is operationally simple, but not especially guided in-product
- mitigation: the user guide now documents the expected support flow clearly

## Operational Caveats

### 1. RLS Depends On Correct AOP Ordering

- if transaction ordering is changed, `SET LOCAL app.current_societe_id` may stop applying to the real business transaction
- this is a high-risk refactor area

### 2. Object Storage Requires Provider-Specific Endpoint Discipline

- Cloudflare R2 and other S3-compatible vendors do not behave exactly like AWS defaults
- path-style addressing is mandatory in the current adapter

### 3. Integration Tests Must Avoid Class-Level `@Transactional`

- listeners using `REQUIRES_NEW` can observe incomplete outer test state and trigger FK failures
- this remains a standing test design rule

### 4. Auth And Cookie Behavior Changes Need End-To-End Verification

- the platform uses multiple session modes, route families, and cookies
- even small auth changes can break the CRM, portal, or impersonation flow in different ways

## Documentation Decisions Applied In This Refresh

- deprecated duplicate user guides were removed in favor of `docs/guides/user/`
- deprecated AI quick/deep context files were removed in favor of the canonical `context/` set
- documentation now treats `societe`, `immeuble`, `tranche`, `vente`, and portal flows as first-class concepts
