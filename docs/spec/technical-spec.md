# Technical Specification

This specification describes how the platform is implemented and operated.

## 1. Stack

### Backend

- Java 21
- Spring Boot 3.5.11
- Spring Security, Spring Data JPA, validation, AOP, cache, actuator
- Liquibase for schema management
- OpenHTMLToPDF and Thymeleaf for document generation
- Bucket4j for rate limiting
- ShedLock for distributed scheduler coordination

### Frontend

- Angular 19
- TypeScript 5.7
- Three.js 0.165 (lazy-loaded, 3D viewer module only)
- `@ngx-translate` for i18n
- Playwright for E2E tests
- Jasmine / Karma for unit tests

### Data and infrastructure

- PostgreSQL 16
- optional Redis
- optional S3-compatible object storage
- optional SMTP or Brevo HTTP email delivery
- optional Twilio SMS
- optional OTLP tracing export

## 2. Repository Structure

| Path | Purpose |
| --- | --- |
| `hlm-backend/` | Spring Boot app |
| `hlm-frontend/` | Angular app |
| `hlm-backend/src/main/resources/db/changelog/changes/` | Liquibase migrations |
| `nginx/nginx.conf` | reference reverse-proxy config |
| `.github/workflows/` | CI/CD and security workflows |

## 3. Backend Architecture

### Module style

Most modules follow:

```text
api/
domain/
repo/
service/
```

### Cross-cutting components

- `SecurityConfig`
- `JwtAuthenticationFilter`
- `CookieTokenHelper`
- `PortalCookieHelper`
- `SocieteContext`
- `RlsContextAspect`
- `TransactionOrderConfig`
- `AsyncConfig`

## 4. Frontend Architecture

### Route families

- `/login` and `/activation`
- `/app/*`
- `/superadmin/*`
- `/portal/*`

### Session behavior

- final browser sessions rely on backend-issued cookies
- the CRM validates sessions via `/auth/me`
- the portal validates sessions via `/api/portal/tenant-info`
- multi-societe selection is handled after login when the backend returns a partial token

### Internationalization

- supported languages: French, English, Arabic
- language selection is stored locally and also persisted through the backend user profile flow

## 5. Persistence And Schema Management

### PostgreSQL usage

- one shared schema
- `societe_id` on tenant-scoped tables
- row-level security enabled on critical domain tables
- JPA `ddl-auto=validate`

### Migration workflow

- Liquibase changelog master file includes additive changesets
- current migration history runs through changeset `074`
- schema changes must ship through new changesets, never ad-hoc manual edits

## 6. Security Design

### Staff sessions

- staff final session cookie: `hlm_auth`
- partial tokens only for societe switching
- token revocation through `token_version`

### Buyer sessions

- portal cookie: `hlm_portal_auth`
- magic-link storage is hash-based
- portal cookies are path-scoped to `/api/portal`

### Browser protections

- CSP
- frame denial
- no MIME sniffing
- permissions policy
- optional HSTS

## 7. Caching

### Default

- Caffeine cache manager
- user security cache
- dashboard and project caches

### Distributed

- Redis cache manager when `app.redis.enabled=true`
- recommended for multi-instance deployments where session revocation and cache coherence matter

## 8. Async And Scheduler Design

### Schedulers

- outbox dispatch
- deposit and reminder lifecycle jobs
- portal token cleanup
- GDPR retention

### Coordination

- ShedLock uses JDBC-backed locks
- `FOR UPDATE SKIP LOCKED` is used for outbox batch claims

### Async propagation

- `SocieteContextTaskDecorator` copies request scope into worker threads

## 9. File And Document Handling

### Generic documents

- stored via the media storage abstraction
- entity metadata preserved in the database

### Property media

- local filesystem by default
- object storage when enabled

### PDF generation

- Thymeleaf HTML templates
- OpenHTMLToPDF renderer
- inline styles are preferred for reliable rendering

## 10. Configuration Model

Primary configuration comes from:

- `hlm-backend/src/main/resources/application.yml`
- environment variables
- `.env` when using Docker Compose

Important config families:

- database and Liquibase
- JWT and cookie settings
- CORS and forward headers
- Redis
- media and object storage
- email and SMS providers
- portal URLs and retention jobs
- reminder, payments, and outbox scheduler tuning
- OTEL and Prometheus metrics

## 11. Deployment Model

### Local

- Docker Compose runs PostgreSQL, Redis, MinIO, backend, and frontend
- backend can also run standalone with a local profile
- frontend can run with `ng serve`

### Production

- Nginx is the reference TLS termination layer
- Spring Boot typically runs behind the reverse proxy
- secure cookies and exact origins must be configured
- object storage, email, and observability can be swapped per environment

## 12. CI/CD

The repository currently uses GitHub Actions for:

- backend unit and integration tests
- frontend test and build
- end-to-end tests
- Docker image build and push
- secret scanning
- Snyk open-source and container scans

## 13. Testing Strategy

### Backend

- unit tests via Maven Surefire
- integration tests via Failsafe and Testcontainers

### Frontend

- Angular unit tests with Karma/Jasmine
- Playwright E2E against a CI build and a real backend

### Key quality expectation

- auth, tenancy, and workflow transitions are high-risk areas and must be validated with realistic tests

## 14. 3D Visualiser Integration

### Three.js bundle isolation

The entire `viewer-3d` module is lazy-loaded via `loadChildren` so the ~600 kB Three.js dependency stays out of the initial Angular bundle. Draco decoder files are served from `/assets/draco/` (copied from `node_modules/three/examples/jsm/libs/draco/gltf` during build via `angular.json` assets glob).

### Zone isolation

The Three.js render loop runs inside `NgZone.runOutsideAngular()`. Only raycaster hover and click events re-enter the zone via RxJS Subjects. This prevents Angular change detection from running on every animation frame.

### GLB delivery

The backend generates a 15-minute pre-signed `GET` URL using the AWS SDK v2 `S3Presigner`. The frontend fetches the binary directly from object storage; the backend carries no streaming load.

### Mesh-to-lot mapping

The `lot_3d_mapping` table links each `mesh_id` string (matching a name inside the GLB) to a `property_id`. The `LotMappingService` holds the index in memory after scene load. Raycaster lookups resolve in O(1) with no additional HTTP calls.

### Status snapshot and caching

`Project3dService.getStatusSnapshot()` is cached with a 10-second TTL (`LOT_STATUS_3D_CACHE`, 500 entries). The backend reads one row per property in the project using a bulk query then maps status → display string in Java. The frontend polls every 30 seconds and calls `updateColors()` which modifies `material.color` in-place on existing mesh materials without rebuilding the scene.

### Property status to display status mapping

```text
DRAFT, ACTIVE   -> DISPONIBLE
RESERVED        -> RESERVE
SOLD            -> VENDU
WITHDRAWN, ARCHIVED -> LIVRE
```

### Portal isolation

`PortalProject3dController` lives under `/api/portal/**` (ROLE_PORTAL only). It calls `Project3dService.portalUserHasAccess()` which verifies the buyer has an active vente linked to the project via a native SQL join. Failures return 404, not 403.

### PDF export

The `Dashboard3dTabComponent` imports `html2canvas` and `jspdf` via dynamic `import()` to keep them out of the main bundle. The export captures the current `div.viewer-wrapper` DOM node.

## 15. 2D Building View

### Component overview

`BuildingViewComponent` (`features/projects/building-view/`) is a standalone Angular component embedded as a tab in `ProjectDetailComponent`. It receives `projectId` as an `@Input` and manages its own data loading.

### Data loading strategy

```text
forkJoin(
  TrancheService.listByProject(projectId),
  ImmeubleService.list(projectId)
) → filter immeubles by trancheId → PropertyService.list({ immeubleId })
  → groupBy(floorNumber) → sort floors descending
```

Immeubles are filtered client-side against `tranche.id` using the `trancheId` field now exposed by `ImmeubleResponse`. If no immeuble matches a tranche (e.g. data pre-dates changeset 059), all immeubles are shown as a fallback.

### DTO changes (no schema change)

- `ImmeubleResponse` now includes `UUID trancheId` mapped from `Immeuble.getTrancheId()`.
- `PropertyResponse` now includes `String orientation` and `UUID trancheId` mapped from the respective entity getters added in changeset 059.
- Frontend `Immeuble` interface and `Property` interface carry the same new nullable fields.

### Status colour contract

| CSS class | Hex | PropertyStatus |
| --- | --- | --- |
| `.status-active` | `#22c55e` solid | ACTIVE |
| `.status-draft` | beige diagonal hatch | DRAFT |
| `.status-reserved` | `#ea580c` solid | RESERVED |
| `.status-sold` | `#1e293b` solid | SOLD |
| `.status-withdrawn` | grey diagonal hatch | WITHDRAWN, ARCHIVED |

Diagonal hatch is implemented with `repeating-linear-gradient(45deg, …)` in CSS — no image assets required.

### Absorption rate

`Math.round((SOLD + RESERVED) / (total − DRAFT) × 100)` — computed locally from the loaded property array, refreshed on each building selection.

## 16. Technical Constraints

- RLS relies on correct AOP ordering
- schema changes must remain additive and traceable
- any auth change must validate both CRM and portal cookies
- storage adapters must stay vendor-agnostic at the controller level
- documentation is part of the technical deliverable and must remain synchronized with code changes
