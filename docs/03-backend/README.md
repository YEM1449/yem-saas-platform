# Backend Developer Guide

Spring Boot 3.5.8 · Java 21 · Maven · PostgreSQL 16

---

## 1. Package Structure

Base package: `com.yem.hlm.backend`

Every module follows the same four-layer split:

| Layer | Sub-package | Contents |
|---|---|---|
| API | `module/api/` | `@RestController` classes + request/response DTOs |
| Domain | `module/domain/` | JPA `@Entity` classes |
| Repository | `module/repo/` | Spring Data JPA `@Repository` interfaces |
| Service | `module/service/` | Business logic `@Service` classes |

### Module Inventory (23 modules)

| Module | Responsibility |
|---|---|
| `audit` | Append-only `commercial_audit_event` log; `AuditEventListener` uses `Propagation.REQUIRES_NEW` |
| `auth` | Login, JWT generation, token revocation, magic-link validation; `AuthService`, `JwtProvider`, `PortalJwtProvider` |
| `commission` | Commission rules (percentage + fixed amount) per societe or project; formula: `agreedPrice × rate/100 + fixedAmount` |
| `common` | Shared utilities: `SocieteContext`, `SocieteContextHelper.requireSocieteId()`, `ErrorResponse`, `ErrorCode`, `StrongPassword` validator |
| `contact` | Contact CRUD; prospect/client detail extensions; GDPR consent fields; `ProspectDetailRepository` |
| `contract` | Sale contracts; one-signed-contract-per-property partial unique index; `@Version` for optimistic locking |
| `dashboard` | KPI aggregates: `CommercialDashboardService`, `ReceivablesDashboardService`; Caffeine/Redis caches |
| `deposit` | Financial deposit records; pessimistic write lock; blocks when ACTIVE reservation exists |
| `document` | Generic multipart attachments linked to any business entity via `entity_type` + `entity_id` |
| `gdpr` | Contact/user anonymization; `DataRetentionScheduler`; anonymization blocked when signed contracts require identity retention |
| `media` | Property-specific media upload/delete; `property_media` table; MinIO/S3 integration |
| `notification` | In-app bell notifications (`notification` table); `recipient_user_id` scoped |
| `outbox` | Transactional outbox pattern; `outbound_message` table; `OutboundDispatcherService.runDispatch()`, `OutboundDispatcherScheduler` |
| `payments` | v2 payment schedule: `payment_schedule_item`, `schedule_payment`, `schedule_item_reminder`; receivables dashboard |
| `portal` | Client portal: `PortalContractService`, `PortalJwtProvider`, magic-link flow; `ROLE_PORTAL` token |
| `project` | Real-estate programs; `name` unique per societe; `ACTIVE`/`ARCHIVED` status |
| `property` | Inventory units; `reference_code` unique per societe; type validation (VILLA/APPARTEMENT); soft delete; RLS enabled |
| `reminder` | Payment call overdue notifications; `payments/service/ReminderService` |
| `reservation` | Short-lived property holds; pessimistic write lock on create; statuses: `ACTIVE`, `EXPIRED`, `CANCELLED`, `CONVERTED_TO_DEPOSIT` |
| `societe` | Company entity management (SUPER_ADMIN only); quota fields; suspension fields |
| `task` | Follow-up tasks with assignee; default list filtered by current user's `assigneeId` |
| `user` | `AppUser` entity; `UserSecurityCacheService` (token version caching); profile update |
| `usermanagement` | Invitation flow; quota enforcement; rate limiting (10 req/h per admin); `AdminUserService`, `InvitationService` |

---

## 2. Adding a New Feature — Step-by-Step

The following numbered steps describe how to add a new domain module from scratch. Replace `<feature>` with your module name (e.g., `valuation`).

### Step 1 — Create Liquibase Changeset

File: `hlm-backend/src/main/resources/db/changelog/053-<feature>.yaml`

Rules:
- Additive only — never modify an existing changeset.
- Include `societe_id UUID NOT NULL`.
- Include FK: `fk_<table>_societe` → `societe(id)`.
- Register the file in `db.changelog-master.yaml`.

```yaml
databaseChangeLog:
  - changeSet:
      id: 053-<feature>
      author: dev
      changes:
        - createTable:
            tableName: <feature>
            columns:
              - column: { name: id, type: UUID, constraints: { primaryKey: true } }
              - column: { name: societe_id, type: UUID, constraints: { nullable: false } }
              - column: { name: created_at, type: TIMESTAMP }
              - column: { name: version, type: BIGINT, defaultValueNumeric: 0 }
        - addForeignKeyConstraint:
            constraintName: fk_<feature>_societe
            baseTableName: <feature>
            baseColumnNames: societe_id
            referencedTableName: societe
            referencedColumnNames: id
```

### Step 2 — Create JPA Entity

```java
@Entity
@Table(name = "<feature>")
public class Feature {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "societe_id", nullable = false)
    private UUID societeId;

    @Version
    private Long version;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() { this.createdAt = Instant.now(); }
}
```

### Step 3 — Create Repository

Every query must include `societeId` as a condition. There are no exceptions.

```java
public interface FeatureRepository extends JpaRepository<Feature, UUID> {

    List<Feature> findBySocieteId(UUID societeId);

    Optional<Feature> findBySocieteIdAndId(UUID societeId, UUID id);

    @Query("SELECT f FROM Feature f WHERE f.societeId = :societeId AND f.status = :status")
    List<Feature> findBySocieteIdAndStatus(UUID societeId, String status);
}
```

### Step 4 — Create Service

`requireSocieteId()` must be the first line of every write and read method.

```java
@Service
@RequiredArgsConstructor
public class FeatureService {

    private final FeatureRepository featureRepository;
    private final SocieteContextHelper societeContextHelper;

    public FeatureResponse create(CreateFeatureRequest request) {
        UUID societeId = societeContextHelper.requireSocieteId();   // MUST be first line
        Feature feature = new Feature();
        feature.setSocieteId(societeId);
        // ... map request fields
        return toResponse(featureRepository.save(feature));
    }

    public List<FeatureResponse> list() {
        UUID societeId = societeContextHelper.requireSocieteId();   // MUST be first line
        return featureRepository.findBySocieteId(societeId)
                .stream().map(this::toResponse).toList();
    }
}
```

### Step 5 — Create DTOs

Use Java records for immutability.

```java
public record CreateFeatureRequest(
    @NotBlank String title,
    @NotNull LocalDate dueDate
) {}

public record FeatureResponse(
    UUID id,
    String title,
    LocalDate dueDate,
    Instant createdAt
) {}
```

### Step 6 — Create Controller

```java
@RestController
@RequestMapping("/api/features")
@RequiredArgsConstructor
@Tag(name = "Feature", description = "Feature management endpoints")
public class FeatureController {

    private final FeatureService featureService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AGENT')")
    public List<FeatureResponse> list() {
        return featureService.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public FeatureResponse create(@Valid @RequestBody CreateFeatureRequest request) {
        return featureService.create(request);
    }
}
```

### Step 7 — Add OpenAPI Tag

Add `@Tag(name = "Feature", description = "...")` on the controller class. All 21 existing controllers already follow this pattern.

### Step 8 — Write Unit Test

File: `src/test/java/.../feature/service/FeatureServiceTest.java` (Surefire, runs via `mvn test`)

```java
@ExtendWith(MockitoExtension.class)
class FeatureServiceTest {

    @Mock FeatureRepository featureRepository;
    @Mock SocieteContextHelper societeContextHelper;
    @InjectMocks FeatureService featureService;

    @Test
    void create_shouldPersistWithSocieteId() {
        UUID societeId = UUID.randomUUID();
        when(societeContextHelper.requireSocieteId()).thenReturn(societeId);
        // ...
    }
}
```

### Step 9 — Write Integration Test

File: `src/test/java/.../feature/api/FeatureIT.java` (Failsafe, runs via `mvn failsafe:integration-test`)

Critical rules:
- **Never** add `@Transactional` to the test class.
- Generate a unique `uid` in `@BeforeEach` and append it to all email addresses.
- Use `adminBearer` for all data setup calls.

```java
@IntegrationTest
class FeatureIT {

    @Autowired MockMvc mvc;
    @Autowired AppUserRepository userRepository;
    @Autowired AppUserSocieteRepository appUserSocieteRepository;

    private String adminBearer;
    private String uid;

    @BeforeEach
    void setUp() {
        uid = UUID.randomUUID().toString().substring(0, 8);
        // create test users with uid-suffixed emails
        // obtain adminBearer via POST /auth/login
    }

    @Test
    void create_shouldReturn201() throws Exception {
        mvc.perform(post("/api/features")
                .header("Authorization", "Bearer " + adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Test","dueDate":"2026-12-31"}"""))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void crossSocieteIsolation_shouldReturn404() throws Exception {
        // Create resource in societe A, try to GET it with societe B token → expect 404
    }
}
```

---

## 3. Multi-Tenant Guard Checklist

Use this checklist when reviewing any new or modified code path.

- [ ] **Service**: call `requireSocieteId()` as the first line of any write/read method
- [ ] **Repository**: `WHERE societe_id = :societeId` on every query
- [ ] **Domain entity**: `societe_id UUID NOT NULL` column
- [ ] **Liquibase**: FK constraint `fk_<table>_societe` referencing `societe(id)`
- [ ] **IT test**: no `@Transactional` on test class; UID-based emails in `@BeforeEach`
- [ ] **IT test for cross-société isolation**: verify société B cannot see société A resources (expect 404)

---

## 4. Testing Strategy

### Unit Tests

- File suffix: `*Test.java`
- Run with: `./mvnw test` (Maven Surefire)
- Use Mockito (`@ExtendWith(MockitoExtension.class)`)
- No Spring context; no database required

### Integration Tests

- File suffix: `*IT.java`
- Run with: `./mvnw failsafe:integration-test` (Maven Failsafe)
- Requires Docker for Testcontainers
- Full Spring context with `@SpringBootTest`

### Full Verify

```bash
cd hlm-backend && ./mvnw verify
```

Runs both Surefire unit tests and Failsafe integration tests in sequence.

### Test Infrastructure

| Component | Purpose |
|---|---|
| `IntegrationTestBase` | Provides Testcontainers PostgreSQL container + `@ActiveProfiles("test")` |
| `@IntegrationTest` | Meta-annotation: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` |
| `application-test.yml` | Provides `JWT_SECRET` test value; disables scheduling |

### WSL2 Docker Socket

If running locally on WSL2 with Docker Desktop:

```bash
export DOCKER_HOST=unix:///mnt/wsl/docker-desktop/shared-sockets/host-services/docker.proxy.sock
```

This is set automatically on `ubuntu-latest` CI runners.

---

## 5. Test Pitfalls (Critical)

### Never use @Transactional on IT test classes

`AuditEventListener` uses `Propagation.REQUIRES_NEW`, which opens a separate database connection. When the test class wraps everything in a single transaction, `AuditEventListener`'s new connection cannot see uncommitted test data and fails with a FK violation (500 instead of the expected 201).

Fix: Remove `@Transactional`. Use unique email UIDs to avoid data collisions instead of relying on rollback.

### Use UID-suffixed emails in @BeforeEach

```java
@BeforeEach
void setUp() {
    uid = UUID.randomUUID().toString().substring(0, 8);
    String email = "testuser+" + uid + "@acme.com";
    // use email for all users created in this test method
}
```

### Use adminBearer for all data setup calls

Always use `adminBearer` when calling setup endpoints (e.g., `POST /api/projects`, `POST /api/properties`). Only use the role under test for the actual operation being tested. For agent-ownership tests where agents cannot create resources via the API, save entities directly via repository.

### IT admin operations require AppUserSociete

`AdminUserService.findUserInSociete()` calls `appUserSocieteRepository.findByIdUserIdAndIdSocieteId(userId, societeId)` and throws `UserNotFoundException` (404) if no membership record exists. Saving a user with `userRepository.save(user)` alone is not sufficient — also call:

```java
appUserSocieteRepository.save(new AppUserSociete(
    new AppUserSocieteId(userId, societeId), "AGENT"
));
```

### ErrorResponse JSON field is "code" not "errorCode"

The `ErrorResponse` record has a field `ErrorCode code`, which serializes to `$.code` in JSON. Use:

```java
.andExpect(jsonPath("$.code").value("QUOTA_UTILISATEURS_ATTEINT"))
// NOT: jsonPath("$.errorCode")
```

---

## 6. RBAC Quick Reference

### Role Storage

`AppUserSociete.role` stores the short form only: `ADMIN`, `MANAGER`, or `AGENT`. The `ROLE_` prefix is never stored in the database. A `CHECK` constraint (`chk_societe_role`) enforces these three values.

### Role Lifecycle

| Operation | Class | What happens |
|---|---|---|
| Saving a role | `AdminUserService.toSocieteRole()` | Strips `ROLE_` prefix before writing to `app_user_societe.role` |
| Generating JWT | `AuthService.toJwtRole()` | Adds `ROLE_` prefix for Spring Security `GrantedAuthority` |

### @PreAuthorize Convention

Use `hasRole('ADMIN')` — Spring Security adds the `ROLE_` prefix automatically.

```java
@PreAuthorize("hasRole('ADMIN')")              // correct
@PreAuthorize("hasRole('ROLE_ADMIN')")         // wrong — never matches
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')") // correct
```

### Assignment Hierarchy

- Only `SUPER_ADMIN` can assign `ADMIN` role.
- Company `ADMIN` can assign `MANAGER` or `AGENT` only.
- Enforced by `SocieteRoleValidator`.

### Wrong prefix consequence

Storing `ROLE_ADMIN` in `app_user_societe.role` violates the `chk_societe_role` CHECK constraint and returns HTTP 500.

---

## 7. Error Handling

### ErrorResponse Record

```java
public record ErrorResponse(ErrorCode code, String message) {}
```

JSON serializes as:
```json
{ "code": "QUOTA_UTILISATEURS_ATTEINT", "message": "..." }
```

The field is `code`, not `errorCode`. All test assertions must use `jsonPath("$.code")`.

### HTTP Status Conventions

| Status | Meaning |
|---|---|
| 400 | Validation error (`@Valid` failure) |
| 401 | Missing or invalid JWT |
| 403 | Authenticated but insufficient role |
| 404 | Resource not found or cross-société access denied |
| 409 | Conflict: duplicate resource or quota exceeded |

### Notable Error Codes

| Code | HTTP | Trigger |
|---|---|---|
| `QUOTA_UTILISATEURS_ATTEINT` | 409 | `QuotaService.enforceUserQuota()` — `maxUtilisateurs` reached |
| `USER_NOT_FOUND` | 404 | `AdminUserService.findUserInSociete()` — no membership |
| `SOCIETE_NOT_FOUND` | 404 | Societe lookup by key or ID fails |

---

## 8. Key Patterns

### Blank-safe property activation

Use `@ConditionalOnExpression` when the property may default to an empty string (e.g., `${EMAIL_HOST:}`). `@ConditionalOnProperty` treats `""` as "present" and would incorrectly activate the bean.

```java
// Correct
@ConditionalOnExpression("!'${app.email.host:}'.isBlank()")
public class SmtpEmailSender implements EmailSender { ... }

// Wrong — activates even when EMAIL_HOST=""
@ConditionalOnProperty(name = "app.email.host")
```

### JPQL null LocalDateTime parameters

PostgreSQL cannot infer the type of a null parameter in `(:param IS NULL OR ...)`. Use `CAST`:

```java
@Query("SELECT t FROM Task t WHERE t.societeId = :societeId " +
       "AND (CAST(:dueBefore AS LocalDateTime) IS NULL OR t.dueDate < :dueBefore)")
List<Task> findBySocieteIdAndOptionalDueDate(UUID societeId, LocalDateTime dueBefore);
```

### ShedLock distributed scheduling

```java
@Scheduled(fixedDelayString = "${app.outbox.polling-interval-ms:5000}")
@SchedulerLock(name = "outbox_dispatcher", lockAtMostFor = "PT1M")
public void poll() { ... }
```

Backed by `JdbcTemplateLockProvider` + `usingDbTime()`. Table created by changeset 052.

### Async context propagation

`SocieteContextTaskDecorator` propagates all 5 ThreadLocal values to `@Async` worker threads:
`societeId`, `userId`, `role`, `superAdmin`, `impersonatedBy`. Wired into `AsyncConfig` via `ThreadPoolTaskExecutor.setTaskDecorator(...)`.

### String.formatted() silent extra-argument drop

`"email: %s".formatted(tenant, email)` silently ignores `email` and uses `tenant` as the value. Count `%s` placeholders carefully — extra arguments are dropped without error.
