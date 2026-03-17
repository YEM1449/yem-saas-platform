# Backend Deep Dive — Engineer Guide

This guide walks through the Spring Boot backend: package layout, layered architecture, request lifecycle, how to add a new feature, and common extension points.

## Table of Contents

1. [Package Layout](#package-layout)
2. [Layered Architecture](#layered-architecture)
3. [Request Lifecycle](#request-lifecycle)
4. [Domain Module Anatomy](#domain-module-anatomy)
5. [Adding a New Feature](#adding-a-new-feature)
6. [Authentication and Security Internals](#authentication-and-security-internals)
7. [Multi-Tenancy Internals](#multi-tenancy-internals)
8. [Error Handling Internals](#error-handling-internals)
9. [RBAC Enforcement](#rbac-enforcement)
10. [Validation](#validation)
11. [Configuration and Profiles](#configuration-and-profiles)
12. [Testing Patterns](#testing-patterns)

---

## Package Layout

```
hlm-backend/src/main/java/com/yem/hlm/backend/
├── HlmBackendApplication.java        ← Spring Boot entry point
├── audit/                            ← Commercial audit log
├── auth/                             ← JWT, login, rate limiting, lockout
│   ├── api/                          ← AuthController, AuthMeController
│   ├── config/                       ← JwtProperties, CacheConfig, SecurityBeansConfig
│   └── security/                     ← SecurityConfig, JwtAuthenticationFilter, CorsConfig
├── commission/                       ← Commission rules and calculations
├── common/                           ← Cross-cutting: error handling, rate limit, validation
│   ├── error/                        ← ErrorCode, ErrorResponse, GlobalExceptionHandler
│   ├── filter/                       ← RequestCorrelationFilter
│   ├── ratelimit/                    ← RateLimiterService
│   └── validation/                   ← @StrongPassword, StrongPasswordValidator
├── contact/                          ← CRM contacts, timeline, interests
├── contract/                         ← Sale contracts, contract PDF
├── dashboard/                        ← Commercial + receivables dashboard
├── deposit/                          ← Deposit workflow, reservation PDF
├── gdpr/                             ← GDPR export, anonymization, retention
├── media/                            ← Property media upload/download
├── notification/                     ← In-app bell notifications
├── outbox/                           ← Transactional outbox email/SMS
├── payments/                         ← Payment schedule v2
├── portal/                           ← Client portal auth + contract views
├── project/                          ← Real estate projects
├── property/                         ← Property catalogue and media
├── reminder/                         ← Automated payment reminders
├── reservation/                      ← Lightweight property holds
├── tenant/                           ← Tenant entity, TenantContext
└── user/                             ← Admin user management
```

Each domain package follows the same internal layout:
```
{domain}/
├── api/
│   ├── {Domain}Controller.java       ← @RestController
│   └── dto/                          ← Request/Response records
├── domain/
│   └── {Entity}.java                 ← @Entity (JPA)
├── repo/
│   └── {Domain}Repository.java       ← JpaRepository<Entity, UUID>
└── service/
    ├── {Domain}Service.java          ← Business logic
    └── {Domain}Exception.java        ← Domain exceptions
```

---

## Layered Architecture

```
HTTP Request
    │
    ▼
[JwtAuthenticationFilter]        ← Validates JWT, sets TenantContext + SecurityContext
[RequestCorrelationFilter]       ← Sets X-Correlation-ID in MDC
    │
    ▼
[@RestController] (api/)         ← Validates DTO, delegates to service, returns DTO
    │
    ▼
[@Service] (service/)            ← Business logic, reads TenantContext, calls repo
    │
    ▼
[JpaRepository] (repo/)          ← Spring Data JPA; all queries scope to tenantId
    │
    ▼
[PostgreSQL]
```

### Rules

- Controllers MUST NOT contain business logic. They validate the request, call a single service method, and map the result to a response DTO.
- Services MUST read `TenantContext.getTenantId()` and pass it to every repository call.
- Services MUST NOT read tenant or user identity from request parameters or request bodies.
- Repositories MUST NOT be called across domain package boundaries. Cross-domain reads go through service interfaces.

---

## Request Lifecycle

1. Request arrives at Tomcat.
2. `RequestCorrelationFilter` reads or generates `X-Correlation-ID` and places it in MDC.
3. `JwtAuthenticationFilter` extracts `Authorization: Bearer <token>`.
4. `JwtDecoder` (Spring Security) verifies signature and expiry.
5. Filter extracts `tid`, `sub`, `roles`, `tv` claims.
6. For non-portal tokens: `UserSecurityCacheService` loads `UserSecurityInfo` from cache; token version is compared.
7. `TenantContext.set(tenantId, userId)` and `SecurityContextHolder` are populated.
8. Spring Security evaluates the URL pattern rule (permitAll / hasRole).
9. Request reaches the `@RestController`. Bean validation (`@Valid`) runs on `@RequestBody`.
10. Controller calls `@Service` method.
11. Service reads `TenantContext.getTenantId()` and executes business logic.
12. Response is serialized to JSON and returned.
13. `JwtAuthenticationFilter` `finally` block calls `TenantContext.clear()`.

---

## Domain Module Anatomy

### Entity

JPA entity with UUID primary key, tenant FK, and `@PrePersist` / `@PreUpdate` lifecycle hooks for audit timestamps. Example:

```java
@Entity
@Table(name = "project")
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    private String name;
    private LocalDateTime createdAt;

    @PrePersist void onCreate() { this.createdAt = LocalDateTime.now(); }
}
```

### Repository

Extends `JpaRepository<Entity, UUID>`. All queries take `tenantId` as the first parameter:

```java
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByTenantId(UUID tenantId);
    Optional<Project> findByTenantIdAndId(UUID tenantId, UUID id);
}
```

### Service

Reads tenant from context, delegates to repo, throws domain exceptions:

```java
@Service @Transactional
public class ProjectService {
    public ProjectResponse get(UUID id) {
        UUID tenantId = TenantContext.getTenantId();
        return projectRepo.findByTenantIdAndId(tenantId, id)
            .map(this::toResponse)
            .orElseThrow(ProjectNotFoundException::new);
    }
}
```

### Controller

Maps HTTP to service call:

```java
@RestController
@RequestMapping("/api/projects")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
public class ProjectController {

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable UUID id) {
        return projectService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest req) {
        return projectService.create(req);
    }
}
```

---

## Adding a New Feature

1. **Add Liquibase changeset** — `hlm-backend/src/main/resources/db/changelog/` next sequential number. Never edit applied changesets.
2. **Add JPA entity** in `{domain}/domain/`. Add `tenant` FK, UUID PK, audit timestamps.
3. **Add repository** in `{domain}/repo/`. Include `findByTenantIdAndId(...)` at minimum.
4. **Add DTOs** in `{domain}/api/dto/` — Java records for request and response.
5. **Add service** in `{domain}/service/`. Read `TenantContext.getTenantId()` at the top of every method.
6. **Add controller** in `{domain}/api/`. Apply `@PreAuthorize` at class and method level. Return 201 on create.
7. **Add domain exceptions** in `{domain}/service/`. Extend `RuntimeException`. Register mapping in `GlobalExceptionHandler`.
8. **Add `ErrorCode` enum value** in `common/error/ErrorCode.java` for the new exception.
9. **Write unit tests** in `src/test/java/.../service/` using Mockito.
10. **Write integration tests** in `src/test/java/.../api/` extending `IntegrationTestBase` (suffix `IT`).

---

## Authentication and Security Internals

### JwtAuthenticationFilter

Located at `auth/security/JwtAuthenticationFilter.java`. Runs before `UsernamePasswordAuthenticationFilter`.

Key steps:
1. Extract token from `Authorization: Bearer` header.
2. Call `jwtProvider.isValid(token)` — delegates to `JwtDecoder.decode()` which throws on invalid/expired.
3. Extract claims: `tid`, `sub`, `roles`, `tv`.
4. If `ROLE_PORTAL` detected: skip `UserSecurityCacheService`, use contactId as principal.
5. Otherwise: load `UserSecurityInfo` from `userSecurityCache`; compare `tv` claim vs `tokenVersion`; reject if mismatch or `enabled = false`.
6. Set `TenantContext`.
7. Set `SecurityContextHolder` with `JwtAuthenticationToken`.
8. `chain.doFilter(...)`.
9. `finally`: `TenantContext.clear()`.

### UserSecurityCacheService

```java
@Cacheable(value = "userSecurityCache", key = "#userId")
public UserSecurityInfo load(UUID userId)
```

Cache entry: `UserSecurityInfo(tokenVersion, enabled)`. Evicted after 60 s TTL. On mismatch or `enabled = false`, filter rejects with 401.

### JwtProvider

- `generate(AppUser user)` builds a `JwtClaimsSet` with all required claims and calls `jwtEncoder.encode(...)`.
- `isValid(String token)` calls `jwtDecoder.decode(token)` — throws `JwtException` on failure.
- `extractClaims(String token)` returns a `Jwt` object with all claims.

---

## Multi-Tenancy Internals

### TenantContext

```java
public class TenantContext {
    private static final ThreadLocal<UUID> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<UUID> USER_ID = new ThreadLocal<>();

    public static UUID getTenantId() { return TENANT_ID.get(); }
    public static UUID getUserId()   { return USER_ID.get(); }

    public static void set(UUID tenantId, UUID userId) {
        TENANT_ID.set(tenantId);
        USER_ID.set(userId);
    }

    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
    }
}
```

`clear()` is always called in the `finally` block of `JwtAuthenticationFilter` to prevent context leakage between requests in a thread-pool environment.

### Enforcement in Services

Every service method begins:
```java
UUID tenantId = TenantContext.getTenantId();
```
Then every repository call passes `tenantId`:
```java
entity = repo.findByTenantIdAndId(tenantId, entityId)
    .orElseThrow(EntityNotFoundException::new);
```

If a service returns 404 for an entity that belongs to another tenant, the caller cannot distinguish "does not exist" from "belongs to another tenant" — this is by design (information hiding).

---

## Error Handling Internals

### GlobalExceptionHandler

`@RestControllerAdvice` at `common/error/GlobalExceptionHandler.java`. Maps every exception type to a structured `ErrorResponse`.

### ErrorResponse

```json
{
  "timestamp": "2026-03-17T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "code": "CONTACT_NOT_FOUND",
  "message": "Contact not found",
  "path": "/api/contacts/550e..."
}
```

### Adding a New Domain Exception

1. Create `MyException extends RuntimeException` in `{domain}/service/`.
2. Add `MY_ERROR_CODE` to `ErrorCode` enum.
3. Add a handler method to `GlobalExceptionHandler`:

```java
@ExceptionHandler(MyException.class)
@ResponseStatus(HttpStatus.NOT_FOUND)
public ErrorResponse handleMy(MyException ex, HttpServletRequest req) {
    return ErrorResponse.of(HttpStatus.NOT_FOUND, ErrorCode.MY_ERROR_CODE, req);
}
```

---

## RBAC Enforcement

### At the Security Config Level

`SecurityConfig` defines URL patterns. `/api/**` requires `hasAnyRole('ADMIN','MANAGER','AGENT')`. `/api/portal/**` requires `hasRole('PORTAL')`.

### At the Method Level

Each controller method uses `@PreAuthorize`:

```java
// Only ADMIN
@PreAuthorize("hasRole('ADMIN')")

// ADMIN or MANAGER
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")

// Any CRM user (ADMIN, MANAGER, AGENT)
// — no method annotation needed; class-level or URL rule covers this
```

Do NOT use `hasRole('ROLE_ADMIN')` — Spring Security adds the `ROLE_` prefix automatically in `hasRole()`.

### Agent Scoping

Some resources scope to the requesting agent. The service reads `TenantContext.getUserId()` and applies filtering:

```java
UUID agentId = TenantContext.getUserId(); // AGENT sees only their rows
```

For ADMIN/MANAGER, the optional query param `agentId` overrides the filter (e.g., in `CommercialDashboardService.resolveEffectiveAgentId()`).

---

## Validation

### Bean Validation

Request DTOs use JSR-380 annotations: `@NotBlank`, `@NotNull`, `@Size`, `@Email`, etc.

Controllers annotate `@RequestBody` with `@Valid`. Validation failures throw `MethodArgumentNotValidException` which `GlobalExceptionHandler` maps to HTTP 400 with a field-level error list.

### Custom Validators

`@StrongPassword` (on password fields) — backed by `StrongPasswordValidator`:
```
^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z\d]).{12,}$
```

---

## Configuration and Profiles

### `application.yml`

All configuration is environment-variable driven with defaults. Run `grep '\${' application.yml` to see all env var mappings.

### `application-local.yml`

Active via `spring.profiles.active=local`. Provides:
- `JWT_SECRET` dev default (so you don't need it in env).
- CORS origins: `localhost:4200`, `127.0.0.1:4200`, `localhost:5173`, `localhost:3000`.
- `MAIL_HEALTH_ENABLED=false`.

### `application-test.yml`

Active via `@ActiveProfiles("test")` in integration tests. Provides:
- Test JWT secret.
- `spring.task.scheduling.enabled=false` — disables schedulers.

### `JwtProperties`

`@ConfigurationProperties("security.jwt")` + `@Validated` + `@NotBlank` on `secret`. Application fails at startup if `JWT_SECRET` is not set.

---

## Testing Patterns

### Unit Tests

Suffix: `*Test.java`. Run with `./mvnw test`. Use Mockito to mock repositories and services.

```java
@ExtendWith(MockitoExtension.class)
class ContactServiceTest {
    @Mock ContactRepository contactRepo;
    @InjectMocks ContactService contactService;

    @BeforeEach
    void setup() {
        TenantContext.set(TENANT_ID, USER_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }
}
```

### Integration Tests

Suffix: `*IT.java`. Run with `./mvnw failsafe:integration-test`. Annotated with `@IntegrationTest` (composed annotation: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`). Testcontainers spins up a real PostgreSQL container.

```java
@IntegrationTest
class ContactControllerIT extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ContactRepository contactRepo;

    @Test
    void createContact_asAdmin_returns201() throws Exception {
        mockMvc.perform(post("/api/contacts")
                .header("Authorization", "Bearer " + adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fullName":"Alice","email":"alice@test.com"}"""))
            .andExpect(status().isCreated());
    }
}
```

**IT Setup Rule:** Use `adminBearer` for all data-setup calls. Only switch to the role under test for the actual operation being tested.
