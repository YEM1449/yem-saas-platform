# Module 01 — Multi-Société Isolation

**Audience**: junior to senior engineers
**Updated**: 2026-03-25 — reflects current `societe`-centric implementation (Wave 4)

> Earlier versions of this module used the term `tenant`. The runtime code was migrated to
> `societe` in changeset 033. All concepts below apply to the current codebase.
> Related source of truth: [../context/ARCHITECTURE.md](../context/ARCHITECTURE.md),
> [../context/DATA_MODEL.md](../context/DATA_MODEL.md),
> [../context/SECURITY_BASELINE.md](../context/SECURITY_BASELINE.md)

---

## 1. Introduction

The YEM platform serves multiple real-estate companies simultaneously from a single database.
Each company is called a **société** (`societe`). All domain data lives in shared tables,
and every row carries a `societe_id` column that identifies its owner.

This is the **shared-database, shared-schema** multi-tenancy model — simple to operate,
but requiring strict discipline: every query **must** filter by `societe_id`.

**Why this model?**

| Model | Isolation | Cost | Our choice |
| --- | --- | --- | --- |
| Separate databases per tenant | Strongest | High (N databases to maintain) | No |
| Separate schemas per tenant | Strong | Medium | No |
| Shared schema, per-row column | Weakest (application must enforce) | Low, easy to operate | ✅ Yes |

The low isolation risk is mitigated by three complementary layers (explained in section 6).

---

## 2. Concepts

### 2.1 The Three Core Records

| Record | Table | Purpose |
| --- | --- | --- |
| `AppUser` | `app_user` | Global platform identity (email, password, lockout, token version) |
| `Societe` | `societe` | Company entity (name, key, quotas, branding, compliance) |
| `AppUserSociete` | `app_user_societe` | Membership join: which user belongs to which company, with what role |

**Key insight**: a user can belong to multiple sociétés. The `AppUserSociete` table is the
source of per-company authorization.

### 2.2 The societe_id Column

Every domain entity table has:

```sql
societe_id UUID NOT NULL REFERENCES societe(id)
```

Tables: `contact`, `property`, `project`, `property_reservation`, `sale_contract`,
`deposit`, `commission_rule`, `task`, `document`, `notification`, `property_media`,
`payment_schedule_item`, `schedule_payment`, `schedule_item_reminder`, `outbound_message`

### 2.3 SocieteContext

`SocieteContext` is a ThreadLocal request-scoped store. In Spring's synchronous servlet
model, one thread handles one request, so ThreadLocal gives zero-overhead request-scoped
storage without requiring injection.

It holds five values:

```java
ThreadLocal<UUID>    societeId        // active company for this request
ThreadLocal<UUID>    userId           // authenticated user UUID
ThreadLocal<String>  role             // ROLE_ADMIN / ROLE_MANAGER / ROLE_AGENT
ThreadLocal<Boolean> superAdmin       // true if SUPER_ADMIN (no societe_id)
ThreadLocal<UUID>    impersonatedBy   // set during SUPER_ADMIN impersonation
```

---

## 3. Real Project Mapping

### 3.1 Where SocieteContext Is Set

File: `hlm-backend/src/main/java/com/yem/hlm/backend/auth/security/JwtAuthenticationFilter.java`

```java
// After JWT validation, in doFilterInternal():
SocieteContext.set(societeId, userId, role, isSuperAdmin, impersonatedBy);

// In finally block — always runs, even on exception:
SocieteContext.clear();
```

The `sid` claim in the JWT contains the active societe UUID.
For `SUPER_ADMIN` tokens, `sid` is absent — `societeId` will be `null`.

### 3.2 How Services Use It

File: `hlm-backend/src/main/java/com/yem/hlm/backend/societe/SocieteContextHelper.java`

```java
// Every service write/read method starts with this:
UUID societeId = helper.requireSocieteId();
// Throws SocieteContextException (400) if societeId is null
```

**Why never trust the request body?**
A malicious client could send `"societeId": "another-company-uuid"` in the request body.
By reading only from `SocieteContext` (populated from the cryptographically signed JWT),
cross-société access is structurally impossible at the application layer.

### 3.3 Repository Layer

```java
// Every repository query includes societeId as first parameter:
List<Contact> findBySocieteIdAndStatus(UUID societeId, ContactStatus status);

// NEVER this pattern (missing societe scope):
List<Contact> findByStatus(ContactStatus status); // WRONG — returns all societes
```

---

## 4. Step-by-Step: Request Lifecycle

```
1. Browser → POST /api/contacts  + Authorization: Bearer <jwt>
         ↓
2. JwtAuthenticationFilter
   • Validates JWT signature + expiry
   • Reads "sid" claim → UUID
   • Checks UserSecurityCacheService (token_version, enabled state)
   • Calls SocieteContext.set(societeId, userId, role, ...)
         ↓
3. SecurityConfig route check
   • /api/** → hasAnyRole(ADMIN, MANAGER, AGENT)
         ↓
4. ContactController.create(request)
   • Delegates to ContactService — no societeId in method signature
         ↓
5. TransactionInterceptor opens transaction (order LOWEST_PRECEDENCE-10, outer proxy)
         ↓
6. RlsContextAspect fires @Before (order LOWEST_PRECEDENCE-1, inner proxy)
   • Reads SocieteContext.getSocieteId()
   • Executes: SET LOCAL app.current_societe_id = '<uuid>'
   • Runs INSIDE the open transaction (session variable scoped to TX)
         ↓
7. ContactService.create(request)
   • Calls requireSocieteId() → reads SocieteContext → validates not null
   • Calls contactRepo.save(new Contact(societeId, ...))
         ↓
8. PostgreSQL RLS policy on contact table
   • Checks: societe_id = current_setting('app.current_societe_id')::uuid
   • Enforces at DB level — defense in depth
         ↓
9. Response returned
10. JwtAuthenticationFilter finally block → SocieteContext.clear()
```

---

## 5. Best Practices

| Rule | Reason |
| --- | --- |
| Always call `requireSocieteId()` as the **first line** of a service method | Fail fast before any DB work |
| **Never** read `societeId` from controller parameters | Client-controlled input — cannot be trusted |
| **Never** call `SocieteContext.getSocieteId()` directly — use `requireSocieteId()` via the helper | Null-safety: getSocieteId() can return null and callers might not check |
| All repository queries must have `WHERE societe_id = :societeId` | Primary isolation mechanism |
| IT tests for isolation: create societe B, verify B cannot see A's resources (expect 404) | Prove the constraint actually works |

---

## 6. Common Mistakes

### Mistake 1: Trusting the Request Body

```java
// WRONG — client controls this value
UUID societeId = request.getSocieteId();
return repo.findBySocieteIdAndId(societeId, id);

// CORRECT — always from SocieteContext
UUID societeId = helper.requireSocieteId();
return repo.findBySocieteIdAndId(societeId, id);
```

### Mistake 2: Missing societe_id in Repository Query

```java
// WRONG — returns data from ALL sociétés
@Query("SELECT t FROM Task t WHERE t.assigneeId = :userId")
List<Task> findByAssignee(UUID userId);

// CORRECT — scoped to active societe
@Query("SELECT t FROM Task t WHERE t.societeId = :societeId AND t.assigneeId = :userId")
List<Task> findBySocieteIdAndAssignee(UUID societeId, UUID userId);
```

### Mistake 3: @Transactional on IT Test Class

```java
// WRONG — AuditEventListener uses Propagation.REQUIRES_NEW, opens a
// separate connection that can't see uncommitted test data → FK violation
@Transactional
class ContactServiceIT extends IntegrationTestBase { ... }

// CORRECT — no @Transactional; use UID-based emails for isolation
class ContactServiceIT extends IntegrationTestBase {
    private String uid;
    @BeforeEach void setUp() { uid = UUID.randomUUID().toString().substring(0,8); }
    // use "contact-" + uid + "@test.com" for all test emails
}
```

### Mistake 4: Scheduler Without runAsSystem()

```java
// WRONG — societeId is null for background threads → requireSocieteId() throws
@Scheduled(cron = "0 0 * * * *")
void runMaintenance() {
    contactService.doSomething(); // throws SocieteContextException
}

// CORRECT — nil-UUID sentinel bypasses RLS; pass explicit societeIds
@Scheduled(cron = "0 0 * * * *")
void runMaintenance() {
    List<UUID> societeIds = societeRepo.findAllActiveIds();
    for (UUID id : societeIds) {
        helper.runAsSystem(id, () -> contactService.doSomethingForSociete(id));
    }
}
```

---

## 7. Exercises

**Exercise 1 — Trace the Isolation**
Open `ContactService.java` and find the `create()` method.
1. Confirm it calls `requireSocieteId()` as the first line.
2. Find the `contactRepo.save(...)` call and confirm the entity constructor receives `societeId`.
3. Open the `Contact` entity — confirm `societe_id` is `@Column(nullable=false)`.

**Exercise 2 — Write an Isolation Test**
Write an IT test that:
1. Creates societe A (use `@BeforeEach` + repository direct save).
2. Creates societe B.
3. Creates a contact in societe A with an admin bearer for A.
4. Logs in as an admin from societe B.
5. Calls `GET /api/contacts/{contactAId}` with B's token.
6. Asserts HTTP 404.

**Exercise 3 — Spot the Missing Scope**
Review `TaskRepository.java`. Find any queries that might be missing `societeId`.
Add it and write an IT test proving cross-société access returns empty results.

**Exercise 4 — Add a New Module**
Create a minimal `note` module (a sticky note attached to a contact per societe):
1. Write changeset 053: `note` table with `id`, `societe_id`, `contact_id`, `content`, `created_by`, `created_at`.
2. Create `Note` entity with `societe_id UUID NOT NULL`.
3. Create `NoteRepository.findBySocieteIdAndContactId(UUID, UUID)`.
4. Create `NoteService.create()` — call `requireSocieteId()` first.
5. Write an IT test proving isolation.

---

## 8. Advanced Topics

### 8.1 Nil-UUID System Bypass

The nil-UUID `00000000-0000-0000-0000-000000000000` is a special sentinel in RLS policies:

```sql
CREATE POLICY societe_isolation_contact ON contact
  USING (
    current_setting('app.current_societe_id', true) = '00000000-0000-0000-0000-000000000000'
    OR (
      current_setting('app.current_societe_id', true) IS NOT NULL
      AND current_setting('app.current_societe_id', true) <> ''
      AND societe_id = current_setting('app.current_societe_id', true)::uuid
    )
  );
```

This allows system schedulers (running without a user request) to query across all sociétés.
`SocieteContextHelper.runAsSystem()` temporarily sets the nil-UUID as the societe context.

### 8.2 Multi-Société Users

A user with memberships in multiple sociétés follows this login flow:

```
POST /auth/login → { requiresSocieteSelection: true, societes: [...] }
POST /auth/switch-societe { societeId: "uuid" } → full CRM JWT with sid claim
```

The `sid` claim in the full JWT determines `SocieteContext` for all subsequent requests.

### 8.3 SUPER_ADMIN

`SUPER_ADMIN` tokens have no `sid` claim. They access `GET /api/admin/societes/**` only.
Any attempt to call a CRM endpoint with a SUPER_ADMIN token will fail at `requireSocieteId()`
because the context has no `societeId`.

To act on behalf of a company member, `SUPER_ADMIN` uses:
`POST /api/admin/societes/{id}/impersonate/{userId}` — returns a scoped impersonation token.
