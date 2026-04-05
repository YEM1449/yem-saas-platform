# Deep Context — HLM CRM (AI Guide)

> Load `quick-context.md` first, then this file for deeper dives.

---

## Backend Package Structure

Base: `hlm-backend/src/main/java/com/yem/hlm/backend/`

Each module follows this layout:
```
<module>/
  api/          Controllers, request/response DTOs
  domain/       JPA entities
  repo/         Spring Data JPA repositories
  service/      Business logic
```

### Module Inventory (25 modules)

| Module | Key Classes | Notes |
|---|---|---|
| `auth` | `AuthService`, `JwtProvider`, `PortalJwtProvider`, `InvitationService` | Login, JWT cookies, activation, magic-link |
| `user` | `AdminUserController`, `AdminUserService`, `AppUser` | CRM user CRUD; path `/api/users` (NOT `/api/admin/users`) |
| `usermanagement` | `UserManagementController`, `InvitationService` | `/api/mon-espace/utilisateurs`; MANAGER can invite |
| `societe` | `Societe`, `SocieteContext`, `SocieteContextHelper` | Multi-tenant core; ThreadLocal isolation |
| `contact` | `ContactController`, `ContactService`, `Contact`, `ContactStatus` | Status pipeline PROSPECT→COMPLETED_CLIENT |
| `property` | `PropertyController`, `Property`, `PropertyType` | APPARTEMENT/VILLA/STUDIO/COMMERCE; filters by projectId/immeubleId |
| `project` | `ProjectController`, `Project` | Real-estate project; has logo, KPIs |
| `immeuble` | `ImmeubleController`, `Immeuble` | Building within a project; FK to project |
| `reservation` | `ReservationController`, `ReservationService` | ACTIVE/EXPIRED/CANCELLED/CONVERTED; pessimistic lock |
| `deposit` | `DepositController`, `DepositService` | Booking deposit; PDF generation; converts to vente |
| `vente` | `VenteController`, `VenteService`, `Vente`, `VenteStatut` | Sale pipeline + state machine |
| `contract` | `ContractController`, `SaleContract` | Sales contract + payment schedule |
| `payments` | `PaymentScheduleService` | v2 payment tracking (v1 `payment/` deleted) |
| `portal` | `PortalVenteController`, `PortalAuthController` | Buyer read-only endpoints; ROLE_PORTAL |
| `document` | `DocumentController`, `DocumentService` | Cross-entity file attachments to S3/R2 |
| `media` | `MediaService` | Property photo uploads to S3/R2 |
| `dashboard` | `CommercialDashboardController`, `ReceivablesDashboardController` | KPI aggregates |
| `commission` | `CommissionRuleController`, `CommissionRule` | Rate/fixed per project or default |
| `task` | `TaskController`, `Task` | CRM to-do tasks (assignee-scoped by default) |
| `notification` | `NotificationController` | In-app bell notifications |
| `outbox` | `OutboundDispatcherScheduler`, `OutboundMessageRepository` | Async email/SMS via polling |
| `audit` | `AuditEventListener`, `AuditController` | Action log via Spring Events |
| `reminder` | `ReminderService` | Scheduled payment/overdue reminders |
| `gdpr` | `GdprController` | Export + anonymisation |
| `common` | `ErrorResponse`, `ErrorCode`, `SocieteContextHelper` | Shared DTOs and utilities |

---

## Security Architecture

### JWT Flow

```
POST /auth/login
  → AuthService.login()
  → JwtProvider.generate(userId, societeId, role)
  → JWT stored as httpOnly cookie "jwt"
  → SocieteContext cleared after response

Subsequent request:
  → JwtAuthenticationFilter extracts JWT
  → Sets SocieteContext.setSocieteId(sid claim)
  → Sets userId, role in SecurityContext
  → Controller/Service proceeds
  → finally: SocieteContext.clear()
```

### Portal JWT Flow

```
POST /api/portal/auth/request-link { societeKey, email }
  → 32-byte SecureRandom token → URL-safe base64
  → SHA-256 hex stored in portal_token table (TTL 48h)
  → EmailSender.send(magic-link URL) — direct, not outbox

GET /api/portal/auth/verify?token=
  → Verify hash, one-time use mark
  → PortalJwtProvider.generate(contactId, societeId)
  → JWT stored as httpOnly cookie "portal_jwt"
  → sub = contactId, roles = ["ROLE_PORTAL"]

Portal request:
  → JwtAuthenticationFilter detects ROLE_PORTAL
  → Skips UserSecurityCacheService (contactId ≠ userId)
  → Sets contactId as principal
  → /api/portal/** endpoints extract contactId from SecurityContext
```

### Role Hierarchy

| Role | Scope | Access |
|---|---|---|
| SUPER_ADMIN | Platform | `/api/admin/**` only |
| ROLE_ADMIN | Société | Full CRUD within société |
| ROLE_MANAGER | Société | CRU (no delete) |
| ROLE_AGENT | Société | Read + own tasks |
| ROLE_PORTAL | Contact | `/api/portal/**` read-only |

**Important**: `AppUserSociete.role` stores `ADMIN`/`MANAGER`/`AGENT` (no `ROLE_` prefix).  
`AuthService.toJwtRole()` adds the prefix for JWT. `AdminUserService.toSocieteRole()` strips it when saving.

---

## Multi-Société Isolation Rules

Every service method must:

1. Call `requireSocieteId()` as the **first line** of any read/write method
2. Pass `societeId` to every repository query
3. All queries must include `WHERE societe_id = :societeId`

```java
// Correct pattern
public List<Foo> list() {
    UUID sid = requireSocieteId(); // throws if null (unauthenticated)
    return repo.findAllBySocieteId(sid);
}
```

Row-Level Security (changeset 051) provides a defense-in-depth layer but the service layer is the primary control.

---

## Vente State Machine (detail)

```
COMPROMIS  ──→  FINANCEMENT  ──→  ACTE_NOTARIE  ──→  LIVRE (terminal)
    │               │                │
    └───────────────┴────────────────┴──→  ANNULE (terminal)
```

Transitions enforced in `VenteService.validateTransition()`. Invalid → HTTP 409 `INVALID_STATUS_TRANSITION`.

Contact auto-advancement:
- Vente created → contact → `ACTIVE_CLIENT`
- Vente reaches `LIVRE` → contact → `COMPLETED_CLIENT`

---

## Contact Status Pipeline (detail)

```
PROSPECT → QUALIFIED_PROSPECT → CLIENT → ACTIVE_CLIENT → COMPLETED_CLIENT
```

- `POST /api/contacts/{id}/qualify` → PROSPECT
- `PATCH /api/contacts/{id}/status` → manual override (ADMIN/MANAGER only)
- Vente creation auto-sets `ACTIVE_CLIENT`
- Vente `LIVRE` auto-sets `COMPLETED_CLIENT`

---

## Liquibase Changeset Rules

- **Additive only**: no DROP or destructive ALTER without a new changeset
- **Next available**: 059
- **Naming**: `NNN-descriptive-name.yaml` in `hlm-backend/src/main/resources/db/changelog/`
- **RLS changesets**: must also update `RlsContextAspect.java` if adding new tables
- **Required on every new domain table**:
  - `societe_id UUID NOT NULL REFERENCES societe(id)`
  - FK constraint named `fk_<table>_societe`
  - Index on `societe_id`
  - RLS policy (or add to changeset 051)

---

## Frontend Component Patterns

### Standalone Components (Angular 19)
```typescript
@Component({
  selector: 'app-foo',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule],
  templateUrl: './foo.component.html',
  styleUrl: './foo.component.css'
})
```

### Angular Template Restrictions
- **No regex literals**: `/[A-Z]/.test(x)` → move to component getter
- **No arrow functions**: `items.filter(x => x.active)` → use pipe or getter
- **No `?.` on method calls**: `obj?.method()` → use `obj && obj.method()` or ngIf
- **`@if`/`@for`** control flow (Angular 17+) preferred over `*ngIf`/`*ngFor`

### Signal-based state
```typescript
loading = signal(false);
error = signal('');
data = signal<Foo[]>([]);
```

### Lazy-loaded routes
```typescript
{ path: 'foo', loadComponent: () => import('./foo.component').then(m => m.FooComponent) }
```

---

## E2E Test Architecture

### Playwright Projects
```typescript
// playwright.config.ts
projects: [
  { name: 'auth-tests',       testMatch: /auth\.spec\.ts/ },
  { name: 'superadmin-tests', testMatch: /superadmin\.spec\.ts/ },
  { name: 'contact-tests',    testMatch: /contacts\.spec\.ts/ },
  { name: 'task-tests',       testMatch: /tasks\.spec\.ts/ },
  { name: 'activation-tests', testMatch: /activation\.spec\.ts/ },
  { name: 'pipeline-tests',   testMatch: /pipeline\.spec\.ts/ },
  { name: 'portal-tests',     testMatch: /portal\.spec\.ts/ },
]
```

### API calls in E2E tests
```typescript
// Always use API_BASE for page.request calls — in CI, baseURL is port 4200 (Python SPA, GET-only)
const API_BASE = process.env['PLAYWRIGHT_API_BASE'] ?? '';
const resp = await page.request.post(`${API_BASE}/api/ventes`, { data, headers });
```

### Auth helper pattern
```typescript
async function login(page: Page): Promise<string> {
  const resp = await page.request.post(`${API_BASE}/auth/login`, {
    data: { email: 'admin@acme.com', password: 'Admin123!Secure' }
  });
  return ''; // JWT is in httpOnly cookie; bearer not needed for page navigation
}
```

### Portal mocking (no real magic-link available in E2E)
```typescript
await page.route('**/api/portal/tenant-info', route =>
  route.fulfill({ json: { societeName: 'ACME', buyerName: 'Jean Dupont' } })
);
await page.route('**/api/portal/ventes', route =>
  route.fulfill({ json: [{ id: 'v1', statut: 'COMPROMIS', ... }] })
);
```

---

## Backend IT Test Patterns

```java
@IntegrationTest  // = @SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("test")
class FooIT extends IntegrationTestBase {  // Testcontainers Postgres

  private String uid;
  private String adminBearer;

  @BeforeEach
  void setup() {
    uid = UUID.randomUUID().toString().substring(0, 8);
    // Create user + AppUserSociete for login to work
    // Use adminBearer for ALL data setup (not the role under test)
    adminBearer = authenticate("admin@acme.com", "Admin123!Secure");
  }
}
```

Key rules:
- Never `@Transactional` on IT test class
- Use `uid` suffix on all emails to prevent `uk_user_email` collisions
- `AppUserSociete` entry required for any user that needs to log in
- `adminBearer` for setup; role-under-test bearer only for the actual operation

---

## Outbox / Email Architecture

```
Service.doSomething()
  → applicationEventPublisher.publishEvent(new SomethingHappenedEvent(...))
  → @TransactionalEventListener(AFTER_COMMIT)
  → OutboxService.enqueue("EMAIL", to, subject, body)
  → OutboundDispatcherScheduler (every 5s)
  → EmailSender.send(to, subject, body) [SMTP or Noop]
```

**Critical**: All event listeners use `@TransactionalEventListener(TransactionPhase.AFTER_COMMIT)` — never `BEFORE_COMMIT`.

Direct email (no outbox): `PortalAuthService` (magic-link) — no User FK for public endpoint.

---

## Redis Cache Keys

| Cache name | TTL | Used for |
|---|---|---|
| `PROJECTS_CACHE` | 60s | Project list per société |
| `SOCIETES_CACHE` | 120s | Société lookup |
| `commercialDashboard` | 30s | KPI summary |
| `receivablesDashboard` | 30s | Aging buckets |

---

## Common Error Codes

| Code | HTTP | Meaning |
|---|---|---|
| `INVALID_STATUS_TRANSITION` | 409 | Vente state machine violation |
| `QUOTA_UTILISATEURS_ATTEINT` | 409 | Max users reached for société |
| `USER_NOT_FOUND` | 404 | User not in société |
| `INVITATION_RATE_LIMIT` | 429 | > 10 invitations/hour |
| `UNAUTHORIZED` | 401 | JWT missing or invalid |
| `FORBIDDEN` | 403 | Role insufficient |

---

## Infrastructure (Production)

| Component | Value |
|---|---|
| Frontend | Cloudflare Workers: `yem-hlm.youssouf-mehdi.workers.dev` |
| Object storage | Cloudflare R2 EU: `https://<account>.eu.r2.cloudflarestorage.com` |
| Database | PostgreSQL (managed, connection via `DB_URL`) |
| Redis | Managed Redis (connection via `REDIS_URL`) |
| Metrics | OTel → Prometheus via `/actuator/prometheus` |

**R2 EU endpoint**: Must use regional endpoint for EU buckets — global endpoint returns 403.
