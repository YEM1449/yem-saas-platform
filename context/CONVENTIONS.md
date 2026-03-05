# CONVENTIONS.md — Code Conventions

_Updated: 2026-03-04_

## Backend Package Structure (per feature)

```
feature/
  api/                 ← Controller + DTOs
    dto/               ← Request/Response DTOs
  domain/              ← JPA Entities
  repo/                ← JpaRepository / JpaSpecificationExecutor
  service/             ← Business logic
  scheduler/           ← Scheduled tasks (if any)
  config/              ← Feature-specific config beans (if any)
```

## Controller Conventions
- Return DTOs only — never expose JPA entities directly.
- Use `@PreAuthorize` at method level (not class level for mixed auth).
- Use `@Valid` on `@RequestBody` parameters.
- Path: `/api/{feature-plural}` (e.g., `/api/contacts`, `/api/properties`).
- Portal paths: `/api/portal/{feature-plural}`.
- Error: let `GlobalExceptionHandler` handle exceptions — don't catch and re-wrap generically.

## RBAC Annotations (correct patterns)
```java
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
@PreAuthorize("hasRole('PORTAL')")
// NOTE: Spring Security adds ROLE_ prefix — never write hasRole('ROLE_ADMIN')
```

## Service Conventions
- Always read tenant from `TenantContext.getTenantId()` — never from parameters (unless testing).
- Throw domain exceptions (map to `ErrorCode` in `GlobalExceptionHandler`).
- Name: `FeatureService.java` or `FeatureNameService.java`.

## Repository Conventions
- Extend `JpaRepository<Entity, UUID>` (or `JpaSpecificationExecutor`).
- JPQL: use `cast(date as LocalDate)` for Hibernate 6 date grouping.
- For top-N: use `Pageable` param on `@Query`, pass `PageRequest.of(0, N)`.
- `Page<Object[]>` requires `countQuery` attribute on `@Query`.
- Native queries with FOR UPDATE SKIP LOCKED must run inside `@Transactional`.

## Error Handling
```java
// Throw in service:
throw new EntityNotFoundException("Contact not found: " + id);
// → GlobalExceptionHandler maps to ErrorCode.NOT_FOUND → 404

// Don't:
return ResponseEntity.status(404).body("not found");  // bypass envelope
```

## Liquibase Conventions
- Changeset id: sequential number matching file name (e.g., `028`).
- Author: your name or `team`.
- Never set `runOnChange: true` on data changesets.
- Never edit a changeset after it has been applied.
- Add table comments for clarity.

## Test Conventions
```java
// Unit test:
@ExtendWith(MockitoExtension.class)
class FeatureServiceTest { ... }

// IT test:
@IntegrationTest                    // = @SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("test")
class FeatureControllerIT extends IntegrationTestBase { ... }

// JWT in IT:
@Autowired JwtProvider jwtProvider;
String token = jwtProvider.generate(userId, tenantId, UserRole.ROLE_ADMIN);
mockMvc.perform(get("/api/feature").header("Authorization", "Bearer " + token))...

// Portal JWT in IT:
@Autowired PortalJwtProvider portalJwtProvider;
String token = portalJwtProvider.generate(contactId, tenantId);
```

## Frontend Conventions
- Use relative API paths: `/api/...`, `/auth/...` (not `http://localhost:8080/...`).
- Two interceptors: `authInterceptor` (CRM JWT → `/api/`, `/auth/`), `portalInterceptor` (portal JWT → `/api/portal/`).
- Standalone components: no NgModules (Angular 19 style).
- JWT storage: `localStorage` key `hlm_access_token` (CRM), `hlm_portal_token` (portal).
- Route guards: `AuthGuard` (CRM), `PortalGuard` (portal).

## Naming Conventions
| Type | Convention | Example |
|------|-----------|---------|
| Entity | PascalCase noun | `SaleContract`, `CommissionRule` |
| Repository | `*Repository` | `CommissionRuleRepository` |
| Service | `*Service` | `CommercialDashboardService` |
| Controller | `*Controller` | `PortalContractController` |
| DTO | `*Request` / `*Response` / `*DTO` | `CreateContactRequest`, `ContactDTO` |
| IT test | `*IT` | `CommercialDashboardIT` |
| Unit test | `*Test` | `JwtProviderTest` |
| Error code | SCREAMING_SNAKE_CASE | `USER_EMAIL_EXISTS` |
