# CONVENTIONS.md — Code and Delivery Conventions

_Updated: 2026-03-05_

## Backend Package Structure (per feature)

```text
feature/
  api/                 # controller + dto
    dto/
  domain/              # JPA entities / enums
  repo/                # repositories and query projections
  service/             # business rules
  scheduler/           # scheduled jobs (optional)
  config/              # feature config (optional)
```

## Controller Conventions
- Return DTOs only, never JPA entities.
- Use method-level `@PreAuthorize` for mixed-role endpoints.
- Validate request DTOs with `@Valid`.
- CRM routes under `/api/*`; portal routes under `/api/portal/*`.
- Do not handcraft ad-hoc error responses; rely on global error handling.

## RBAC Annotation Patterns
```java
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
@PreAuthorize("hasRole('PORTAL')")
// Spring adds ROLE_ prefix automatically
```

## Service Conventions
- Tenant must come from `TenantContext.getTenantId()` (except explicit test-only helper paths).
- Prefer domain exceptions mapped by `GlobalExceptionHandler` and `ErrorCode`.
- Keep service APIs business-oriented; avoid controller DTO leakage into repository layer.

## Repository Conventions
- Use `JpaRepository<..., UUID>` (and `JpaSpecificationExecutor` when needed).
- All tenant-scoped queries must include tenant filters.
- Native `FOR UPDATE SKIP LOCKED` queries must run inside transactions.
- Paginated custom queries must provide valid count query when returning `Page`.

## Error Handling Contract
```java
throw new EntityNotFoundException("Contact not found: " + id);
// mapped to ErrorResponse with stable ErrorCode + HTTP status
```

Do not bypass the envelope with raw string/error bodies.

## Liquibase Conventions
- New schema changes are additive and sequentially numbered.
- Never edit already-applied changesets.
- Keep change intent explicit in file name (example: `028_add_xyz.yaml`).

## Testing Conventions

```java
@ExtendWith(MockitoExtension.class)
class FeatureServiceTest { ... }

@IntegrationTest
class FeatureControllerIT extends IntegrationTestBase { ... }
```

- Unit tests: `*Test` via Surefire.
- Integration tests: `*IT` via Failsafe + Testcontainers.
- IT authentication helpers:
  - CRM: `JwtProvider.generate(...)`
  - Portal: `PortalJwtProvider.generate(...)`

## Frontend Conventions
- Use relative backend paths only.
- Keep CRM and portal auth concerns separated:
  - CRM key: `hlm_access_token`, `authInterceptor`, CRM guard.
  - Portal key: `hlm_portal_token`, `portalInterceptor`, portal guard.
- Angular style is standalone components and lazy feature routes.

## Documentation Conventions
- Update docs/context whenever API, workflow semantics, commands, or CI behavior changes.
- Use `context/*` as compact truth for coding agents; use `docs/*` for broader human guidance.
- Avoid duplicated competing docs; prefer linking to a canonical source when possible.

## Definition of Done (PR Quality)
1. Tenant isolation and RBAC behavior verified for changed paths.
2. Relevant unit and/or integration tests added or updated.
3. Commands in docs remain runnable and consistent with `context/COMMANDS.md`.
4. Documentation updated for any behavior/contract change.
5. No secrets or sensitive tokens introduced in code or docs.

## Naming Conventions
| Type | Convention | Example |
|------|------------|---------|
| Entity | PascalCase noun | `SaleContract` |
| Repository | `*Repository` | `SaleContractRepository` |
| Service | `*Service` | `CommercialDashboardService` |
| Controller | `*Controller` | `PortalAuthController` |
| DTO | `*Request` / `*Response` / `*DTO` | `CreateContactRequest` |
| Integration test | `*IT` | `PortalAuthIT` |
| Unit test | `*Test` | `JwtProviderTest` |
| Error code | SCREAMING_SNAKE_CASE | `PORTAL_TOKEN_INVALID` |
