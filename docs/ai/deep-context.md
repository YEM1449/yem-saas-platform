# Deep Context (Structured)

## Module map
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

## Key flows

### Login / JWT / TenantContext
- `/auth/login` authenticates tenant+user and returns JWT.
- `JwtProvider` signs HS256 tokens with claims: `sub`, `tid`, `roles`.
- `JwtAuthenticationFilter` validates tokens, sets `TenantContext`, and adds authorities.
- All non-public endpoints require authentication (`SecurityConfig`).

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

## Known gaps / next steps
- Contact interest creation has a TODO to verify property ownership within tenant scope before insert.
