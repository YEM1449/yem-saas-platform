# Testing Guide — Engineer Guide

> Legacy note
>
> Parts of this guide still refer to the older `TenantContext` naming. The current runtime context is
> `SocieteContext`, and the maintained testing expectations are documented in
> [../../context/ARCHITECTURE.md](../../context/ARCHITECTURE.md) and
> [../../spec/requirements-spec.md](../../spec/requirements-spec.md).

This guide covers the unit test and integration test strategies, base classes, test data setup, how to run tests, and common patterns.

## Table of Contents

1. [Test Taxonomy](#test-taxonomy)
2. [Running Tests](#running-tests)
3. [Unit Test Patterns](#unit-test-patterns)
4. [Integration Test Infrastructure](#integration-test-infrastructure)
5. [Integration Test Patterns](#integration-test-patterns)
6. [RBAC Test Pattern](#rbac-test-pattern)
7. [Portal Test Pattern](#portal-test-pattern)
8. [Common Pitfalls](#common-pitfalls)

---

## Test Taxonomy

| Type | Suffix | Plugin | Command |
|------|--------|--------|---------|
| Unit test | `*Test.java` | Maven Surefire | `./mvnw test` |
| Integration test | `*IT.java` | Maven Failsafe | `./mvnw failsafe:integration-test` |

The `*IT` suffix is required for Failsafe to pick up integration tests. Tests named `*Test.java` run under Surefire (unit tests only, no Testcontainers).

---

## Running Tests

### All unit tests

```bash
cd hlm-backend
./mvnw test
```

### All integration tests (requires Docker)

```bash
cd hlm-backend
./mvnw failsafe:integration-test
```

### Both together

```bash
cd hlm-backend
./mvnw verify
```

### Single test class

```bash
./mvnw test -Dtest=ContactServiceTest
./mvnw failsafe:integration-test -Dit.test=ContactControllerIT
```

### Frontend tests

```bash
cd hlm-frontend
npm test -- --watch=false --browsers=ChromeHeadless
```

---

## Unit Test Patterns

Unit tests use JUnit 5 + Mockito. They do not start a Spring context.

### Service Unit Test

```java
@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactRepository contactRepo;

    @Mock
    private OutboxMessageService outboxService;

    @InjectMocks
    private ContactService contactService;

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID   = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setup() {
        TenantContext.set(TENANT_ID, USER_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getContact_notFound_throwsException() {
        when(contactRepo.findByTenantIdAndId(TENANT_ID, UUID.randomUUID()))
            .thenReturn(Optional.empty());

        assertThrows(ContactNotFoundException.class,
            () -> contactService.get(UUID.randomUUID()));
    }
}
```

Always call `TenantContext.set(...)` in `@BeforeEach` and `TenantContext.clear()` in `@AfterEach` in unit tests. Without this, services that call `TenantContext.getTenantId()` will receive `null`.

---

## Integration Test Infrastructure

### Testcontainers

Integration tests use Testcontainers to spin up a real PostgreSQL container. The container is started once per JVM invocation (using `@Container` on a `static` field and `@DynamicPropertySource`) and reused across all IT classes.

### `IntegrationTestBase`

Base class for all integration tests. Provides:
- `adminBearer` — JWT for the seed ADMIN user (`admin@acme.com`)
- `managerBearer` — JWT for a MANAGER user created during setup
- `agentBearer` — JWT for an AGENT user created during setup
- Shared `ObjectMapper`
- Helper methods for common assertions

### `@IntegrationTest` Annotation

Composed annotation applied to all IT classes:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public @interface IntegrationTest {}
```

Usage:

```java
@IntegrationTest
class ContactControllerIT extends IntegrationTestBase { ... }
```

### `application-test.yml`

Active when `@ActiveProfiles("test")` is set. Key settings:
- Provides a hardcoded JWT secret (so tests do not need `JWT_SECRET` in env).
- `spring.task.scheduling.enabled=false` — disables all schedulers to prevent interference.

---

## Integration Test Patterns

### Basic CRUD Test

```java
@IntegrationTest
class ProjectControllerIT extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;

    @Test
    void createProject_asAdmin_returns201() throws Exception {
        String body = """
            {"name": "Résidence Test", "location": "Casablanca"}
            """;

        mockMvc.perform(post("/api/projects")
                .header("Authorization", "Bearer " + adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Résidence Test"));
    }

    @Test
    void listProjects_asAgent_returns200() throws Exception {
        mockMvc.perform(get("/api/projects")
                .header("Authorization", "Bearer " + agentBearer))
            .andExpect(status().isOk());
    }
}
```

### Chained Setup (Create parent then child)

Always use `adminBearer` for all data-setup calls regardless of the role being tested:

```java
@Test
void createProperty_asManager_returns201() throws Exception {
    // Setup: create project as admin
    String projectId = mockMvc.perform(post("/api/projects")
            .header("Authorization", "Bearer " + adminBearer)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name": "Test Project"}"""))
        .andExpect(status().isCreated())
        .andReturn().getResponse()
        // extract id from response...

    // Actual test: create property as manager
    mockMvc.perform(post("/api/properties")
            .header("Authorization", "Bearer " + managerBearer)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"projectId": "%s", "type": "APPARTEMENT", ...}""".formatted(projectId)))
        .andExpect(status().isCreated());
}
```

### Repository Direct Insertion (For agent-ownership tests)

When an AGENT cannot create a resource via API but you need to test that they can READ their own resource:

```java
@Autowired SaleContractRepository contractRepo;
@Autowired TenantRepository tenantRepo;
@Autowired UserRepository userRepo;

@Test
void getContract_asAgentOwnContract_returns200() throws Exception {
    // Agent cannot create contracts via the setup chain, so insert directly
    Tenant tenant = tenantRepo.findById(TENANT_ID).orElseThrow();
    AppUser agent = userRepo.findById(AGENT_USER_ID).orElseThrow();
    SaleContract contract = new SaleContract(tenant, agent, ...);
    contractRepo.save(contract);

    // Now test the agent can read their own contract
    mockMvc.perform(get("/api/contracts/" + contract.getId())
            .header("Authorization", "Bearer " + agentBearer))
        .andExpect(status().isOk());
}
```

---

## RBAC Test Pattern

Test the full permission matrix for each operation:

```java
@Test
void deleteProperty_asAdmin_returns204() throws Exception {
    // Setup
    String id = createProperty(adminBearer);

    // Admin can delete
    mockMvc.perform(delete("/api/properties/" + id)
            .header("Authorization", "Bearer " + adminBearer))
        .andExpect(status().isNoContent());
}

@Test
void deleteProperty_asManager_returns403() throws Exception {
    String id = createProperty(adminBearer);

    // Manager cannot delete
    mockMvc.perform(delete("/api/properties/" + id)
            .header("Authorization", "Bearer " + managerBearer))
        .andExpect(status().isForbidden());
}

@Test
void deleteProperty_asAgent_returns403() throws Exception {
    String id = createProperty(adminBearer);

    mockMvc.perform(delete("/api/properties/" + id)
            .header("Authorization", "Bearer " + agentBearer))
        .andExpect(status().isForbidden());
}

@Test
void deleteProperty_noToken_returns401() throws Exception {
    String id = createProperty(adminBearer);

    mockMvc.perform(delete("/api/properties/" + id))
        .andExpect(status().isUnauthorized());
}
```

---

## Portal Test Pattern

Generate portal bearer tokens directly using `PortalJwtProvider` — no need to go through the magic link flow:

```java
@Autowired PortalJwtProvider portalJwtProvider;

@Test
void getPortalContracts_asContact_returnsOwnOnly() throws Exception {
    UUID contactId = createContactAndContract(adminBearer);
    UUID tenantId  = SEED_TENANT_ID;

    String portalBearer = portalJwtProvider.generate(contactId, tenantId);

    mockMvc.perform(get("/api/portal/contracts")
            .header("Authorization", "Bearer " + portalBearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
}

@Test
void getPortalContracts_asOtherContact_returnsEmpty() throws Exception {
    UUID otherContactId = UUID.randomUUID();
    String portalBearer = portalJwtProvider.generate(otherContactId, SEED_TENANT_ID);

    mockMvc.perform(get("/api/portal/contracts")
            .header("Authorization", "Bearer " + portalBearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
}
```

---

## E2E Tests (Playwright)

E2E tests live in `hlm-frontend/e2e/` and run against the full stack.

### Setup

```bash
# Start the full stack first
docker compose up -d --wait --wait-timeout 180

# Then run Playwright
cd hlm-frontend
npx playwright test
```

`playwright.config.ts` sets `workers: 1` to run tests serially — this prevents login rate-limit races when multiple tests authenticate simultaneously.

### Auth setup

Tests use a `setup` project (`e2e/auth.setup.ts`) that logs in once and saves `storageState` to `playwright/.auth/user.json`. Subsequent test projects load this state and skip the login form.

### data-testid requirement

Every form input and action button that E2E tests interact with needs a `data-testid` attribute. Text-based selectors (`button:has-text("Nouveau")`) break across language changes. Always use `data-testid` as the primary selector.

**Current data-testid map:**

| Surface | Attribute | Element |
|---------|-----------|---------|
| Login | `email` | Email input |
| Login | `password` | Password input |
| Login | `login-button` | Submit button |
| Login | `error-message` | Error banner |
| Shell | `logout-button` | Logout button |
| Contacts | `create-contact` | New contact button |
| Contacts | `firstName` | First name input |
| Contacts | `lastName` | Last name input |
| Contacts | `save-button` | Save button |
| Tasks | `task-title` | Title input |
| Tasks | `task-submit` | Submit button |

### Common E2E pitfalls

- **`button[type="submit"]` fallback is dangerous.** Buttons without `type` default to `submit`, so `button[type="submit"]` matches ALL buttons — Playwright returns the first in DOM order (e.g., "Annuler" before "Créer"). Always use `data-testid`.
- **Seed account**: E2E tests use `admin@acme.com / Admin123!Secure` — not the `admin@demo.ma` demo seeds.

---

## Common Pitfalls

### Scheduler interference

Integration tests disable schedulers via `spring.task.scheduling.enabled=false` in `application-test.yml`. If a test requires scheduler behavior, call the scheduler method directly:

```java
@Autowired ReservationExpiryScheduler expiryScheduler;

@Test
void expiredReservation_getsMarkedExpired() {
    // Setup reservation with past expiresAt
    UUID id = createReservationWithPastExpiry(adminBearer);

    // Invoke scheduler directly
    expiryScheduler.expireStaleReservations();

    // Verify status changed
    mockMvc.perform(get("/api/reservations/" + id)
            .header("Authorization", "Bearer " + adminBearer))
        .andExpect(jsonPath("$.status").value("EXPIRED"));
}
```

### @Transactional on IT test classes

**Never** annotate an IT test class with `@Transactional`. `AuditEventListener` uses `Propagation.REQUIRES_NEW`, which opens a separate DB connection that cannot see uncommitted test-transaction data → FK violation (500 instead of 201/200).

Use unique email UIDs instead of rollback:

```java
@BeforeEach
void setup() {
    String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    userRepository.save(new User("admin-" + uid + "@test.local", "hash"));
}
```

### TenantContext in unit tests

Unit tests that test service methods must set `SocieteContext` in `@BeforeEach` — otherwise `SocieteContextHelper.requireSocieteId()` throws and the service fails. Always call `SocieteContext.clear()` in `@AfterEach`.

### IT test class naming

Integration tests MUST end with `IT` to be picked up by Maven Failsafe. A class named `ContactControllerTest` runs under Surefire without a database; `ContactControllerIT` runs under Failsafe with Testcontainers.

### Outbox dispatcher in IT tests

When testing code that writes to the outbox, the dispatcher is NOT automatically invoked (scheduler disabled). To test delivery end-to-end, inject `OutboundDispatcherService` and call `runDispatch()` directly. Ensure `nextRetryAt` is in the past and call `messageRepo.flush()` before `runDispatch()` so the native `FOR UPDATE SKIP LOCKED` query can find the row.

### Password compliance

All test user passwords must comply with `@StrongPassword` (min 12 chars, upper, lower, digit, special). Compliant examples: `Admin123!Secure`, `TestPass123!`, `CorrectPass123!`.
