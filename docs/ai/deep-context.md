# Deep Context (Structured)

## Module map

### Backend (Spring Boot)
- `hlm-backend/src/main/java/com/yem/hlm/backend/auth`
  - `api/AuthController`, `api/AuthMeController`
  - `security/SecurityConfig`, `JwtAuthenticationFilter`
  - `config/JwtBeansConfig`, `JwtProperties`
  - `service/JwtProvider`, `AuthService`
- `hlm-backend/src/main/java/com/yem/hlm/backend/tenant`
  - `api/TenantController`
  - `context/TenantContext`
  - `service/TenantService`
- `hlm-backend/src/main/java/com/yem/hlm/backend/property`
  - `api/PropertyController`, `PropertyDashboardController`
  - `service/PropertyService`, `PropertyDashboardService`
  - `repo/PropertyRepository`
- `hlm-backend/src/main/java/com/yem/hlm/backend/contact`
  - `api/ContactController`
  - `service/ContactService`
  - `repo/ContactRepository`, `ContactInterestRepository`
- `hlm-backend/src/main/java/com/yem/hlm/backend/deposit`
  - `api/DepositController`
  - `service/DepositService`
  - `repo/DepositRepository`
- `hlm-backend/src/main/java/com/yem/hlm/backend/notification`
  - `api/NotificationController`
  - `service/NotificationService`
  - `repo/NotificationRepository`
- `hlm-backend/src/main/java/com/yem/hlm/backend/common/error`
  - `ErrorResponse`, `ErrorCode`, `GlobalExceptionHandler`

### Frontend (Angular)
- `frontend/src/app/core/auth`
  - `AuthService` (login, logout, token storage)
  - `authInterceptor` (adds Bearer token, logs out on 401)
  - `authGuard` (protects `/app/*` routes)
- `frontend/src/app/features`
  - `login` (login page)
  - `shell` (app shell + navigation)
  - `properties` (properties list view)
- `frontend/src/environments`
  - `environment.ts` (dev config, proxy-friendly)
  - `environment.production.ts` (prod config)
- Proxy: `frontend/proxy.conf.json`

## Key flows

### Login / JWT / TenantContext
- `/auth/login` authenticates tenant+user and returns JWT.
- `JwtProvider` signs HS256 tokens with claims: `sub`, `tid`, `roles`.
- `JwtAuthenticationFilter` validates tokens, sets `TenantContext`, and adds authorities.
- Angular `AuthService` stores `accessToken` in `localStorage`; `authInterceptor` attaches it.

### Property lifecycle
- Create: `PropertyService.create` validates type-specific required fields, ensures unique reference code per tenant.
- Read/list: `PropertyService.getById` and `listAll` query by `tenant_id`, exclude soft-deleted records.
- Update: `PropertyService.update` applies updates and records `updated_by`.
- Delete: `PropertyService.softDelete` sets `deleted_at` (soft delete).
- Dashboard: `PropertyDashboardService` aggregates tenant-scoped stats for `/dashboard/properties/summary`.

### Deposit lifecycle
- `DepositService.create` creates PENDING deposit, validates uniqueness and reservation status, sets due date and reference.
- Confirmation: `confirm` transitions to CONFIRMED, updates contact to CLIENT and creates `client_detail` if missing.
- Cancel/expire: `cancel` or `expireDeposit` resets contact to qualified prospect (unless already CLIENT).
- Notifications emitted on pending/confirmed/cancelled/expired and due-soon.

### Notifications
- `NotificationService.notify` persists tenant+recipient notifications; dedupe is enforced by DB unique index.
- `list` fetches notifications for current tenant/user; `markRead` updates read state.

## Environments & configs
- Backend config: `hlm-backend/src/main/resources/application.yml`.
- Required env vars: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, optional `JWT_TTL_SECONDS`.
- Frontend dev proxy avoids CORS by forwarding `/auth`, `/api`, `/actuator` to `http://localhost:8080`.

## Tests & tooling
- Backend unit tests: `./mvnw test`.
- Backend integration tests: `./mvnw failsafe:integration-test` (requires Docker).
- Frontend: `npm start`, `npm run build`.

## Known gaps / next steps
- Contact interest creation has a TODO to verify property ownership within tenant scope before insert.
