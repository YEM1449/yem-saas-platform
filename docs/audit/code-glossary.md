# Code Glossary (Ground Truth)

## Backend domain entities
- **Tenant**: `tenant/domain/Tenant.java`, persisted via Liquibase tenant table init (`001-init-tenant-user.yaml`).
- **User** with roles (`ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_AGENT`): `user/domain/User.java`, `user/domain/UserRole.java`, roles migration `008-add-user-roles.yaml`.
- **Contact / Prospect / Client details**: `contact/domain/Contact.java`, `ProspectDetail.java`, `ClientDetail.java`.
- **Property** with lifecycle enum: `property/domain/Property.java`, `PropertyStatus.java`.
- **Deposit** reservation workflow: `deposit/domain/Deposit.java`, `DepositStatus.java`.
- **Notification** center: `notification/domain/Notification.java`, `NotificationType.java`.

## API entry points
- Auth: `/auth/login`, `/auth/me` via `auth/api/AuthController.java`, `AuthMeController.java`.
- Tenant onboarding: `/tenants` via `tenant/api/TenantController.java`.
- Contacts: `/api/contacts...` via `contact/api/ContactController.java`.
- Properties: `/api/properties...` and dashboard `/dashboard/properties/summary` via property controllers.
- Deposits: `/api/deposits...` via `deposit/api/DepositController.java`.
- Notifications: `/api/notifications...` via `notification/api/NotificationController.java`.
- Admin users: `/api/admin/users...` via `user/api/AdminUserController.java`.

## Tenancy/RBAC terminology in code
- JWT claim `tid` extracted in `auth/service/JwtProvider.java` and applied by `auth/security/JwtAuthenticationFilter.java`.
- Thread-bound tenant scope: `tenant/context/TenantContext.java`.
- RBAC checks via `@PreAuthorize` in controllers and `SecurityConfig` defaults.

## Frontend glossary
- Route shell under `/app` with guarded children in `app.routes.ts`.
- Implemented pages: login, properties, contacts, prospects, notifications, admin users.
- Core auth artifacts: `core/auth/auth.service.ts`, `auth.guard.ts`, `auth.interceptor.ts`, `admin.guard.ts`.
